[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Import-Module (Join-Path $PSScriptRoot 'ProductionReleasePolicy.psm1') -Force

function New-ValidPolicyCase {
    $candidate = '1111111111111111111111111111111111111111'
    return @{
        CandidateCommit = $candidate
        ExpectedVersion = '2.0.0'
        RepositoryVersion = '2.0.0'
        Repository = 'Qrzzzz/lyrics-card-generator-android'
        RemoteMainCommit = $candidate
        WorkflowEvent = 'workflow_dispatch'
        WorkflowRef = 'refs/heads/main'
        WorkflowSha = $candidate
        TriggerSha = $candidate
        TagExists = $false
        ReleaseExists = $false
        QualityGateRuns = @(
            [pscustomobject]@{
                id = 42
                name = 'Android Quality Gate'
                head_sha = $candidate
                head_branch = 'main'
                event = 'push'
                status = 'completed'
                conclusion = 'success'
                run_started_at = '2026-08-24T00:00:00Z'
                html_url = 'https://github.com/Qrzzzz/lyrics-card-generator-android/actions/runs/42'
                head_repository = [pscustomobject]@{ full_name = 'Qrzzzz/lyrics-card-generator-android' }
            }
        )
    }
}

function Invoke-PolicyCase {
    param([Parameter(Mandatory = $true)][hashtable] $Case)
    return Assert-ProductionCandidatePolicy @Case
}

function Assert-Rejected {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][scriptblock] $Mutate,
        [Parameter(Mandatory = $true)][string] $MessagePattern
    )

    $case = New-ValidPolicyCase
    & $Mutate $case
    try {
        $null = Invoke-PolicyCase -Case $case
        throw "Negative contract '$Name' was accepted."
    } catch {
        if ($_.Exception.Message -like "Negative contract '$Name'*") { throw }
        if ($_.Exception.Message -notmatch $MessagePattern) {
            throw "Negative contract '$Name' failed for the wrong reason: $($_.Exception.Message)"
        }
    }
}

$accepted = Invoke-PolicyCase -Case (New-ValidPolicyCase)
if ($accepted.id -ne 42) { throw 'The valid contract did not select the exact Quality Gate run.' }

Assert-Rejected -Name 'unmerged-or-stale-main' -Mutate { param($case) $case.RemoteMainCommit = '2222222222222222222222222222222222222222' } -MessagePattern 'not the current origin/main tip'
Assert-Rejected -Name 'wrong-trigger-sha' -Mutate { param($case) $case.TriggerSha = '2222222222222222222222222222222222222222' } -MessagePattern 'same main commit'
Assert-Rejected -Name 'wrong-workflow-sha' -Mutate { param($case) $case.WorkflowSha = '2222222222222222222222222222222222222222' } -MessagePattern 'same main commit'
Assert-Rejected -Name 'wrong-version' -Mutate { param($case) $case.RepositoryVersion = '2.0.1' } -MessagePattern 'does not match Gradle version'
Assert-Rejected -Name 'duplicate-tag' -Mutate { param($case) $case.TagExists = $true } -MessagePattern 'already exists'
Assert-Rejected -Name 'duplicate-release' -Mutate { param($case) $case.ReleaseExists = $true } -MessagePattern 'already exists'
Assert-Rejected -Name 'wrong-dispatch-ref' -Mutate { param($case) $case.WorkflowRef = 'refs/heads/feature' } -MessagePattern 'refs/heads/main'
Assert-Rejected -Name 'missing-gate' -Mutate { param($case) $case.QualityGateRuns = @() } -MessagePattern 'No successful Android Quality Gate'
Assert-Rejected -Name 'another-sha-green' -Mutate { param($case) $case.QualityGateRuns[0].head_sha = '2222222222222222222222222222222222222222' } -MessagePattern 'No successful Android Quality Gate'
Assert-Rejected -Name 'pull-request-green' -Mutate { param($case) $case.QualityGateRuns[0].event = 'pull_request' } -MessagePattern 'No successful Android Quality Gate'
Assert-Rejected -Name 'wrong-branch-green' -Mutate { param($case) $case.QualityGateRuns[0].head_branch = 'feature' } -MessagePattern 'No successful Android Quality Gate'
Assert-Rejected -Name 'failed-gate' -Mutate { param($case) $case.QualityGateRuns[0].conclusion = 'failure' } -MessagePattern 'No successful Android Quality Gate'
Assert-Rejected -Name 'foreign-repository-gate' -Mutate { param($case) $case.QualityGateRuns[0].head_repository.full_name = 'fork/example' } -MessagePattern 'No successful Android Quality Gate'

function Read-RepositoryText {
    param([Parameter(Mandatory = $true)][string] $RelativePath)
    $path = Join-Path $repositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file is missing: $RelativePath" }
    return [IO.File]::ReadAllText($path)
}

