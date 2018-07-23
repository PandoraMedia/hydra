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

package com.pandora.hydra.server.persistence;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * @author Justin Guerra
 * @since 9/14/16
 */
public class FileStoreTest {

    @Test
    public void purgeAllOldTests() {

        Multimap<String, TestTime> radioTests = buildFakeTestTimes("radio", 10);

        Instant twoDaysAgo = Clock.systemUTC().instant().minus(2, ChronoUnit.DAYS);
        TestTime oldTest = new TestTime("oldTest", 0, false, null, Timestamp.from(twoDaysAgo));
        radioTests.put("radio", oldTest);

        Map<String, Multimap<String, TestTime>> cache = Maps.newHashMap();
        cache.put("fakeBuild", radioTests);

        FileStore fileStore = new FileStore(cache);
        fileStore.purgeObsoleteTests();

        Set<TestTime> times = new HashSet<>(cache.get("fakeBuild").asMap().get("radio"));

        assertTrue(!times.contains(oldTest));
    }

    private Multimap<String, TestTime> buildFakeTestTimes(String projectName, int numTests) {

        Multimap<String,TestTime> multiMap = HashMultimap.create();

        Instant now = Clock.systemUTC().instant();
        for (int i = 0; i < numTests; ++i) {
            multiMap.put(projectName, new TestTime("fakeTest" + i, 0, false, null, Timestamp.from(now)));
        }

        return multiMap;
    }

}