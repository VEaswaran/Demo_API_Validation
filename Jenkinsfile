@Library('enterprise-cicd@main') _

// Quality Gates (remove any gate from QG to skip it in Jenkins):
//   QG1 = Unit Tests
//   QG2 = Integration Tests
//   QG3 = Coverage Gate (JaCoCo)
//   QG4 = Security Scan (Snyk)
springGkePipeline(
    appName:      'order-api',
    QG:           ['QG1', 'QG2', 'QG3', 'QG4'],
    gcpProjectId: 'project-d7b18774-cce1-4b87-a4a',
    gcpRegion:    'us-central1',
    gkeCluster:   'order-api-cluster',
    arRepo:       'order-api',
    imageName:    'order-api',
    dev1Ns:       'order-api-dev1',
    dev2Ns:       'order-api-dev2',
    snykScanJob:  'order-api-snyk-scan',
    healthPath:   '/actuator/health',
    smokePath:    '/api/orders/1001',
    smokeExpect:  '"orderId":1001'
)


