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

import com.google.common.util.concurrent.Striped;
import com.pandora.hydra.server.persistence.TestStore;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * @author Justin Guerra
 * @since 3/16/18
 */
@Service
public class PartitionService implements Partitioner {
    private static final Logger LOG = Logger.getLogger(PartitionService.class);

    private final TestStore testStore;
    private final TestRunCache cache;
    private final PartitioningStrategy strategy;

    private final Striped<Lock> striped;

    @Autowired
    protected PartitionService(TestStore testStore, TestRunCache cache, PartitioningStrategy strategy) {
        this.testStore = testStore;
        this.cache = cache;
        this.strategy = strategy;
        this.striped = Striped.lazyWeakLock(Runtime.getRuntime().availableProcessors() * 4);
    }

    @Override
    public Set<String> getTestBlacklist(PartitionRequest partitionRequest) {
        TestRun testRun = getOrComputeTestRun(partitionRequest);
        return buildTestBlacklist(testRun, partitionRequest.getHostName());
    }

    @Override
    public Set<Set<String>> getThreadGrouping(PartitionRequest request, int numThreads) {
        TestRun testRun = getOrComputeTestRun(request);
        Partition partition = testRun.getPartitionByName(request.getHostName());

        if(partition.getAllProjectNames().size() > 1) {
            LOG.warn("Thread balancing is not supported with multi project builds");
        }

        TestContainer testContainerForProject = collapsePartition(partition);

        Set<TestContainer> fakeTestContainers = new HashSet<>();
        for (int i = 0; i < numThreads; ++i) {
            fakeTestContainers.add(new TestContainer("thread" + i, request.getBuildName()));
        }

        PartitionUtil.greedyPartition(testContainerForProject.getTestTimes(), fakeTestContainers);

        Rebalancer rebalancer = Rebalancer.newSizeRebalancer();
        if (rebalancer.isRebalanceNeeded(fakeTestContainers) && canRebalance(fakeTestContainers)) {
            rebalancer.balanceTestContainers(fakeTestContainers);
        }

        Set<Set<String>> threadSplits = new LinkedHashSet<>();
        for (TestContainer fakeTestContainer : fakeTestContainers) {
            threadSplits.add(new LinkedHashSet<>(fakeTestContainer.getClasses()));
        }

        return threadSplits;

    }

    private TestRun getOrComputeTestRun(PartitionRequest request) {
        return cache.getCachedTestRun(request)
                .orElseGet(() -> computeTestRun(request));
    }

    private TestRun computeTestRun(PartitionRequest partitionRequest) {
        Lock lock = striped.get(cache.getCacheKey(partitionRequest));
        lock.lock();
        try {
            Optional<TestRun> cachedTestRun = cache.getCachedTestRun(partitionRequest);
            if(cachedTestRun.isPresent()) {
                return cachedTestRun.get();
            }

            Map<String, Collection<TestTime>> projectToTestTime = testStore.getTestTimes(partitionRequest.getBuildName());
            Set<Partition> partitions = createPartitionsFromHostList(partitionRequest.getHostList());
            for (Map.Entry<String, Collection<TestTime>> projects : projectToTestTime.entrySet()) {
                String currentProject = projects.getKey();
                Set<TestContainer> testContainers = partitions.stream()
                        .map(p -> p.getTestContainerForProject(currentProject))
                        .collect(Collectors.toSet());
                LOG.info("Calculating partitions for project " + currentProject);
                strategy.distributeTestTestTimes(partitionRequest, projects.getValue(), testContainers);

                LOG.info("Expected runtimes for " + currentProject + ":");
                testContainers.forEach(test -> LOG.info(test.toString()));
            }

            TestRun testRun = new TestRun(partitions);
            cache.cacheTestRun(partitionRequest, testRun);
            return testRun;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param partition - partition to collapse
     * @return a TestContainer that contains all tests, regardless of project
     */
    private TestContainer collapsePartition(Partition partition) {
        TestContainer testContainer = new TestContainer("notAHost", "collapsed");

        partition.getAllProjectNames()
                .stream()
                .map(partition::getTestContainerForProject)
                .map(TestContainer::getTestTimes)
                .flatMap(Collection::stream)
                .forEach(testContainer::addTestTime);

        return testContainer;
    }

    private Set<Partition> createPartitionsFromHostList(Set<String> hostList) {
        return hostList.stream().map(Partition::new).collect(Collectors.toSet());
    }


    /**
     * Generates a set of test names that forHost should not run, i.e. every test that is not in forHost's Partition
     *
     * @param testRun - the test run source
     * @param forHost - the host to generate the excludes set
     * @return - set of test names that shouldn't be run by forHost
     */
    private Set<String> buildTestBlacklist(TestRun testRun, String forHost) {
        return testRun.getPartitions().stream()
                .filter(entry -> !entry.getHostName().equals(forHost))
                .map(Partition::getAllTestNames)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }



    private boolean canRebalance(Set<TestContainer> testContainers) {
        int totalNumberTests = testContainers.stream().mapToInt(p -> p.getClasses().size()).sum();
        if(totalNumberTests < testContainers.size()) {
            LOG.info("No need to rebalance by size because there are are fewer tests than partitions");
            return true;
        }
        return false;
    }
}
