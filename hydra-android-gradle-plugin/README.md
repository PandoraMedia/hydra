## Pandora Hydra Gradle plugin ##

The plugin works by decorating an existing test task to talk to the hydra-server.
The plugin is responsible for creating a hydra-client, fetching the test blacklist from the hydra-server, and also reporting test results
to the hydra-server.

##### Here are some caveats of this plugin:
- If you are using a version of Android Gradle Plugin (AGP) _prior_ to 3.4, you *must also* use a 1.x version of Hydra. Likewise, if you are using AGP 3.4+, you *must also* use Hydra 2.0.0+.
- Pandora Hydra Android plugin must be applied after Android Application/Library plugin, as it needs to decorate tasks created by the aforementioned plugins.
- Pandora Hydra plugin should be applied _directly_ to each module containing tests you wish to balance; it will fail if subprojects are identified. 
This is in contrast to the Hydra Core plugin, which can simply be applied at the root level, and will then be automatically applied
to the entire tree of subprojects.
- You need to check application flavours and list some or all of them as `balancedTests` to get it to work
- Please be careful with `maxParallelForks` / `maxHeapSize = "Xg"` as it might result in excessive memory consumption and significantly slow down the build.
 Memory consumption can be estimated as `maxParallelForks * maxHeapSize + 2GB (for daemon and root thread)`
- Thread balancing is currently not implemented for Android plugin; `balanceThreads` option is simply ignored.


Here is a simple example for a project comprised of a single app module, which in turn contains _all_ the tests:  
```
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
    dependencies {
        classpath 'com.pandora.hydra:hydra-android-gradle-plugin:2.0.+'
    }
}

apply plugin: 'com.android.application'
//This enables all test tasks to fork as many threads as available CPU sores (2x of actual CPU cores for Intel HyperThreading enabled CPUs)
project.tasks.withType(Test) {
    maxParallelForks = Runtime.runtime.availableProcessors()
}

apply plugin: 'com.pandora.hydra.android'
hydra {
    balancedTests = ['testReleaseUnitTest']
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

Next is a snippet for a more complicated project, comprised of one or more app modules and associated library modules,
each of which contains unit tests that need to be balanced:
```
def hydraClosure = { someProject ->
    configure(someProject) {
        apply plugin: 'com.pandora.hydra.android'
        hydra {
            balancedTests = ['testReleaseUnitTest']
        }
    }
}

subprojects { nextSubproject ->
    plugins.withType(com.android.build.gradle.AppPlugin) {
        hydraClosure(nextSubproject)
    }

    plugins.withType(com.android.build.gradle.LibraryPlugin) {
        hydraClosure(nextSubproject)
    }
}
```

Please note that you might need to publish plugin jar and its dependencies to your local Nexus/Artifactory or provide it as a flat dir


## Configuration

The plugin can be configured inside of a `hydra { }` configuration block.

+ `balancedTests` is an array of tests that will have a balanced counterpart created. The plugin will create a new task for 
each test is in this list using the naming convention `originalTest_balanced`. The new test task wraps the original and automatically
handles reading the test blacklist and publishing test results to the hydra server

For convenience it is also possible to fully configure a client in the hydra configuration block. While this can be useful for testing
you will generally want to include this configuration in your CI build

+ `hydraServer` - uri of the hydra server
+ `hydraHostList` - list of hosts included in a test run
+ `jobName` - name of the job (on CI server) executing a test run
+ `buildTag` - a unique name associated with a given execution of jobName
+ `slaveName` - the name of the host running the test
