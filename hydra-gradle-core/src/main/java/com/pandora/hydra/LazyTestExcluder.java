package com.pandora.hydra;

import com.pandora.hydra.client.HydraClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.specs.Spec;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
	private final Supplier<Set<String>> exclusionSupplier;

    private volatile Set<String> blacklist;

    private LazyTestExcluder(Project project, Supplier<HydraClient> hydraClientSupplier, Supplier<Set<String>> exclusionSupplier) {
		this.hydraClient = hydraClientSupplier;
		this.projectName = project.getName();
		this.project = project;

		if(exclusionSupplier == null) {
			this.exclusionSupplier = this::fetchTestExcludesListFromHydraServer;
		} else {
			this.exclusionSupplier = exclusionSupplier;
		}
	}

	public static LazyTestExcluder fromHydraServer(Project project, Supplier<HydraClient> hydraClientSupplier) {
		return new LazyTestExcluder(project, hydraClientSupplier, null);
	}

	public static LazyTestExcluder fromExclusionFile(Project project, String pathToExclusionFile) {
		Supplier<Set<String>> exclusionSupplier = () -> {
			Path exclusionPath = Paths.get(pathToExclusionFile);
			try {
				return new LinkedHashSet<>(Files.readAllLines(exclusionPath));
			} catch(IOException e) {
				throw new GradleException("Unable to read exclusions from " + pathToExclusionFile);
			}
		};

		return new LazyTestExcluder(project, () -> null, exclusionSupplier);
	}

    @Override
    public boolean isSatisfiedBy(FileTreeElement fileTreeElement) {
        if(blacklist == null) {
            synchronized(this) {
				if(blacklist == null) {
					blacklist = exclusionSupplier.get();
					logTestBlackListIfSpecified();
				}
			}
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

    private Set<String> fetchTestExcludesListFromHydraServer() {
        try {
            // We're switching to project-specific blacklists, but if you run into problems with this
            // for some reason, you can switch back to cumulative blacklists by using the following
            // line instead:
            // blacklist = hydraClient.get().getExcludes();
            return hydraClient.get().getExcludes(projectName);
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
