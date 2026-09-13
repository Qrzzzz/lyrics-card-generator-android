[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$rendererRoot = Join-Path $repositoryRoot 'renderer'

Push-Location $rendererRoot
try {
    & node '--test' 'scripts/dependency-security-contract.test.mjs'
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & node 'scripts/dependency-security-contract.mjs' $repositoryRoot
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

exit 0
