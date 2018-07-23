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
import com.pandora.hydra.common.TestSuite;
import com.pandora.hydra.server.persistence.model.Build;
import com.pandora.hydra.server.persistence.model.Project;
import com.pandora.hydra.server.persistence.model.TestTime;
import com.pandora.hydra.server.persistence.repo.BuildRepo;
import com.pandora.hydra.server.persistence.repo.ProjectRepo;
import com.pandora.hydra.server.persistence.repo.TestRepo;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Justin Guerra
 * @since 2/12/18
 */
@Component
@Profile("!file_repo")
public class SqlStore implements TestStore {

    private static final Logger LOG = Logger.getLogger(SqlStore.class);

    private final TestRepo testRepo;
    private final BuildRepo buildRepo;
    private final ProjectRepo projectRepo;

    @Autowired
    public SqlStore(TestRepo testRepo, BuildRepo buildRepo, ProjectRepo projectRepo) {
        this.testRepo = testRepo;
        this.buildRepo = buildRepo;
        this.projectRepo = projectRepo;
    }

    @Override
    public Map<String, Collection<TestTime>> getTestTimes(String buildName) {
        Build build = buildRepo.findByName(buildName);
        if(build == null) {
            return Collections.emptyMap();
        }

        List<TestTime> testTimesByBuild = testRepo.findTestTimesByBuild(build);
        if(testTimesByBuild == null || testTimesByBuild.isEmpty()) {
            return Collections.emptyMap();
        }

        Multimap<String, TestTime> map = HashMultimap.create();
        for (TestTime testTime : testTimesByBuild) {
            map.put(testTime.getProject().getName(), testTime);
        }

        return map.asMap();
    }

    @Override
    public void addTestTimes(String projectName, List<TestSuite> testTimes, String host, String buildName) {
        Build build = getOrCreateBuild(buildName);
        Project project = getOrCreateProject(projectName);

        for (TestSuite testSuite : testTimes) {
            TestTime test = getOrCreateTestTime(testSuite.getClassName(), build, project);
            test.setBuild(build);
            test.setProject(project);
            test.update(testSuite, host);

            testRepo.save(test);
        }
    }

    @Override
    @Scheduled(fixedDelay = 3_600_000)
    public void purgeObsoleteTests() {
        for (Build build : buildRepo.findAll()) {

            List<TestTime> testTimesByBuild = testRepo.findTestTimesByBuild(build);
            for (TestTime t : PersistenceUtil.findObsoleteTests(testTimesByBuild)) {
                LOG.info("Deleting obsolete test " + t);
                testRepo.delete(t);
            }
        }
    }

    private TestTime getOrCreateTestTime(String testName, Build build, Project project) {
        Supplier<TestTime> testTimeSupplier = () -> {
            TestTime testTime = new TestTime();
            testTime.setTestName(testName);
            return testTime;
        };

        return lookupOrSave(testName, () -> testRepo.findByTestNameAndBuildAndProject(testName, build, project), testTimeSupplier, testRepo::save);
    }

    private Project getOrCreateProject(String projectName) {
        Supplier<Project> projectSupplier = () -> {
            Project project = new Project();
            project.setName(projectName);
            return project;
        };

        return lookupOrSave(projectName, () -> projectRepo.findByName(projectName), projectSupplier, projectRepo::save);
    }

    private Build getOrCreateBuild(String buildName) {
        Supplier<Build> buildSupplier = () -> {
            Build build = new Build();
            build.setName(buildName);
            return build;
        };

        return lookupOrSave(buildName, () -> buildRepo.findByName(buildName), buildSupplier, buildRepo::save);
    }

    private <T> T lookupOrSave(String name, Supplier<T> querySupplier, Supplier<T> newSupplier, Function<T,T> saveFunction) {
        int attempts = 0;
        T byName;
        do {
            byName = querySupplier.get();
            if(byName == null) {
                LOG.debug(name + "doesn't exist. Attempting to save");
                byName = trySave(newSupplier.get(), saveFunction);
            }
        } while (byName == null && attempts++ < 3);

        if(byName == null) {
            LOG.error("Failed to lookup or save " + name + " after " + attempts + " attempts");
            throw new IllegalStateException("Something went wrong trying to save " + name);
        }


        return byName;
    }

    private <T> T trySave(T toSave, Function<T,T> repo) {
        try {
            return repo.apply(toSave);
        } catch (Exception e) {
            LOG.info("Failed to save " + toSave);
            return null;
        }
    }

    @Override
    public void clearTestTimes(String build) {
        throw new NotImplementedException("Clear is not implemented on SqlStore");
    }
}
