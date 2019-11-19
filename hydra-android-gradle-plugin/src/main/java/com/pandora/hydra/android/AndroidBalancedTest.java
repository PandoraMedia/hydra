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

import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.tasks.factory.AndroidUnitTest;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

/**
 * A special test case than can distribute tests to worker threads in an optimal manner
 *
 * @author Justin Guerra
 * @since 12/22/17
 */
public class AndroidBalancedTest extends AndroidUnitTest {

    //Android plugin does not provide setters for these fields
    //so have to add new set of them and implement setters
    private String hSdkPlatformDirPath;
    private Provider<Directory> hMergedManifest;
    private BuildableArtifact hResCollection;
    private BuildableArtifact hAssetsCollection;

    public AndroidBalancedTest() {
        setGroup("verification");
        setDescription("Runs a subset of tests as determined by the hydra server");
    }

    @InputFiles
    @Optional
//    @Override
    public BuildableArtifact getResCollection() {
        return this.hResCollection;
    }

    @InputFiles
    @Optional
//    @Override
    public BuildableArtifact getAssetsCollection() {
        return hAssetsCollection;
    }

    @Input
//    @Override
    public String getSdkPlatformDirPath() {
        return hSdkPlatformDirPath;
    }

    @InputFiles
//    @Override
    public Provider<Directory> getMergedManifest() {
        return hMergedManifest;
    }

    void setSdkPlatformDirPath(String hSdkPlatformDirPath) {
        this.hSdkPlatformDirPath = hSdkPlatformDirPath;
    }

    void setMergedManifest(Provider<Directory> hMergedManifest) {
        this.hMergedManifest = hMergedManifest;
    }

    void setResCollection(BuildableArtifact hResCollection) {
        this.hResCollection = hResCollection;
    }

    void setAssetsCollection(BuildableArtifact hAssetsCollection) {
        this.hAssetsCollection = hAssetsCollection;
    }



}
