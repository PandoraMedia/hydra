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

package com.pandora.hydra.android;

import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import com.pandora.hydra.BalancedTestFactory;
import com.pandora.hydra.HydraPluginExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

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
public class HydraAndroidPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project.getSubprojects().isEmpty()) {
            project.getLogger().info("Applying to leaf project: " + project.getName());
            project.getPluginManager().apply(AppPlugin.class);
            HydraPluginExtension hydraExtension = project.getExtensions().create("hydra", HydraPluginExtension.class);
            project.afterEvaluate(p -> {
                if(hydraExtension.isBalanceThreads()) {
                    project.getLogger().info("Hydra Android doesn't support thread balancing. Ignoring setting");
                    hydraExtension.setBalanceThreads(false);
                }

                BalancedTestFactory<AndroidBalancedTest, AndroidUnitTest> factory = new BalancedTestFactory<>(AndroidBalancedTest.class,
                        AndroidUnitTest.class,
                        (balancedTest, originalTest) -> {
                            balancedTest.setMergedManifest(originalTest.getMergedManifest());
                            balancedTest.setResCollection(originalTest.getResCollection());
                            balancedTest.setSdkPlatformDirPath(originalTest.getSdkPlatformDirPath());
                        });
                factory.createBalancedTest(p, hydraExtension);
            });
        } else {
            project.getLogger().lifecycle("Hydra Android mod should not be applied on entire tree, please apply it at app module level");
            throw new RuntimeException("Hydra Android mod should not be applied on entire tree, please apply it at app module level");
        }
    }

}
