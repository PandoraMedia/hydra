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

import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.pandora.hydra.client.Configuration;
import com.pandora.hydra.client.HydraClient;
import com.pandora.hydra.common.TestSuite;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Plugin that is used to create balanced versions of tests. A balanced test only runs a subset of available tests, expecting
 * the rest to be run on other machines.
 *
 * This plugin works by searching for tests that are defined in {@link HydraPluginExtension#balancedTests}. Upon finding these tests
 * a new test task is configured that will only run a portion of the tests define, optionally balance the tests in a uniform manner
 * across threads, and report the results back to the hydra server. The new test that is created has the name originalTestName_balanced.
 * For example, say you wanted a balanced version of a task named "integrationTest" then this plugin will create a new test task with the
 * name "integrationTest_balanced". The tests that are run are determined by fetching an excludes list from the hydra server.
 * Any tests not in the excludes list will be run.
 *
 * A test can optionally be configured to balance tests across threads. Thread balancing attempts to distribute the tests in such a way
 * that each gradle worker has an equal amount of work.
 *
 * @author Justin Guerra
 * @author Alex Dubrouski
 * @since 12/21/17
 */
public class HydraPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        createHydraExtensionsFor(project);
    }

    /**
     * Creates HydraPluginExtension's for project. These extension objects will be used after the project is configured to
     * create a balanced test.
     *
     * If project is a root project in a multi-project build then extensions are created for all subprojects instead of the
     * root project.
     *
     * @param project the project to create an extension for, or a root project whose subprojects will have extensions created
     */
    private void createHydraExtensionsFor(Project project) {
        if (project.getSubprojects().isEmpty()) {
            project.getLogger().info("Applying to leaf project: " + project.getName());
            HydraPluginExtension hydraExtension = project.getExtensions().create("hydra", HydraPluginExtension.class);
            applyAfterEvaluateClosure(project, hydraExtension);
        } else {
            project.getLogger().lifecycle("Hydra Android mod should not be applied on entire tree, please apply it at app module level");
            throw new RuntimeException("Hydra Android mod should not be applied on entire tree, please apply it at app module level");
        }
    }

    /**
     * Responsible for creating balanced versions of test classes after a project is finished evaluating
     */
    private void applyAfterEvaluateClosure(Project project, HydraPluginExtension hydraExtension) {
        project.afterEvaluate ( pr -> {
            Set<String> balancedTests = hydraExtension.getBalancedTests();
            if(balancedTests == null ||  balancedTests.isEmpty()) {
                return;
            }

            List<AndroidUnitTest> testTasks = new ArrayList<>();
            for (String balancedTest : balancedTests) {
                Set<Task> tasksByName = pr.getTasksByName(balancedTest, true);
                tasksByName.forEach(t -> testTasks.add(verifyAndCastToTest(t)));
            }

            String projectName = pr.getName();
            //defer creation till a balanced test is actually executed
            Supplier<HydraClient> clientSupplier = () -> {
                Configuration configuration = Configuration.newConfigurationFromEnv(buildOverrideMap(hydraExtension));
                return new HydraClient(configuration);
            };

            LazyTestExcluder lazyExcluder = new LazyTestExcluder(projectName, clientSupplier);

            for (AndroidUnitTest originalTest : testTasks) {

                BalancedTest balancedTest = pr.getTasks()
                        .create(originalTest.getName() + "_balanced", BalancedTest.class, new BalancedTestConfigurer(originalTest));

                balancedTest.exclude(lazyExcluder);

                BalancedTestListener testListener = new BalancedTestListener(balancedTest.getProject().getName());
                balancedTest.addTestListener(testListener);

                if(hydraExtension.isBalanceThreads()) {
                    balancedTest.setProperty("balanceThreads", true);
                    balancedTest.setProperty("envOverrides", buildOverrideMap(hydraExtension));
                }

                balancedTest.doLast(task -> {
                    try {
                        clientSupplier.get().postTestRuntimes(new ArrayList<>(testListener.tests.values()));
                    } catch (IOException e) {
                        project.getLogger().lifecycle("Problem posting test runtime to hydra server for project " + projectName);
                        e.printStackTrace();
                    }
                });
            }

        });
    }

    private static AndroidUnitTest verifyAndCastToTest(Task task) {
        if(!(task instanceof AndroidUnitTest)) {
            throw new GradleException("Task " + task.getName() + " cannot be balanced because it is not a Test");
        }
        return (AndroidUnitTest) task;
    }

    /**
     * The hydra extension object can be used to override environment variables that are normally present when a balanced test
     * is run in jenkins
     * @param extension - represents items in a hydra {} configuration in the build file
     * @return a map of strings that are used to override environmental variables in a hydra-client
     */
    private Map<String, String> buildOverrideMap(HydraPluginExtension extension) {
        Map<String, String> overrideMap = new HashMap<>();

        addIfPresent(overrideMap, extension::getHydraServer, "HYDRA_SERVER");
        addIfPresent(overrideMap, extension::getHydraHostList, "HYDRA_HOST_LIST");
        addIfPresent(overrideMap, extension::getBuildTag, "BUILD_TAG");
        addIfPresent(overrideMap, extension::getSlaveName, "VM_HOSTNAME");
        addIfPresent(overrideMap, extension::getJobName, "JOB_NAME");

        return overrideMap;
    }

    private <T> void addIfPresent(Map<String, String> map, Supplier<T> supplier, String key) {
        T value= supplier.get();
        if(value != null) {
            map.put(key, value.toString());
        }
    }

    /**
     * Queries the test load balancer server to retrieve a black list of tests that should be skipped.
     * Defers queries to the hydra-server until execution time
     */
    private static class LazyTestExcluder implements Spec<FileTreeElement> {

        private final Supplier<HydraClient> hydraClient;
        private final String projectName;

        private volatile Set<String> blacklist;

        LazyTestExcluder(String projectName, Supplier<HydraClient> hydraClientSupplier) {
            this.hydraClient = hydraClientSupplier;
            this.projectName = projectName;
        }

        @Override
        public boolean isSatisfiedBy(FileTreeElement fileTreeElement) {
            if(blacklist == null) {
                fetchTestExcludesListFromHydraServer();
            }
            if(fileTreeElement.isDirectory()) {
                return false;
            } else {
                RelativePath relativePath = fileTreeElement.getRelativePath();
                String fullyQualifiedName = buildQualifiedNameWithoutExtension(relativePath);
                return blacklist.contains(fullyQualifiedName);
            }
        }

        private synchronized void fetchTestExcludesListFromHydraServer() {
            if(blacklist != null) {
                return;
            }

            try {
                blacklist = hydraClient.get().getExcludes();
            } catch (IOException e) {
                throw new GradleException("Unable to fetch tests from hydra server for project " + projectName, e);
            }
        }

        private String buildQualifiedNameWithoutExtension(RelativePath path) {
            LinkedList<String> segments = new LinkedList<>(Arrays.asList(path.getSegments()));
            String fileName = segments.pollLast();
            segments.addLast(stripFileExtension(fileName));

            return String.join(".", segments);
        }

        private String stripFileExtension(String fileName) {
            Objects.requireNonNull(fileName);

            int lastIndexOf = fileName.lastIndexOf('.');
            if(lastIndexOf < 0) {
                return fileName;
            } else {
                return fileName.substring(0, lastIndexOf);
            }
        }
    }

    /**
     * Keeps track of test runtimes and outcomes so they can be reported back to the hydra server
     */
    private static class BalancedTestListener implements TestListener {

        private final String projectName;
        private final ConcurrentMap<String, TestSuite> tests;

        private BalancedTestListener(String projectName) {
            this.projectName = projectName;
            this.tests = new ConcurrentHashMap<>();
        }

        @Override
        public void beforeSuite(TestDescriptor suite) { }

        @Override
        public void afterSuite(TestDescriptor suite, TestResult result) {

        }

        @Override
        public void beforeTest(TestDescriptor testDescriptor) { }

        @Override
        public void afterTest(TestDescriptor testDescriptor, TestResult result) {
            String className = testDescriptor.getClassName();
            if(className == null) {
                return;
            }

            long failedTestCount = result.getFailedTestCount();
            long elapsed = result.getEndTime() - result.getStartTime();

            TestSuite testSuite = tests.get(className);
            if(testSuite != null) {
                //RedisDependencyRunner can cause this situation
                boolean failed = testSuite.isFailed() || failedTestCount > 0;
                tests.put(className, new TestSuite(projectName, className, elapsed + testSuite.getRunTime(), failed));
            } else {
                tests.put(className, new TestSuite(projectName, className, elapsed, failedTestCount > 0));
            }

        }
    }

}
