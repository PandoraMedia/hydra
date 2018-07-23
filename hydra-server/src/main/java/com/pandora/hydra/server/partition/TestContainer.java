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

import com.pandora.hydra.server.persistence.model.TestTime;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a group of tests that will be run for a given host and a given project
 *
 * @author Justin Guerra
 * @since 10/24/16
 */
public class TestContainer implements Comparable<TestContainer> {

    private final String hostName;
    private final String projectName;

    private final LinkedList<TestTime> testTimes;
    private long time;

    private boolean sorted;

    public TestContainer(String hostName, String projectName) {
        this.testTimes = new LinkedList<>();
        this.hostName = hostName;
        this.projectName = projectName;
    }

    void addTestTime(TestTime testTime) {
        this.time += testTime.getTime();
        testTimes.add(testTime);
        sorted = false;
    }

    TestTime removeFromEnd() {
        sortIfNeeded();

        TestTime testTime = testTimes.pollLast();
        time -= testTime.getTime();
        return testTime;
    }

    TestTime getAndRemoveTestWithMaxRunTimeOf(long diff) {
        sortIfNeeded();

        for (Iterator<TestTime> it = testTimes.iterator(); it.hasNext(); ) {
            TestTime next = it.next();
            if(!next.isFailed() && next.getTime() <= diff) {
                time -= next.getTime();
                it.remove();
                return next;
            }
        }

        return null;
    }

    private void sortIfNeeded() {
        if(sorted) {
            return;
        }

        testTimes.sort(Comparator.comparingLong(TestTime::getTime));
        sorted = true;
    }

    @Override
    public int compareTo(TestContainer o) {
        return Long.compare(getTime(), o.getTime());
    }

    public Set<String> getClasses() {
        return testTimes.stream().map(TestTime::getTestName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @return Returns a Set of test times sorted in descending order of test run times
     */
    public List<TestTime> getTestTimes() {
        return testTimes;
    }

    public long size() {
        return testTimes.size();
    }

    public long getTime() {
        return time;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public String toString() {
        return String.format("TestContainer[hostName=%s, projectName=%s, numberTests=%d, time=%d]", hostName,
                projectName, testTimes.size(), time);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestContainer testContainer = (TestContainer) o;

        if (!hostName.equals(testContainer.hostName)) return false;
        return testTimes.equals(testContainer.testTimes);

    }

    @Override
    public int hashCode() {
        int result = hostName.hashCode();
        result = 31 * result + testTimes.hashCode();
        return result;
    }


}
