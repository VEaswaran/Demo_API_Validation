@Library('enterprise-cicd@main') _

springGkePipeline(
    appName:      'order-api',
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

