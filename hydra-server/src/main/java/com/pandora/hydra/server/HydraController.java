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

package com.pandora.hydra.server;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.pandora.hydra.common.TestSuite;
import com.pandora.hydra.server.partition.PartitionRequest;
import com.pandora.hydra.server.persistence.TestStore;
import com.pandora.hydra.server.partition.Partitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * @author Justin Guerra
 * @since 8/16/16
 */
@RestController
public class HydraController {

    private static final Logger LOG = LoggerFactory.getLogger(HydraController.class);

    private final TestStore testStore;
    private final Partitioner partitioner;

	@Autowired
	public HydraController(Partitioner partitioner, TestStore store) {
		this.partitioner = partitioner;
		this.testStore = store;
	}

    @RequestMapping(value = "/tests/{build}/runtimes", method = RequestMethod.DELETE)
    void clearRuntimes(@PathVariable String build) {
        testStore.clearTestTimes(build);
    }

    @RequestMapping(value = "/tests/{build}/{host}/{project}/runtimes", method = RequestMethod.POST)
    void saveTestResults(@PathVariable String build, @PathVariable String project,
						 @RequestBody List<TestSuite> testTimes, @PathVariable("host") String host) {
        LOG.info(String.format("Received %d test runtimes for build %s on project %s from host %s", testTimes.size(), build, project, host));
        testStore.addTestTimes(project, testTimes, host, build);
    }

    @RequestMapping(value = "/tests/{build}/{host}/excludes", method = RequestMethod.GET)
    ResponseEntity<Set<String>> getTestBlacklistForHost(@PathVariable String build, @PathVariable String host, @RequestParam(name = "host_list") String hostList,
														@RequestParam(name = "build_tag", required = false) String buildTag) {
        Set<String> hostNames = getAndValidateHostList(host, hostList);
        LOG.info(String.format("Fetching test black list for host %s running build %s with build tag %s", host, build, buildTag));
        Set<String> exclusionsFor = partitioner.getTestBlacklist(new PartitionRequest(host, build, hostNames, buildTag));
        return ResponseEntity.ok(exclusionsFor);
    }

    @RequestMapping(value = "/tests/{build}/{host}/{project}/excludes", method = RequestMethod.GET)
    ResponseEntity<Set<String>> getTestBlacklistForHostAndProject(@PathVariable String build, @PathVariable String host, @PathVariable String project,
                                                                  @RequestParam(name = "host_list") String hostList,
                                                                  @RequestParam(name = "build_tag", required = false) String buildTag) {
        Set<String> hostNames = getAndValidateHostList(host, hostList);
        LOG.info(String.format("Fetching test black list for host %s and project %s running build %s with build tag %s", host, project, build, buildTag));
        Set<String> exclusionsFor = partitioner.getTestBlacklist(new PartitionRequest(host, build, hostNames, buildTag), project);
        return ResponseEntity.ok(exclusionsFor);
    }

    @RequestMapping(value = "/tests/{build}/{host}/threads", method = RequestMethod.GET)
    ResponseEntity<Set<Set<String>>> getOptimalThreadGrouping(@PathVariable String build, @PathVariable String host, @RequestParam(name = "host_list") String hostList,
																@RequestParam(name = "build_tag", required = false) String buildTag, @RequestParam(name = "num_threads") int numThreads) {
        Set<String> hostNames = getAndValidateHostList(host, hostList);
        Set<Set<String>> lists = partitioner.getThreadGrouping(new PartitionRequest(host, build, hostNames, buildTag), numThreads);
        return ResponseEntity.ok(lists);
    }

    private Set<String> getAndValidateHostList(@PathVariable String host, @RequestParam(name = "host_list") String hostList) {
        if(!hostList.matches("\\S+(,\\s*\\S+)*")) {
            throw new RuntimeException("Host list must be a comma separated list of host names");
        }

        Set<String> hostNames = Sets.newHashSet(Splitter.on(',').trimResults().omitEmptyStrings().split(hostList));

        if(!hostNames.contains(host)) {
            throw new IllegalArgumentException("Host " + host + " was not included in host_list " + hostList);
        }
        return hostNames;
    }

}
