[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtureSourceRoot = Join-Path $repositoryRoot 'tests\fixtures\device-gate\pass'
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('lyrics-card-device-gate-fixture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixtureRoot 'logs') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'device-gate-evidence.json') -Destination $fixtureRoot
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'release-metadata.json') -Destination $fixtureRoot
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'logs\instrumentation.log') -Destination (Join-Path $fixtureRoot 'logs')
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'logs\logcat.txt') -Destination (Join-Path $fixtureRoot 'logs')
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'payloads\production-apk.fixture') -Destination (Join-Path $fixtureRoot 'lyrics-card-generator-android-2.0.0.apk')
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'payloads\production-aab.fixture') -Destination (Join-Path $fixtureRoot 'lyrics-card-generator-android-2.0.0.aab')
Copy-Item -LiteralPath (Join-Path $fixtureSourceRoot 'payloads\test-apk.fixture') -Destination (Join-Path $fixtureRoot 'app-production-release-androidTest.apk')
$fixtureEvidencePath = Join-Path $fixtureRoot 'device-gate-evidence.json'
$fixtureMetadataPath = Join-Path $fixtureRoot 'release-metadata.json'
$fixtureApkPath = Join-Path $fixtureRoot 'lyrics-card-generator-android-2.0.0.apk'
$fixtureAabPath = Join-Path $fixtureRoot 'lyrics-card-generator-android-2.0.0.aab'
$fixtureTestApkPath = Join-Path $fixtureRoot 'app-production-release-androidTest.apk'
$expectedRepository = 'Qrzzzz/lyrics-card-generator-android'
$expectedCommit = '1111111111111111111111111111111111111111'

Import-Module (Join-Path $PSScriptRoot 'DeviceGateEvidence.psm1') -Force

function Read-Fixture {
    return (Get-Content -LiteralPath $fixtureEvidencePath -Raw | ConvertFrom-Json)
}

function Copy-Evidence {
    param([object] $Evidence)
    return ($Evidence | ConvertTo-Json -Depth 20 | ConvertFrom-Json)
}

function Invoke-EvidencePolicy {
    param([object] $Evidence, [switch] $AllowFixture)
    $arguments = @{
        Evidence = $Evidence
        EvidenceRoot = $fixtureRoot
        ExpectedRepository = $expectedRepository
        ExpectedSourceCommit = $expectedCommit
        ExpectedEvidenceRunId = 2002
        ExpectedEvidenceRunAttempt = 1
        ExpectedEvidenceWorkflowPath = '.github/workflows/capture-device-gate-evidence.yml'
        ExpectedEvidenceWorkflowEvent = 'workflow_dispatch'
        ExpectedEvidenceArtifactName = 'final-device-evidence-test-fixture'
    }
    if ($AllowFixture) { $arguments.AllowTestFixture = $true }
    return Assert-DeviceGateEvidence @arguments
}

function Assert-Rejected {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][scriptblock] $Mutate,
        [Parameter(Mandatory = $true)][string] $MessagePattern
    )
    $case = Copy-Evidence (Read-Fixture)
    & $Mutate $case
    try {
        $null = Invoke-EvidencePolicy -Evidence $case -AllowFixture
        throw "Negative fixture '$Name' was accepted."
    } catch {
        if ($_.Exception.Message -like "Negative fixture '$Name'*") { throw }
        if ($_.Exception.Message -notmatch $MessagePattern) {
            throw "Negative fixture '$Name' failed for the wrong reason: $($_.Exception.Message)"
        }
    }
}

$schemaPath = Join-Path $repositoryRoot 'config\device-gate-evidence.schema.json'
$schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
if ($schema.title -ne 'Lyrics Card Generator Android final device gate evidence') { throw 'Device-gate JSON schema is malformed.' }
$testJsonCommand = Get-Command Test-Json -ErrorAction SilentlyContinue
if ($testJsonCommand -and $testJsonCommand.Parameters.ContainsKey('SchemaFile')) {
    $schemaValid = Get-Content -LiteralPath $fixtureEvidencePath -Raw | Test-Json -SchemaFile $schemaPath -ErrorAction Stop
    if (-not $schemaValid) { throw 'The positive device-gate fixture does not satisfy the JSON schema.' }
}

