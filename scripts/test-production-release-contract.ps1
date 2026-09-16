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
        CandidateOnMain = $true
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

Assert-Rejected -Name 'unmerged-commit' -Mutate { param($case) $case.CandidateOnMain = $false } -MessagePattern 'not an ancestor'
$advancedMainCase = New-ValidPolicyCase
$advancedMainCase.RemoteMainCommit = '2222222222222222222222222222222222222222'
$null = Invoke-PolicyCase -Case $advancedMainCase
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
$workflowJson = & node (Join-Path $repositoryRoot 'renderer/scripts/read-yaml.mjs') (Join-Path $repositoryRoot '.github/workflows/release.yml')
if ($LASTEXITCODE -ne 0) { throw 'Production workflow YAML could not be parsed.' }
$workflowData = ($workflowJson -join "`n") | ConvertFrom-Json -AsHashtable
$authorizeJob = $workflowData.jobs.'authorize-candidate'
$signedJob = $workflowData.jobs.'signed-candidate'
$verifier = Read-RepositoryText 'scripts/verify-production-candidate.ps1'
$policy = Get-Content -LiteralPath (Join-Path $repositoryRoot 'config/production-signing-policy.json') -Raw | ConvertFrom-Json

if ($workflowData.permissions -isnot [System.Collections.IDictionary] -or $workflowData.permissions.Count -ne 0 -or
    $authorizeJob.permissions.Count -ne 2 -or $authorizeJob.permissions.actions -ne 'read' -or
    $authorizeJob.permissions.contents -ne 'read') {
    throw 'The authorization job must have only read permissions.'
}
if ($authorizeJob.Contains('environment') -or ($authorizeJob | ConvertTo-Json -Depth 100) -match '\$\{\{\s*secrets\.') {
    throw 'The authorization job must not access the production environment or secrets.'
}
$signingEnvironment = if ($signedJob.environment -is [string]) { $signedJob.environment } else { $signedJob.environment.name }
if (@($signedJob.needs).Count -ne 1 -or @($signedJob.needs)[0] -ne 'authorize-candidate' -or
    $signingEnvironment -ne 'production-signing' -or $authorizeJob['continue-on-error'] -or $signedJob['continue-on-error']) {
    throw 'Signing must depend on authorization and use the protected production environment.'
}
function Assert-SignedPermissions([System.Collections.IDictionary] $Job) {
    $expected = @{ actions = 'read'; contents = 'read'; attestations = 'write'; 'id-token' = 'write' }
    if ($Job.permissions -isnot [System.Collections.IDictionary] -or $Job.permissions.Count -ne $expected.Count -or $Job.Contains('env')) {
        throw 'Signing must use only its required permissions and no job-level environment bindings.'
    }
    foreach ($key in $expected.Keys) {
        if ($Job.permissions[$key] -cne $expected[$key]) { throw "Unexpected signing permission: $key" }
    }
}
Assert-SignedPermissions $signedJob
$extraPermission = $signedJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
$extraPermission.permissions.packages = 'write'
$rejected = $false
try { Assert-SignedPermissions $extraPermission } catch { $rejected = $true }
if (-not $rejected) { throw 'Extra signing write permission was accepted.' }
$signedBlock = [regex]::Match($workflow, '(?ms)^  signed-candidate:.*\z').Value

function Get-TrustedDispatchScript {
    param([System.Collections.IDictionary] $Job, [switch] $Signing)

    $firstStep = @($Job.steps)[0]
    if ($firstStep['run'] -isnot [string] -or [string]::IsNullOrWhiteSpace($firstStep['run']) -or
        $firstStep.Contains('uses') -or $firstStep['shell'] -ne 'pwsh' -or
        $firstStep.Contains('if') -or $firstStep['continue-on-error']) {
        throw 'Trusted inline identity validation must run unconditionally before any checkout or repository code.'
    }
    $checkouts = @($Job.steps | Where-Object { $_['uses'] -like 'actions/checkout@*' })
    if ($checkouts.Count -ne 1 -or $checkouts[0].with.ref -cne '${{ github.workflow_sha }}' -or
        $checkouts[0].with.'persist-credentials' -ne $false) {
        throw 'Release policy code must be checked out from the trusted workflow SHA, never candidate input or a job output.'
    }
    $bindings = @{
        CANDIDATE_COMMIT = '${{ inputs.commit }}'
        WORKFLOW_SHA = '${{ github.workflow_sha }}'
        TRIGGER_SHA = '${{ github.sha }}'
        WORKFLOW_EVENT = '${{ github.event_name }}'
        WORKFLOW_REF = '${{ github.ref }}'
        REPOSITORY = '${{ github.repository }}'
        API_BASE_URL = '${{ github.api_url }}'
        GH_TOKEN = '${{ github.token }}'
    }
    if ($Signing) { $bindings.AUTHORIZED_COMMIT = '${{ needs.authorize-candidate.outputs.source_commit }}' }
    if ($firstStep.env.Count -ne $bindings.Count) { throw 'Trusted dispatch must not receive unexpected environment bindings or secrets.' }
    foreach ($key in $bindings.Keys) {
        if ($firstStep.env[$key] -cne $bindings[$key]) { throw "Trusted dispatch binding is missing or incorrect: $key" }
    }
    return [scriptblock]::Create($firstStep['run'])
}

