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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a test partition for a single host
 *
 * @author Justin Guerra
 * @since 10/31/16
 */
public class Partition {

    private final String hostName;

    /**
     * Mapping of project name to test container. A Partition can consist of multiple projects that are independently time balanced.
     * For the host that is actually running a test it's expected that different projects are independent (i.e. run in parallel).
     */
    private final Map<String, TestContainer> projectContainers;

    public Partition(String nodeName) {
        this.hostName = nodeName;
        this.projectContainers = new HashMap<>();
    }

    public TestContainer getTestContainerForProject(String project) {
        TestContainer testContainer = projectContainers.get(project);
        if(testContainer == null) {
            testContainer = new TestContainer(hostName, project);
            projectContainers.put(project, testContainer);
        }

        return testContainer;
    }

    public Set<String> getAllTestNames() {
        return projectContainers.values()
                                .stream()
                                .map(TestContainer::getClasses)
                                .flatMap(Collection::stream)
                                .collect(Collectors.toSet());
    }

    public Set<String> getAllTestNames(String projectName) {
        return getTestContainerForProject(projectName).getClasses();
    }

    Set<String> getAllProjectNames() {
        return projectContainers.keySet();
    }

    String getHostName() {
        return hostName;
    }

    @Override
    public String toString() {
        return "Partition{" +
                "hostName='" + hostName + '\'' +
                ", projectContainers=" + projectContainers.values() +
                '}';
    }
}
