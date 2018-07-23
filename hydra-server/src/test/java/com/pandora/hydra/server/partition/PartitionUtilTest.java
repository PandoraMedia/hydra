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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * @author Justin Guerra
 * @since 4/27/18
 */
public class PartitionUtilTest {

    @Test
    public void greedyPartitionFailuresOnSameHost() {
        Collection<TestTime> host1Tests = generateTests(10, "host1");
        host1Tests.forEach(t -> t.setFailed(true));

        Collection<TestTime> host2Tests = generateTests(10, "host2");
        host2Tests.forEach(t -> t.setFailed(true));

        Set<TestContainer> containers = createTestContainers(2);

        List<TestTime> allTests = new ArrayList<>();
        allTests.addAll(host1Tests);
        allTests.addAll(host2Tests);

        PartitionUtil.greedyPartitionFailuresOnSameHost(allTests, containers);

        for (TestContainer container : containers) {
            Collection<TestTime> expected = container.getHostName().equals("host1") ? host1Tests : host2Tests;
            assertEquals(expected, new HashSet<>(container.getTestTimes()));
        }
    }

    @Test
    public void greedyPartitionSameTimes() {
        Collection<TestTime> testData = generateTests(10);

        Set<TestContainer> containers = createTestContainers(2);

        PartitionUtil.greedyPartition(testData, containers);
        containers.forEach(c -> assertEquals(5, c.getTestTimes().size()));
    }

    @Test
    public void greedyPartitionDifferentTimes() {
        Timestamp now = Timestamp.from(Clock.systemUTC().instant());
        TestTime test1 = new TestTime("test1", 10, false, "host1", now);
        TestTime test2 = new TestTime("test2", 5, false, "host1", now);
        TestTime test3 = new TestTime("test3", 1, false, "host1", now);
        TestTime test4 = new TestTime("test4", 2, false, "host1", now);

        Collection<TestTime> testTimes = Lists.newArrayList(test1, test2, test3, test4);
        Set<TestContainer> testContainers = createTestContainers(2);

        PartitionUtil.greedyPartition(testTimes, testContainers);

        Iterator<TestContainer> iterator = testContainers.iterator();
        TestContainer first = iterator.next();
        TestContainer second = iterator.next();

        assertEquals(18, first.getTime() + second.getTime());
        assertEquals(4, first.getTestTimes().size() + second.getTestTimes().size());

        long longerTime = Math.max(first.getTime(), second.getTime());
        long shorterTime = Math.min(first.getTime(), second.getTime());

        assertEquals(10, longerTime);
        assertEquals(8, shorterTime);
    }

    private Set<TestContainer> createTestContainers(int num) {
        return IntStream.rangeClosed(1, num)
                .mapToObj(i -> new TestContainer("host" + i, "whatever"))
                .collect(Collectors.toSet());
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