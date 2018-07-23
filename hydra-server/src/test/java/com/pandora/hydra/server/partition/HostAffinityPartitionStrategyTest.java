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

import com.google.common.collect.Sets;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author Justin Guerra
 * @since 10/25/16
 */
@RunWith(MockitoJUnitRunner.class)
public class HostAffinityPartitionStrategyTest {

    private HostAffinityPartitionStrategy partitioner;

    @Before
    public void setup() {
        partitioner = new HostAffinityPartitionStrategy();
    }

    @Test
    public void partitionWithHostNames() {

        String[] hostNames = {"host1", "host2"};
        Collection<TestTime> testData = generateTests(10, hostNames);

        Set<String> hostSet = Stream.of(hostNames).collect(Collectors.toSet());
        Set<TestContainer> containers = hostSet.stream().map(h -> new TestContainer(h, "whatever")).collect(Collectors.toSet());

        partitioner.distributeTestTestTimes(new PartitionRequest("host1", "whatever", hostSet, "tag"), testData, containers);

        containers.forEach(c -> assertEquals(5, c.getTestTimes().size()));
    }

    @Test
    public void partitionWithoutHostNames() {

        Collection<TestTime> testData = generateTests(10);
        Set<String> hostSet = Sets.newHashSet("host1", "host2");

        Set<TestContainer> containers = hostSet.stream().map(h -> new TestContainer(h, "whatever")).collect(Collectors.toSet());

        partitioner.distributeTestTestTimes(new PartitionRequest("host1", "whatever", hostSet, "tag"), testData, containers);

        containers.forEach(c -> assertEquals(5, c.getTestTimes().size()));
    }


    private Collection<TestTime> generateTests(int numTests, String... hostNames) {

        Set<TestTime> fakeTests = Sets.newHashSet();
        Timestamp now = Timestamp.from(Clock.systemUTC().instant());
        for(int i = 0; i < numTests; ++i) {
            String host = hostNames.length == 0 ? null : hostNames[i%hostNames.length];
            fakeTests.add(new TestTime("test" + i, 1,false,host, now));
        }

        return fakeTests;
    }
}