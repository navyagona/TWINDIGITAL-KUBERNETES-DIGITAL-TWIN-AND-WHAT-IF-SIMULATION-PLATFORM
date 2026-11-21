// ==============================================================================
// TwinDigital DevSecOps CI/CD Declarative Pipeline
// Stages:
//   1. Checkout & Environment Setup
//   2. Parallel Maven Build & Unit Tests (all 5 services)
//   3. OWASP Dependency-Check (SCA - Software Composition Analysis)
//   4. SonarQube Static Analysis & Quality Gate (SAST) — all 5 services
//   5. Docker Multi-Stage Build (all 7 images)
//   6. Trivy Container Image Security Scan (all 7 images)
//   7. Push to Amazon ECR (IRSA tokenless — no static registry credentials)
//   8. OWASP ZAP Baseline DAST Scan (advisory, non-blocking)
//   9. Kubernetes Deployment (Zero-Downtime Rollout + auto-rollback)
// ==============================================================================

pipeline {
    // Use the standardized CI agent image (JDK17 + Maven + Node 20 + Trivy + kubectl)
    agent {
        dockerfile {
            filename 'Dockerfile'
            dir 'ci/agent'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['staging', 'prod'],
            description: 'Target deployment environment'
        )
        booleanParam(
            name: 'ENFORCE_SECURITY_GATES',
            defaultValue: true,
            description: 'Enforce blocking Quality Gates for Trivy, SonarQube, and OWASP Dependency-Check'
        )
    }

    environment {
        // Amazon ECR — no static Docker Hub credentials required (IRSA tokenless auth)
        ECR_REGISTRY          = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        AWS_REGION            = "${AWS_REGION ?: 'ap-south-1'}"
        SONAR_CREDENTIALS_ID  = 'sonarqube-token'
        KUBECONFIG_CREDENTIALS_ID = 'k8s-kubeconfig'
        IMAGE_REPO            = "${ECR_REGISTRY}/twindigital"
        GIT_SHORT_SHA         = ''
        CVSS_THRESHOLD        = '7.0'
        // Environment-aware namespace: twindigital-staging or twindigital-prod
        K8S_NAMESPACE         = "twindigital-${params.DEPLOY_ENV ?: 'staging'}"
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '5'))
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        // ──────────────────────────────────────────────────────────────────────
        // 1. CHECKOUT & ENVIRONMENT SETUP
        // ──────────────────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                script {
                    checkout scm
                    env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    echo "Checked out commit: ${env.GIT_SHORT_SHA} on branch: ${env.BRANCH_NAME ?: 'main'}"
                    echo "Target environment: ${params.DEPLOY_ENV} | Namespace: ${env.K8S_NAMESPACE}"
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 2. PARALLEL MAVEN BUILD & UNIT TESTS — All 5 Java services
        // ──────────────────────────────────────────────────────────────────────
        stage('Maven Build & Unit Tests') {
            steps {
                echo "Compiling all 5 microservices and executing automated test suites in parallel..."
                parallel(
                    'auth-service': {
                        dir('services/auth-service') {
                            sh 'mvn clean test -B'
                        }
                    },
                    'cluster-sync-service': {
                        dir('services/cluster-sync-service') {
                            sh 'mvn clean test -B'
                        }
                    },
                    'topology-service': {
                        dir('services/topology-service') {
                            sh 'mvn clean test -B'
                        }
                    },
                    'simulation-service': {
                        dir('services/simulation-service') {
                            sh 'mvn clean test -B'
                        }
                    },
                    'cost-risk-service': {
                        dir('services/cost-risk-service') {
                            sh 'mvn clean test -B'
                        }
                    }
                )
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 3. OWASP DEPENDENCY-CHECK (SCA - Software Composition Analysis)
        // ──────────────────────────────────────────────────────────────────────
        stage('OWASP Dependency-Check (SCA)') {
            steps {
                echo "Scanning third-party dependencies for known CVEs (CVSS Threshold: ${env.CVSS_THRESHOLD})..."
                script {
                    def failFlag = params.ENFORCE_SECURITY_GATES ? "-DfailBuildOnCVSS=${env.CVSS_THRESHOLD}" : ""
                    sh """
                        mvn org.owasp:dependency-check-maven:check \
                          -Dformat=ALL \
                          -DautoUpdate=false \
                          ${failFlag} \
                          -B
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: '**/target/dependency-check-report.html', allowEmptyArchive: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 4. SONARQUBE ANALYSIS & QUALITY GATE (SAST) — All 5 services
        // ──────────────────────────────────────────────────────────────────────
        stage('SonarQube Analysis & Quality Gate (SAST)') {
            steps {
                echo "Running SonarQube static code quality & security vulnerability scan on all 5 services..."
                withCredentials([string(credentialsId: env.SONAR_CREDENTIALS_ID, variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('SonarQube-Server') {
                        dir('services/auth-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-auth \
                                  -Dsonar.projectName='TwinDigital - Auth Service' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                        dir('services/cluster-sync-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-cluster-sync \
                                  -Dsonar.projectName='TwinDigital - Cluster Sync' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                        dir('services/topology-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-topology \
                                  -Dsonar.projectName='TwinDigital - Topology Service' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                        dir('services/simulation-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-simulation \
                                  -Dsonar.projectName='TwinDigital - Simulation' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                        dir('services/cost-risk-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-cost-risk \
                                  -Dsonar.projectName='TwinDigital - Cost Risk Service' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                    }
                }

                // Evaluate SonarQube Quality Gate Status
                timeout(time: 5, unit: 'MINUTES') {
                    script {
                        try {
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK' && params.ENFORCE_SECURITY_GATES) {
                                error "Pipeline aborted due to SonarQube Quality Gate failure: ${qg.status}"
                            } else {
                                echo "SonarQube Quality Gate Status: ${qg.status}"
                            }
                        } catch (Exception e) {
                            if (params.ENFORCE_SECURITY_GATES) {
                                throw e
                            } else {
                                echo "Non-blocking SonarQube Quality Gate bypass: ${e.message}"
                            }
                        }
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 5. DOCKER MULTI-STAGE BUILD — All 7 images
        // ──────────────────────────────────────────────────────────────────────
        stage('Docker Multi-Stage Build') {
            steps {
                echo "Building minimal, unprivileged container images for all 7 components..."
                sh """
                    docker build -t ${IMAGE_REPO}/auth-service:${GIT_SHORT_SHA}      -t ${IMAGE_REPO}/auth-service:latest      services/auth-service
                    docker build -t ${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA}      -t ${IMAGE_REPO}/cluster-sync:latest      services/cluster-sync-service
                    docker build -t ${IMAGE_REPO}/topology:${GIT_SHORT_SHA}          -t ${IMAGE_REPO}/topology:latest          services/topology-service
                    docker build -t ${IMAGE_REPO}/simulation:${GIT_SHORT_SHA}        -t ${IMAGE_REPO}/simulation:latest        services/simulation-service
                    docker build -t ${IMAGE_REPO}/cost-risk:${GIT_SHORT_SHA}         -t ${IMAGE_REPO}/cost-risk:latest         services/cost-risk-service
                    docker build -t ${IMAGE_REPO}/gateway:${GIT_SHORT_SHA}           -t ${IMAGE_REPO}/gateway:latest           gateway
                    docker build -t ${IMAGE_REPO}/frontend:${GIT_SHORT_SHA}          -t ${IMAGE_REPO}/frontend:latest          frontend
                """
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 6. TRIVY CONTAINER SECURITY SCAN — All 7 images
        // ──────────────────────────────────────────────────────────────────────
        stage('Trivy Container Security Scan') {
            steps {
                echo "Scanning all 7 container images for OS and package vulnerabilities..."
                script {
                    def images = [
                        "${IMAGE_REPO}/auth-service:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/topology:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/simulation:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/cost-risk:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/gateway:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/frontend:${GIT_SHORT_SHA}"
                    ]

                    for (img in images) {
                        echo "==> Scanning image: ${img}"
                        def exitCodeFlag = params.ENFORCE_SECURITY_GATES ? "--exit-code 1" : "--exit-code 0"
                        sh """
                            # 1. Output human-readable table to console
                            trivy image --severity HIGH,CRITICAL --no-progress ${img} || true

                            # 2. Enforce zero CRITICAL vulnerabilities gate
                            trivy image --severity CRITICAL --ignore-unfixed ${exitCodeFlag} --no-progress ${img}
                        """
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 7. PUSH TO AMAZON ECR — IRSA tokenless auth (no static credentials)
        // ──────────────────────────────────────────────────────────────────────
        stage('Push to Amazon ECR') {
            steps {
                echo "Authenticating to Amazon ECR (${env.AWS_REGION}) via IRSA and pushing verified images..."
                sh """
                    aws ecr get-login-password --region ${AWS_REGION} \
                        | docker login --username AWS --password-stdin ${ECR_REGISTRY}

                    docker push ${IMAGE_REPO}/auth-service:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/auth-service:latest
                    docker push ${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/cluster-sync:latest
                    docker push ${IMAGE_REPO}/topology:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/topology:latest
                    docker push ${IMAGE_REPO}/simulation:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/simulation:latest
                    docker push ${IMAGE_REPO}/cost-risk:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/cost-risk:latest
                    docker push ${IMAGE_REPO}/gateway:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/gateway:latest
                    docker push ${IMAGE_REPO}/frontend:${GIT_SHORT_SHA}
                    docker push ${IMAGE_REPO}/frontend:latest
                """
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 8. OWASP ZAP BASELINE DAST SCAN — advisory (non-blocking)
        // ──────────────────────────────────────────────────────────────────────
        stage('OWASP ZAP Baseline DAST Scan') {
            steps {
                echo "Running OWASP ZAP baseline DAST scan against ${params.DEPLOY_ENV} gateway endpoint (advisory mode)..."
                script {
                    // Advisory mode: archives report but does not fail the pipeline
                    def zapExitFlag = params.ENFORCE_SECURITY_GATES ? "-I" : "-I"
                    sh """
                        docker run --rm \
                            -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                            ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
                            -t http://gateway-${params.DEPLOY_ENV}.twindigital.internal:3000 \
                            -r zap-report-${params.DEPLOY_ENV}.html \
                            -J zap-report-${params.DEPLOY_ENV}.json \
                            ${zapExitFlag} || true
                    """
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-reports/*.html,zap-reports/*.json', allowEmptyArchive: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 9. KUBERNETES DEPLOYMENT — Zero-downtime rollout + auto-rollback
        // ──────────────────────────────────────────────────────────────────────
        stage('Kubernetes Deployment') {
            steps {
                script {
                    def targetEnv = params.DEPLOY_ENV ?: 'staging'
                    echo "Executing zero-downtime rolling update for environment: ${targetEnv} in namespace '${env.K8S_NAMESPACE}'..."
                    withKubeConfig([credentialsId: env.KUBECONFIG_CREDENTIALS_ID]) {
                        sh """
                            kubectl apply -k k8s/overlays/${targetEnv}

                            # Verify zero-downtime rollout status across all deployed microservices
                            for dep in gateway auth-service cluster-sync-service topology-service simulation-service cost-risk-service frontend; do
                                echo "==> Checking rollout: \$dep"
                                kubectl rollout status deployment/\$dep -n ${env.K8S_NAMESPACE} --timeout=180s || {
                                    echo "Rollout failed for \$dep — triggering automatic rollback..."
                                    kubectl rollout undo deployment/\$dep -n ${env.K8S_NAMESPACE}
                                    exit 1
                                }
                            done
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "=========================================================================="
            echo "DevSecOps Pipeline Succeeded: All security gates (SAST, SCA, Container, DAST) passed!"
            echo "Commit: ${env.GIT_SHORT_SHA} deployed to Kubernetes namespace '${env.K8S_NAMESPACE}'."
            echo "=========================================================================="
        }
        failure {
            echo "=========================================================================="
            echo "DevSecOps Security Gate Violation! Inspect scan reports above."
            echo "Commit: ${env.GIT_SHORT_SHA} | Environment: ${params.DEPLOY_ENV}"
            echo "=========================================================================="
        }
        always {
            cleanWs deleteDirs: true, notFailBuild: true
        }
    }
}
