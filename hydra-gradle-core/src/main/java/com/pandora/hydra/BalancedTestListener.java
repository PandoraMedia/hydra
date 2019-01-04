package com.pandora.hydra;

import com.pandora.hydra.common.TestSuite;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps track of test runtimes and outcomes so they can be reported back to the hydra server
 */
public class BalancedTestListener implements TestListener {

    private final String projectName;
    private final ConcurrentMap<String, TestSuite> tests;

    public BalancedTestListener(String projectName) {
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
        TestDescriptor parent = testDescriptor.getParent();
        if(parent == null) {
            return;
        }

        String className = parent.getClassName();
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

    public ConcurrentMap<String, TestSuite> getTests() {
        return tests;
    }
}
