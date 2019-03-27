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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Maintains a cache of partitions for a short amount of time. This is to ensure that for a given test run
 * each host retrieves a consistent (with other hosts) collection of tests to run
 *
 * @author Justin Guerra
 * @since 10/25/16
 */
public class TestRunCache {
    private static final Logger LOG = Logger.getLogger(TestRunCache.class);

    private final Cache<String, TestRun> cache;

    public TestRunCache(long ttl, TimeUnit timeUnit) {
        this.cache = CacheBuilder.newBuilder().expireAfterAccess(ttl, timeUnit).build();
    }

    public Optional<TestRun> getCachedTestRun(PartitionRequest request) {
        String cacheKey = getCacheKey(request);
        TestRun testRun = cache.getIfPresent(cacheKey);
        if (testRun == null) {
            return Optional.empty();
        } else {
            Set<String> partitionNames = testRun.getPartitionNames();
            Set<String> hostList = request.getHostList();

            if(!partitionNames.equals(hostList)) {
                LOG.info("Partitions changed since last run, invalidating cache");
                cache.invalidate(cacheKey);
                return Optional.empty();
            }

            return Optional.of(testRun);
        }
    }

    public void cacheTestRun(PartitionRequest request, TestRun toCache) {
        cache.put(getCacheKey(request), toCache);
    }

    String getCacheKey(PartitionRequest request) {
        if(StringUtils.isEmpty(request.getBuildTag())) {
            LOG.debug("No build tag included in request. Falling back on build name");
            return request.getBuildName();
        }

        return request.getBuildTag();
    }

}
