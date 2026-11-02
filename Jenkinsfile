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

        stage('Test') {
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
            }
        }
    }
}
