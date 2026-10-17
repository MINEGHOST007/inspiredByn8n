param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

$repoRoot = $PSScriptRoot
$platformRoot = Join-Path $repoRoot 'code\platform'
$maven = Join-Path $platformRoot 'mvnw.cmd'
if (-not $Arguments -or $Arguments.Count -eq 0) { $Arguments = @('help') }

Push-Location $platformRoot
try {
    $env:W6W_CLI_ARGS = ConvertTo-Json -InputObject @($Arguments) -Compress
    $env:W6W_ROOT = $repoRoot
    & $maven -f pom.xml install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "W6W module build failed with exit code $LASTEXITCODE" }

    & $maven -f modules/w6w-cli/pom.xml exec:java
    if ($LASTEXITCODE -ne 0) { throw "W6W CLI command failed with exit code $LASTEXITCODE" }
}
finally {
    Remove-Item Env:W6W_CLI_ARGS -ErrorAction SilentlyContinue
    Remove-Item Env:W6W_ROOT -ErrorAction SilentlyContinue
    Pop-Location
}
