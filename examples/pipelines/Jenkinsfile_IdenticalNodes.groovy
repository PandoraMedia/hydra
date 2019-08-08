// The primary use case for this pipeline is if you *don't* want to reserve specific machines *exclusively* for running tests.
// For example, you might have a pool of machines that are all equally capable of running the tests as well as other arbitrary
// jobs. With this pipeline, Jenkins will choose _N_ of these available nodes via its normal scheduling algorithms, leaving them
// free to perform other work when no tests need to be executed.
// 
// This code assumes the following:
// * You have a pool of machines with  _identical_ hardware;
// * Each of those machines has _exactly_ one executor;
// * All of these machines share a common label

artifactsToPreserveFromEachNode = '**/TEST-*.xml'
numNodes = 2

def createEnvVarMap() {
    return [
       "HYDRA_HOST_LIST=" + (1..numNodes).collect { "node${it}" }.join(','),
       "HYDRA_SERVER=CHANGE_THIS" // URL of your hydra service, e.g. https://your-hydraserver.yourdomain.com
    ]
}

def checkoutSource() {
    // Checkout the source code as you normally would
}

def intRangeToString(int lowerBoundInclusive, int upperBoundInclusive) {
    List<String> range = []
    for (int i = lowerBoundInclusive; i <= upperBoundInclusive; i++) {
        range += i.toString()
    }
    return range
}

def createParallelStages() {
    def nodeLabel = 'CHANGE_THIS' // Label that is common to all of your identically-configured test machines
    return ['failFast': true] + intRangeToString(1, numNodes).collectEntries { buildNum -> 
        [("Executor$buildNum") : {
            node(nodeLabel) {
                withEnv(createEnvVarMap() + "VM_HOSTNAME=node${buildNum}") {
                    stage("Test$buildNum") {
                        deleteDir()
                        checkoutSource()
                        sh "CHANGE_THIS" // Command line to execute tests via Hydra, e.g. './gradlew testReleaseUnitTest_balanced'
                        stash includes: artifactsToPreserveFromEachNode, name: "Executor$buildNum"
                    }
                }
            }
        }]
    }
}

pipeline {
    agent { label 'jenkins' }
    options {
        timestamps()
    }
    stages {
        stage('Test') {
            steps {
                script {
                    parallel(createParallelStages())
                }
            }
        }
        stage('Results') {
            steps {
                script {
                    for (i = 1; i <= getNumNodes(); i++) {
                        dir ("Executor$i") {
                            deleteDir()
                            unstash name: "Executor$i"
                        }
                    }
                    // Due to a longstanding bug in Jenkins JUnit plugin, we need to adjust the timestamps for all test result files.
                    // See here: https://issues.jenkins-ci.org/browse/JENKINS-6268?page=com.atlassian.jira.plugin.system.issuetabpanels%3Aall-tabpanel
                    sh "find . -type f -name 'TEST-*.xml' -exec touch {} \\;"
                    junit allowEmptyResults: true, testResults: '**/TEST-*.xml'
                    archiveArtifacts artifacts: artifactsToPreserveFromEachNode
                }
            }
        }
    }
}
