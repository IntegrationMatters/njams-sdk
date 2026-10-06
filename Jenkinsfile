#!/usr/bin/env groovy

pipeline {
    agent { label 'master' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    // Triggering is handled by the (multibranch) job configuration via webhook /
    // branch indexing, so no explicit `triggers` block is required.

    tools {
        jdk 'openJDK-11.0.20'
        maven 'Maven 3.8.5'
        nodejs 'NodeJS 6.9.1'      // PATH is extended automatically by the tools block
    }

    stages {
        // Build the whole reactor once. This also compiles the sample modules,
        // which is what verifies they still build against the current SDK. The
        // samples are not published (maven.deploy.skip=true in their POMs).
        stage('Build') {
            steps {
                sh """mvn clean install -Pjenkins-cli \
                    -DrevisionNumberPlugin.revision=${env.BUILD_NUMBER} \
                    -DscmBranch=${env.BRANCH_NAME} \
                    -DscmCommit=${env.GIT_COMMIT}"""
            }
            post {
                always {
                    junit 'njams-sdk/target/surefire-reports/*.xml'
                    junit allowEmptyResults: true, testResults: 'njams-sdk/target/failsafe-reports/*.xml'
                    archiveArtifacts 'njams-sdk/target/*.jar'
                }
            }
        }

        stage('Checkstyle') {
            steps {
                dir('njams-sdk') {
                    sh 'mvn site'
                    publishHTML([allowMissing         : false,
                                 alwaysLinkToLastBuild: true,
                                 keepAll              : false,
                                 reportDir            : 'target/site/',
                                 reportFiles          : 'checkstyle.html',
                                 reportName           : 'Checkstyle results',
                                 reportTitles         : ''])
                    // run again to fail on error
                    sh 'mvn validate -Pcheckstyle'
                }
            }
            post {
                always {
                    archiveArtifacts '**/checkstyle-result.xml'
                }
            }
        }

        stage('Javadoc') {
            steps {
                dir('njams-sdk') {
                    sh 'mvn javadoc:javadoc'
                    publishHTML([allowMissing         : false,
                                 alwaysLinkToLastBuild: true,
                                 keepAll              : false,
                                 reportDir            : 'target/reports/apidocs/',
                                 reportFiles          : 'index.html',
                                 reportName           : 'Javadoc',
                                 reportTitles         : ''])
                }
            }
        }

        stage('Trivy scan') {
            steps {
                sh 'docker build --progress=plain . -t im/sdk_trivy:latest'
            }
        }

        // Single, self-documenting deploy gate. Only branches that own a SNAPSHOT version
        // may publish to Nexus, so that a feature/bugfix branch (which still carries the
        // version it was branched from) can never overwrite the SNAPSHOT of its version branch.
        // To change which branches publish, edit only the `branch` pattern below.
        // `when { branch }` matches the bare branch name, so it is not broken by an 'origin/'
        // prefix; REGEXP is used because the default GLOB is too loose (a '*-dev' glob would
        // also match a feature branch like 'foo-dev').
        // Matched:     master, 5.0.X, 6.1.X (maintenance), 6.0-dev, 6.0.1-dev (development)
        // Not matched: fix123, SDK-375, bug-SDK-415, PR-12
        // Only njams-sdk and the root POM are published; the sample modules are
        // skipped via maven.deploy.skip in their POMs.
        stage('Deploy to Nexus') {
            when {
                branch pattern: '^(master|[0-9]+\\.[0-9]+\\.X|[0-9]+\\.[0-9]+(\\.[0-9]+)?-dev)$',
                       comparator: 'REGEXP'
            }
            steps {
                // Reuse the artifacts already compiled and tested above: no `clean`
                // and tests are not re-run.
                sh 'mvn deploy -Pjenkins-cli -DskipTests'
            }
        }
    }
}