$positive = Invoke-EvidencePolicy -Evidence (Read-Fixture) -AllowFixture
if (@($positive.gates).Count -ne 16 -or @($positive.environments).Count -ne 5) {
    throw 'The positive fixture did not preserve the complete matrix.'
}
$null = Assert-DeviceGateArtifactBinding `
    -Evidence $positive `
    -ReleaseMetadataPath $fixtureMetadataPath `
    -ProductionApkPath $fixtureApkPath `
    -ProductionAabPath $fixtureAabPath `
    -TestApkPath $fixtureTestApkPath
$fixtureBadging = @"
package: name='com.qrzzzz.lyricscard.test' versionCode='20000' versionName='2.0.0'
instrumentation: name='androidx.test.runner.AndroidJUnitRunner' targetPackage='com.qrzzzz.lyricscard'
"@
$fixtureCertificate = 'Signer #1 certificate SHA-256 digest: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
Assert-TestApkInspection -Evidence $positive -Badging $fixtureBadging -CertificateOutput $fixtureCertificate

try {
    $null = Invoke-EvidencePolicy -Evidence (Read-Fixture)
    throw 'The production validator accepted testFixture=true.'
} catch {
    if ($_.Exception.Message -eq 'The production validator accepted testFixture=true.') { throw }
    if ($_.Exception.Message -notmatch 'Test fixture evidence is never valid') { throw }
}

Assert-Rejected -Name 'not-run-top-level' -Mutate { param($e) $e.status = 'NOT RUN' } -MessagePattern 'status must be READY'
Assert-Rejected -Name 'blocked-top-level' -Mutate { param($e) $e.status = 'BLOCKED' } -MessagePattern 'status must be READY'
Assert-Rejected -Name 'old-source-commit' -Mutate { param($e) $e.candidate.sourceCommit = '2222222222222222222222222222222222222222' } -MessagePattern 'does not match candidate'
Assert-Rejected -Name 'wrong-evidence-run' -Mutate { param($e) $e.run.workflowRunId = 2003 } -MessagePattern 'workflow identity/run/attempt'
Assert-Rejected -Name 'wrong-production-apk-sha' -Mutate { param($e) $e.candidate.productionApk.sha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'installed APK hashes do not match'
Assert-Rejected -Name 'wrong-aab-certificate' -Mutate { param($e) $e.candidate.productionAab.certificateSha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'certificates do not match'
Assert-Rejected -Name 'wrong-test-apk-package' -Mutate { param($e) $e.candidate.testApk.package = 'com.example.test' } -MessagePattern 'production package plus .test'
Assert-Rejected -Name 'wrong-test-apk-target' -Mutate { param($e) $e.candidate.testApk.targetPackage = 'com.example' } -MessagePattern 'targetPackage'
Assert-Rejected -Name 'missing-webview-version' -Mutate { param($e) $e.environments[1].webView.version = '' } -MessagePattern 'webView.version is required'
Assert-Rejected -Name 'wrong-system-image-api' -Mutate { param($e) $e.environments[1].systemImage = 'system-images;android-29;google_apis;x86_64' } -MessagePattern 'does not bind API 30'
Assert-Rejected -Name 'missing-api36-environment' -Mutate { param($e) $e.environments[3].apiLevel = 35; $e.environments[3].systemImage = 'system-images;android-35;google_apis;x86_64' } -MessagePattern 'Exactly one AVD environment is required for API 36'
Assert-Rejected -Name 'duplicate-environment' -Mutate { param($e) $e.environments[1].id = 'api26' } -MessagePattern 'Duplicate environment id'
Assert-Rejected -Name 'not-run-gate' -Mutate { param($e) $e.gates[3].status = 'NOT RUN' } -MessagePattern 'is not PASS'
Assert-Rejected -Name 'blocked-gate' -Mutate { param($e) $e.gates[4].status = 'BLOCKED' } -MessagePattern 'is not PASS'
Assert-Rejected -Name 'retry-laundering' -Mutate { param($e) $e.gates[3].attempts = 2 } -MessagePattern 'exactly one attempt'
Assert-Rejected -Name 'missing-required-gate' -Mutate { param($e) $e.gates = @($e.gates | Where-Object { $_.id -ne 'api30-serif-measure-spec-1x-2x' }) } -MessagePattern 'Required gate.*missing'
Assert-Rejected -Name 'wrong-gate-environment' -Mutate { param($e) $e.gates[3].environmentId = 'api33' } -MessagePattern 'wrong environment'
Assert-Rejected -Name 'missing-log-file' -Mutate { param($e) $e.gates[3].logs[0].path = 'logs/missing.log' } -MessagePattern 'log is missing'
Assert-Rejected -Name 'wrong-log-sha' -Mutate { param($e) $e.gates[3].logs[0].sha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'log SHA-256 mismatch'
Assert-Rejected -Name 'missing-log-kind' -Mutate { param($e) $e.gates[3].logs = @($e.gates[3].logs | Where-Object { $_.kind -ne 'logcat' }) } -MessagePattern 'requires instrumentation/manual and logcat'
Assert-Rejected -Name 'wrong-device-apk-sha' -Mutate { param($e) $e.environments[1].installedArtifacts.productionApkSha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'installed APK hashes do not match'
Assert-Rejected -Name 'wrong-test-certificate' -Mutate { param($e) $e.environments[1].installedArtifacts.testCertificateSha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'test certificate does not match'

try {
    $null = Assert-DeviceGateArtifactBinding `
        -Evidence (Read-Fixture) `
        -ReleaseMetadataPath $fixtureMetadataPath `
        -ProductionApkPath $fixtureApkPath `
        -ProductionAabPath $fixtureAabPath `
        -TestApkPath $fixtureMetadataPath
    throw 'Artifact binding accepted the wrong test APK bytes.'
} catch {
    if ($_.Exception.Message -eq 'Artifact binding accepted the wrong test APK bytes.') { throw }
    if ($_.Exception.Message -notmatch 'Artifact bytes do not match') { throw }
}

