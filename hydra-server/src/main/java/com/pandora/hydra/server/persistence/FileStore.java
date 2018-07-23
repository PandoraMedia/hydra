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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pandora.hydra.common.TestSuite;
import com.pandora.hydra.server.persistence.model.TestTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Saves new test time data to file system
 *
 * @author Justin Guerra
 * @since 8/16/16
 */
@Component
@Profile("file_repo")
public class FileStore implements TestStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileStore.class);
    private static final String BASE_FILE_NAME = "test.current";

    private final Map<String, Multimap<String,TestTime>> fullCache;
    private final Deque<String> updateDeque = new LinkedBlockingDeque<>();

    @Value("${hydra.repo}")
    private String repoDir;

    public FileStore() {
        fullCache = new HashMap<>();
    }

    public FileStore(Map<String, Multimap<String, TestTime>> fullCache) {
        this.fullCache = fullCache;
    }

    @PostConstruct
    public void init() throws IOException {
        Path repo = Paths.get(repoDir);
        if(!Files.exists(repo)) {
            LOG.info("Creating dir " + repo.toAbsolutePath());
            Files.createDirectory(repo);
        }

        Files.walk(repo, 2)
                .filter(x -> !Files.isDirectory(x))
                .filter(x -> x.getFileName().endsWith(BASE_FILE_NAME))
                .forEach(this::populateCache);
    }

    private synchronized void populateCache(Path pathToCache) {
        final Map<String, Set<TestTime>> projectResults;
        try(Reader reader = Files.newBufferedReader(pathToCache, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            JsonParser parser = new JsonParser();
            projectResults = gson.fromJson(parser.parse(reader), new TypeToken<Map<String, Set<TestTime>>>() {}.getType());
        } catch (IOException e) {
            LOG.error("Unable to parse " + pathToCache);
            throw new RuntimeException(e);
        }

        final HashMultimap<String, TestTime> multimap = HashMultimap.create();

        for(Map.Entry<String, Set<TestTime>> entry : projectResults.entrySet()) {
            entry.getValue().forEach(x-> multimap.put(entry.getKey(), x));
        }

        String buildName = pathToCache.getParent().getFileName().toString();
        fullCache.put(buildName,multimap);
    }

    @Override
    public synchronized Map<String, Collection<TestTime>> getTestTimes(String buildName) {
        Multimap<String, TestTime> testTimes = fullCache.get(buildName);

        Map<String, Collection<TestTime>> times;
        if (testTimes != null) {
            times = testTimes.asMap();
        } else if (fullCache.containsKey("default")) {
            times = fullCache.get("default").asMap();
        } else {
            times = Collections.emptyMap();
        }

        return Collections.unmodifiableMap(times);
    }

    @Override
    public synchronized void addTestTimes(String project, List<TestSuite> testTimes, String host, String build) {
        Objects.requireNonNull(testTimes);
        Objects.requireNonNull(host);
        Objects.requireNonNull(build);

        Multimap<String, TestTime> testCache = fullCache.get(build);
        if(testCache == null) {
            testCache = HashMultimap.create();
            fullCache.put(build, testCache);
        }

        for(TestSuite suite : testTimes) {
            final String key;
            if(!suite.getClassName().endsWith(".class")) {
                key = suite.getClassName() + ".class";
            } else {
                key = suite.getClassName();
            }

            final Optional<TestTime> testTime = testCache.get(project).stream().filter(x -> x.getTestName().equals(key)).findFirst();
            if(testTime.isPresent()) {
                testCache.remove(project, testTime.get());
            }

            testCache.put(project, TestTime.of(suite, host));
        }

        synchronized (updateDeque) {
            updateDeque.offer(build);
        }
    }

    @Override
    public synchronized void clearTestTimes(String build) {
        fullCache.remove(build);
    }

    @Override
    @Scheduled(fixedDelay = 3_600_000)
    public synchronized void purgeObsoleteTests() {
        for(Map.Entry<String, Multimap<String, TestTime>> entry : fullCache.entrySet()) {
            Map<String, Collection<TestTime>> projectTimes = entry.getValue().asMap();

            for (Map.Entry<String, Collection<TestTime>> project : projectTimes.entrySet()) {
                Collection<TestTime> projectTestTimes = project.getValue();
                Set<TestTime> obsoleteTests = PersistenceUtil.findObsoleteTests(projectTestTimes);

                for (Iterator<TestTime> it = projectTestTimes.iterator(); it.hasNext(); ) {
                    TestTime test = it.next();
                    if(obsoleteTests.contains(test)) {
                        LOG.info("Removing obsolete test " + test.getTestName()
                                + " on project " + project.getKey() + " on build " + entry.getKey());
                        it.remove();
                    }
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void writeTestsToFile() {
        Set<String> buildsToUpdate;
        synchronized (updateDeque) {
            if(updateDeque.isEmpty()) {
                return;
            }

            buildsToUpdate = new HashSet<>(updateDeque);
            updateDeque.clear();
        }

        final Gson gson = new Gson();
        for(String build : buildsToUpdate) {

            Path buildFolder = Paths.get(repoDir, build);
            if(!Files.exists(buildFolder)) {
                try {
                    Files.createDirectory(buildFolder);
                } catch (IOException e) {
                    LOG.error("Couldn't create directory " + buildFolder);
                    continue;
                }
            }

            Map<String, Collection<TestTime>> testTimes = getTestTimes(build);
            Path tmpPath = Paths.get(repoDir, build, "test.tmp");

            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(tmpPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                gson.toJson(gson.toJsonTree(testTimes), gson.newJsonWriter(writer));
            } catch (IOException e) {
                LOG.error("Problem writing temp file " + tmpPath.toAbsolutePath(),e);
                continue;
            }

            Path newFilePath = Paths.get(repoDir, build, BASE_FILE_NAME);
            try {
                Files.move(tmpPath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                LOG.info(String.format("Successfully wrote %s to disk",newFilePath.toAbsolutePath()));
            } catch (IOException e) {
                LOG.error("Problem writing file for build " + newFilePath.toAbsolutePath());
            }
        }
    }


}
