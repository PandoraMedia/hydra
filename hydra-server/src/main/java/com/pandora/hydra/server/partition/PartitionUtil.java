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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Justin Guerra
 * @since 4/27/18
 */
public class PartitionUtil {

    private static final Logger LOG = Logger.getLogger(PartitionUtil.class);

    /**
     * Set partition approximation algorithm for splitting tests across a group of partitions that ensures
     * test failures are always run on the host they previously failed on
     */
    public static void greedyPartitionFailuresOnSameHost(Collection<TestTime> testTimes, Set<TestContainer> testContainers) {
        Map<String, TestContainer> hostToContainerMap = testContainers.stream()
                .collect(Collectors.toMap(TestContainer::getHostName, Function.identity()));

        List<TestTime> unassignedTests = new ArrayList<>();
        for (TestTime test : testTimes) {
            if (test.isFailed()) {
                if (hostToContainerMap.containsKey(test.getHostName())) {
                    hostToContainerMap.get(test.getHostName()).addTestTime(test);
                } else {
                    unassignedTests.add(test);
                }
            } else {
                unassignedTests.add(test);
            }
        }

        greedyPartition(unassignedTests, testContainers);
    }

    /**
     * Set partition approximation algorithm that evenly distributes tests to test containers
     */
    public static void greedyPartition(Collection<TestTime> testTimes, Set<TestContainer> testContainers) {
        if(testContainers.isEmpty()) {
            throw new IllegalArgumentException("Queue must be populated with a partition for each host");
        }

        List<TestTime> sortedList = testTimes.stream()
                .sorted(Comparator.comparingLong(TestTime::getTime).reversed())
                .collect(Collectors.toList());

        PriorityQueue<TestContainer> queues = new PriorityQueue<>(testContainers);

        greedyPartition(sortedList, queues);
    }

    private static void greedyPartition(List<TestTime> sortedTestTimes, PriorityQueue<TestContainer> testContainers) {

        for (TestTime testTime: sortedTestTimes) {
            final TestContainer testContainer = testContainers.poll();
            testContainer.addTestTime(testTime);
            testContainers.offer(testContainer);
        }

        if(LOG.isDebugEnabled()) {
            testContainers.forEach(p -> LOG.debug(String.format("Host %s has %d test cases for a total runtime of %d ",
                    p.getHostName(), p.getTestTimes().size(), p.getTime())));
        }
    }
}
