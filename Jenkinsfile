pipeline {
    agent any

    parameters {
        string(name: 'GCP_PROJECT_ID', defaultValue: 'project-d7b18774-cce1-4b87-a4a', description: 'GCP Project ID')
        string(name: 'GCP_REGION', defaultValue: 'us-central1', description: 'GCP Region')
        string(name: 'GKE_CLUSTER', defaultValue: 'order-api-cluster', description: 'GKE Cluster Name')
        string(name: 'AR_REPO', defaultValue: 'order-api', description: 'Artifact Registry Repository')
        string(name: 'IMAGE_NAME', defaultValue: 'order-api', description: 'Container Image Name')
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'latest'}"
        IMAGE_URI = "${params.GCP_REGION}-docker.pkg.dev/${params.GCP_PROJECT_ID}/${params.AR_REPO}/${params.IMAGE_NAME}:${IMAGE_TAG}"
        USE_GKE_GCLOUD_AUTH_PLUGIN = 'True'
        DEV1_NS = 'order-api-dev1'
        DEV2_NS = 'order-api-dev2'
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
                                -Namespace '${env.DEV1_NS}' `
                                -Repository '${params.AR_REPO}' `
                                -ImageName '${params.IMAGE_NAME}' `
                                -Tag '${env.IMAGE_TAG}'
                        """
                    }
                }
            }
        }

        stage('Deploy to dev1') {
            when { expression { return isUnix() } }
            steps {
                script { deployEnv(env.DEV1_NS) }
            }
        }

        stage('Approve Switch Live - dev1') {
            when { expression { return isUnix() } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: 'dev1 new version staged (rollout paused). Proceed with SWITCH LIVE? Old pod goes down, new pod takes over.', ok: 'Switch Live'
                }
            }
        }

        stage('Switch Live - dev1') {
            when { expression { return isUnix() } }
            steps {
                script { switchLiveAndSmoke(env.DEV1_NS) }
            }
        }

        stage('Approve Deploy - dev2') {
            when { expression { return isUnix() } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: 'dev1 is live. Proceed with deployment to dev2?', ok: 'Deploy to dev2'
                }
            }
        }

        stage('Deploy to dev2') {
            when { expression { return isUnix() } }
            steps {
                script { deployEnv(env.DEV2_NS) }
            }
        }

        stage('Approve Switch Live - dev2') {
            when { expression { return isUnix() } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    input message: 'dev2 new version staged (rollout paused). Proceed with SWITCH LIVE? Old pod goes down, new pod takes over.', ok: 'Switch Live'
                }
            }
        }

        stage('Switch Live - dev2') {
            when { expression { return isUnix() } }
            steps {
                script { switchLiveAndSmoke(env.DEV2_NS) }
            }
        }
    }
}

def deployEnv(String ns) {
    echo "Staging ${env.IMAGE_URI} into ${ns} (rollout paused - current pod keeps serving until approval)"
    sh """
        gcloud container clusters get-credentials ${params.GKE_CLUSTER} --region=${params.GCP_REGION} --project=${params.GCP_PROJECT_ID}
        kubectl get namespace ${ns} >/dev/null 2>&1 || kubectl create namespace ${ns}
        kubectl rollout resume deployment/order-api -n ${ns} 2>/dev/null || true
        kubectl rollout pause deployment/order-api -n ${ns} 2>/dev/null || true
        sed "s|IMAGE_PLACEHOLDER|${env.IMAGE_URI}|g" k8s/deployment.yaml | kubectl apply -n ${ns} -f -
        kubectl apply -n ${ns} -f k8s/service.yaml
        kubectl get deployment order-api -n ${ns} -o wide
    """
}

def switchLiveAndSmoke(String ns) {
    sh "kubectl rollout resume deployment/order-api -n ${ns} || true"
    sh """
        kubectl rollout status deployment/order-api -n ${ns} --timeout=10m
        kubectl wait --for=condition=ready pod -l app=order-api -n ${ns} --timeout=5m
        kubectl get endpoints order-api -n ${ns}
    """
    String ip = ''
    for (int i = 0; i < 30 && !ip; i++) {
        ip = sh(returnStdout: true,
                script: "kubectl get service order-api -n ${ns} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'").trim()
        if (!ip) { sleep time: 10, unit: 'SECONDS' }
    }
    if (!ip) { error "LoadBalancer IP not assigned for ${ns}" }
    echo "SWITCHED LIVE: ${ns} -> ${env.IMAGE_URI} @ http://${ip}"
    sh """
        set -e
        healthy=""
        for i in \$(seq 1 30); do
            if curl -fs "http://${ip}/actuator/health" 2>/dev/null | grep -q UP; then healthy=1; break; fi
            sleep 10
        done
        [ -n "\$healthy" ] || { echo "SMOKE FAILED: ${ns} /actuator/health"; exit 1; }
        curl -fs "http://${ip}/api/orders/1001" | grep -q '"orderId":1001' || { echo "SMOKE FAILED: ${ns} /api/orders/1001"; exit 1; }
        echo "SMOKE PASSED (${ns}): http://${ip}/api/orders/1001"
    """
}
