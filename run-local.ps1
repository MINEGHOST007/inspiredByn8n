$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$platformRoot = Join-Path $repoRoot 'code\platform'
$maven = Join-Path $platformRoot 'mvnw.cmd'
$workflow = Join-Path $repoRoot 'code\workflows\domains\customer-service\refund-processing\workflow.cwm.yaml'
$scenarioDir = Join-Path (Split-Path $workflow) 'tests\scenarios'
$results = Join-Path $repoRoot 'artifacts\test_results\results.xml'
$database = Join-Path $repoRoot 'data\w6w-demo'

Push-Location $platformRoot
try {
    & $maven install
    if ($LASTEXITCODE -ne 0) { throw "Maven install and verification failed with exit code $LASTEXITCODE" }

    & $maven -f modules/w6w-test/pom.xml exec:java `
        "-Dexec.args=`"$workflow`" `"$scenarioDir`" `"$results`" `"$database`""
    if ($LASTEXITCODE -ne 0) { throw "Local scenario run failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
