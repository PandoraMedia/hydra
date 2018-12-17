## Pandora Hydra Gradle plugin ##

The plugin works by decorating an existing test task to talk to the hydra-server.
The plugin is responsible for creating a hydra-client, fetching the test blacklist from the hydra-server, and also reporting test results
to the hydra-server.

#####Here are some caveats of this plugin:
- Pandora Hydra Android plugin must be applied after Android Application plugin, as it needs to decorate tasks created by Application plugin
- Pandora Hydra plugin should be applied at application leaf project level, it will fail if subprojects are identified, please use Hydra core plugin
 for Android library projects instead. Core plugin supports trees of projects
- You need to check application flavours and list some or all of them as `balancedTests` to get it to work
- Please be careful with `maxParallelForks` / `maxHeapSize = "Xg"` as it might result in excessive memory consumption and significantly slow down the build.
 Memory consumption can be estimated as `maxParallelForks * maxHeapSize + 2GB (for daemon and root thread)`


Here is the example of build configuration: 
```
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath 'com.pandora.hydra:hydra-android-gradle-plugin:1.6.6'
    }
}

apply plugin: 'com.android.application'
//This enables all test task to fork as many threads as available CPU sores (2x of actual CPU cores for Intel HyperThreading enabled CPUs)
project.tasks.withType(Test) {
    maxParallelForks = Runtime.runtime.availableProcessors()
}

apply plugin: 'com.pandora.hydra.android'
hydra {
    balancedTests = ['testReleaseUnitTest']
    balanceThreads = true
}

android {
    compileSdkVersion 28
    defaultConfig {
        applicationId "com.example.test1"
        minSdkVersion 15
        targetSdkVersion 28
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation 'com.android.support:appcompat-v7:28.0.0'
    implementation 'com.android.support.constraint:constraint-layout:1.1.3'
    testImplementation 'junit:junit:4.12'
    androidTestImplementation 'com.android.support.test:runner:1.0.2'
    androidTestImplementation 'com.android.support.test.espresso:espresso-core:3.0.2'
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

For convenience it is also possible to fully configure a client in the hydra configuration block. While this can be useful for testing
you will generally want to include this configuration in your CI build

+ `hydraServer` - uri of the hydra server
+ `hydraHostList` - list of hosts included in a test run
+ `jobName` - name of the job (on CI server) executing a test run
+ `buildTag` - a unique name associated with a given execution of jobName
+ `slaveName` - the name of the host running the test

### More on thread balancing 

By setting `balanceThreads true` you enable balancing test between threads
Please note that `maxParallelForks` for integrationTest task should be at least 2 to be able to balance the load between threads
Also please check amount of free memory on target hosts, as Gradle will spawn multiple JVMs and each of them will consume
memory specified in `maxHeapSize = "Xg"`, thus if you are running 2 parallel jobs on each server, using 10 threads,
you need to have 2 * 10 * X GB of free memory (120GB in example above)