$workflow = Read-RepositoryText '.github/workflows/release.yml'
$verifier = Read-RepositoryText 'scripts/verify-production-candidate.ps1'
$policy = Get-Content -LiteralPath (Join-Path $repositoryRoot 'config/production-signing-policy.json') -Raw | ConvertFrom-Json

if ($workflow -notmatch '(?ms)^permissions:\s*\{\}\s*.*?authorize-candidate:.*?permissions:\s+actions:\s*read\s+contents:\s*read') {
    throw 'The authorization job must have only read permissions.'
}
$authorizeBlock = [regex]::Match($workflow, '(?ms)^  authorize-candidate:.*?(?=^  signed-candidate:)').Value
if ($authorizeBlock -match 'production-signing|\$\{\{\s*secrets\.') {
    throw 'The authorization job must not access the production environment or secrets.'
}
if (([regex]::Matches($workflow, '(?m)^\s+id-token:\s*write\s*$')).Count -ne 1 -or
    ([regex]::Matches($workflow, '(?m)^\s+attestations:\s*write\s*$')).Count -ne 1) {
    throw 'OIDC and attestation write permissions must exist only on the signed-candidate job.'
}
if ($workflow -notmatch 'actions/attest-build-provenance@[0-9a-f]{40}' -or $workflow -notmatch '(?m)^\s+subject-path:\s*release-assets/\*\s*$') {
    throw 'All publishable release assets must receive pinned GitHub build provenance.'
}
if ($workflow.IndexOf('Remove temporary signing material') -gt $workflow.IndexOf('Attest all publishable release assets')) {
    throw 'Temporary signing material must be removed before provenance generation and upload.'
}
if ($workflow -notmatch '(?m)^\s*npm\.cmd run audit:security\s*$') {
    throw 'The production workflow must preserve Renderer dependency auditing.'
}
if (([regex]::Matches($workflow, 'verify-production-candidate\.ps1')).Count -lt 3) {
    throw 'Source/tag/release/Quality Gate policy must be checked before approval, after approval, and before provenance.'
}
if ($workflow -match "(?m)-\w+\s+'\$\{\{\s*inputs\.") {
    throw 'Untrusted workflow inputs must enter PowerShell through environment variables, not expression interpolation.'
}
if ($verifier -notmatch 'actions/workflows/ci\.yml/runs\?branch=main&event=push&status=success&head_sha=\$candidate' -or
    $verifier -notmatch 'refs/remotes/origin/main' -or
    $verifier -notmatch 'releases/tags/') {
    throw 'The live verifier must bind main, exact-SHA Quality Gate, tag, and Release state.'
}
if ($policy.certificateSha256 -notmatch '^[0-9a-f]{64}$' -or
    $policy.trustAnchor.releaseTag -ne 'v1.0.0' -or
    $policy.trustAnchor.apkSha256 -notmatch '^[0-9a-f]{64}$' -or
    $policy.lastVerifiedRelease.releaseTag -ne 'v1.0.1' -or
    $policy.lastVerifiedRelease.apkSha256 -notmatch '^[0-9a-f]{64}$' -or
    $policy.lastVerifiedRelease.certificateSha256 -ne $policy.certificateSha256) {
    throw 'The production certificate continuity policy must contain an auditable release trust anchor.'
}
foreach ($metadataField in @('schemaVersion = 2', 'qualityGateRunId', 'workflowRef', 'workflowSha', 'artifactDigests', 'certificateSha256', 'previousReleaseApkSha256')) {
    if ($workflow.IndexOf($metadataField, [StringComparison]::Ordinal) -lt 0) {
        throw "Release metadata contract is missing field: $metadataField"
    }
}
foreach ($readinessLiteral in @("status = 'PROVISIONAL'", "deviceGate = 'NOT RUN'", 'finalReady = $false', "finalGateWorkflow = '.github/workflows/final-device-gate.yml'")) {
    if ($workflow.IndexOf($readinessLiteral, [StringComparison]::Ordinal) -lt 0) {
        throw "Signed candidate metadata must remain non-final: $readinessLiteral"
    }
}
$finalGateWorkflow = Read-RepositoryText '.github/workflows/final-device-gate.yml'
if ($finalGateWorkflow -match 'production-signing|\$\{\{\s*secrets\.' -or
    $finalGateWorkflow -match '(?m)^\s+(id-token|attestations):\s*write\s*$') {
    throw 'The post-signing final device gate must not access signing authority or write attestations.'
}
foreach ($requiredFinalGateBinding in @('candidate_run_id', 'evidence_run_id', 'gh attestation verify', 'validate-device-gate-evidence.ps1')) {
    if ($finalGateWorkflow.IndexOf($requiredFinalGateBinding, [StringComparison]::Ordinal) -lt 0) {
        throw "The post-signing final gate is missing binding: $requiredFinalGateBinding"
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

Write-Output 'Production release contract PASS (1 positive, 13 negative policy cases, workflow permissions/provenance/continuity checks).'
exit 0
