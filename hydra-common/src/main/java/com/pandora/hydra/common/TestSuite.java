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

package com.pandora.hydra.common;

import java.nio.file.Path;

/**
 * @author Justin Guerra
 * @since 10/19/16
 */
public class TestSuite {
    private transient String project;
    private String className;
    private long runTime;
    private boolean failed;

    public TestSuite() {
    }

    public TestSuite(String project, String className, long runTime, boolean failed) {
        this.className = className;
        this.runTime = runTime;
        this.project = project;
        this.failed = failed;
    }

    public static TestSuite from(Path file, String errors, String failures, String className, String packageName, String runtime, String projectName) {
        String fulllyQualified;
        if(packageName != null) {
            fulllyQualified = packageName + "." + className;
        } else if(className.matches("\\w+(\\.\\w+)*")) {
            fulllyQualified = className;
        } else {
            throw new IllegalStateException("Can't handle this format: " + packageName + " " + className);
        }

        long runtimeMS = (long) (Double.parseDouble(runtime) * 1000);
        boolean failed = Integer.parseInt(errors) + Integer.parseInt(failures) > 0;
        String project = projectName != null ? projectName : file.getParent().getFileName().toString();
        return new TestSuite(project, fulllyQualified, runtimeMS, failed);
    }

    public String getClassName() {
        return className;
    }

    public long getRunTime() {
        return runTime;
    }

    public String getProject() {
        return project;
    }

    public boolean isFailed() {
        return failed;
    }

    @Override
    public String toString() {
        return className + ":" + runTime;
    }

}
