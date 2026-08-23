[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $CandidateCommit,
    [Parameter(Mandatory = $true)][string] $ExpectedVersion,
    [Parameter(Mandatory = $true)][string] $Repository,
    [Parameter(Mandatory = $true)][string] $WorkflowEvent,
    [Parameter(Mandatory = $true)][string] $WorkflowRef,
    [Parameter(Mandatory = $true)][string] $WorkflowSha,
    [Parameter(Mandatory = $true)][string] $TriggerSha,
    [Parameter(Mandatory = $true)][string] $GitHubToken,
    [string] $ApiBaseUrl = 'https://api.github.com'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'ProductionReleasePolicy.psm1') -Force

function Invoke-GitHubJson {
    param([Parameter(Mandatory = $true)][string] $Uri)

    $headers = @{
        Accept = 'application/vnd.github+json'
        Authorization = "Bearer $GitHubToken"
        'X-GitHub-Api-Version' = '2022-11-28'
    }
    return Invoke-RestMethod -Method Get -Uri $Uri -Headers $headers
}

function Test-GitHubReleaseExists {
    param([Parameter(Mandatory = $true)][string] $Tag)

    $uri = "$ApiBaseUrl/repos/$Repository/releases/tags/$([uri]::EscapeDataString($Tag))"
    try {
        $null = Invoke-GitHubJson -Uri $uri
        return $true
    } catch {
        $statusCode = [int]$_.Exception.Response.StatusCode
        if ($statusCode -eq 404) {
            return $false
        }
        throw
    }
}

if ([string]::IsNullOrWhiteSpace($GitHubToken)) {
    throw 'A GitHub token is required for exact Quality Gate and Release verification.'
}

$candidate = $CandidateCommit.ToLowerInvariant()
if ($candidate -notmatch '^[0-9a-f]{40}$') {
    throw 'Candidate commit must be a full lowercase 40-character SHA.'
}
if ($ExpectedVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw 'Version must be a production x.y.z version.'
}

$actual = (git rev-parse HEAD).Trim().ToLowerInvariant()
if ($LASTEXITCODE -ne 0 -or $actual -ne $candidate) {
    throw "Checked out '$actual', expected '$candidate'."
}

git fetch --no-tags --force origin '+refs/heads/main:refs/remotes/origin/main'
if ($LASTEXITCODE -ne 0) {
    throw 'Could not refresh origin/main.'
}
$remoteMain = (git rev-parse refs/remotes/origin/main).Trim().ToLowerInvariant()
if ($LASTEXITCODE -ne 0) {
    throw 'Could not resolve refreshed origin/main.'
}

$gradle = Get-Content 'app/build.gradle.kts' -Raw
$repositoryVersion = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $repositoryVersion) {
    throw 'Could not read versionName from app/build.gradle.kts.'
}

$tag = "v$ExpectedVersion"
$null = git ls-remote --exit-code --tags origin "refs/tags/$tag" 2>$null
$tagStatus = $LASTEXITCODE
if ($tagStatus -notin @(0, 2)) {
    throw "Could not determine whether remote tag '$tag' exists."
}
$tagExists = $tagStatus -eq 0
$releaseExists = Test-GitHubReleaseExists -Tag $tag

$qualityUri = "$ApiBaseUrl/repos/$Repository/actions/workflows/ci.yml/runs?branch=main&event=push&status=success&head_sha=$candidate&per_page=100"
$qualityResponse = Invoke-GitHubJson -Uri $qualityUri
$qualityRuns = @($qualityResponse.workflow_runs)

$qualityRun = Assert-ProductionCandidatePolicy `
    -CandidateCommit $candidate `
    -ExpectedVersion $ExpectedVersion `
    -RepositoryVersion $repositoryVersion `
    -Repository $Repository `
    -RemoteMainCommit $remoteMain `
    -WorkflowEvent $WorkflowEvent `
    -WorkflowRef $WorkflowRef `
    -WorkflowSha $WorkflowSha `
    -TriggerSha $TriggerSha `
    -TagExists $tagExists `
    -ReleaseExists $releaseExists `
    -QualityGateRuns $qualityRuns

if ($env:GITHUB_OUTPUT) {
    "source_commit=$candidate" >> $env:GITHUB_OUTPUT
    "quality_gate_run_id=$($qualityRun.id)" >> $env:GITHUB_OUTPUT
    "quality_gate_run_url=$($qualityRun.html_url)" >> $env:GITHUB_OUTPUT
}

Write-Output "Production candidate policy PASS: source=$candidate quality_gate_run=$($qualityRun.id) version=$ExpectedVersion"
exit 0
