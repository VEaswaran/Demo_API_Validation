pipeline {
    agent any

    parameters {
        string(name: 'GCP_PROJECT_ID', defaultValue: 'project-d7b18774-cce1-4b87-a4a', description: 'GCP Project ID')
        string(name: 'GCP_REGION', defaultValue: 'us-central1', description: 'GCP Region')
        string(name: 'GKE_CLUSTER', defaultValue: 'order-api-cluster', description: 'GKE Cluster Name')
        string(name: 'K8S_NAMESPACE', defaultValue: 'order-api', description: 'Kubernetes Namespace')
        string(name: 'AR_REPO', defaultValue: 'order-api', description: 'Artifact Registry Repository')
        string(name: 'IMAGE_NAME', defaultValue: 'order-api', description: 'Container Image Name')
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'latest'}"
        IMAGE_URI = "${params.GCP_REGION}-docker.pkg.dev/${params.GCP_PROJECT_ID}/${params.AR_REPO}/${params.IMAGE_NAME}:${IMAGE_TAG}"
        USE_GKE_GCLOUD_AUTH_PLUGIN = 'True'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Unit Tests') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x gradlew && ./gradlew clean test --no-daemon'
                    } else {
                        bat 'gradlew.bat clean test --no-daemon'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew integrationTest --no-daemon'
                    } else {
                        bat 'gradlew.bat integrationTest --no-daemon'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'build/test-results/integrationTest/*.xml'
                }
            }
        }

        stage('Coverage Gate') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew jacocoTestReport jacocoTestCoverageVerification --no-daemon'
                    } else {
                        bat 'gradlew.bat jacocoTestReport jacocoTestCoverageVerification --no-daemon'
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/reports/jacoco/test/html/**', allowEmptyArchive: true
                }
            }
        }

        stage('Build & Push Image') {
            steps {
                script {
                    if (isUnix()) {
                        sh """
                            gcloud config set project ${params.GCP_PROJECT_ID}
                            gcloud builds submit --tag ${env.IMAGE_URI} .
                        """
                    } else {
                        powershell """
                            \\$ErrorActionPreference = 'Stop'
                            .\\deploy-gke.ps1 `
                                -ProjectId '${params.GCP_PROJECT_ID}' `
                                -Region '${params.GCP_REGION}' `
                                -ClusterName '${params.GKE_CLUSTER}' `
                                -Namespace '${params.K8S_NAMESPACE}' `
                                -Repository '${params.AR_REPO}' `
                                -ImageName '${params.IMAGE_NAME}' `
                                -Tag '${env.IMAGE_TAG}'
                        """
                    }
                }
            }
        }

        stage('Deploy to GKE') {
            when {
                expression { return isUnix() }
            }
            steps {
                sh """
                    gcloud container clusters get-credentials ${params.GKE_CLUSTER} --region=${params.GCP_REGION} --project=${params.GCP_PROJECT_ID}
                    kubectl get namespace ${params.K8S_NAMESPACE} >/dev/null 2>&1 || kubectl create namespace ${params.K8S_NAMESPACE}
                    sed "s|IMAGE_PLACEHOLDER|${env.IMAGE_URI}|g" k8s/deployment.yaml | kubectl apply -n ${params.K8S_NAMESPACE} -f -
                    kubectl apply -n ${params.K8S_NAMESPACE} -f k8s/service.yaml
                    kubectl rollout status deployment/order-api -n ${params.K8S_NAMESPACE} --timeout=10m
                    kubectl get service order-api -n ${params.K8S_NAMESPACE}
                """
                script {
                    env.APP_IP = ''
                    for (int i = 0; i < 30 && !env.APP_IP; i++) {
                        env.APP_IP = sh(returnStdout: true,
                                script: "kubectl get service order-api -n ${params.K8S_NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'").trim()
                        if (!env.APP_IP) { sleep time: 10, unit: 'SECONDS' }
                    }
                    if (!env.APP_IP) { error 'LoadBalancer external IP was not assigned in time' }
                    echo "Service external IP: ${env.APP_IP}"
                }
            }
        }

        stage('Smoke Test') {
            when {
                expression { return isUnix() }
            }
            steps {
                sh '''
                    set -e
                    healthy=""
                    for i in $(seq 1 30); do
                        if curl -fs "http://$APP_IP/actuator/health" 2>/dev/null | grep -q UP; then healthy=1; break; fi
                        sleep 10
                    done
                    [ -n "$healthy" ] || { echo "SMOKE FAILED: /actuator/health never reported UP"; exit 1; }
                    curl -fs "http://$APP_IP/api/orders/1001" | grep -q '"orderId":1001' \
                        || { echo "SMOKE FAILED: GET /api/orders/1001"; exit 1; }
                    echo "SMOKE PASSED: http://$APP_IP/api/orders/1001"
                '''
            }
        }
    }
}
