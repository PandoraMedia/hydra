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

package com.pandora.hydra;

import com.pandora.hydra.client.HydraClient;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Justin Guerra
 * @since 1/3/18
 */
class ThreadBalancer {

    static Set<FileVisitDetails> createBalancedOrdering(FileTree files, int maxParallelForks, HydraClient hydraClient) {
        final Map<String, FileVisitDetails> fileList = new HashMap<>();
        files.visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                String transformedName = fileDetails.getRelativePath().getPathString().replaceAll("/", ".");
                fileList.put(transformedName, fileDetails);
            }
        });

        Set<List<String>> partitions = getThreadPartitions(maxParallelForks, hydraClient);
        return createTestOrdering(fileList, partitions);
    }

    /**
     * Takes a Map of FileVisitDetails and a desired partition and merges them in such a way that gradle will run the tests on the desired thread
     *
     * For example, say gradle is configured to run tests across two threads, and partitions look like
     *
     * thread1 = [test1, test2]
     * thread2 = [test3, test4]
     *
     * Then this method will return a set that looks like
     *
     * [test1_fileVisitDetails, test3_fileVisitDetails, test2_fileVisitDetails, test4_fileVisitDetails].
     *
     * Because gradle uses a simple round robin approach to distribute tests to worker threads this will result in
     * thread1 getting test1 and test2, and thread2 getting test3 and test4
     *
     * @param files - Map of file names to file visit details
     * @param partitions - Desired thread partitioning
     * @return - A sorted set of tests that when distributed to threads in a round robin order will result in each thread running
     * the tests associated with its partition
     */
    private static Set<FileVisitDetails> createTestOrdering(Map<String, FileVisitDetails> files, Set<List<String>> partitions) {
        if(partitions.size() <= 1) {
            return new HashSet<>(files.values());
        }

        Set<FileVisitDetails> newOrder = new LinkedHashSet<>();
        Deque<Deque<String>> copiedPartitions = copy(partitions);
        while(!copiedPartitions.isEmpty()) {
            for(Iterator<Deque<String>> it =  copiedPartitions.iterator(); it.hasNext(); ) {
                Deque<String> copiedPartition = it.next();
                String test = copiedPartition.pollFirst();
                FileVisitDetails details = files.get(test);
                if (details != null) {
                    newOrder.add(details);
                    files.remove(test);
                }

                if(copiedPartition.isEmpty()) {
                    it.remove();
                }
            }
        }
        if(!files.isEmpty()) {
            newOrder.addAll(files.values());
        }

        return newOrder;
    }

    private static Set<List<String>> getThreadPartitions(int maxThreads, HydraClient client) {
        Set<List<String>> partitions;
        try {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int testThreads = maxThreads <= availableProcessors ? maxThreads : availableProcessors;
            partitions = client.getThreadPartitions(testThreads);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to retrieve partitions", e);
        }
        return partitions;
    }

    private static Deque<Deque<String>> copy(Set<List<String>> threadPartitions) {
        Deque<Deque<String>> copy = new LinkedList<>();
        threadPartitions.forEach(l -> copy.add(new LinkedList<>(l)));
        return copy;
    }
}
