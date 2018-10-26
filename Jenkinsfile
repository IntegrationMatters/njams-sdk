#!/usr/bin/env groovy

import groovy.json.JsonOutput

properties([
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')),
    pipelineTriggers([
        //pollSCM('')
    ])
])

node ('master') {
    def scmInfo
    def mvnHome
    env.JAVA_HOME = tool 'jdk-8u92'

    def nodeHome = tool name: 'NodeJS 6.9.1', type: 'jenkins.plugins.nodejs.tools.NodeJSInstallation'
    env.PATH = "${nodeHome}/bin:${env.PATH}"

   stage('Preparation') { // for display purposes
      // Get the Maven tool.
      // ** NOTE: This 'M3' Maven tool must be configured
      // **       in the global configuration.
      mvnHome = tool 'Maven 3.2.1'
      echo 'Getting source code...'
      scmInfo = checkout scm
      echo "scm: ${scmInfo}"
   }
   stage('Build Root Pom') {
       echo "Build the root pom"
       sh "'${mvnHome}/bin/mvn' clean deploy -N -Pjenkins-cli"
   }
   stage('Build SDK') {
        echo "Build"
        dir ('njams-sdk') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy  -Psonar,jenkins-cli -DrevisionNumberPlugin.revision=${env.BUILD_NUMBER} -DscmBranch=${scmInfo.GIT_BRANCH} -DscmCommit=${scmInfo.GIT_COMMIT}"
            } finally {
                junit 'target/surefire-reports/*.xml'
            }
            withSonarQubeEnv('sonar') {
              sh "'${mvnHome}/bin/mvn' org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar"
            }
            archiveArtifacts 'target/*.jar'
        }
   }
   stage('Build cloud communication') {
        echo "Build"
        dir ('njams-sdk-communication-cloud') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy  -Psonar,jenkins-cli -DrevisionNumberPlugin.revision=${env.BUILD_NUMBER} -DscmBranch=${scmInfo.GIT_BRANCH} -DscmCommit=${scmInfo.GIT_COMMIT}"
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
        dir ('njams-sdk-sample-client') {
            try {
                sh "'${mvnHome}/bin/mvn' clean deploy  -Psonar,jenkins-cli"
            } finally {
                //junit 'target/surefire-reports/*.xml'
            }
            archiveArtifacts 'target/*.jar'
        }
   }
   stage('Javadoc') {
       echo "Build Javadoc"
       dir ('njams-sdk') {
	      try {
             sh "'${mvnHome}/bin/mvn' validate -Pcheckstyle "
          } finally {
             archiveArtifacts '**/checkstyle-result.xml'
             step([$class: 'hudson.plugins.checkstyle.CheckStylePublisher', pattern: '**/checkstyle-result.xml', unstableTotalAll:'0'])
          }

          sh "'${mvnHome}/bin/mvn' javadoc:javadoc"

          publishHTML([allowMissing: false,
              alwaysLinkToLastBuild: false,
              keepAll: false,
              reportDir: 'target/site/apidocs/',
              reportFiles: 'index.html',
              reportName: 'Javadoc',
              reportTitles: ''])
       }
   }
}
