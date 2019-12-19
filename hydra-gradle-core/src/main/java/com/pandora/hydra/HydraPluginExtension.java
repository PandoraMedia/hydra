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

package com.pandora.hydra;

import java.util.Set;

/**
 * Configuration associated with a hydra { } block
 *
 * @author Justin Guerra
 * @since 12/21/17
 */
public class HydraPluginExtension {

    //HYDRA related variables. These should generally be provided as environmental variables in the jenkins config, but can
    //also be specified in the config
    private String hydraServer;
    private String hydraHostList;

    //Jenkins related environment variables. They should only be overridden when testing locally
    private String jobName;
    private String buildTag;
    private String slaveName;

    /**
     * Specifies whether the plugin should try and optimally distribute tests to gradle worker threads
     */
    private boolean balanceThreads;

    /**
     * The names of tests that the plugin will create a balanced version of
     */
    private Set<String> balancedTests;

    /**
     * Should we dump the list of tests that Hydra server indicated should be skipped on this node?
     * Useful for debugging.
     */
    private boolean logTestExclusions;

    /**
     * How many times (including the initial attempt) should the client attempt network operations before giving up?
     */
    private Integer numClientAttempts;

    /**
     * How long (in milliseconds) should the client wait before giving up on a network request?
     */
    private Long clientTimeout;

    public String getHydraServer() {
        return hydraServer;
    }

    public void setHydraServer(String hydraServer) {
        this.hydraServer = hydraServer;
    }

    public String getHydraHostList() {
        return hydraHostList;
    }

    public void setHydraHostList(String hydraHostList) {
        this.hydraHostList = hydraHostList;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getBuildTag() {
        return buildTag;
    }

    public void setBuildTag(String buildTag) {
        this.buildTag = buildTag;
    }

    public String getSlaveName() {
        return slaveName;
    }

    public void setSlaveName(String slaveName) {
        this.slaveName = slaveName;
    }

    public boolean isBalanceThreads() {
        return balanceThreads;
    }

    public void setBalanceThreads(boolean balanceThreads) {
        this.balanceThreads = balanceThreads;
    }

    public Set<String> getBalancedTests() {
        return balancedTests;
    }

    public void setBalancedTests(Set<String> balancedTests) {
        this.balancedTests = balancedTests;
    }

    public boolean isLogTestExclusions() {
        return logTestExclusions;
    }

    public void setLogTestExclusions(boolean logTestExclusions) {
        this.logTestExclusions = logTestExclusions;
    }

	public Integer getNumClientAttempts() {
        return numClientAttempts;
    }

    public void setNumClientAttempts(Integer numClientAttempts) {
        this.numClientAttempts = numClientAttempts;
    }

    public Long getClientTimeout() {
        return clientTimeout;
    }

    public void setClientTimeout(Long clientTimeout) {
        this.clientTimeout = clientTimeout;
    }
}
