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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.tasks.testing.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Plugin that is used to create balanced versions of tests. A balanced test only runs a subset of available tests, expecting
 * the rest to be run on other machines.
 *
 * This plugin works by searching for tests that are defined in {@link HydraPluginExtension#getBalancedTests()}. Upon finding these tests
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

    private final HydraProjectEvalListener listener = new HydraProjectEvalListener();

    @Override
    public void apply(Project project) {
        project.getGradle().addProjectEvaluationListener(listener);
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
            project.getLogger().debug("Applying to root project: " + project.getName());
            HydraPluginExtension hydraExtension = project.getExtensions().create("hydra", HydraPluginExtension.class);
            listener.addPluginExtension(project, hydraExtension);
        } else {
            for (Project subproject : project.getSubprojects()) {
                project.getLogger().debug("Applying to subproject: " + subproject.getName());
                createHydraExtensionsFor(subproject);
            }
        }
    }

    /**
     * Responsible for creating balanced versions of test classes after a project is finished evaluating
     */
    private class HydraProjectEvalListener implements ProjectEvaluationListener {

        private ConcurrentMap<String, HydraPluginExtension> extensions = new ConcurrentHashMap<>();

        private void addPluginExtension(Project p, HydraPluginExtension extension) {
            extensions.put(p.getName(), extension);
        }

        @Override
        public void beforeEvaluate(Project project) {
            //this method is called too late to create the plugin extension
        }

        @Override
        public void afterEvaluate(Project project, ProjectState projectState) {
            HydraPluginExtension hydraExtension = extensions.get(project.getName());
            if(hydraExtension == null) {
                return;
            }

            BalancedTestFactory<BalancedTest, Test> factory = new BalancedTestFactory<>(BalancedTest.class, Test.class);
            factory.createBalancedTest(project, hydraExtension, true);
        }
    }

}
