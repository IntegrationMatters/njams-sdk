#!/usr/bin/env groovy
properties([
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')),
    pipelineTriggers([
        //pollSCM('')
    ])
])

node('master') {
    def version = '4.2.2-SNAPSHOT'
    def branch
    def scmInfo
    def mvnHome
    env.JAVA_HOME = tool 'jdk-8u92'
    def nodeHome = tool name: 'NodeJS 6.9.1', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
    env.PATH = "${nodeHome}/bin:${env.PATH}"

    stage('Preparation') { // for display purposes
        // Get the Maven tool.
        // ** NOTE: This 'M3' Maven tool must be configured
        // **       in the global configuration.
        mvnHome = tool 'Maven 3.8.5'
        echo 'Getting source code...'
        scmInfo = checkout scm
        echo "scm: ${scmInfo}"
        branch = "-${scmInfo.GIT_BRANCH}"
    }
    stage('Build Root Pom') {
        echo "Build the root pom"
        sh "'${mvnHome}/bin/mvn' clean deploy -N -Pjenkins-cli -Drevision=${version} -Dchangelist=${branch}"
    }
    stage('Build SDK') {
        echo "Build"
        dir('njams-sdk') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy  -Psonar,jenkins-cli -Drevision=${version} -Dchangelist=-${scmInfo.GIT_BRANCH} -DrevisionNumberPlugin.revision=${env.BUILD_NUMBER} -DscmBranch=${scmInfo.GIT_BRANCH} -DscmCommit=${scmInfo.GIT_COMMIT}"
            } finally {
                junit 'target/surefire-reports/*.xml'
                junit allowEmptyResults: true, testResults: 'target/failsafe-reports/*.xml'
            }
            withSonarQubeEnv('sonar') {
                sh "'${mvnHome}/bin/mvn' org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar"
            }
            archiveArtifacts 'target/*.jar'
        }
    }
    stage('Build cloud communication') {
        echo "Build"
        dir('njams-sdk-communication-cloud') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy -U  -Psonar,jenkins-cli -Drevision=${version} -Dchangelist=-${scmInfo.GIT_BRANCH} -DrevisionNumberPlugin.revision=${env.BUILD_NUMBER} -DscmBranch=${scmInfo.GIT_BRANCH} -DscmCommit=${scmInfo.GIT_COMMIT}"
            } finally {
                //junit 'target/surefire-reports/*.xml'
            }
            withSonarQubeEnv('sonar') {
                sh "'${mvnHome}/bin/mvn' org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar"
            }
            archiveArtifacts 'target/*.jar'
        }
    }
    stage('Build client sample') {
        echo "Build"
        dir('njams-sdk-sample-client') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy  -Psonar,jenkins-cli -Drevision=${version} -Dchangelist=-${scmInfo.GIT_BRANCH}"
            } finally {
                //junit 'target/surefire-reports/*.xml'
            }
            archiveArtifacts 'target/*.jar'
        }
    }
    stage('Checkstyle') {
        echo "Build Checkstyle"
        dir('njams-sdk') {
            try {
                sh "'${mvnHome}/bin/mvn' site -Drevision=${version} -Dchangelist=-${scmInfo.GIT_BRANCH}"
            } finally {
                archiveArtifacts '**/checkstyle-result.xml'
            }

            publishHTML([allowMissing         : false,
                         alwaysLinkToLastBuild: true,
                         keepAll              : false,
                         reportDir            : 'target/site/',
                         reportFiles          : 'checkstyle.html',
                         reportName           : 'Checkstyle results',
                         reportTitles         : ''])

            // run again to fail on error
            sh "'${mvnHome}/bin/mvn' validate -Pcheckstyle "

        }
    }
    stage('Javadoc') {
        echo "Build Javadoc"
        dir('njams-sdk') {
            sh "'${mvnHome}/bin/mvn' javadoc:javadoc -Drevision=${version} -Dchangelist=-${scmInfo.GIT_BRANCH}"

            publishHTML([allowMissing         : false,
                         alwaysLinkToLastBuild: true,
                         keepAll              : false,
                         reportDir            : 'target/site/apidocs/',
                         reportFiles          : 'index.html',
                         reportName           : 'Javadoc',
                         reportTitles         : ''])
        }
    }

    stage('Trivy scan') {
        sh "docker build --progress=plain . -t im/sdk_trivy:latest"
    }
}
