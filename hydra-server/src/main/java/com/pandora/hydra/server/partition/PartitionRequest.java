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

import java.util.Set;

public class PartitionRequest {

    /**
     * The name of the host making the request to the hydra server
     */
    private final String hostName;

    /**
     * The name of the build that tests are being distributed for. Test metadata is saved based off buildname
     */
    private final String buildName;

    /**
     * The set of all hosts that will be running tests
     */
    private final Set<String> hostList;

    /**
     * A unique name associated with a given test run
     */
    private final String buildTag;

    public PartitionRequest(String hostName, String buildName, Set<String> hostList, String buildTag) {
        this.hostName = hostName;
        this.buildName = buildName;
        this.hostList = hostList;
        this.buildTag = buildTag;
    }

    public String getHostName() {
        return hostName;
    }

    public String getBuildName() {
        return buildName;
    }

    public Set<String> getHostList() {
        return hostList;
    }

    public String getBuildTag() {
        return buildTag;
    }
}
