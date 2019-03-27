package com.pandora.hydra;

import com.pandora.hydra.client.HydraClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.specs.Spec;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Queries the test load balancer server to retrieve a black list of tests that should be skipped.
 * Defers queries to the hydra-server until execution time
 */
public class LazyTestExcluder implements Spec<FileTreeElement> {

    private final Supplier<HydraClient> hydraClient;
    private final String projectName;
    private final Project project;

    private volatile Set<String> blacklist;

    public LazyTestExcluder(Project project, Supplier<HydraClient> hydraClientSupplier) {
        this.hydraClient = hydraClientSupplier;
        this.projectName = project.getName();
        this.project = project;
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

    private void logTestBlackListIfSpecified() {
        final HydraPluginExtension pluginExtension = project.getExtensions().getByType(HydraPluginExtension.class);
        if (!pluginExtension.isLogTestExclusions()) {
            return;
        }

        String hostname = pluginExtension.getSlaveName();
        if (hostname == null || hostname.length() < 1) {
            hostname = System.getenv("NODE_NAME");
        }
        String exclusionsFilename = projectName + '_' + hostname + "_test_exclusions.txt";
        File exclusionsFile = new File(project.getRootProject().getBuildDir(), "hydra_client/" + exclusionsFilename);
        exclusionsFile.getParentFile().mkdirs();
        project.getLogger().log(LogLevel.INFO, "Logging Hydra test exclusions to " + exclusionsFile.getAbsolutePath());

        try (PrintWriter exclusionsWriter = new PrintWriter(new BufferedWriter(new FileWriter(exclusionsFile)))) {
            for (String testExclusion : blacklist) {
                exclusionsWriter.println(testExclusion);
            }
        } catch (IOException e) {
            project.getLogger().log(LogLevel.WARN, "Unable to write Hydra test exclusions to " + exclusionsFile.getAbsolutePath(), e);
        }
    }

    private synchronized void fetchTestExcludesListFromHydraServer() {
        if(blacklist != null) {
            return;
        }

        try {
            blacklist = hydraClient.get().getExcludes();
            logTestBlackListIfSpecified();
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
