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

package com.pandora.hydra.client;

import com.google.common.base.Splitter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Justin Guerra
 * @since 8/26/16
 */
public class Configuration {

    static final String ENV_HOST_NAME = "VM_HOSTNAME";
    static final String ENV_HYDRA_ADDRESS = "HYDRA_SERVER";
    static final String ENV_HYDRA_HOSTS = "HYDRA_HOST_LIST";
    static final String ENV_HYDRA_HTTPS = "HYDRA_HTTPS";
    static final String ENV_HYDRA_CLIENT_TIMEOUT = "HYDRA_CLIENT_TIMEOUT";
    static final String ENV_HYDRA_CLIENT_ATTEMPTS = "HYDRA_CLIENT_ATTEMPTS";

    //from jenkins
    static final String ENV_JOB_NAME = "JOB_NAME";
    static final String ENV_BUILD_TAG = "BUILD_TAG";

    private final String remoteHost;
    private final Integer remotePort;
    private final String slaveName;
    private final List<String> hostList;
    private final String buildTag;
    private final String jobName;
    private final long clientTimeout;
    private final boolean https;
    private final int clientAttempts;

    private Configuration(String remoteHost, Integer remotePort, String slaveName, String jobName,
                          List<String> hostList, String buildTag, long clientTimeout, boolean https, int clientAttempts) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.slaveName = slaveName;
        this.jobName = jobName;
        this.hostList = hostList;
        this.buildTag = buildTag;
        this.clientTimeout = clientTimeout;
        this.https = https;
        this.clientAttempts = clientAttempts;
    }

    public static Configuration newConfigurationFromEnv(Map<String, String> envOverrides) {
        return newConfigurationFromEnv(System.getenv(), envOverrides);
    }

    static Configuration newConfigurationFromEnv(Map<String, String> env, Map<String, String> envOverrides) {
        String envSlaveName = env.get(ENV_HOST_NAME);
        String envRemoteHost = env.get(ENV_HYDRA_ADDRESS);
        String envHostList = env.get(ENV_HYDRA_HOSTS);
        String envJobName = env.get(ENV_JOB_NAME);
        String envBuildTag = env.get(ENV_BUILD_TAG);
        String envTimeout = env.get(ENV_HYDRA_CLIENT_TIMEOUT);
        String envHttps = env.get(ENV_HYDRA_HTTPS);
        String envRetries = env.get(ENV_HYDRA_CLIENT_ATTEMPTS);

        if(envJobName != null && envJobName.contains("/")) {
            envJobName = envJobName.substring(0, envJobName.indexOf("/"));
        }

        String slaveName = chooseValue(envSlaveName, envOverrides.get(ENV_HOST_NAME), "Slave's name must be specified via env variable VM_HOSTNAME");
        String remoteHost = chooseValue(envRemoteHost, envOverrides.get(ENV_HYDRA_ADDRESS), "Remote host address must be specified via env variable HYDRA_SERVER");
        String jobName = chooseValue(envJobName, envOverrides.get(ENV_JOB_NAME), "Build name must be specified via env variable JOB_NAME");
        String hostList = chooseValue(envHostList, envOverrides.get(ENV_HYDRA_HOSTS), "The list of hosts must be specified via env variable HYDRA_HOST_LIST");
        String buildTag = chooseValue(envBuildTag, envOverrides.get(ENV_BUILD_TAG));

        String parsedHostname = parseRemoteHost(remoteHost);
        Integer parsedPortNum = parseRemotePort(remoteHost);

        String timeoutString = chooseValue(envTimeout, envOverrides.get(ENV_HYDRA_CLIENT_TIMEOUT));
        long clientTimeout = timeoutString != null ? Long.parseLong(timeoutString) : 10_000;

        String retriesString = chooseValue(envRetries, envOverrides.get(ENV_HYDRA_CLIENT_ATTEMPTS));
        int clientRetries = retriesString != null ? Integer.parseInt(retriesString) : 1;

        String httpsString = chooseValue(envHttps, envOverrides.get(ENV_HYDRA_HTTPS));
        boolean https = Boolean.parseBoolean(httpsString);

        return new Configuration(parsedHostname, parsedPortNum, slaveName, jobName, parseHostList(hostList), buildTag,
                clientTimeout, https, clientRetries);
    }

    private static String parseRemoteHost(String hostname) {
        return hostname.split(":")[0];
    }

    private static Integer parseRemotePort(String hostname) {
        if(!hostname.contains(":")) {
            return null;
        } else {
            return Integer.valueOf(hostname.split(":")[1]);
        }
    }

    static List<String> parseHostList(String hostList) {
        Matcher rangeMatcher = Pattern.compile("([\\w-]+)\\{(\\d+)-(\\d+)}").matcher(hostList);
        if(rangeMatcher.matches()) {
            String name = rangeMatcher.group(1);
            int lowerRange = Integer.parseInt(rangeMatcher.group(2));
            int upperRange = Integer.parseInt(rangeMatcher.group(3));

            if(lowerRange < upperRange) {
                return IntStream.rangeClosed(lowerRange, upperRange).mapToObj(i -> name + i).collect(Collectors.toList());
            } else {
                throw new IllegalArgumentException("lower range must be small than upper range on " + hostList);
            }
        } else if(hostList.matches("\\S+(,\\s*\\S+)*")) {
            return Splitter.on(',').trimResults().omitEmptyStrings().splitToList(hostList);
        } else {
            throw new IllegalArgumentException("Host list must be a comma separated list of host names, or in the form hostName{1-n}");
        }
    }

    private static String chooseValue(String envVersion, String override) {
        if(override != null) {
            return override;
        } else {
            return envVersion;
        }
    }

    private static String chooseValue(String envVersion, String override, String errorMsg) {
        return Objects.requireNonNull(chooseValue(envVersion, override), errorMsg);
    }

    public String getJobName() {
        return jobName;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getSlaveName() {
        return slaveName;
    }

    public List<String> getHostList() {
        return hostList;
    }

    public Integer getRemotePort() {
        return remotePort;
    }

    public String getBuildTag() {
        return buildTag;
    }

    public long getClientTimeout() {
        return clientTimeout;
    }

    public int getClientAttempts() { return clientAttempts; }

    public boolean isHttps() {
        return https;
    }
}
