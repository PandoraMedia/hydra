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

package com.pandora.hydra.server.persistence.model;

import com.pandora.hydra.common.TestSuite;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

import static javax.persistence.GenerationType.SEQUENCE;

/**
 * Represents the time it takes to run a test suite.
 *
 *
 * @author Justin Guerra
 * @since 9/2/16
 */
@Entity
@Table(name = "test")
public class TestTime {

    @Id
    @SequenceGenerator(name="test_id_seq", sequenceName="test_id_seq")
    @GeneratedValue(strategy=SEQUENCE, generator="test_id_seq")
    private long id;

    /**
     * Name of the test suite
     */
    @Column(name = "name")
    private String testName;

    /**
     * The time it takes to run the test suite
     */
    @Column(name = "time")
    private long time;

    /**
     * Tracks if any individual test within the test suite failed
     */
    @Column
    private boolean failed;

    /**
     * The name of the host that this test was last run on
     */
    @Column(name = "hostname")
    private String hostName;

    @ManyToOne
    @JoinColumn(name="project_id")
    private Project project;

    @ManyToOne
    @JoinColumn(name="build_id")
    private Build build;

    @Column(name = "last_updated")
    private Timestamp lastUpdated;

    public static TestTime of(TestSuite suite, String host) {
        return new TestTime(suite.getClassName(), suite.getRunTime(), suite.isFailed(), host, Timestamp.from(Clock.systemUTC().instant()));
    }

    public TestTime() {
    }

    public TestTime(String testName, long time, boolean failed, String hostName, Timestamp lastUpdated) {
        this.testName = testName;
        this.time = time;
        this.failed = failed;
        this.hostName = hostName;
        this.lastUpdated = lastUpdated;
    }

    public void update(TestSuite suite, String host) {
        this.testName = suite.getClassName();
        this.hostName = host;
        this.time = suite.getRunTime();
        this.failed = suite.isFailed();
        this.lastUpdated = Timestamp.from(Clock.systemUTC().instant());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Build getBuild() {
        return build;
    }

    public void setBuild(Build build) {
        this.build = build;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestTime)) return false;
        TestTime testTime = (TestTime) o;
        return Objects.equals(testName, testTime.testName) &&
                Objects.equals(project, testTime.project) &&
                Objects.equals(build, testTime.build);
    }

    @Override
    public int hashCode() {

        return Objects.hash(testName, project, build);
    }

    @Override
    public String toString() {
        return "TestTime{" +
                "id=" + id +
                ", testName='" + testName + '\'' +
                ", time=" + time +
                ", failed=" + failed +
                ", hostName='" + hostName + '\'' +
                ", project=" + project +
                ", build=" + build +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
