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

import com.google.common.collect.Iterables;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Justin Guerra
 * @since 4/30/18
 */
@RunWith(MockitoJUnitRunner.class)
public class PersistenceUtilTest {

    @Mock
    private TestTime testTime;

    private Clock clock = Clock.fixed(Instant.ofEpochMilli(1521498000000L), ZoneId.systemDefault());

    @Test
    public void testOlderThanOneDay() {
        Instant instant = clock.instant();

        Instant oneDayAgo = instant.minus(1, ChronoUnit.DAYS);
        testTimeLastUpdatedAt(oneDayAgo);

        assertTrue(PersistenceUtil.isTestOlderThanAverage(instant, testTime));
    }

    @Test
    public void testFromSameDay() {
        Instant instant = clock.instant();
        testTimeLastUpdatedAt(instant);
        assertFalse(PersistenceUtil.isTestOlderThanAverage(instant, testTime));
    }

    @Test
    public void missingLastUpdated() {
        //null timestamps should be deleted
        assertTrue(PersistenceUtil.isTestOlderThanAverage(clock.instant(), testTime));
    }

    @Test
    public void findObsoleteTests() {
        Instant instant = clock.instant();
        List<TestTime> tests = new ArrayList<>();
        for(int i = 0; i < 10; ++i) {
            TestTime mock = mock(TestTime.class);
            doReturn(Timestamp.from(instant)).when(mock).getLastUpdated();
            tests.add(mock);
        }

        Instant oldInstant = instant.minus(5, ChronoUnit.DAYS);
        testTimeLastUpdatedAt(oldInstant);

        tests.add(testTime);

        Set<TestTime> obsoleteTests = PersistenceUtil.findObsoleteTests(tests);
        assertEquals(1, obsoleteTests.size());
        assertEquals(testTime, Iterables.getFirst(obsoleteTests, null));
    }

    @Test
    public void findObsoleteTestsNothingObsolete() {
        Instant instant = clock.instant();
        List<TestTime> tests = new ArrayList<>();
        for(int i = 0; i < 10; ++i) {
            TestTime mock = mock(TestTime.class);
            doReturn(Timestamp.from(instant)).when(mock).getLastUpdated();
            tests.add(mock);
        }

        Set<TestTime> obsoleteTests = PersistenceUtil.findObsoleteTests(tests);
        assertEquals(0, obsoleteTests.size());
    }

    private void testTimeLastUpdatedAt(Instant lastUpdated) {
        Timestamp timestamp = Timestamp.from(lastUpdated);
        doReturn(timestamp).when(testTime).getLastUpdated();
    }

}