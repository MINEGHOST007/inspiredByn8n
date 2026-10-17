$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$platformRoot = Join-Path $repoRoot 'code\platform'
$maven = Join-Path $platformRoot 'mvnw.cmd'

$env:W6W_WORKFLOWS_DIR = Join-Path $repoRoot 'code\workflows'
$env:W6W_DB_PATH = (Join-Path $repoRoot 'data\w6w-app').Replace('\', '/')
$env:W6W_BIND_ADDRESS = '127.0.0.1'
$env:W6W_RUNTIME_MODE = 'local-demo'

Push-Location $platformRoot
try {
    & $maven -f pom.xml install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "W6W build failed with exit code $LASTEXITCODE" }

    Write-Host 'W6W local workspace will be available at http://127.0.0.1:8080'
    & $maven -f modules/w6w-app/pom.xml spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "W6W local application exited with code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
