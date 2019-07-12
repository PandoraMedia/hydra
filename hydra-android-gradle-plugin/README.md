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


Here is a simple example for a project comprised of a single app module which contains all the tests.
The following changes should be made to your root-level build.gradle:  
```
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
    dependencies {
        // If you are using Android Gradle Plug-in < 3.4, change the below versions to 1.7.5
        classpath 'com.pandora.hydra:hydra-android-gradle-plugin:2.0.0'
        classpath "com.pandora.hydra:hydra-gradle-plugin:2.0.0"
    }
}
```
Then make these changes to your app-level build.gradle:
```
apply plugin: 'com.android.application'
// This enables tests to be parallelized across N processes on each machine, where N = # of logical CPU cores.
// Note that on CPUs which support hyperthreading, this will result in 2x the # of actual CPU cores.
project.tasks.withType(Test) {
    maxParallelForks = Runtime.runtime.availableProcessors()
}

apply plugin: 'com.pandora.hydra.android'
hydra {
    balancedTests = ['testReleaseUnitTest']
}

android {
  // All the usual Android-specific configuration 
  ...
}

```

Here is an example for a more complicated project, comprised of one or more app modules that depend on multiple 
libraries (both Android and Java), each of which contains unit tests that need to be balanced.
Make the following changes in your _root-level_ build.gradle:
```
def hydraClosure = { someProject ->
    configure(someProject) {
        if (it.plugins.findPlugin('com.android.application') != null ||
            it.plugins.findPlugin('com.android.library') != null) {
            // Use the Android-specific Hydra plug-in for Android apps and libraries...
            apply plugin: 'com.pandora.hydra.android'
        }
        else if (it.plugins.findPlugin('java') != null) {
            // ...use the 'generic' Hydra plug-in for plain Java projects
            apply plugin: 'com.pandora.hydra'
        }

        // Regardless of which plug-in was applied, set Hydra-specific properties here:
        if (it.hasProperty('hydra')) {
            hydra {
                balancedTests = ['testReleaseUnitTest']
            }            
        }
    }
}

def unitTestsClosure = {
    // You can configure arbitrary unit test properties in here, for both Android and Java projects.
    // Some other examples might include test inclusion/exclusion filters and JVM arguments.

    // Specify max number of processes on each machine.
    it.maxParallelForks = Runtime.runtime.availableProcessors()
}

subprojects { nextSubproject ->
    plugins.withType(com.android.build.gradle.AppPlugin) {
        hydraClosure(nextSubproject)
    }

    plugins.withType(com.android.build.gradle.LibraryPlugin) {
        hydraClosure(nextSubproject)
    }
}

// The following closure will consistently apply test-related configuration to all of your subprojects recursively:
subprojects { nextSubproject ->
    plugins.whenPluginAdded { nextPlugin ->
        switch (nextPlugin.class.name) {
            case 'com.android.build.gradle.AppPlugin':
            case 'com.android.build.gradle.LibraryPlugin':
                hydraClosure(nextSubproject)
                nextSubproject.android.testOptions.unitTests.all {
                    unitTestsClosure(it)
                }
                break
            case JavaPlugin.name:
                hydraClosure(nextSubproject)
                nextSubproject.test {
                    unitTestsClosure(it)
                }
                break
        }
    }
}
```

If you need to make changes to the plugin, you can test them locally by publishing your modified artifacts to your 
local Maven repository via:
```
./gradlew -PsonatypeUsername=DOESNOTMATTER -PsonatypePassword=DOESNOTMATTER publishM
```
After doing so, change the versions of the Hydra plug-ins you specified previously to 2.0.0-SNAPSHOT.


## Configuration

The plugin can be configured inside of a `hydra { }` configuration block. For more details, refer to the Configuration
section of `../hydra-gradle-plugin/README.md`
