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

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains all partitions for a test run
 *
 * @author Justin Guerra
 * @since 4/19/18
 */
public class TestRun {

    private final Set<Partition> partitions;

    public TestRun(Collection<Partition> partitions) {
        this.partitions = ImmutableSet.copyOf(partitions);
    }

    public Set<String> getPartitionNames() {
        return partitions.stream().map(Partition::getHostName).collect(Collectors.toSet());
    }

    public Set<Partition> getPartitions() {
        return partitions;
    }

    public Partition getPartitionByName(String partitionName) {
        return partitions.stream().filter(p -> p.getHostName().equals(partitionName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("No partition with name " + partitionName));
    }
}
