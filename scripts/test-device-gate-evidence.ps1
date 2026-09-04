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

function Assert-RejectedLog {
    param([string] $Name, [scriptblock] $Mutate, [string] $MessagePattern, [string] $Kind = 'instrumentation', [string] $GateId = 'api26-core')

    $case = Copy-Evidence (Read-Fixture)
    $relativePath = @($case.gates[0].logs | Where-Object { $_.kind -eq $Kind })[0].path
    $path = Join-Path $fixtureRoot $relativePath
    $originalBytes = [IO.File]::ReadAllBytes($path)
    try {
        $changed = & $Mutate ([IO.File]::ReadAllText($path))
        [IO.File]::WriteAllText($path, $changed, [Text.UTF8Encoding]::new($false))
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        foreach ($gate in $case.gates) {
            foreach ($log in $gate.logs) {
                if ($log.path -eq $relativePath) { $log.sha256 = $hash }
            }
        }
        try {
            $null = Invoke-EvidencePolicy -Evidence $case -AllowFixture
            throw "Hash-consistent negative log '$Name' was accepted."
        } catch {
            if ($_.Exception.Message -like 'Hash-consistent negative log*' -or $_.Exception.Message -notmatch $MessagePattern) { throw }
        }
        # Exercise the producer's public entry point as well: it must reject the
        # current run before capture can record PASS or advance to another gate.
        $producerGate = @($case.gates | Where-Object id -eq $GateId)[0]
        try {
            Assert-DeviceGateRunEvidence -Gate $producerGate -EvidenceRoot $fixtureRoot
            throw "Producer accepted negative log '$Name'."
        } catch {
            if ($_.Exception.Message -like 'Producer accepted negative log*' -or $_.Exception.Message -notmatch $MessagePattern) { throw }
        }
    } finally {
        [IO.File]::WriteAllBytes($path, $originalBytes)
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
if (@($positive.gates).Count -ne 18 -or @($positive.environments).Count -ne 5) {
    throw 'The positive fixture did not preserve the complete matrix.'
}
foreach ($gate in $positive.gates) { Assert-DeviceGateRunEvidence -Gate $gate -EvidenceRoot $fixtureRoot }
$null = Assert-DeviceGateArtifactBinding `
    -Evidence $positive `
    -ReleaseMetadataPath $fixtureMetadataPath `
    -ProductionApkPath $fixtureApkPath `
    -ProductionAabPath $fixtureAabPath `
    -TestApkPath $fixtureTestApkPath
$fixtureBadging = @"
package: name='com.qrzzzz.lyricscard.test' versionCode='20000' versionName='2.0.0'
"@
$fixtureManifestXmlTree = @"
E: manifest
  E: instrumentation
    A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.qrzzzz.lyricscard.ui.ReleaseEvidenceTestRunner"
    A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)="com.qrzzzz.lyricscard"
    A: http://schemas.android.com/apk/res/android:functionalTest(0x01010023)=false
  E: application
"@
$fixtureCertificate = 'Signer #1 certificate SHA-256 digest: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
Assert-TestApkInspection -Evidence $positive -Badging $fixtureBadging -ManifestXmlTree $fixtureManifestXmlTree -CertificateOutput $fixtureCertificate

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
Assert-Rejected -Name 'missing-build-fingerprint' -Mutate { param($e) $e.environments[1].buildFingerprint = '' } -MessagePattern 'buildFingerprint is required'
Assert-Rejected -Name 'emulator-as-physical' -Mutate { param($e) $e.environments[4].isEmulator = $true } -MessagePattern 'isEmulator does not match'
Assert-Rejected -Name 'physical-as-avd' -Mutate { param($e) $e.environments[1].isEmulator = $false } -MessagePattern 'isEmulator does not match'
Assert-Rejected -Name 'non-boolean-emulator-identity' -Mutate { param($e) $e.environments[4].isEmulator = 'false' } -MessagePattern 'isEmulator does not match'
Assert-Rejected -Name 'missing-measured-ram' -Mutate { param($e) $e.environments[1].PSObject.Properties.Remove('actualRamMiB') } -MessagePattern 'actualRamMiB is required'
Assert-Rejected -Name 'invalid-measured-ram' -Mutate { param($e) $e.environments[1].actualRamMiB = 0 } -MessagePattern 'actualRamMiB must be'
Assert-Rejected -Name 'inflated-measured-ram' -Mutate { param($e) $e.environments[1].actualRamMiB = 8192 } -MessagePattern 'actualRamMiB is outside'
Assert-Rejected -Name 'too-small-measured-ram' -Mutate { param($e) $e.environments[1].actualRamMiB = 1024 } -MessagePattern 'actualRamMiB is outside'
Assert-Rejected -Name 'atf-is-not-talkback' -Mutate { param($e) ($e.gates | Where-Object id -eq 'api33-talkback').testSelector = 'AccessibilityFrameworkTest' } -MessagePattern 'ATF alone is insufficient'
Assert-Rejected -Name 'mock-ui-is-not-cancel-retry' -Mutate { param($e) ($e.gates | Where-Object id -eq 'api30-cancel-retry-temp-cleanup').testSelector = 'com.qrzzzz.lyricscard.ui.EditorExportProductionTest#exportControlsExposeOnlyOneAndTwoXAndMatchBusyFailureAndSuccessStates' } -MessagePattern 'UI state alone is insufficient'
Assert-Rejected -Name 'export-route-is-not-save-share' -Mutate { param($e) ($e.gates | Where-Object id -eq 'physical-core-save-share').testSelector = 'AvdMatrixSmokeTest export route only' } -MessagePattern 'not export-route readiness'
Assert-Rejected -Name 'wrong-system-image-api' -Mutate { param($e) $e.environments[1].systemImage = 'system-images;android-29;google_apis;x86_64' } -MessagePattern 'does not bind API 30'
Assert-Rejected -Name 'missing-api36-environment' -Mutate { param($e) $e.environments[3].apiLevel = 35; $e.environments[3].systemImage = 'system-images;android-35;google_apis;x86_64' } -MessagePattern 'Exactly one AVD environment is required for API 36'
Assert-Rejected -Name 'duplicate-environment' -Mutate { param($e) $e.environments[1].id = 'api26' } -MessagePattern 'Duplicate environment id'
Assert-Rejected -Name 'not-run-gate' -Mutate { param($e) $e.gates[3].status = 'NOT RUN' } -MessagePattern 'is not PASS'
Assert-Rejected -Name 'blocked-gate' -Mutate { param($e) $e.gates[4].status = 'BLOCKED' } -MessagePattern 'is not PASS'
Assert-Rejected -Name 'retry-laundering' -Mutate { param($e) $e.gates[3].attempts = 2 } -MessagePattern 'exactly one attempt'
Assert-Rejected -Name 'missing-required-gate' -Mutate { param($e) $e.gates = @($e.gates | Where-Object { $_.id -ne 'api30-serif-measure-spec-1x-2x' }) } -MessagePattern 'Required gate.*missing'
Assert-Rejected -Name 'missing-api30-core' -Mutate { param($e) $e.gates = @($e.gates | Where-Object { $_.id -ne 'api30-core' }) } -MessagePattern 'Required gate.*missing'
Assert-Rejected -Name 'missing-api30-recovery-atf' -Mutate { param($e) $e.gates = @($e.gates | Where-Object { $_.id -ne 'api30-recovery-atf' }) } -MessagePattern 'Required gate.*missing'
Assert-Rejected -Name 'wrong-gate-environment' -Mutate { param($e) $e.gates[3].environmentId = 'api33' } -MessagePattern 'wrong environment'
Assert-Rejected -Name 'missing-log-file' -Mutate { param($e) $e.gates[3].logs[0].path = 'logs/missing.log' } -MessagePattern 'log is missing'
Assert-Rejected -Name 'wrong-log-sha' -Mutate { param($e) $e.gates[3].logs[0].sha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'log SHA-256 mismatch'
Assert-Rejected -Name 'missing-log-kind' -Mutate { param($e) $e.gates[3].logs = @($e.gates[3].logs | Where-Object { $_.kind -ne 'logcat' }) } -MessagePattern 'requires instrumentation and logcat'
Assert-Rejected -Name 'manual-is-not-instrumentation' -Mutate { param($e) $e.gates[0].logs[0].kind = 'manual' } -MessagePattern 'exactly one instrumentation result'
Assert-Rejected -Name 'endurance-marker-with-short-run' -Mutate { param($e) ($e.gates | Where-Object id -eq 'endurance-30m').completedAt = '2026-08-24T00:15:00Z' } -MessagePattern 'actual completed thirty-minute'
Assert-Rejected -Name 'gate-outside-producer-run' -Mutate { param($e) $e.gates[0].startedAt = '2026-08-23T23:59:00Z' } -MessagePattern 'timestamps are outside'
Assert-Rejected -Name 'test-certificate-is-not-production' -Mutate { param($e) $e.candidate.testApk.certificateSha256 = ('b' * 64) } -MessagePattern 'Release-test and production APK certificates do not match'
Assert-Rejected -Name 'wrong-device-apk-sha' -Mutate { param($e) $e.environments[1].installedArtifacts.productionApkSha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'installed APK hashes do not match'
Assert-Rejected -Name 'wrong-test-certificate' -Mutate { param($e) $e.environments[1].installedArtifacts.testCertificateSha256 = '2222222222222222222222222222222222222222222222222222222222222222' } -MessagePattern 'test certificate does not match'

# Preserve READY/PASS and recompute every log hash: content failures must not be
# accepted merely because the JSON labels and byte digests are self-consistent.
Assert-RejectedLog -Name 'failure-with-positive-ok-summary' -Mutate { param($text) $text.Replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -2') } -MessagePattern 'failed or unsupported test status'
Assert-RejectedLog -Name 'instrumentation-failed' -Mutate { param($text) "INSTRUMENTATION_FAILED: Process crashed`n$text" } -MessagePattern 'instrumentation reports failure'
Assert-RejectedLog -Name 'zero-tests' -Mutate { param($text) "OK (0 tests)`nINSTRUMENTATION_CODE: -1`n" } -MessagePattern 'positive test count'
Assert-RejectedLog -Name 'skipped-only' -Mutate { param($text) $text.Replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -3') } -MessagePattern 'skipped-only'
Assert-RejectedLog -Name 'assumptions-only' -Mutate { param($text) $text.Replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: -4') } -MessagePattern 'skipped-only'
Assert-RejectedLog -Name 'summary-without-test-events' -Mutate { param($text) "OK (1 test)`nINSTRUMENTATION_CODE: -1`n" } -MessagePattern 'incomplete, skipped-only'
Assert-RejectedLog -Name 'started-without-completion' -Mutate { param($text) $text.Replace('INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: 1') } -MessagePattern 'repeats a test'
Assert-RejectedLog -Name 'wrong-successful-method' -Mutate { param($text) $text.Replace('productionMainActivitySixStepPreviewAndOneTwoXExportsWork', 'differentSuccessfulTest') } -MessagePattern 'required test.*did not finish with INSTRUMENTATION_STATUS_CODE: 0'
Assert-RejectedLog -Name 'runner-did-not-complete' -Mutate { param($text) $text.Replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0') } -MessagePattern 'positive test count'
Assert-RejectedLog -Name 'old-injected-talkback-marker' -GateId 'api33-talkback' -Kind 'logcat' -Mutate { param($text) $text.Replace('input=kernel-console-swipe-double-tap', 'input=swipe-double-tap') } -MessagePattern 'real active service and completed gesture'
Assert-RejectedLog -Name 'no-real-cancellation' -GateId 'api30-cancel-retry-temp-cleanup' -Kind 'logcat' -Mutate { param($text) $text.Replace('cancellationObserved=true', 'cancellationObserved=false') } -MessagePattern 'real cancelled partial export'

# AndroidJUnitRunner emits -3 for ignored tests and -4 for assumption failures.
# A non-required API 33 test may be skipped by the full API 36 run, but a required
# gate method must still have its own successful terminal event.
$protocolFixture = [IO.File]::ReadAllText((Join-Path $fixtureRoot 'logs/instrumentation.log'))
$talkbackTerminal = [regex]::new('(?m)(INSTRUMENTATION_STATUS: class=com\.qrzzzz\.lyricscard\.ui\.TalkBackReleaseTest\r?\n(?:INSTRUMENTATION_STATUS: [^\r\n]*\r?\n)*INSTRUMENTATION_STATUS_CODE: )0\r?$')
if ($talkbackTerminal.Matches($protocolFixture).Count -ne 1) { throw 'The protocol fixture must have one TalkBack completion.' }
$fixtureRunCount = [int][regex]::Match($protocolFixture, '(?m)^OK \(([0-9]+) tests\)').Groups[1].Value
$ignoredTalkback = $talkbackTerminal.Replace($protocolFixture, '${1}-3').Replace("OK ($fixtureRunCount tests)", "OK ($($fixtureRunCount - 1) tests)")
$assumedTalkback = $talkbackTerminal.Replace($protocolFixture, '${1}-4')
foreach ($text in @($ignoredTalkback, $assumedTalkback)) {
    & (Get-Module DeviceGateEvidence) { param($log) Assert-InstrumentationResult -Text $log -GateId 'api36-connected-production' } $text
    try {
        & (Get-Module DeviceGateEvidence) { param($log) Assert-InstrumentationResult -Text $log -GateId 'api33-talkback' } $text
        throw 'A skipped required TalkBack method was accepted.'
    } catch {
        if ($_.Exception.Message -notmatch 'required test.*did not finish with INSTRUMENTATION_STATUS_CODE: 0') { throw }
    }
}

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
    @{ Name = 'inspected-test-package'; Badging = $fixtureBadging.Replace('com.qrzzzz.lyricscard.test', 'com.example.test'); Manifest = $fixtureManifestXmlTree; Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-version'; Badging = $fixtureBadging.Replace("versionCode='20000'", "versionCode='1'"); Manifest = $fixtureManifestXmlTree; Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-target'; Badging = $fixtureBadging; Manifest = $fixtureManifestXmlTree.Replace('com.qrzzzz.lyricscard"', 'com.example"'); Certificate = $fixtureCertificate; Pattern = 'package/version/targetPackage' },
    @{ Name = 'inspected-test-unsafe-runner'; Badging = $fixtureBadging; Manifest = $fixtureManifestXmlTree.Replace('com.qrzzzz.lyricscard.ui.ReleaseEvidenceTestRunner', 'androidx.test.runner.AndroidJUnitRunner'); Certificate = $fixtureCertificate; Pattern = 'credential-safe release evidence runner' },
    @{ Name = 'inspected-test-certificate'; Badging = $fixtureBadging; Manifest = $fixtureManifestXmlTree; Certificate = 'Signer #1 certificate SHA-256 digest: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'; Pattern = 'certificate does not match' }
)) {
    try {
        Assert-TestApkInspection -Evidence $positive -Badging $inspectionCase.Badging -ManifestXmlTree $inspectionCase.Manifest -CertificateOutput $inspectionCase.Certificate
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
Assert-TestApkInspection -Evidence $blankVersionEvidence -Badging $blankVersionBadging -ManifestXmlTree $fixtureManifestXmlTree -CertificateOutput $fixtureCertificate

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

function Assert-FinalConsumerRejectsUnattestedTestApk {
    $step = [regex]::Match($finalWorkflow, '(?ms)^      - name: Verify candidate provenance and final device evidence\r?\n(?<step>.*?)(?=^      - name: |\z)').Groups['step'].Value
    $code = [regex]::Match($step, '(?ms)^        run: \|\r?\n(?<code>.*)\z').Groups['code'].Value
    if (-not $code) { throw 'The final consumer must have an executable provenance/evidence verification step.' }
    $candidateDir = Join-Path $fixtureRoot 'final-gate-input/candidate'
    $evidenceDir = Join-Path $fixtureRoot 'final-gate-input/evidence'
    $null = New-Item -ItemType Directory -Path $candidateDir, $evidenceDir -Force
    Copy-Item -LiteralPath $fixtureMetadataPath, $fixtureApkPath, $fixtureAabPath -Destination $candidateDir
    Copy-Item -LiteralPath $fixtureEvidencePath, $fixtureTestApkPath -Destination $evidenceDir
    $values = @{
        SOURCE_COMMIT = $expectedCommit
        CANDIDATE_RUN_ID = '1001'
        CANDIDATE_RUN_ATTEMPT = '1'
        CANDIDATE_ARTIFACT_NAME = 'production-candidate-2.0.0-111111111111'
        REPOSITORY = $expectedRepository
        ANDROID_HOME = (Join-Path $fixtureRoot 'sdk-not-invoked')
    }
    $saved = @{}
    $verifiedNames = [Collections.Generic.List[string]]::new()
    $originalDirectory = [Environment]::CurrentDirectory
    $nativeExitCode = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
    $originalExitCode = if ($nativeExitCode) { $nativeExitCode.Value } else { 0 }
    function gh {
        if ($args[0] -ne 'attestation' -or $args[1] -ne 'verify' -or
            $args -notcontains '--source-digest' -or $args -notcontains $expectedCommit -or
            $args -notcontains '--signer-workflow' -or $args -notcontains "$expectedRepository/.github/workflows/release.yml") {
            throw 'Unexpected command or missing source/workflow constraints in the final consumer.'
        }
        $name = [IO.Path]::GetFileName($args[2])
        $verifiedNames.Add($name)
        # Simulate public candidate assets having valid attestations while the
        # self-hosted evidence contains a substituted, unattested test APK.
        $global:LASTEXITCODE = if ($name -eq 'app-production-release-androidTest.apk') { 1 } else { 0 }
    }
    try {
        foreach ($key in $values.Keys) {
            $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
            [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
        }
        Push-Location $fixtureRoot
        [Environment]::CurrentDirectory = $fixtureRoot
        try {
            try {
                & ([scriptblock]::Create(($code -replace '(?m)^          ', '')))
                throw 'The final consumer accepted an unattested test APK.'
            } catch {
                if ($_.Exception.Message -notmatch 'Candidate/test attestation failed: app-production-release-androidTest.apk') { throw }
            }
            if ($verifiedNames.Count -ne 4 -or $verifiedNames[$verifiedNames.Count - 1] -ne 'app-production-release-androidTest.apk' -or
                (Test-Path -LiteralPath 'final-gate-result/final-device-gate-verdict.json')) {
                throw 'The final consumer did not reject the test APK before producing a final verdict.'
            }
        } finally {
            [Environment]::CurrentDirectory = $originalDirectory
            Pop-Location
        }
    } finally {
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
        $global:LASTEXITCODE = $originalExitCode
    }
}
Assert-FinalConsumerRejectsUnattestedTestApk
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
foreach ($literal in @('runs-on: [self-hosted, Windows, X64, lcg-device-gate]', 'environment: final-device-gate', 'capture-device-gate-evidence.ps1', 'name: ${{ steps.input-policy.outputs.test_artifact_name }}', 'actions/download-artifact@', 'gh attestation verify', 'actions/upload-artifact@')) {
    if ($captureWorkflow.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Capture workflow is missing controlled producer binding: $literal" }
}
$captureTimeout = [regex]::Match($captureWorkflow, '(?m)^\s+timeout-minutes:\s*(\d+)\s*(?:#.*)?$')
if (-not $captureTimeout.Success -or [int]$captureTimeout.Groups[1].Value -lt 60 -or [int]$captureTimeout.Groups[1].Value -gt 360) {
    throw 'The device matrix requires a bounded timeout between 60 and 360 minutes.'
}
if ($captureWorkflow -match '(?m)^\s+pattern:\s*production-device-test-') {
    throw 'The capture producer must download the exact candidate test artifact, not a wildcard match.'
}
if ($captureWorkflow -match '\$\{\{\s*secrets\.' -or $captureWorkflow -match '(?m)^\s+(id-token|attestations):\s*write\s*$') {
    throw 'The capture producer must not read GitHub signing secrets or write attestations.'
}
if ($captureWorkflow -match 'LYRICS_CARD_(STORE|KEY)_' -or $captureWorkflow -match 'gradlew\.bat') {
    throw 'The device runner must consume the attested test APK and must not rebuild it or require signing credentials.'
}
$captureScript = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'scripts\capture-device-gate-evidence.ps1'))
foreach ($literal in @("Start-Process -FilePath `$adb", "'threadtime', '-T', '1'", '-RedirectStandardOutput $logcatPath', 'Stop-Process -Id $logcatProcess.Id', 'Streaming logcat produced no evidence')) {
    if ($captureScript.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Capture script is missing isolated streaming logcat binding: $literal" }
}
if ($captureScript -match "@\('logcat',\s*'-c'\)") { throw 'Capture script must not require clearing protected device log buffers.' }
$testProguardRules = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'app\test-proguard-rules.pro'))
foreach ($literal in @('-keep class androidx.test.** { *; }', '-keep class androidx.tracing.** { *; }')) {
    if ($testProguardRules.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Release AndroidTest shrinker rules are missing: $literal" }
}
$releaseManifest = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'app\src\release\AndroidManifest.xml'))
foreach ($literal in @('androidx.activity.ComponentActivity', 'android:exported="false"')) {
    if ($releaseManifest.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) { throw "Exact-release instrumentation host activity manifest is missing: $literal" }
}
$appBuild = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'app\build.gradle.kts'))
if ($appBuild.IndexOf('releaseImplementation(libs.androidx.compose.ui.test.manifest)', [StringComparison]::Ordinal) -lt 0) {
    throw 'The exact minified release APK must package the non-exported Compose instrumentation host.'
}
$productionProguardRules = [IO.File]::ReadAllText((Join-Path $repositoryRoot 'app\proguard-rules.pro'))
foreach ($literal in @('-keep class androidx.tracing.** { *; }', '-keep class kotlin.** { *; }', '-keep class kotlinx.coroutines.** { *; }', '-keep class androidx.compose.** { *; }', '-keep class androidx.lifecycle.** { *; }', '-keep class androidx.savedstate.** { *; }', '-keep class androidx.activity.** { *; }')) {
    if ($productionProguardRules.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) {
        throw "The minified production APK must retain the release instrumentation runtime: $literal"
    }
}
foreach ($literal in @('-keep,allowoptimization class com.qrzzzz.lyricscard.** {', 'public protected *;')) {
    if ($productionProguardRules.IndexOf($literal, [StringComparison]::Ordinal) -lt 0) {
        throw "The minified production APK must preserve the target-app ABI used by release instrumentation: $literal"
    }
}
# Documentation wording and historical issue labels are not executable security boundaries.

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

Write-Output 'Device-gate evidence contract PASS (18 fixture-only gates, shared producer/final raw success/skip/failure/marker checks, measured environments, and executable final-consumer test provenance rejection).'
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$resolvedFixtureRoot = [IO.Path]::GetFullPath($fixtureRoot)
if (-not $resolvedFixtureRoot.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetFileName($resolvedFixtureRoot) -notlike 'lyrics-card-device-gate-fixture-*') {
    throw 'Refusing to remove a contract fixture outside its temporary directory.'
}
[IO.Directory]::Delete($resolvedFixtureRoot, $true)
exit 0
