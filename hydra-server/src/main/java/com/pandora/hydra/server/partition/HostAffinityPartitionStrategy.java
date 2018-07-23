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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PartitioningStrategy that splits tests into equally sized (by time) chunks of tests while trying to preserve a test suites
 * affinity for a host that is was previously run on. This is the best strategy to use when you have a consistent group of hosts
 * running tests. Over time this strategy will resize partitions, allowing similar runtimes even across heterogeneous hardware
 *
 * If a test is run in distributed mode for the first time tests are split using a approximate set-partition algorithm that creates a group of test
 * runs that will finish in roughly the same time. On subsequent runs test suites are assigned to the host they previously ran on. We then
 * check if the test runs finished in roughly the same time (Within a configurable threshold). If test runs did not finish in
 * roughly the same amount of time then some tests will be rebalanced - faster hosts get more tests from slower hosts. This process will continue
 * until tests runs across hosts finish in about the same amount of time. The re-balancing algorithm does not necessarily generate the most optimal solution
 * (especially when there is a limited number of tests), however, when there is a large number of tests. It can generate sets that very similar in size. Generally within a couple
 * test runs all hosts should be well balanced
 *
 * If a new host is added to an existing run then a full greedy balance is performed instead of trying to re-balance.
 *
 * When a new test is being run for the first time the partitioner has no way of knowing it exists, and cannot isolate it
 * to a single host. In this case the test is run across all hosts in its first run, on subsequent runs it will only be run on a single host.
 *
 * @author Justin Guerra
 * @since 10/24/16
 */
public class HostAffinityPartitionStrategy implements PartitioningStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(HostAffinityPartitionStrategy.class);

    @Value("${hydra.rebalance_threshold}")
    private int rebalanceThreshold;

    @Override
    public void distributeTestTestTimes(PartitionRequest request, Collection<TestTime> testTimes, Set<TestContainer> testContainers) {

        if(isNewHostDetected(request.getHostList(), testContainers)) {
            LOG.info("New host detected. Performing a full greedy partition");
            PartitionUtil.greedyPartitionFailuresOnSameHost(testTimes, testContainers);
        } else {
            List<TestTime> testsWithNoHostAffinity = assignTestsWithHostAffinities(testTimes, testContainers);
            PartitionUtil.greedyPartitionFailuresOnSameHost(testsWithNoHostAffinity, testContainers);

            Rebalancer rebalancer = Rebalancer.newTimeRebalancer(rebalanceThreshold);
            if (rebalancer.isRebalanceNeeded(testContainers)) {
                rebalancer.balanceTestContainers(testContainers);
            }
        }
    }

    private List<TestTime> assignTestsWithHostAffinities(Collection<TestTime> testTimes, Set<TestContainer> testContainers) {
        List<TestTime> noAffinity = new ArrayList<>();
        Map<String, TestContainer> hostToTestContainer = testContainers.stream().collect(Collectors.toMap(TestContainer::getHostName, Function.identity()));
        for(TestTime test : testTimes) {
            if (test.getHostName() != null && hostToTestContainer.containsKey(test.getHostName())) {
                TestContainer container = hostToTestContainer.get(test.getHostName());
                container.addTestTime(test);
            } else {
                noAffinity.add(test);
            }
        }
        return noAffinity;
    }

    private boolean isNewHostDetected(Set<String> hostList, Set<TestContainer> containers) {
        Set<String> savedHosts = containers.stream().map(TestContainer::getHostName).collect(Collectors.toSet());
        return !hostList.equals(savedHosts);
    }
}
