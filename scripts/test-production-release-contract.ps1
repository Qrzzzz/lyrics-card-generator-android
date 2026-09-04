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
$signedBlock = [regex]::Match($workflow, '(?ms)^  signed-candidate:.*\z').Value

function Get-TrustedDispatchScript {
    param([string] $Job, [string] $StepName)

    $firstStep = [regex]::Match($Job, '(?m)^      - name: (.+)\r?$').Groups[1].Value.Trim()
    if ($firstStep -ne $StepName) { throw 'Trusted inline identity validation must run before any checkout or repository code.' }
    $refs = @([regex]::Matches($Job, '(?m)^          ref: (.+)\r?$') | ForEach-Object { $_.Groups[1].Value.Trim() })
    if ($refs.Count -ne 1 -or $refs[0] -cne '${{ github.workflow_sha }}') {
        throw 'Release policy code must be checked out from the trusted workflow SHA, never candidate input or a job output.'
    }
    $step = [regex]::Match($Job, '(?ms)^      - name: ' + [regex]::Escape($StepName) + '\r?\n(?<step>.*?)(?=^      - name: |\z)').Groups['step'].Value
    $code = [regex]::Match($step, '(?ms)^        run: \|\r?\n(?<code>.*)\z').Groups['code'].Value
    if (-not $code) { throw 'Trusted dispatch validation must be inline in the workflow definition.' }
    return [scriptblock]::Create(($code -replace '(?m)^          ', ''))
}

$authorizeDispatch = Get-TrustedDispatchScript -Job $authorizeBlock -StepName 'Validate trusted dispatch before checkout'
$signedDispatch = Get-TrustedDispatchScript -Job $signedBlock -StepName 'Revalidate trusted dispatch before signing checkout'

