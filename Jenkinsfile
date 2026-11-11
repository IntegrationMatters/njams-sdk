#!/usr/bin/env groovy

pipeline {
    agent { label 'master' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        // git-commit-id-maven-plugin resolves git.branch from a symbolic ref. A multibranch checkout
        // leaves a detached HEAD, where there is none and the plugin falls back to the full commit SHA.
        // Its CI rescue path reads GIT_LOCAL_BRANCH, which a declarative pipeline does not export by
        // itself, so it is set here from the branch the pipeline already knows.
        GIT_LOCAL_BRANCH = "${env.BRANCH_NAME}"
    }

    // Triggering is handled by the (multibranch) job configuration via webhook /
    // branch indexing, so no explicit `triggers` block is required.

    // Build and tests run on Java 11; only the Sonar analysis stage switches to a newer JDK.
    tools {
        jdk 'openJDK-11.0.20'
        maven 'Maven 3.9.6'
    }

    stages {
        // Build the whole reactor once. This also compiles the sample modules,
        // which is what verifies they still build against the current SDK, and runs the
        // njams-sdk-jms-namespace-tests modules, which check that the SDK selects the JMS
        // implementation matching the JMS API actually on the classpath (javax only, jakarta
        // only, both at once, and neither). Neither group is published
        // (maven.deploy.skip=true in their POMs).
        stage('Build') {
            steps {
                // Commit and branch are resolved by git-commit-id-maven-plugin from the checkout
                // itself, the branch via the GIT_LOCAL_BRANCH set above. Neither needs to be passed
                // in, so they are no longer lost in the deploy stage below.
                // The sonarcloud profile attaches the JaCoCo agent and writes the coverage reports
                // the Sonar analysis stage consumes.
                sh 'mvn clean install -Psonarcloud'
            }
            post {
                always {
                    junit 'njams-sdk/target/surefire-reports/*.xml'
                    junit allowEmptyResults: true, testResults: 'njams-sdk/target/failsafe-reports/*.xml'
                    archiveArtifacts 'njams-sdk/target/*.jar'
                }
            }
        }

        // Analyses njams-sdk only, reusing the classes and coverage reports of the Build stage (no clean).
        // The SonarCloud scanner needs a newer Java runtime than the build, hence the stage-level JDK.
        stage('Sonar analysis') {
            tools {
                jdk 'openJDK-25'
            }
            steps {
                withCredentials([string(credentialsId: 'sonar-cloud-token', variable: 'SONAR_TOKEN')]) {
                    sh 'mvn -Psonarcloud -f njams-sdk/pom.xml org.sonarsource.scanner.maven:sonar-maven-plugin:sonar'
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
        // Only njams-sdk and the root POM are published; the sample modules and the
        // njams-sdk-jms-namespace-tests modules are skipped via maven.deploy.skip in their POMs.
        stage('Deploy to Nexus') {
            when {
                branch pattern: '^(master|[0-9]+\\.[0-9]+\\.X|[0-9]+\\.[0-9]+(\\.[0-9]+)?-dev)$',
                       comparator: 'REGEXP'
            }
            steps {
                // Reuse the artifacts already compiled and tested above: no `clean`
                // and tests are not re-run.
                sh 'mvn deploy -DskipTests'
            }
        }
    }
}
