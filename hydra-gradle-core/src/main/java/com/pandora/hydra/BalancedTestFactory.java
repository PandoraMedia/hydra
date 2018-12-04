package com.pandora.hydra;

import com.pandora.hydra.client.Configuration;
import com.pandora.hydra.client.HydraClient;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.testing.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Justin Guerra
 * @since 2018-11-30
 */
public class BalancedTestFactory {

    public static <T extends Test> void createBalancedTest(Project project, HydraPluginExtension hydraExtension, Class<T> type) {

        Set<String> balancedTests = hydraExtension.getBalancedTests();
        if(balancedTests == null ||  balancedTests.isEmpty()) {
            return;
        }

        List<T> testTasks = new ArrayList<>();
        for (String balancedTest : balancedTests) {
            Set<Task> tasksByName = project.getTasksByName(balancedTest, true);
            tasksByName.forEach(t -> testTasks.add(verifyAndCastToTest(t, type)));
        }

        String projectName = project.getName();
        //defer creation till a balanced test is actually executed
        Supplier<HydraClient> clientSupplier = () -> {
            Configuration configuration = Configuration.newConfigurationFromEnv(buildOverrideMap(hydraExtension));
            return new HydraClient(configuration);
        };

        LazyTestExcluder lazyExcluder = new LazyTestExcluder(projectName, clientSupplier);

        for (T originalTest : testTasks) {
            T balancedTest = project.getTasks()
                    .create(originalTest.getName() + "_balanced", type, new BalancedTestConfigurer<>(originalTest));

            balancedTest.exclude(lazyExcluder);

            BalancedTestListener testListener = new BalancedTestListener(balancedTest.getProject().getName());
            balancedTest.addTestListener(testListener);

            if(hydraExtension.isBalanceThreads()) {
                balancedTest.setProperty("balanceThreads", true);
                balancedTest.setProperty("envOverrides", buildOverrideMap(hydraExtension));
            }

            balancedTest.doLast(task -> {
                try {
                    clientSupplier.get().postTestRuntimes(new ArrayList<>(testListener.getTests().values()));
                } catch (IOException e) {
                    project.getLogger().lifecycle("Problem posting test runtime to hydra server for project " + projectName);
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * The hydra extension object can be used to override environment variables that are normally present when a balanced test
     * is run in jenkins
     * @param extension - represents items in a hydra {} configuration in the build file
     * @return a map of strings that are used to override environmental variables in a hydra-client
     */
    private static Map<String, String> buildOverrideMap(HydraPluginExtension extension) {
        Map<String, String> overrideMap = new HashMap<>();

        addIfPresent(overrideMap, extension::getHydraServer, "HYDRA_SERVER");
        addIfPresent(overrideMap, extension::getHydraHostList, "HYDRA_HOST_LIST");
        addIfPresent(overrideMap, extension::getBuildTag, "BUILD_TAG");
        addIfPresent(overrideMap, extension::getSlaveName, "VM_HOSTNAME");
        addIfPresent(overrideMap, extension::getJobName, "JOB_NAME");

        return overrideMap;
    }

    private static <S> void addIfPresent(Map<String, String> map, Supplier<S> supplier, String key) {
        S value= supplier.get();
        if(value != null) {
            map.put(key, value.toString());
        }
    }

    private static <T> T verifyAndCastToTest(Task task, Class<T> type) {
        if(!type.isInstance(task)) {
            throw new GradleException("Task " + task.getName() + " cannot be balanced because it is not a " + type.getSimpleName());
        }
        return type.cast(task);
    }

}
