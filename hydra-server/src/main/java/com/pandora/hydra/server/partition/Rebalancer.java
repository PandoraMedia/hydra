/*
 * Copyright Pandora Media Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.pandora.hydra.server.partition;

import com.pandora.hydra.server.persistence.model.TestTime;
import org.apache.log4j.Logger;

import java.util.Comparator;
import java.util.LongSummaryStatistics;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Justin Guerra
 * @since 4/27/18
 */
public class Rebalancer {

    private static final Logger LOG = Logger.getLogger(Rebalancer.class);

    /**
     * Creates a new rebalancer that takes a set of test containers that already have had tests distributed to them
     * and attempts to reblance the tests so that test container is within rebalanceThreshold seconds of eachother
     *
     * @param rebalanceThreshold the amount of time in seconds to try and rebalance to
     * @return a new rebalancer that balances test containers based off their runtime
     */
    public static Rebalancer newTimeRebalancer(int rebalanceThreshold) {
        BiFunction<Long, TestContainer, TestTime> pollingFunc = (l, t) -> t.getAndRemoveTestWithMaxRunTimeOf(l);
        return new Rebalancer(TestContainer::getTime, Comparator.naturalOrder(), pollingFunc, rebalanceThreshold);
    }

    /**
     * Creates a new rebalancer that takes a set of test containers that already have had tests distributed to them, and
     * attempts to rebalance the tests by time while keeping the number of tests on each container equal
     * @return a new rebalncer that preserves the size of test containers
     */
    public static Rebalancer newSizeRebalancer() {
        Comparator<TestContainer> minComparator = Comparator.comparingLong(TestContainer::size).thenComparing(TestContainer::getTime);
        BiFunction<Long, TestContainer, TestTime> pollingFunc = (l,t) -> t.removeFromEnd();
        return new Rebalancer(TestContainer::size, minComparator, pollingFunc, 1);
    }

    private final Function<TestContainer, Long> extractor;
    private final Comparator<TestContainer> descendingComparator;
    private final Comparator<TestContainer> ascendingComparator;
    private final BiFunction<Long, TestContainer, TestTime> pollTestTime;
    private final int balanceThreshold;

    Rebalancer(Function<TestContainer, Long> extractor, Comparator<TestContainer> ascendingComparator,
               BiFunction<Long, TestContainer, TestTime> pollTestTime, int balanceThreshold) {
        this.extractor = extractor;
        this.ascendingComparator = ascendingComparator;
        this.descendingComparator = ascendingComparator.reversed();
        this.pollTestTime = pollTestTime;
        this.balanceThreshold = balanceThreshold;
    }

    public void balanceTestContainers(Set<TestContainer> testContainers) {
        if(!isRebalanceNeeded(testContainers)) {
            LOG.info("Partitions are already appropriately sized");
            return;
        }

        PriorityQueue<TestContainer> minQueue = new PriorityQueue<>(ascendingComparator);
        PriorityQueue<TestContainer> maxQueue = new PriorityQueue<>(descendingComparator);

        minQueue.addAll(testContainers);
        maxQueue.addAll(testContainers);

        LOG.info("Pre-balanced");
        testContainers.forEach(test -> LOG.info(test.toString()));

        int step = 0;
        while(isRebalanceNeeded(testContainers)) {
            if(step++ > 1000) {
                LOG.info("Stopping re-balance after 1000 iterations failed to bring the partitions into balance");
                break;
            }

            TestContainer minTestContainer = minQueue.poll();
            TestContainer maxTestContainer = maxQueue.poll();

            long diff = extractor.apply(maxTestContainer) - extractor.apply(minTestContainer);
            TestTime testTime = pollTestTime.apply(diff, maxTestContainer);

            if(testTime == null) {
                LOG.info("No more suitable test classes found for balancing. Stopping balance");
                break;
            }

            minTestContainer.addTestTime(testTime);

            minQueue.add(minTestContainer);
            maxQueue.add(maxTestContainer);
        }

        LOG.info("After-balance");
        testContainers.forEach(test -> LOG.info(test.toString()));
    }

    public boolean isRebalanceNeeded(Set<TestContainer> testContainers) {
        LongSummaryStatistics stats = testContainers.stream()
                .mapToLong(extractor::apply)
                .summaryStatistics();
        return (stats.getMax() - stats.getMin()) > balanceThreshold;
    }
}
