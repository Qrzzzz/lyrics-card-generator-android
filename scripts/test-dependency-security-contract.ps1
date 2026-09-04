[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Read-RepositoryText {
    param([Parameter(Mandatory = $true)][string] $RelativePath)

    $path = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file is missing: $RelativePath"
    }
    return [IO.File]::ReadAllText($path)
}

function Assert-Match {
    param(
        [Parameter(Mandatory = $true)][string] $Text,
        [Parameter(Mandatory = $true)][string] $Pattern,
        [Parameter(Mandatory = $true)][string] $Message
    )

    if ($Text -notmatch $Pattern) {
        throw $Message
    }
}

$dependabot = Read-RepositoryText '.github/dependabot.yml'
Assert-Match $dependabot '(?m)^version:\s*2\s*$' 'Dependabot configuration must use schema version 2.'
$ecosystems = @([regex]::Matches($dependabot, '(?m)^\s*- package-ecosystem:\s*([^\s#]+)\s*$') | ForEach-Object { $_.Groups[1].Value })
$expectedEcosystems = @('npm', 'gradle', 'github-actions')
if ($ecosystems.Count -ne $expectedEcosystems.Count -or (Compare-Object $ecosystems $expectedEcosystems)) {
    throw "Dependabot ecosystems must be exactly: $($expectedEcosystems -join ', ')."
}
Assert-Match $dependabot '(?ms)- package-ecosystem:\s*npm\s+directory:\s*/renderer\s' 'Dependabot npm coverage must target /renderer.'
Assert-Match $dependabot '(?ms)- package-ecosystem:\s*gradle\s+directory:\s*/\s' 'Dependabot Gradle coverage must target the repository root.'
Assert-Match $dependabot '(?ms)- package-ecosystem:\s*github-actions\s+directory:\s*/\s' 'Dependabot GitHub Actions coverage must target the repository root.'

$package = Get-Content -LiteralPath (Join-Path $repositoryRoot 'renderer/package.json') -Raw | ConvertFrom-Json
if ($package.scripts.'audit:security' -ne 'node scripts/audit-security.mjs') {
    throw 'Renderer audit:security must fail npm audit on high or critical advisories.'
}

$ci = Read-RepositoryText '.github/workflows/ci.yml'
$release = Read-RepositoryText '.github/workflows/release.yml'
Assert-Match $ci '(?m)^\s*npm\.cmd run audit:security\s*$' 'The normal CI gate must execute Renderer dependency auditing.'
Assert-Match $release '(?m)^\s*npm\.cmd run audit:security\s*$' 'The production candidate gate must execute Renderer dependency auditing.'

$dependencyWorkflow = Read-RepositoryText '.github/workflows/dependency-security.yml'
Assert-Match $dependencyWorkflow 'actions/dependency-review-action@[0-9a-f]{40}' 'Dependency Review must be pinned to a full commit SHA.'
Assert-Match $dependencyWorkflow 'gradle/actions/dependency-submission@[0-9a-f]{40}' 'Gradle Dependency Submission must be pinned to a full commit SHA.'
Assert-Match $dependencyWorkflow '(?m)^\s*fail-on-severity:\s*high\s*$' 'Dependency Review must reject high and critical advisories.'
Assert-Match $dependencyWorkflow '(?m)^\s*fail-on-scopes:\s*runtime, development, unknown\s*$' 'Dependency Review must cover runtime, development, and unknown scopes.'
Assert-Match $dependencyWorkflow '(?ms)submit-gradle-dependencies:.*?permissions:\s+contents:\s*write\s' 'Only the Gradle submission job may request contents: write.'

foreach ($forbidden in @('production-signing', '${{ secrets.', 'upload-artifact', 'gh release', 'softprops/action-gh-release')) {
    if ($dependencyWorkflow.Contains($forbidden, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Dependency security workflow must not reference release/signing capability: $forbidden"
    }
}

$workflowFiles = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot '.github/workflows') -Filter '*.yml' -File
foreach ($workflowFile in $workflowFiles) {
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($workflowFile.FullName)) {
        $lineNumber++
        if ($line -match '^\s*-?\s*uses:\s*(\S+)') {
            $reference = $Matches[1]
            if ($reference -notmatch '@[0-9a-f]{40}$') {
                throw "Action reference is not pinned to a full commit SHA: $($workflowFile.Name):$lineNumber ($reference)"
            }
        }
    }
}

Write-Output 'Dependency security contract PASS.'
exit 0
