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

package com.pandora.hydra.server.configuration;

import com.pandora.hydra.server.partition.HostAffinityPartitionStrategy;
import com.pandora.hydra.server.partition.PartitionRequest;
import com.pandora.hydra.server.partition.PartitionUtil;
import com.pandora.hydra.server.partition.PartitioningStrategy;
import com.pandora.hydra.server.partition.TestContainer;
import com.pandora.hydra.server.partition.TestRunCache;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Justin Guerra
 * @since 10/25/16
 */
@Configuration
public class HydraConfiguration {

    @Value("${hydra.cache.ttl:15}")
    private long cacheTtl;

    @Bean
    public TestRunCache getTestRunCache() {
        return new TestRunCache(cacheTtl, TimeUnit.MINUTES);
    }

    @Bean
    @ConditionalOnProperty(name = "hydra.partition.strategy", havingValue = "affinity")
    public PartitioningStrategy getHostAffinityStrategy() {
        return new HostAffinityPartitionStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "hydra.partition.strategy", havingValue = "greedy_with_failures")
    public PartitioningStrategy getGreedyStrategyWithFailuresOnSameHost() {
        return (PartitionRequest r, Collection<TestTime> t, Set<TestContainer> c) -> PartitionUtil.greedyPartitionFailuresOnSameHost(t, c);
    }

    @Bean
    @ConditionalOnProperty(name = "hydra.partition.strategy", havingValue = "greedy")
    public PartitioningStrategy getGreedyStrategy() {
        return (PartitionRequest r, Collection<TestTime> t, Set<TestContainer> c) -> PartitionUtil.greedyPartition(t, c);
    }
}
