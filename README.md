## Hydra ##

Hydra is a Gradle plugin and companion server application that aids in running tests in parallel across a cluster of servers. Hydra relies on Gradle and a CI environment 
to run the tests. Hydra itself consists of a backend server and a client. The backend is
responsible for maintaining a database of test execution time and calculating test partitions that have similar execution time. The client
is responsible for communicating with the backend and limiting a test run to the calculated test partition. 

The ideal use case for Hydra is for cases where a single CI server is overwhelmed when trying to run tests in parallel. We use
Hydra for one of our integration test suites that relies heavily on test databases.      

## Getting Started

In order to run tests on a cluster you will need Hydra and a CI environment (our examples are for Jenkins, but a similar approach 
should work for different environments).

## Hydra Setup

#### Hydra Server

Hydra-server is a Spring Boot application and can be run directly in standalone mode or it can be run as a docker container. 
The server can be run with a file backed storage scheme in standalone mode, but requires a SQL database when run as a container.

[Server Documentation](hydra-server/README.md)

#### Hydra Client

Hydra client can be integrated with an existing Gradle task by using the Gradle plugin.

Add the dependency and apply plugin 

```
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.pandora.hydra:hydra-gradle-plugin:1.6.5'
    }
}

apply plugin: 'com.pandora.hydra'
```

Configure plugin to run against an existing task

``` 
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
   
   
   hydra {
       balancedTests = ['integrationTest']
   }

```

The Gradle plugin will create a new task called `integrationTest_balanced` that can be directly invoked. The new task wraps the original test 
task adding Hydra specific behavior, namely, fetching a test blacklist from the backend, filtering the tests that are run, 
and reporting test execution time to the server. 


#### CI Setup

The CI environment is responsible for performing the heavy lifting of executing jobs in parallel and aggregating the results. 
In practice this is done by having the CI environment kick off, in parallel, a batch of identical jobs. In Jenkins we implemented this first
with a multi-configuration (matrix) build, but later switched to running multiple stages in parallel when we switched to build 
pipelines.  
 
The build script should run the task that is created by the Gradle plugin, e.g. `integrationTest_balanced`. The build script
should also expose environment variables that have the location of the `hydra-server` and a list of nodes that will be involved in the test run. The full list
of environment configurable environment variables can be found [here.](hydra-client/README.md)

After the tests are run, the CI server should aggregate the individual test reports from each node and publish a combined 
report.  


#### Test cluster groups

At some point, increasing the number of physical hosts in a test cluster won't help to decrease overall build time, thus you might start to split the cluster into subgroups
and balance the load between them using custom a Jenkins pipeline.
This pipeline is designed for the multi branch pipeline plugin or BranchSource:

