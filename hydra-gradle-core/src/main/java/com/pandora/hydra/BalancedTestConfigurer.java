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

import org.apache.commons.beanutils.BeanUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;

import java.lang.reflect.InvocationTargetException;

/**
 * Configures a newly created BalancedTest based off an original test. Only a subset of the configuration from the original test
 * is copied.
 *
 * @author Justin Guerra
 * @since 1/16/18
 */
public class BalancedTestConfigurer<T extends Test> implements Action<T> {

    private final T originalTest;

    public BalancedTestConfigurer(T originalTest) {
        this.originalTest = originalTest;
    }

    @Override
    public void execute(T balancedTest) {
        balancedTest.setSystemProperties(originalTest.getSystemProperties());
        balancedTest.setEnvironment(originalTest.getEnvironment());
        balancedTest.setMinHeapSize(originalTest.getMinHeapSize());
        balancedTest.setMaxHeapSize(originalTest.getMaxHeapSize());
        balancedTest.setMaxParallelForks(originalTest.getMaxParallelForks());
        balancedTest.setTestClassesDirs(originalTest.getTestClassesDirs());
        balancedTest.setIgnoreFailures(originalTest.getIgnoreFailures());
        balancedTest.setDependsOn(originalTest.getDependsOn());
        balancedTest.setClasspath(originalTest.getClasspath());

        TestLoggingContainer originalLoggingContainer = originalTest.getTestLogging();
        balancedTest.testLogging(testLoggingContainer -> copyProperties(testLoggingContainer, originalLoggingContainer));

        balancedTest.getOutputs().upToDateWhen(task -> false);
    }

    private void copyProperties(Object dest, Object original) {
        try {
            BeanUtils.copyProperties(dest, original);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GradleException("Unable to copy test logging config");
        }

    }
}
