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

import java.util.Set;

/**
 * @author Justin Guerra
 * @since 8/16/16
 */
public interface Partitioner {

    /**
     * Returns a collection of test names that a given host (defined by the partitionRequest) should not run. The blacklist
     * can be used to calculate a group of tests that should be run (all tests - blacklisted tests = tests to run)
     *
     * @param partitionRequest
     * @return a set of class names that a host should not run
     */
    Set<String> getTestBlacklist(PartitionRequest partitionRequest);

    /**
     * Calculates an optimal test balancing at the thread level. Long running tests are spread across each thread
     * so that we can ensure that threads will finish at roughly the same time. Note: this only works when using the gradle
     * plugin with single project builds
     *
     * @param request
     * @param numThreads number of threads that the host will run the test across
     * @return a set containing numThreads members, each of which is a collection of tests that should be run on a specific thread
     */
    Set<Set<String>> getThreadGrouping(PartitionRequest request, int numThreads);
}
