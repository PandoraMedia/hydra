## Pandora Hydra Gradle plugin ##

The plugin works by decorating an existing test task to talk to the hydra-server.
The plugin is responsible for creating a hydra-client, fetching the test blacklist from the hydra-server, and also reporting test results
to the hydra-server. 

Here is the example of build configuration: 
```
buildscript {
       repositories {
           mavenCentral()
       }
       dependencies {
           classpath 'com.pandora.hydra:hydra-gradle-plugin:2.0.0'
       }
   }
   
   apply plugin: 'com.pandora.hydra'
   
   task integrationTest(type: Test) {
       minHeapSize = "1g"
       maxHeapSize = "6g"
   
       outputs.upToDateWhen { false }
       testClassesDirs = sourceSets.integrationTest.output.classesDirs
       classpath = sourceSets.integrationTest.runtimeClasspath
   
       ignoreFailures true
   
       testLogging {
           events 'passed', 'failed'
       }
   
       maxParallelForks = 10
   }
   
   task testReport(type: TestReport) {
       destinationDir = file("$buildDir/reports/allTests")
   
       if(!destinationDir.exists()) {
           destinationDir.mkdir()
       }
   
       // Include the results from the `test` task in all subprojects
       reportOn test, integrationTest
   }
   
   task unitTestReport(type: TestReport) {
       destinationDir = file("$buildDir/reports/unitTest")
       // Include the results from the `test` task in all subprojects
       reportOn test
   }
   
   task integrationTestReport(type: TestReport) {
       destinationDir = file("$buildDir/reports/integrationTest")
       // Include the results from the `test` task in all subprojects
       reportOn integrationTest
   }
   
   task allReport(dependsOn: ['unitTestReport', 'integrationTestReport', 'testReport'])
   
   test {
       maxParallelForks = 8
   }
   
   hydra {
       balancedTests = ['integrationTest']
   }
```
Please note that you might need to publish plugin jar and it's dependencies to your local Nexus/Artifactory or provide it as a flat dir


## Configuration

The plugin can be configured inside of a `hydra { }` configuration block.

+ `balancedTests` is an array of tests that will have a balanced counterpart created. The plugin will create a new task for 
each test is in this list using the naming convention `originalTest_balanced`. The new test task wraps the original and automatically
handles reading the test blacklist and publishing test results to the hydra server
+ `balanceThreads` attempts to create optimal test partitions across the _threads_ on an individual node (must be running with maxParallelForks >= 2).
This can be useful because Gradle assigns tests to worker threads at test discovery time, and if you have bad luck your slowest tests
can all be assigned to the same thread. Thread balancing is the most fragile feature in the hydra plugin, and should be disabled if you run
into any problems
+ `logTestExclusions` is a boolean which defaults to `false`. Setting this to true will create a series of node- and
project-specific text files, each of which contains the full list of tests that the Hydra server instructed the client
to skip over. Primarily useful for debugging client/server interactions.

For convenience it is also possible to fully configure a client in the hydra configuration block. While this can be useful for testing
you will generally want to include this configuration in your CI build

+ `hydraServer` - uri of the hydra server
+ `hydraHostList` - list of hosts included in a test run
+ `jobName` - name of the job (on CI server) executing a test run
+ `buildTag` - a unique name associated with a given execution of jobName
+ `slaveName` - the name of the host running the test
+ `networkRetryCount` - how many times should network requests be attempted? Can be used as a workaround for transient 
network/server issues. defaults to 1.
+ `retryDelayMs` - how long to wait, in milliseconds, before retrying a network request. Only applicable if 
networkRetryCount > 1.

### More on thread balancing 

By setting `balanceThreads true` you enable balancing test between threads
Please note that `maxParallelForks` for integrationTest task should be at least 2 to be able to balance the load between threads
Also please check amount of free memory on target hosts, as Gradle will spawn multiple JVMs and each of them will consume
memory specified in `maxHeapSize = "Xg"`, thus if you are running 2 parallel jobs on each server, using 10 threads,
you need to have 2 * 10 * X GB of free memory (120GB in example above)



