@Library('enterprise-cicd@main') _

// Quality Gates (remove any gate from QG to skip it in Jenkins):
//   QG1 = Unit Tests
//   QG2 = Integration Tests
//   QG3 = Coverage Gate (JaCoCo)
//   QG4 = Security Scan (Snyk)
//
// Promotion Environments (each maps to its own GCP Project & GKE Cluster in shared-library resources/environments.json):
//   Available: dev1, dev2, test, perf, stage, prod
springGkePipeline(
    appName:     'order-api',
    QG:          ['QG1', 'QG2', 'QG3', 'QG4'],
    promoteTo:   ['dev1', 'dev2'],
    healthPath:  '/actuator/health',
    smokePath:   '/api/orders/1001',
    smokeExpect: '"orderId":1001'
)



