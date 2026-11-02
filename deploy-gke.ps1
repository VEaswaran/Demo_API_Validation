param(
    [Parameter(Mandatory = $true)][string]$ProjectId,
    [string]$Region = "us-central1",
    [string]$ClusterName = "order-api-cluster",
    [string]$Namespace = "order-api",
    [string]$Repository = "order-api",
    [string]$ImageName = "order-api",
    [string]$Tag = (Get-Date -Format "yyyyMMdd-HHmmss")
)

$ErrorActionPreference = "Stop"

$sdkPaths = @(
    "${env:ProgramFiles(x86)}\Google\Cloud SDK\google-cloud-sdk\bin",
    "$env:LOCALAPPDATA\Google\Cloud SDK\bin"
)
foreach ($p in $sdkPaths) {
    if ((Test-Path $p) -and ($env:Path -notlike "*$p*")) {
        $env:Path = "$p;$env:Path"
    }
}
$env:USE_GKE_GCLOUD_AUTH_PLUGIN = "True"
$gcloud = if (Get-Command gcloud.cmd -ErrorAction SilentlyContinue) { "gcloud.cmd" } else { "gcloud" }

function Invoke-Checked {
    param([scriptblock]$Command)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed (exit $LASTEXITCODE): $Command"
        }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Test-CommandSuccess {
    param([scriptblock]$Command)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $Command *> $null
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $prev
    }
}

$registryHost = "$Region-docker.pkg.dev"
$image = "$registryHost/$ProjectId/$Repository/${ImageName}:$Tag"

Write-Host "==> Configuring gcloud project $ProjectId"
Invoke-Checked { & $gcloud config set project $ProjectId }

Write-Host "==> Enabling required APIs"
Invoke-Checked { & $gcloud services enable container.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com }

Write-Host "==> Ensuring Artifact Registry repository '$Repository' exists"
if (-not (Test-CommandSuccess { & $gcloud artifacts repositories describe $Repository --location=$Region })) {
    Invoke-Checked { & $gcloud artifacts repositories create $Repository --repository-format=docker --location=$Region }
}

Write-Host "==> Ensuring GKE Autopilot cluster '$ClusterName' exists"
if (-not (Test-CommandSuccess { & $gcloud container clusters describe $ClusterName --region=$Region })) {
    Invoke-Checked { & $gcloud container clusters create-auto $ClusterName --region=$Region }
}

Write-Host "==> Running tests"
Invoke-Checked { & "$PSScriptRoot\gradlew.bat" clean test }

Write-Host "==> Building and pushing image $image"
if (Test-CommandSuccess { docker info }) {
    Invoke-Checked { & $gcloud auth configure-docker $registryHost --quiet }
    Invoke-Checked { docker build --platform linux/amd64 -t $image $PSScriptRoot }
    Invoke-Checked { docker push $image }
} else {
    Write-Host "Local Docker daemon not running; building image in Cloud Build..."
    Invoke-Checked { & $gcloud builds submit --tag $image $PSScriptRoot }
}

Write-Host "==> Deploying to GKE cluster '$ClusterName' in namespace '$Namespace'"
Invoke-Checked { & $gcloud container clusters get-credentials $ClusterName --region=$Region }
if (-not (Test-CommandSuccess { kubectl get namespace $Namespace })) {
    Invoke-Checked { kubectl create namespace $Namespace }
}
(Get-Content "$PSScriptRoot\k8s\deployment.yaml" -Raw) -replace "IMAGE_PLACEHOLDER", $image | kubectl apply -n $Namespace -f -
if ($LASTEXITCODE -ne 0) { throw "kubectl apply deployment failed" }
Invoke-Checked { kubectl apply -n $Namespace -f "$PSScriptRoot\k8s\service.yaml" }
Invoke-Checked { kubectl rollout status deployment/order-api -n $Namespace --timeout=10m }

Write-Host "==> Waiting for external IP"
$ip = ""
for ($i = 0; $i -lt 60 -and -not $ip; $i++) {
    $ip = kubectl get service order-api -n $Namespace -o jsonpath="{.status.loadBalancer.ingress[0].ip}"
    if (-not $ip) { Start-Sleep -Seconds 10 }
}

if ($ip) {
    Write-Host "Deployed. Try: curl http://$ip/api/orders/1001"
} else {
    Write-Host "External IP not ready yet. Check with: kubectl get service order-api -n $Namespace"
}