```
//We assume we have two subclusters "jenkins-ut0{1..2}" and "jenkins-ut0{3..4}"
//Checking whether one root node has more jobs running than the other
//Please note that root nodes should have 2x executors, as they run root job + 1 stage
def findRootNode() {
    Jenkins jenkins = Jenkins.instance
    Node firstRootNode = jenkins.getNode('jenkins-ut01')
    Node secondRootNode = jenkins.getNode('jenkins-ut03')
    if (firstRootNode.getComputer().countBusy() < secondRootNode.getComputer().countBusy()) {
        return firstRootNode.getDisplayName()
    } else if (firstRootNode.getComputer().countBusy() > secondRootNode.getComputer().countBusy()) {
        return secondRootNode.getDisplayName()
    } else {
    //It is not true random, because Random is re-initialized every time
    //But we have to use it this way, because otherwise pipeline will keep queuing jobs on 1st subcluster
        int rootNodeNumber = ((new java.util.Random()).nextInt(2) + 1)
        echo "Running on subcluster: ${rootNodeNumber}"
        return rootNodeNumber == 1 ? firstRootNode.getDisplayName() : secondRootNode.getDisplayName()
    }
}

//Generating map of custom params used to configure pipeline
//contains keys rootNode (root node display name), base (base name to generate node names), list (comma-separated list of nodes)
//and baseNodeNumber (as int)
def getGradleParams() {
    def result = [:]
    String rootNode = findRootNode()
    result['rootNode'] = rootNode
    String list = rootNode
    String base = rootNode.substring(0, rootNode.length() - 2)
    result['base'] = base
    int baseNodeNumber = Integer.valueOf(rootNode.substring(rootNode.length() - 2, rootNode.length())).intValue()
    result['baseNodeNumber'] = baseNodeNumber
    for (i = baseNodeNumber + 1; i <= baseNodeNumber + 1; i++ ) {
        if (i < 10) {
            list += ", ${base}0${i}"
        } else {
            list += ", ${base}${i}"
        }
    }
    result['list'] = list
    return result
}

def pm = getGradleParams()

//Util method to generate node names
String getCurrentNode(String baseName, int number) {
    if (number < 10) {
        return baseName + '0' + String.valueOf(number)
    } else {
        return baseName + String.valueOf(number)
    }
}

pipeline {
    //Setting root node as agent to run root job
    agent { label pm['rootNode'] }
    //Polling
    triggers { pollSCM('H/10 * * * 1-5') }
    //Standard options
    options {
        buildDiscarder(logRotator(numToKeepStr:'30'))
        skipDefaultCheckout()
        timeout(time: 20, unit: 'MINUTES')
        timestamps()
    }
    //Server hostname and list of hosts which will run the tests
    //as hydra ties balance to hosts
    environment {
        HYDRA_SERVER = 'hydra'
        HYDRA_HOST_LIST = pm['list'].toString()
    }
    stages {
        //Checking out repo and stashing to save time
        //This relies on Jenkins BranchSource pluing (multibranch pipeline) so does not configure repository
        stage('Checkout') {
             steps {
                deleteDir()
                checkout scm
                stash includes: '**/*', name: 'repo'
            }
        }
        stage('hydra') {
            //Running everything in parallel
            parallel {
                stage('first') {
                    //Dynamically assigning agent to each stage
                    agent {
                        label getCurrentNode(pm['base'], pm['baseNodeNumber'])
                    }
                    environment {
                        VM_HOSTNAME = getCurrentNode(pm['base'], pm['baseNodeNumber'])
                    }
                    steps {
                        //Clean up workspace and unstash repo from root job
                        deleteDir()
                        unstash 'repo'
                        //Running tests (hydra will get distribution from server and start only subgroup of tests automatically) and stashing results
                        sh "./gradlew integrationTest_balanced -x test -PisJenkins=true --continue"
                        stash includes: '**/test-results/integrationTest_balanced/TEST*.xml', name: getCurrentNode(pm['base'], pm['baseNodeNumber'])
                    }
                }
                //Second stage, etc
                stage('second') {
                    agent {
                        label getCurrentNode(pm['base'], pm['baseNodeNumber'] + 1)
                    }
                    environment {
                        VM_HOSTNAME = getCurrentNode(pm['base'], pm['baseNodeNumber'] + 1)
                    }
                    steps {
                        deleteDir()
                        unstash 'repo'
                        sh "./gradlew integrationTest_balanced -x test -PisJenkins=true --continue"
                        stash includes: '**/test-results/integrationTest_balanced/TEST*.xml', name: getCurrentNode(pm['base'], pm['baseNodeNumber'] + 1)
                    }
                }
            }
        }
    }
    post {
        always {
            //Receiving results
            unstash getCurrentNode(pm['base'], pm['baseNodeNumber'])
            unstash getCurrentNode(pm['base'], pm['baseNodeNumber'] + 1)
            //Publishing JUnit results
            junit allowEmptyResults: true, testResults: '**/TEST*.xml'
            //Publishing HTML results
            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'build/reports/tests/integrationTest_balanced', reportFiles: 'index.html', reportName: 'JUnit Report', reportTitles: ''])
        }
    }
}
```
An example for 8 nodes is in `examples/pipelines/Jenkinsfile`
You can also experiment and add more cluster groups.

## Acknowledgements
* Back end is powered by [Spring](http://spring.io/).
* Built by [gradle](http://gradle.org/).
* Tested by [junit](http://junit.org/).
