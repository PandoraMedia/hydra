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
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author Justin Guerra
 * @since 2018-11-30
 */
public class BalancedTestFactory<T extends Test, U extends Test> {

    private final Class<T> balancedTestType;
    private final Class<U> originalTestType;
    private final BiConsumer<T, U> extraConfigurer;

    public BalancedTestFactory(Class<T> balancedType, Class<U> originalType) {
        this(balancedType, originalType, (a,b) -> {});
    }

    public BalancedTestFactory(Class<T> balancedType, Class<U> originalType, BiConsumer<T, U> extraConfigurer) {
        this.balancedTestType = balancedType;
        this.originalTestType = originalType;
        this.extraConfigurer = extraConfigurer;
    }

    public void createBalancedTest(Project project, HydraPluginExtension hydraExtension, boolean recursively) {

        Set<String> balancedTests = hydraExtension.getBalancedTests();
        if(balancedTests == null ||  balancedTests.isEmpty()) {
            return;
        }

        List<U> testTasks = new ArrayList<>();
        for (String balancedTest : balancedTests) {
            Set<Task> tasksByName = project.getTasksByName(balancedTest, recursively);
            tasksByName.forEach(t -> testTasks.add(verifyAndCastToTest(t, originalTestType)));
        }

        String projectName = project.getName();
        //defer creation till a balanced test is actually executed
        Supplier<HydraClient> clientSupplier = () -> {
            Configuration configuration = Configuration.newConfigurationFromEnv(buildOverrideMap(hydraExtension));
            return new HydraClient(configuration);
        };

        LazyTestExcluder lazyExcluder = new LazyTestExcluder(project, clientSupplier);

        for (U originalTest : testTasks) {
            T balancedTest = project.getTasks()
                    .create(originalTest.getName() + "_balanced", balancedTestType, new BalancedTestConfigurer<>(originalTest, extraConfigurer));

            balancedTest.exclude(lazyExcluder);

            BalancedTestListener testListener = new BalancedTestListener(balancedTest.getProject().getName());
            balancedTest.addTestListener(testListener);

            if(hydraExtension.isBalanceThreads()) {
                balancedTest.setProperty("balanceThreads", true);
                balancedTest.setProperty("envOverrides", buildOverrideMap(hydraExtension));
            }

            Task finalizer = project.getTasks().create(balancedTest.getName() + "_finalizer");
            finalizer.doLast(task -> {
                try {
                    clientSupplier.get().postTestRuntimes(new ArrayList<>(testListener.getTests().values()));
                } catch (IOException e) {
                    project.getLogger().lifecycle("Problem posting test runtime to hydra server for project " + projectName);
                    e.printStackTrace();
                }
            });

            //use finalizedBy so that it always runs regardless of whether tests fail or not
            balancedTest.finalizedBy(finalizer);
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

        addIfPresent(overrideMap, extension::getHydraServer, Configuration.ENV_HYDRA_ADDRESS);
        addIfPresent(overrideMap, extension::getHydraHostList, Configuration.ENV_HYDRA_HOSTS);
        addIfPresent(overrideMap, extension::getBuildTag, Configuration.ENV_BUILD_TAG);
        addIfPresent(overrideMap, extension::getSlaveName, Configuration.ENV_HOST_NAME);
        addIfPresent(overrideMap, extension::getJobName, Configuration.ENV_JOB_NAME);
        addIfPresent(overrideMap, extension::getNumClientAttempts, Configuration.ENV_HYDRA_CLIENT_ATTEMPTS);
        addIfPresent(overrideMap, extension::getClientTimeout, Configuration.ENV_HYDRA_CLIENT_TIMEOUT);

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