$authorizeDispatch = Get-TrustedDispatchScript -Job $authorizeJob
$signedDispatch = Get-TrustedDispatchScript -Job $signedJob -Signing
$earlySignedSecret = $signedJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
$earlySignedSecret.steps[0].env.LEAK = '${{ secrets.LYRICS_CARD_KEYSTORE_BASE64 }}'
$rejected = $false
try { $null = Get-TrustedDispatchScript -Job $earlySignedSecret -Signing } catch { $rejected = $true }
if (-not $rejected) { throw 'A signing secret was exposed before identity revalidation.' }

# Display names and YAML field order do not authorize code. Exercise renamed
# jobs through the same hostile dispatch cases as the actual workflow below.
$renamedAuthorize = $authorizeJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
$renamedSigned = $signedJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
foreach ($job in @($renamedAuthorize, $renamedSigned)) {
    for ($index = 0; $index -lt $job.steps.Count; $index++) { $job.steps[$index].name = "Stage $index" }
}
$renamedAuthorizeDispatch = Get-TrustedDispatchScript -Job $renamedAuthorize
$renamedSignedDispatch = Get-TrustedDispatchScript -Job $renamedSigned -Signing
foreach ($mutation in @('checkout-first', 'missing-code', 'skipped-guard', 'ignored-failure', 'untrusted-ref', 'wrong-binding', 'early-secret')) {
    $job = $authorizeJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
    switch ($mutation) {
        'checkout-first' { $job.steps[0] = $job.steps[1] }
        'missing-code' { $null = $job.steps[0].Remove('run') }
        'skipped-guard' { $job.steps[0]['if'] = $false }
        'ignored-failure' { $job.steps[0]['continue-on-error'] = $true }
        'untrusted-ref' { $job.steps[1].with.ref = '${{ inputs.commit }}' }
        'wrong-binding' { $job.steps[0].env.WORKFLOW_SHA = '${{ inputs.commit }}' }
        'early-secret' { $job.steps[0].env.LEAK = '${{ secrets.LYRICS_CARD_KEYSTORE_BASE64 }}' }
    }
    $rejected = $false
    try { $null = Get-TrustedDispatchScript -Job $job } catch { $rejected = $true }
    if (-not $rejected) { throw "Unsafe workflow wiring was accepted: $mutation" }
}