function Invoke-TrustedDispatchContract {
    param(
        [string] $Name,
        [scriptblock] $Gate,
        [hashtable] $Overrides = @{},
        [string] $RemoteMain = '1111111111111111111111111111111111111111',
        [string] $ExpectedFailure = '',
        [int] $ExpectedApiCalls = 1
    )

    $values = @{
        AUTHORIZED_COMMIT = '1111111111111111111111111111111111111111'
        CANDIDATE_COMMIT = '1111111111111111111111111111111111111111'
        WORKFLOW_SHA = '1111111111111111111111111111111111111111'
        TRIGGER_SHA = '1111111111111111111111111111111111111111'
        WORKFLOW_EVENT = 'workflow_dispatch'
        WORKFLOW_REF = 'refs/heads/main'
        GH_TOKEN = 'local-contract-no-network'
        REPOSITORY = 'Qrzzzz/lyrics-card-generator-android'
        API_BASE_URL = 'https://api.contract.invalid'
    }
    foreach ($key in $Overrides.Keys) { $values[$key] = $Overrides[$key] }
    $saved = @{}
    $fixture = Join-Path ([IO.Path]::GetTempPath()) ('lcg-trusted-source-contract-' + [guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts')
    $marker = Join-Path $fixture 'candidate-validator-executed'
    # This candidate replaces its own verifier with an unconditional authorization.
    # No credentials, GitHub dispatch or signing operation is used by the fixture.
    @'
Set-Content -LiteralPath 'candidate-validator-executed' -Value 'self-authorized'
Write-Output 'source_commit=2222222222222222222222222222222222222222'
'@ | Set-Content -LiteralPath (Join-Path $fixture 'scripts/verify-production-candidate.ps1') -Encoding utf8
    $apiCalls = [System.Collections.Generic.List[string]]::new()
    function Invoke-RestMethod {
        param($Method, $Uri, $Headers)
        if ($Method -ne 'Get' -or $Uri -ne 'https://api.contract.invalid/repos/Qrzzzz/lyrics-card-generator-android/git/ref/heads/main') {
            throw 'The trusted gate requested an unexpected endpoint.'
        }
        $apiCalls.Add($Uri)
        return [pscustomobject]@{ object = [pscustomobject]@{ type = 'commit'; sha = $RemoteMain } }
    }
    try {
        foreach ($key in $values.Keys) {
            $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
        }
        Push-Location $fixture
        try {
            $failure = $null
            try {
                & $Gate
                $null = & './scripts/verify-production-candidate.ps1'
            } catch {
                $failure = $_.Exception.Message
            }
            if ($ExpectedFailure) {
                if (-not $failure -or $failure -notmatch $ExpectedFailure) {
                    throw "Trusted dispatch '$Name' did not reject for the expected reason: $failure"
                }
                if (Test-Path -LiteralPath $marker) { throw "Untrusted candidate code executed in '$Name'." }
            } elseif ($failure -or -not (Test-Path -LiteralPath $marker)) {
                throw "Valid trusted dispatch '$Name' did not reach the wired candidate fixture: $failure"
            }
            if ($apiCalls.Count -ne $ExpectedApiCalls) { throw "Unexpected API call count in '$Name': $($apiCalls.Count)" }
        } finally {
            Pop-Location
        }
    } finally {
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
        $resolvedFixture = [IO.Path]::GetFullPath($fixture)
        if (-not $resolvedFixture.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolvedFixture) -notlike 'lcg-trusted-source-contract-*') {
            throw 'Refusing to remove a contract fixture outside its temporary directory.'
        }
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}

foreach ($gate in @($authorizeDispatch, $signedDispatch)) {
    Invoke-TrustedDispatchContract -Name 'valid-main' -Gate $gate
    Invoke-TrustedDispatchContract -Name 'candidate-replaces-its-own-validator' -Gate $gate `
        -Overrides @{ CANDIDATE_COMMIT = '2222222222222222222222222222222222222222'; AUTHORIZED_COMMIT = '2222222222222222222222222222222222222222' } `
        -ExpectedFailure 'trusted workflow and trigger SHA before checkout' -ExpectedApiCalls 0
    Invoke-TrustedDispatchContract -Name 'main-advanced-before-checkout' -Gate $gate `
        -RemoteMain '2222222222222222222222222222222222222222' -ExpectedFailure 'fresh remote main before checkout'
}
Invoke-TrustedDispatchContract -Name 'poisoned-authorization-output' -Gate $signedDispatch `
    -Overrides @{ AUTHORIZED_COMMIT = '2222222222222222222222222222222222222222' } `
    -ExpectedFailure 'trusted workflow and trigger SHA before checkout' -ExpectedApiCalls 0

if (([regex]::Matches($workflow, '(?m)^\s+id-token:\s*write\s*$')).Count -ne 1 -or
    ([regex]::Matches($workflow, '(?m)^\s+attestations:\s*write\s*$')).Count -ne 1) {
    throw 'OIDC and attestation write permissions must exist only on the signed-candidate job.'
}
if ($workflow -notmatch 'actions/attest-build-provenance@[0-9a-f]{40}' -or $workflow -notmatch '(?m)^\s+subject-path:\s*release-assets/\*\s*$') {
    throw 'All publishable release assets must receive pinned GitHub build provenance.'
}
$testApkPath = 'app/build/outputs/apk/androidTest/production/release/app-production-release-androidTest.apk'
if ($signedBlock -notmatch '(?m)^\s+\.\\gradlew\.bat :app:assembleProductionReleaseAndroidTest ' -or
    $signedBlock -notmatch ('(?m)^\s+subject-path: ' + [regex]::Escape($testApkPath) + '\s*$') -or
    $signedBlock -notmatch ('(?m)^\s+path: ' + [regex]::Escape($testApkPath) + '\s*$') -or
    $signedBlock.IndexOf('name: production-device-test-${{ steps.assets.outputs.version }}-${{ steps.assets.outputs.short_sha }}', [StringComparison]::Ordinal) -lt 0 -or
    $signedBlock.IndexOf('Production device-test APK must use the same production certificate.', [StringComparison]::Ordinal) -lt 0) {
    throw 'The signing job must build, verify, attest and separately upload the production device-test APK.'
}
if ($signedBlock -match '(?m)^\s+Copy-Item[^\r\n]*deviceTest' -or
    $signedBlock -notmatch '(?m)^\s+path: release-assets/\*\s*$') {
    throw 'Device-test APKs must stay outside the public production candidate assets.'
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

Write-Output 'Production release contract PASS (1 positive, 13 negative policy cases; 2 positive, 5 negative executable dispatch cases; workflow permissions/provenance/continuity/device-test artifact checks).'
exit 0
