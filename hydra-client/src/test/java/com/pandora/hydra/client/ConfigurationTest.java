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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Justin Guerra
 * @since 4/26/17
 */
public class ConfigurationTest {

    @Test
    public void csvHostList() {
        List<String> expectedHostList = Arrays.asList("jenkins-ut1", "jenkins-ut2", "jenkins-ut3", "jenkins-ut4");
        List<String> parsedHostList = Configuration.parseHostList("jenkins-ut1,jenkins-ut2,jenkins-ut3,jenkins-ut4");

        assertEquals(expectedHostList, parsedHostList);
    }

    @Test
    public void rangeHostList() {
        List<String> expectedHostList = Arrays.asList("jenkins-ut1", "jenkins-ut2", "jenkins-ut3", "jenkins-ut4");
        List<String> parsedHostList = Configuration.parseHostList("jenkins-ut{1-4}");

        assertEquals(expectedHostList, parsedHostList);
    }

    @Test
    public void rangeHostListDoubleDigit() {
        List<String> expectedHostList = Arrays.asList("jenkins-ut10", "jenkins-ut11", "jenkins-ut12", "jenkins-ut13");
        List<String> parsedHostList = Configuration.parseHostList("jenkins-ut{10-13}");

        assertEquals(expectedHostList, parsedHostList);
    }

    @Test
    public void configurationFromEnvironment() {

        List<String> hosts = Arrays.asList("host1", "host2", "host3");
        Map<String, String> env = createEnvironmentMap(hosts);

        Configuration configuration = Configuration.newConfigurationFromEnv(env, Collections.emptyMap());

        assertEquals("whatever", configuration.getSlaveName());
        assertEquals("hydra.com", configuration.getRemoteHost());
        assertEquals("jobName", configuration.getJobName());
        assertEquals("buildTag", configuration.getBuildTag());
        assertEquals(500, configuration.getClientTimeout());
        assertEquals(hosts, configuration.getHostList());
        assertTrue(configuration.isHttps());
        assertNull(configuration.getRemotePort());
    }

    @Test
    public void configurationFromEnvironmentWithOverrides() {
        Map<String, String> derp = createEnvironmentMap(Arrays.asList("host1", "host2", "host3"));

        List<String> overriddenHosts = Arrays.asList("host2", "host3", "host4");

        Map<String, String> overrides = new HashMap<>();

        overrides.put(Configuration.ENV_HOST_NAME, "otherHostName");
        overrides.put(Configuration.ENV_HYDRA_ADDRESS, "hydra:12345");
        overrides.put(Configuration.ENV_HYDRA_HOSTS, String.join(",", overriddenHosts));
        overrides.put(Configuration.ENV_JOB_NAME, "coolJob");
        overrides.put(Configuration.ENV_BUILD_TAG, "otherTag");
        overrides.put(Configuration.ENV_HYDRA_CLIENT_TIMEOUT, "1000");
        overrides.put(Configuration.ENV_HYDRA_HTTPS, "false");

        Configuration configuration = Configuration.newConfigurationFromEnv(derp, overrides);

        assertEquals("otherHostName", configuration.getSlaveName());
        assertEquals("hydra", configuration.getRemoteHost());
        assertEquals(12345, configuration.getRemotePort().intValue());
        assertEquals("coolJob", configuration.getJobName());
        assertEquals("otherTag", configuration.getBuildTag());
        assertEquals(1000, configuration.getClientTimeout());
        assertEquals(overriddenHosts, configuration.getHostList());
        assertFalse(configuration.isHttps());
    }

    private Map<String, String> createEnvironmentMap(List<String> hosts) {
        Map<String, String> env = new HashMap<>();

        env.put(Configuration.ENV_HOST_NAME, "whatever");
        env.put(Configuration.ENV_HYDRA_ADDRESS, "hydra.com");
        env.put(Configuration.ENV_HYDRA_HOSTS, String.join(",", hosts));
        env.put(Configuration.ENV_JOB_NAME, "jobName");
        env.put(Configuration.ENV_BUILD_TAG, "buildTag");
        env.put(Configuration.ENV_HYDRA_CLIENT_TIMEOUT, "500");
        env.put(Configuration.ENV_HYDRA_HTTPS, "true");

        return env;
    }

}