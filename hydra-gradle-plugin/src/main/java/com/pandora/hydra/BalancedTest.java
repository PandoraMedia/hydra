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
import com.google.common.reflect.TypeToken;
import com.pandora.hydra.client.Configuration;
import com.pandora.hydra.client.HydraClient;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestClassScanner;
import org.gradle.api.tasks.InputFiles;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A special test case than can distribute tests to worker threads in an optimal manner
 *
 * @author Justin Guerra
 * @since 12/22/17
 */
public class BalancedTest extends AndroidUnitTest {

    private Map<String, String> envOverrides;
    private boolean balanceThreads;

    private HydraClient hydraClient;

    public BalancedTest() {
        setGroup("verification");
        setDescription("Runs a subset of tests as determined by the hydra server");
    }

    @Override
    @InputFiles
    public FileTree getCandidateClassFiles() {
        FileTree candidateClassFiles = super.getCandidateClassFiles();
        if(balanceThreads) {
            return createDelegatingTree(candidateClassFiles);
        } else {
            return candidateClassFiles;
        }
    }

    private FileTree createDelegatingTree(final FileTree tree) {
        TypeToken<? extends FileTree>.TypeSet interfaceSet = TypeToken.of(tree.getClass()).getTypes().interfaces();
        Class<?>[] interfaces = interfaceSet.rawTypes().toArray(new Class<?>[0]);

        return (FileTree) Proxy.newProxyInstance(FileTree.class.getClassLoader(), interfaces, (Object proxy, Method method, Object[] args) -> {
            if(!method.getName().equals("visit")) {
                return method.invoke(tree, args);
            }

            Type[] parameterTypes = method.getGenericParameterTypes();
            if(parameterTypes.length != 1 || !FileVisitor.class.isAssignableFrom((Class<?>) parameterTypes[0])) {
                return method.invoke(tree, args);
            }

            FileVisitor visitor = (FileVisitor) args[0];
            if(!visitor.getClass().getName().contains(DefaultTestClassScanner.class.getSimpleName())) {
                return method.invoke(tree, args);
            } else {
                Set<FileVisitDetails> ordering = ThreadBalancer.createBalancedOrdering(tree, getMaxParallelForks(), getHydraClient());
                ordering.forEach(visitor::visitFile);
                return tree;
            }
        });
    }

    private synchronized HydraClient getHydraClient() {
        if(hydraClient == null) {
            Map<String, String> overrides = envOverrides != null ? envOverrides : Collections.emptyMap();
            Configuration configuration = Configuration.newConfigurationFromEnv(overrides);
            hydraClient = new HydraClient(configuration);
        }

        return hydraClient;
    }

    public boolean isBalanceThreads() {
        return balanceThreads;
    }

    public void setBalanceThreads(boolean balanceThreads) {
        this.balanceThreads = balanceThreads;
    }

    public Map<String, String> getEnvOverrides() {
        return envOverrides;
    }

    public void setEnvOverrides(Map<String, String> envOverrides) {
        this.envOverrides = envOverrides;
    }

}
