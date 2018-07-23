#!groovy

//We assume we have two subclusters "jenkins-ut0{1..4}" and "jenkins-ut0{5..8}"
//Checking whether one root node has more jobs running than the other
//Please note that root nodes should have 2x executors, as they run root job + 1 stage
def findRootNode() {
    Jenkins jenkins = Jenkins.instance
    Node firstRootNode = jenkins.getNode('jenkins-ut01')
    Node secondRootNode = jenkins.getNode('jenkins-ut05')
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
    for (i = baseNodeNumber + 1; i <= baseNodeNumber + 3; i++ ) {
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
        stage('thydra') {
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
                stage('third') {
                    agent {
                        label getCurrentNode(pm['base'], pm['baseNodeNumber'] + 2)
                    }
                    environment {
                        VM_HOSTNAME = getCurrentNode(pm['base'], pm['baseNodeNumber'] + 2)
                    }
                    steps {
                        deleteDir()
                        unstash 'repo'
                        sh "./gradlew integrationTest_balanced -x test -PisJenkins=true --continue"
                        stash includes: '**/test-results/integrationTest_balanced/TEST*.xml', name: getCurrentNode(pm['base'], pm['baseNodeNumber'] + 2)
                    }
                }
                stage('fourth') {
                    agent {
                        label getCurrentNode(pm['base'], pm['baseNodeNumber'] + 3)
                    }
                    environment {
                        VM_HOSTNAME = getCurrentNode(pm['base'], pm['baseNodeNumber'] + 3)
                    }
                    steps {
                        deleteDir()
                        unstash 'repo'
                        sh "./gradlew integrationTest_balanced -x test -PisJenkins=true --continue"
                        stash includes: '**/test-results/integrationTest_balanced/TEST*.xml', name: getCurrentNode(pm['base'], pm['baseNodeNumber'] + 3)
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
            unstash getCurrentNode(pm['base'], pm['baseNodeNumber'] + 2)
            unstash getCurrentNode(pm['base'], pm['baseNodeNumber'] + 3)
//Publishing JUnit results
            junit allowEmptyResults: true, testResults: '**/TEST*.xml'
//Publishing HTML results
            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'build/reports/tests/integrationTest_balanced', reportFiles: 'index.html', reportName: 'JUnit Report', reportTitles: ''])
        }
    }
}