foreach ($inspectionCase in @(
    @{ Name = 'inspected-test-package'; Badging = $fixtureBadging.Replace('com.qrzzzz.lyricscard.test', 'com.example.test'); Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-version'; Badging = $fixtureBadging.Replace("versionCode='20000'", "versionCode='1'"); Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-target'; Badging = $fixtureBadging.Replace("targetPackage='com.qrzzzz.lyricscard'", "targetPackage='com.example'"); Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-certificate'; Badging = $fixtureBadging; Certificate = 'Signer #1 certificate SHA-256 digest: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'; Pattern = 'certificate does not match' }
)) {
    try {
        Assert-TestApkInspection -Evidence $positive -Badging $inspectionCase.Badging -CertificateOutput $inspectionCase.Certificate
        throw "Negative inspection fixture '$($inspectionCase.Name)' was accepted."
    } catch {
        if ($_.Exception.Message -like 'Negative inspection fixture*') { throw }
        if ($_.Exception.Message -notmatch $inspectionCase.Pattern) { throw }
    }
}

$blankVersionBadging = $fixtureBadging.Replace("versionCode='20000'", "versionCode=''").Replace("versionName='2.0.0'", "versionName=''")
$blankVersionEvidence = Read-Fixture
$blankVersionEvidence.candidate.testApk.versionCode = 0
$blankVersionEvidence.candidate.testApk.versionName = ''
Assert-TestApkInspection -Evidence $blankVersionEvidence -Badging $blankVersionBadging -CertificateOutput $fixtureCertificate

function New-ValidWorkflowRun {
    return [pscustomobject]@{
        id = 2002
        run_attempt = 1
        head_repository = [pscustomobject]@{ full_name = $expectedRepository }
        head_sha = $expectedCommit
        head_branch = 'main'
        status = 'completed'
        conclusion = 'success'
        event = 'workflow_dispatch'
        path = '.github/workflows/capture-device-gate-evidence.yml'
    }
}
$null = Assert-DeviceGateWorkflowRun -Run (New-ValidWorkflowRun) -ExpectedRepository $expectedRepository -ExpectedSourceCommit $expectedCommit -ExpectedPath '.github/workflows/capture-device-gate-evidence.yml' -ExpectedEvent 'workflow_dispatch'
foreach ($runCase in @(
    @{ Name = 'foreign-repository'; Mutate = { param($r) $r.head_repository.full_name = 'fork/example' } },
    @{ Name = 'old-source'; Mutate = { param($r) $r.head_sha = '2222222222222222222222222222222222222222' } },
    @{ Name = 'wrong-branch'; Mutate = { param($r) $r.head_branch = 'feature' } },
    @{ Name = 'incomplete'; Mutate = { param($r) $r.status = 'in_progress' } },
    @{ Name = 'failed'; Mutate = { param($r) $r.conclusion = 'failure' } },
    @{ Name = 'wrong-event'; Mutate = { param($r) $r.event = 'push' } },
    @{ Name = 'wrong-workflow'; Mutate = { param($r) $r.path = '.github/workflows/other.yml' } }
)) {
    $run = New-ValidWorkflowRun
    & $runCase.Mutate $run
    try {
        $null = Assert-DeviceGateWorkflowRun -Run $run -ExpectedRepository $expectedRepository -ExpectedSourceCommit $expectedCommit -ExpectedPath '.github/workflows/capture-device-gate-evidence.yml' -ExpectedEvent 'workflow_dispatch'
        throw "Negative workflow-run fixture '$($runCase.Name)' was accepted."
    } catch {
        if ($_.Exception.Message -like 'Negative workflow-run fixture*') { throw }
        if ($_.Exception.Message -notmatch 'does not match repository/source/main/success/identity policy') { throw }
    }
}

$realEvidencePath = Join-Path $repositoryRoot 'device-gate-evidence\final\device-gate-evidence.json'
if (Test-Path -LiteralPath $realEvidencePath) {
    throw 'A real final device-gate evidence file must not be committed by the host-only contract task.'
}

$releaseWorkflow = [IO.File]::ReadAllText((Join-Path $repositoryRoot '.github\workflows\release.yml'))
$finalWorkflow = [IO.File]::ReadAllText((Join-Path $repositoryRoot '.github\workflows\final-device-gate.yml'))
$checklist = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'RELEASE_CHECKLIST.md'))
foreach ($literal in @("status = 'PROVISIONAL'", "deviceGate = 'NOT RUN'", 'finalReady = $false', '.github/workflows/final-device-gate.yml')) {
    if ($releaseWorkflow.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Release candidate metadata is missing fail-closed readiness literal: $literal" }
}
if ($finalWorkflow -match 'production-signing|\$\{\{\s*secrets\.' -or $finalWorkflow -match '(?m)^\s+(id-token|attestations):\s*write\s*$') {
    throw 'The post-signing final device gate must not access signing environment, secrets, or write attestations.'
}
foreach ($literal in @('actions/download-artifact@', 'gh attestation verify', 'validate-device-gate-evidence.ps1', 'candidate_run_id', 'evidence_run_id')) {
    if ($finalWorkflow.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Final device-gate workflow is missing: $literal" }
}
foreach ($literal in @('actions/runs/$RunId', '.github/workflows/capture-device-gate-evidence.yml', 'Assert-DeviceGateWorkflowRun', 'run_attempt', 'environment: final-device-gate', 'aapt2.exe', 'apksigner.bat')) {
    if ($finalWorkflow.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Final device-gate workflow is missing producer/API/APK binding: $literal" }
}
$captureWorkflowPath = Join-Path $repositoryRoot '.github\workflows\capture-device-gate-evidence.yml'
if (-not (Test-Path -LiteralPath $captureWorkflowPath -PathType Leaf)) {
    throw 'The authorized device capture producer is missing.'
}
$captureWorkflow = [IO.File]::ReadAllText($captureWorkflowPath)
foreach ($literal in @('runs-on: [self-hosted, Windows, X64, lcg-device-gate]', 'environment: final-device-gate', 'capture-device-gate-evidence.ps1', 'assembleProductionReleaseAndroidTest', 'actions/download-artifact@', 'actions/upload-artifact@')) {
    if ($captureWorkflow.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Capture workflow is missing controlled producer binding: $literal" }
}
if ($captureWorkflow -match '\$\{\{\s*secrets\.' -or $captureWorkflow -match '(?m)^\s+(id-token|attestations):\s*write\s*$') {
    throw 'The capture producer must not read GitHub signing secrets or write attestations.'
}
foreach ($literal in @('Final Device Gate', 'device-gate-evidence.json', 'FINAL READY')) {
    if ($checklist.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Release checklist is missing final device-gate enforcement: $literal" }
}
$provenance = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'docs\RELEASE_PROVENANCE.md'))
foreach ($literal in @('consumer/validator', 'capture-device-gate-evidence.yml', 'final-device-gate` environment', '#10 signed candidate', 'GitHub build provenance')) {
    if ($provenance.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Release provenance is missing the final-verdict trust boundary: $literal" }
}

foreach ($scriptPath in @(
    (Join-Path $PSScriptRoot 'DeviceGateEvidence.psm1'),
    (Join-Path $PSScriptRoot 'capture-device-gate-evidence.ps1'),
    (Join-Path $PSScriptRoot 'validate-device-gate-evidence.ps1'),
    (Join-Path $PSScriptRoot 'test-device-gate-evidence.ps1')
)) {
    $tokens = $null
    $errors = $null
    $null = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "PowerShell parse failed for $scriptPath`: $($errors[0].Message)" }
}

$workflowLines = [IO.File]::ReadAllLines((Join-Path $repositoryRoot '.github\workflows\final-device-gate.yml'))
for ($lineIndex = 0; $lineIndex -lt $workflowLines.Count; $lineIndex++) {
    $runMatch = [regex]::Match($workflowLines[$lineIndex], '^(\s*)run:\s*\|\s*$')
    if (-not $runMatch.Success) { continue }
    $baseIndent = $runMatch.Groups[1].Value.Length
    $body = [Collections.Generic.List[string]]::new()
    for ($bodyIndex = $lineIndex + 1; $bodyIndex -lt $workflowLines.Count; $bodyIndex++) {
        $line = $workflowLines[$bodyIndex]
        if ([string]::IsNullOrWhiteSpace($line)) { $body.Add(''); continue }
        $indent = [regex]::Match($line, '^\s*').Value.Length
        if ($indent -le $baseIndent) { break }
        $body.Add($line.Substring([Math]::Min($baseIndent + 2, $line.Length)))
    }
    $tokens = $null
    $errors = $null
    $null = [Management.Automation.Language.Parser]::ParseInput(($body -join "`n"), [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "PowerShell run block parse failed near workflow line $($lineIndex + 1): $($errors[0].Message)" }
}

Write-Output 'Device-gate evidence contract PASS (1 fixture-only positive, negative/fail-closed cases, artifact/log/API/workflow/checklist binding).'
[IO.Directory]::Delete($fixtureRoot, $true)
exit 0