function Invoke-TrustedDispatchContract {
    param(
        [string] $Name,
        [scriptblock] $Gate,
        [hashtable] $Overrides = @{},
        [string] $RemoteMain = '1111111111111111111111111111111111111111',
        [string] $ExpectedFailure = '',
        [int] $ExpectedApiCalls = 2,
        [string] $ComparisonStatus = 'ahead'
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
        if ($Method -ne 'Get') {
            throw 'The trusted gate requested an unexpected endpoint.'
        }
        $apiCalls.Add($Uri)
        if ($Uri -eq 'https://api.contract.invalid/repos/Qrzzzz/lyrics-card-generator-android/git/ref/heads/main') {
            return [pscustomobject]@{ object = [pscustomobject]@{ type = 'commit'; sha = $RemoteMain } }
        }
        if ($Uri -eq "https://api.contract.invalid/repos/Qrzzzz/lyrics-card-generator-android/compare/$($values.CANDIDATE_COMMIT)...$RemoteMain") {
            return [pscustomobject]@{ status = $ComparisonStatus; merge_base_commit = @{ sha = $values.CANDIDATE_COMMIT } }
        }
        throw 'The trusted gate requested an unexpected endpoint.'
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

foreach ($gate in @($authorizeDispatch, $signedDispatch, $renamedAuthorizeDispatch, $renamedSignedDispatch)) {
    Invoke-TrustedDispatchContract -Name 'valid-main' -Gate $gate
    Invoke-TrustedDispatchContract -Name 'candidate-replaces-its-own-validator' -Gate $gate `
        -Overrides @{ CANDIDATE_COMMIT = '2222222222222222222222222222222222222222'; AUTHORIZED_COMMIT = '2222222222222222222222222222222222222222' } `
        -ExpectedFailure 'trusted workflow and trigger SHA before checkout' -ExpectedApiCalls 0
    Invoke-TrustedDispatchContract -Name 'main-advanced-before-checkout' -Gate $gate `
        -RemoteMain '2222222222222222222222222222222222222222'
    Invoke-TrustedDispatchContract -Name 'candidate-removed-from-main' -Gate $gate -RemoteMain '2222222222222222222222222222222222222222' -ComparisonStatus 'diverged' -ExpectedFailure 'no longer in remote main history'
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
function Assert-SigningCleanupOrder([System.Collections.IDictionary] $Job) {
    $cleanup = @()
    $artifacts = @()
    $keyUse = @()
    for ($index = 0; $index -lt $Job.steps.Count; $index++) {
        $step = $Job.steps[$index]
        if ($step['run'] -match '(?m)^\s*Remove-Item\s+-LiteralPath\s+\$signingDirectory\b') {
            if ($step['if'] -notmatch '^\s*(?:\$\{\{\s*)?always\(\)(?:\s*\}\})?\s*$' -or $step['continue-on-error']) {
                throw 'Signing cleanup must run on failure and must not ignore errors.'
            }
            $cleanup += $index
        } elseif (($step['env'] | ConvertTo-Json -Depth 100) -match '\$\{\{\s*secrets\.' -or
            $step['run'] -match '\$signingDirectory\b|LYRICS_CARD_(?:STORE|KEY)') {
            $keyUse += $index
        }
        if ($step['uses'] -match '^actions/(?:attest-build-provenance|upload-artifact)@') { $artifacts += $index }
    }
    if ($cleanup.Count -ne 1 -or $keyUse.Count -eq 0 -or $artifacts.Count -eq 0 -or
        $cleanup[0] -le ($keyUse | Measure-Object -Maximum).Maximum -or
        $cleanup[0] -ge ($artifacts | Measure-Object -Minimum).Minimum) {
        throw 'Temporary signing material must be removed after its last use and before provenance generation and upload.'
    }
}
Assert-SigningCleanupOrder $signedJob
Assert-SigningCleanupOrder $renamedSigned
foreach ($placement in @('too-early', 'too-late')) {
    $job = $signedJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
    $cleanupStep = @($job.steps | Where-Object { $_['run'] -match '(?m)^\s*Remove-Item\s+-LiteralPath\s+\$signingDirectory\b' })[0]
    $otherSteps = @($job.steps | Where-Object { $_ -ne $cleanupStep })
    $job.steps = if ($placement -eq 'too-early') { @($otherSteps[0], $cleanupStep) + $otherSteps[1..($otherSteps.Count - 1)] } else { $otherSteps + @($cleanupStep) }
    $rejected = $false
    try { Assert-SigningCleanupOrder $job } catch { $rejected = $true }
    if (-not $rejected) { throw "Unsafe signing cleanup placement was accepted: $placement" }
}
$withoutCleanup = $signedJob | ConvertTo-Json -Depth 100 | ConvertFrom-Json -AsHashtable
$withoutCleanup.steps = @($withoutCleanup.steps | Where-Object { $_['run'] -notmatch 'Remove-Item\s+-LiteralPath\s+\$signingDirectory\b' })
$rejected = $false
try { Assert-SigningCleanupOrder $withoutCleanup } catch { $rejected = $true }
if (-not $rejected) {
    throw 'Temporary signing material must be removed before provenance generation and upload.'
}
if ($workflow -notmatch '(?m)^\s*npm(?:\.cmd)? run audit:security\s*$') {
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
    $policy.lastVerifiedRelease.releaseTag -notmatch '^v(?:[01]\.\d+\.\d+|(?:[2-9]|[1-9]\d+)\.\d+)$' -or
    $policy.lastVerifiedRelease.sourceCommit -notmatch '^[0-9a-f]{40}$' -or
    $policy.lastVerifiedRelease.apkSha256 -notmatch '^[0-9a-f]{64}$' -or
    $policy.lastVerifiedRelease.certificateSha256 -ne $policy.certificateSha256) {
    throw 'The production certificate continuity policy must contain an auditable release trust anchor.'
}
foreach ($metadataField in @('schemaVersion = 2', 'qualityGateRunId', 'workflowRef', 'workflowSha', 'artifactDigests', 'certificateSha256', 'previousReleaseApkSha256')) {
    if ($workflow.IndexOf($metadataField, [StringComparison]::Ordinal) -lt 0) {
        throw "Release metadata contract is missing field: $metadataField"
    }
}
# Shared Action pins live in dependency-security; final readiness and consumer
# authority live in the device-evidence contract.
Write-Output 'Production release contract PASS (frozen-main ancestry, exact-SHA gates, hostile dispatch rejection, permissions, provenance and certificate continuity).'
exit 0
