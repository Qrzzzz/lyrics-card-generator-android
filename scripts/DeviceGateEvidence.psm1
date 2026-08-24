Set-StrictMode -Version Latest

$script:RequiredGateEnvironment = [ordered]@{
    'api26-core' = @{ Api = 26; Kind = 'AVD' }
    'api26-renderer-webview' = @{ Api = 26; Kind = 'AVD' }
    'api26-recovery-atf' = @{ Api = 26; Kind = 'AVD' }
    'api30-serif-measure-spec-1x-2x' = @{ Api = 30; Kind = 'AVD' }
    'api30-export-20x' = @{ Api = 30; Kind = 'AVD' }
    'api30-cancel-retry-temp-cleanup' = @{ Api = 30; Kind = 'AVD' }
    'api30-memory' = @{ Api = 30; Kind = 'AVD' }
    'api33-core' = @{ Api = 33; Kind = 'AVD' }
    'api33-recovery-atf' = @{ Api = 33; Kind = 'AVD' }
    'api33-talkback' = @{ Api = 33; Kind = 'AVD' }
    'api33-font-scale-200' = @{ Api = 33; Kind = 'AVD' }
    'api36-connected-production' = @{ Api = 36; Kind = 'AVD' }
    'api36-export-20x' = @{ Api = 36; Kind = 'AVD' }
    'four-gb-2x-memory' = @{ Api = 30; Kind = 'AVD'; RamMiB = 4096 }
    'endurance-30m' = @{ Api = 36; Kind = 'AVD' }
    'physical-core-save-share' = @{ Kind = 'PHYSICAL' }
}

function Assert-Text {
    param([object] $Value, [string] $Name)
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value)) { throw "$Name is required." }
}

function Assert-Sha256 {
    param([object] $Value, [string] $Name)
    if ($Value -isnot [string] -or $Value -cnotmatch '^[0-9a-f]{64}$') { throw "$Name must be a lowercase SHA-256." }
}

function Assert-CommitSha {
    param([object] $Value, [string] $Name)
    if ($Value -isnot [string] -or $Value -cnotmatch '^[0-9a-f]{40}$') { throw "$Name must be a lowercase full commit SHA." }
}

function Get-RequiredProperty {
    param([object] $Object, [string] $Name, [string] $Context)
    if ($null -eq $Object) { throw "$Context is required." }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) { throw "$Context.$Name is required." }
    return $property.Value
}

function Assert-UtcTimestamp {
    param([object] $Value, [string] $Name)
    if ($Value -is [datetimeoffset]) { return [datetimeoffset]$Value }
    if ($Value -is [datetime]) {
        $date = [datetime]$Value
        if ($date.Kind -eq [DateTimeKind]::Unspecified) { $date = [datetime]::SpecifyKind($date, [DateTimeKind]::Utc) }
        return [datetimeoffset]$date.ToUniversalTime()
    }
    Assert-Text $Value $Name
    $parsed = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParseExact(
        [string]$Value,
        'yyyy-MM-ddTHH:mm:ssZ',
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal,
        [ref]$parsed
    )) { throw "$Name must use UTC yyyy-MM-ddTHH:mm:ssZ." }
    return $parsed
}

function Assert-Artifact {
    param([object] $Artifact, [string] $Name)
    Assert-Text (Get-RequiredProperty $Artifact 'name' $Name) "$Name.name"
    $bytes = Get-RequiredProperty $Artifact 'bytes' $Name
    if ([long]$bytes -le 0) { throw "$Name.bytes must be positive." }
    Assert-Sha256 (Get-RequiredProperty $Artifact 'sha256' $Name) "$Name.sha256"
    Assert-Sha256 (Get-RequiredProperty $Artifact 'certificateSha256' $Name) "$Name.certificateSha256"
}

function Resolve-EvidenceLogPath {
    param([string] $EvidenceRoot, [string] $RelativePath)
    if ($RelativePath -notmatch '^[A-Za-z0-9._/-]+$' -or $RelativePath -match '(^|/)\.\.(/|$)') {
        throw "Evidence log path '$RelativePath' is unsafe."
    }
    $root = [IO.Path]::GetFullPath($EvidenceRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath((Join-Path $root ($RelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)))
    if (-not $resolved.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Evidence log path '$RelativePath' escapes the evidence root."
    }
    return $resolved
}

function Assert-DeviceGateEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object] $Evidence,
        [Parameter(Mandatory = $true)][string] $EvidenceRoot,
        [Parameter(Mandatory = $true)][string] $ExpectedRepository,
        [Parameter(Mandatory = $true)][string] $ExpectedSourceCommit,
        [Parameter(Mandatory = $true)][long] $ExpectedEvidenceRunId,
        [Parameter(Mandatory = $true)][int] $ExpectedEvidenceRunAttempt,
        [Parameter(Mandatory = $true)][string] $ExpectedEvidenceWorkflowPath,
        [Parameter(Mandatory = $true)][string] $ExpectedEvidenceWorkflowEvent,
        [Parameter(Mandatory = $true)][string] $ExpectedEvidenceArtifactName,
        [switch] $AllowTestFixture
    )

    if ((Get-RequiredProperty $Evidence 'schemaVersion' 'evidence') -ne 1) { throw 'Unsupported device-gate schemaVersion.' }
    if ((Get-RequiredProperty $Evidence 'evidenceType' 'evidence') -ne 'lyrics-card-final-device-gate') { throw 'Unexpected device-gate evidenceType.' }
    $isFixture = [bool](Get-RequiredProperty $Evidence 'testFixture' 'evidence')
    if ($isFixture -and -not $AllowTestFixture) { throw 'Test fixture evidence is never valid for a production gate.' }
    if ((Get-RequiredProperty $Evidence 'status' 'evidence') -ne 'READY') { throw 'Final device-gate evidence status must be READY.' }

    $candidate = Get-RequiredProperty $Evidence 'candidate' 'evidence'
    $repository = [string](Get-RequiredProperty $candidate 'repository' 'candidate')
    $sourceCommit = [string](Get-RequiredProperty $candidate 'sourceCommit' 'candidate')
    if ($repository -ne $ExpectedRepository) { throw "Evidence repository '$repository' does not match '$ExpectedRepository'." }
    Assert-CommitSha $sourceCommit 'candidate.sourceCommit'
    if ($sourceCommit -cne $ExpectedSourceCommit) { throw "Evidence source commit '$sourceCommit' does not match candidate '$ExpectedSourceCommit'." }
    foreach ($field in @('candidateRunId', 'candidateRunAttempt', 'qualityGateRunId')) {
        if ([long](Get-RequiredProperty $candidate $field 'candidate') -le 0) { throw "candidate.$field must be positive." }
    }
    Assert-Text (Get-RequiredProperty $candidate 'candidateArtifactName' 'candidate') 'candidate.candidateArtifactName'
    Assert-Sha256 (Get-RequiredProperty $candidate 'releaseMetadataSha256' 'candidate') 'candidate.releaseMetadataSha256'
    $package = [string](Get-RequiredProperty $candidate 'package' 'candidate')
    if ($package -ne 'com.qrzzzz.lyricscard') { throw "Unexpected production package '$package'." }
    $versionName = [string](Get-RequiredProperty $candidate 'versionName' 'candidate')
    if ($versionName -notmatch '^\d+\.\d+\.\d+$') { throw 'candidate.versionName must be x.y.z.' }
    $versionCode = [int](Get-RequiredProperty $candidate 'versionCode' 'candidate')
    if ($versionCode -le 0) { throw 'candidate.versionCode must be positive.' }

    $productionApk = Get-RequiredProperty $candidate 'productionApk' 'candidate'
    $productionAab = Get-RequiredProperty $candidate 'productionAab' 'candidate'
    $testApk = Get-RequiredProperty $candidate 'testApk' 'candidate'
    Assert-Artifact $productionApk 'candidate.productionApk'
    Assert-Artifact $productionAab 'candidate.productionAab'
    Assert-Artifact $testApk 'candidate.testApk'
    if ($productionApk.name -ne "lyrics-card-generator-android-$versionName.apk" -or
        $productionAab.name -ne "lyrics-card-generator-android-$versionName.aab" -or
        $testApk.name -cnotmatch '^[A-Za-z0-9._-]+\.apk$') {
        throw 'Candidate artifact names do not match the production version/safe test APK contract.'
    }
    if ($productionApk.certificateSha256 -cne $productionAab.certificateSha256) { throw 'Production APK and AAB certificates do not match.' }
    if ((Get-RequiredProperty $testApk 'package' 'candidate.testApk') -ne "$package.test") { throw 'Test APK package must be the production package plus .test.' }
    if ((Get-RequiredProperty $testApk 'targetPackage' 'candidate.testApk') -ne $package) { throw 'Test APK targetPackage must equal the production package.' }
    $null = Get-RequiredProperty $testApk 'versionName' 'candidate.testApk'
    if ([int](Get-RequiredProperty $testApk 'versionCode' 'candidate.testApk') -lt 0) { throw 'candidate.testApk.versionCode is invalid.' }

    $run = Get-RequiredProperty $Evidence 'run' 'evidence'
    if ([long](Get-RequiredProperty $run 'workflowRunId' 'run') -ne $ExpectedEvidenceRunId -or
        [int](Get-RequiredProperty $run 'workflowRunAttempt' 'run') -ne $ExpectedEvidenceRunAttempt -or
        (Get-RequiredProperty $run 'workflowPath' 'run') -ne $ExpectedEvidenceWorkflowPath -or
        (Get-RequiredProperty $run 'workflowEvent' 'run') -ne $ExpectedEvidenceWorkflowEvent -or
        (Get-RequiredProperty $run 'artifactName' 'run') -ne $ExpectedEvidenceArtifactName) {
        throw 'Device evidence workflow identity/run/attempt/artifact does not match the verified producer run.'
    }
    $startedAt = Assert-UtcTimestamp (Get-RequiredProperty $run 'startedAt' 'run') 'run.startedAt'
    $completedAt = Assert-UtcTimestamp (Get-RequiredProperty $run 'completedAt' 'run') 'run.completedAt'
    if ($completedAt -lt $startedAt) { throw 'run.completedAt precedes run.startedAt.' }
    Assert-Text (Get-RequiredProperty $run 'runner' 'run') 'run.runner'
    Assert-Text (Get-RequiredProperty $run 'authorizationReference' 'run') 'run.authorizationReference'
    if ((Get-RequiredProperty $run 'stopOnFirstFailure' 'run') -ne $true) { throw 'Device evidence must preserve stopOnFirstFailure=true.' }

    $environments = @(Get-RequiredProperty $Evidence 'environments' 'evidence')
    if ($environments.Count -ne 5) { throw 'Exactly five device environments are required.' }
    $environmentById = @{}
    foreach ($environment in $environments) {
        $id = [string](Get-RequiredProperty $environment 'id' 'environment')
        if ($environmentById.ContainsKey($id)) { throw "Duplicate environment id '$id'." }
        $environmentById[$id] = $environment
        $kind = [string](Get-RequiredProperty $environment 'kind' "environment[$id]")
        if ($kind -notin @('AVD', 'PHYSICAL')) { throw "environment[$id].kind is invalid." }
        $apiLevel = [int](Get-RequiredProperty $environment 'apiLevel' "environment[$id]")
        Assert-Text (Get-RequiredProperty $environment 'androidVersion' "environment[$id]") "environment[$id].androidVersion"
        Assert-Text (Get-RequiredProperty $environment 'systemImage' "environment[$id]") "environment[$id].systemImage"
        Assert-Sha256 (Get-RequiredProperty $environment 'deviceIdSha256' "environment[$id]") "environment[$id].deviceIdSha256"
        if ([int](Get-RequiredProperty $environment 'ramMiB' "environment[$id]") -lt 1024) { throw "environment[$id].ramMiB is invalid." }
        if ($kind -eq 'AVD') {
            Assert-Text (Get-RequiredProperty $environment 'avdName' "environment[$id]") "environment[$id].avdName"
            if ($environment.systemImage -notmatch "(^|;)android-$apiLevel(;|$)") { throw "environment[$id].systemImage does not bind API $apiLevel." }
        } else {
            foreach ($field in @('manufacturer', 'model', 'securityPatch', 'authorizationReference')) {
                Assert-Text (Get-RequiredProperty $environment $field "environment[$id]") "environment[$id].$field"
            }
        }
        $webView = Get-RequiredProperty $environment 'webView' "environment[$id]"
        Assert-Text (Get-RequiredProperty $webView 'package' "environment[$id].webView") "environment[$id].webView.package"
        Assert-Text (Get-RequiredProperty $webView 'version' "environment[$id].webView") "environment[$id].webView.version"
        $installed = Get-RequiredProperty $environment 'installedArtifacts' "environment[$id]"
        if ($installed.sourceCommit -cne $sourceCommit -or $installed.package -ne $package -or
            $installed.versionName -ne $versionName -or [int]$installed.versionCode -ne $versionCode) {
            throw "environment[$id] is bound to a different source/package/version."
        }
        if ($installed.productionApkSha256 -cne $productionApk.sha256 -or $installed.testApkSha256 -cne $testApk.sha256) {
            throw "environment[$id] installed APK hashes do not match the candidate."
        }
        if ($installed.certificateSha256 -cne $productionApk.certificateSha256) {
            throw "environment[$id] production certificate does not match the candidate."
        }
        if ($installed.testCertificateSha256 -cne $testApk.certificateSha256) {
            throw "environment[$id] test certificate does not match the candidate."
        }
    }

    foreach ($api in @(26, 30, 33, 36)) {
        if (@($environments | Where-Object { $_.kind -eq 'AVD' -and [int]$_.apiLevel -eq $api }).Count -ne 1) {
            throw "Exactly one AVD environment is required for API $api."
        }
    }
    if (@($environments | Where-Object { $_.kind -eq 'PHYSICAL' }).Count -ne 1) { throw 'Exactly one authorized physical environment is required.' }

    $gates = @(Get-RequiredProperty $Evidence 'gates' 'evidence')
    $gateById = @{}
    foreach ($gate in $gates) {
        $gateId = [string](Get-RequiredProperty $gate 'id' 'gate')
        if (-not $script:RequiredGateEnvironment.Contains($gateId)) { throw "Unexpected gate '$gateId'." }
        if ($gateById.ContainsKey($gateId)) { throw "Duplicate gate '$gateId'." }
        $gateById[$gateId] = $gate
        if ((Get-RequiredProperty $gate 'status' "gate[$gateId]") -ne 'PASS') { throw "gate[$gateId] is not PASS." }
        if ([int](Get-RequiredProperty $gate 'attempts' "gate[$gateId]") -ne 1) { throw "gate[$gateId] must record exactly one attempt." }
        Assert-Text (Get-RequiredProperty $gate 'testSelector' "gate[$gateId]") "gate[$gateId].testSelector"
        $gateStarted = Assert-UtcTimestamp (Get-RequiredProperty $gate 'startedAt' "gate[$gateId]") "gate[$gateId].startedAt"
        $gateCompleted = Assert-UtcTimestamp (Get-RequiredProperty $gate 'completedAt' "gate[$gateId]") "gate[$gateId].completedAt"
        if ($gateCompleted -lt $gateStarted) { throw "gate[$gateId].completedAt precedes startedAt." }
        $environmentId = [string](Get-RequiredProperty $gate 'environmentId' "gate[$gateId]")
        if (-not $environmentById.ContainsKey($environmentId)) { throw "gate[$gateId] references unknown environment '$environmentId'." }
        $expected = $script:RequiredGateEnvironment[$gateId]
        $actualEnvironment = $environmentById[$environmentId]
        $expectedApi = $expected['Api']
        $expectedRamMiB = $expected['RamMiB']
        if ($actualEnvironment.kind -ne $expected['Kind'] -or ($expectedApi -and [int]$actualEnvironment.apiLevel -ne $expectedApi)) {
            throw "gate[$gateId] is assigned to the wrong environment."
        }
        if ($expectedRamMiB -and [int]$actualEnvironment.ramMiB -ne $expectedRamMiB) {
            throw "gate[$gateId] requires an exact $expectedRamMiB MiB AVD."
        }

        $logs = @(Get-RequiredProperty $gate 'logs' "gate[$gateId]")
        if ($logs.Count -lt 2) { throw "gate[$gateId] requires instrumentation/manual and logcat evidence." }
        $logKinds = @($logs | ForEach-Object { [string](Get-RequiredProperty $_ 'kind' "gate[$gateId].log") })
        if ('logcat' -notin $logKinds -or @(@('instrumentation', 'manual') | Where-Object { $_ -in $logKinds }).Count -eq 0) {
            throw "gate[$gateId] is missing instrumentation/manual or logcat evidence."
        }
        foreach ($log in $logs) {
            $relativePath = [string](Get-RequiredProperty $log 'path' "gate[$gateId].log")
            $expectedHash = [string](Get-RequiredProperty $log 'sha256' "gate[$gateId].log")
            Assert-Sha256 $expectedHash "gate[$gateId].log.sha256"
            $logPath = Resolve-EvidenceLogPath -EvidenceRoot $EvidenceRoot -RelativePath $relativePath
            if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) { throw "gate[$gateId] log is missing: $relativePath" }
            $actualHash = (Get-FileHash -LiteralPath $logPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualHash -cne $expectedHash) { throw "gate[$gateId] log SHA-256 mismatch: $relativePath" }
        }
    }
    foreach ($requiredGate in $script:RequiredGateEnvironment.Keys) {
        if (-not $gateById.ContainsKey($requiredGate)) { throw "Required gate '$requiredGate' is missing." }
    }
    if ($gateById.Count -ne $script:RequiredGateEnvironment.Count) { throw 'The final device-gate matrix contains unexpected entries.' }

    return $Evidence
}

function Assert-TestApkInspection {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object] $Evidence,
        [Parameter(Mandatory = $true)][string] $Badging,
        [Parameter(Mandatory = $true)][string] $CertificateOutput
    )

    $packageMatch = [regex]::Match($Badging, "(?m)^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'")
    $instrumentationMatch = [regex]::Match($Badging, "(?m)^instrumentation: name='([^']+)' targetPackage='([^']+)'\s*$")
    if (-not $packageMatch.Success -or -not $instrumentationMatch.Success) {
        throw 'Could not read package/version/instrumentation target from the test APK.'
    }
    $testApk = $Evidence.candidate.testApk
    $inspectedVersionCode = if ([string]::IsNullOrEmpty($packageMatch.Groups[2].Value)) { 0 } else { [int]$packageMatch.Groups[2].Value }
    if ($packageMatch.Groups[1].Value -ne $testApk.package -or
        $inspectedVersionCode -ne ([int]$testApk.versionCode) -or
        $packageMatch.Groups[3].Value -ne $testApk.versionName -or
        $instrumentationMatch.Groups[2].Value -ne $testApk.targetPackage) {
        throw 'Test APK package/version/targetPackage does not match device evidence.'
    }
    $certificateMatch = [regex]::Match($CertificateOutput, '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$')
    if (-not $certificateMatch.Success) { throw 'Could not read the test APK certificate SHA-256.' }
    $certificate = ($certificateMatch.Groups[1].Value -replace ':', '').ToLowerInvariant()
    if ($certificate -cne $testApk.certificateSha256) { throw 'Test APK certificate does not match device evidence.' }
}

function Assert-DeviceGateWorkflowRun {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object] $Run,
        [Parameter(Mandatory = $true)][string] $ExpectedRepository,
        [Parameter(Mandatory = $true)][string] $ExpectedSourceCommit,
        [Parameter(Mandatory = $true)][string] $ExpectedPath,
        [Parameter(Mandatory = $true)][string] $ExpectedEvent
    )

    if ([long](Get-RequiredProperty $Run 'id' 'workflowRun') -le 0 -or
        [int](Get-RequiredProperty $Run 'run_attempt' 'workflowRun') -le 0) {
        throw 'Workflow run id/attempt must be positive.'
    }
    $headRepository = Get-RequiredProperty $Run 'head_repository' 'workflowRun'
    if ((Get-RequiredProperty $headRepository 'full_name' 'workflowRun.head_repository') -ne $ExpectedRepository -or
        (Get-RequiredProperty $Run 'head_sha' 'workflowRun') -cne $ExpectedSourceCommit -or
        (Get-RequiredProperty $Run 'head_branch' 'workflowRun') -ne 'main' -or
        (Get-RequiredProperty $Run 'status' 'workflowRun') -ne 'completed' -or
        (Get-RequiredProperty $Run 'conclusion' 'workflowRun') -ne 'success' -or
        (Get-RequiredProperty $Run 'event' 'workflowRun') -ne $ExpectedEvent -or
        (Get-RequiredProperty $Run 'path' 'workflowRun') -ne $ExpectedPath) {
        throw "Workflow run $($Run.id) does not match repository/source/main/success/identity policy for $ExpectedPath."
    }
    return $Run
}

function Assert-DeviceGateArtifactBinding {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][object] $Evidence,
        [Parameter(Mandatory = $true)][string] $ReleaseMetadataPath,
        [Parameter(Mandatory = $true)][string] $ProductionApkPath,
        [Parameter(Mandatory = $true)][string] $ProductionAabPath,
        [Parameter(Mandatory = $true)][string] $TestApkPath
    )

    foreach ($path in @($ReleaseMetadataPath, $ProductionApkPath, $ProductionAabPath, $TestApkPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required candidate artifact is missing: $path" }
    }
    $metadataHash = (Get-FileHash -LiteralPath $ReleaseMetadataPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($metadataHash -cne $Evidence.candidate.releaseMetadataSha256) { throw 'release-metadata.json SHA-256 does not match device evidence.' }
    $metadata = Get-Content -LiteralPath $ReleaseMetadataPath -Raw | ConvertFrom-Json
    if ($metadata.schemaVersion -ne 2 -or $metadata.readiness.status -ne 'PROVISIONAL' -or
        $metadata.readiness.deviceGate -ne 'NOT RUN' -or $metadata.readiness.finalReady -ne $false) {
        throw 'Release metadata must remain PROVISIONAL / device NOT RUN / finalReady false before final device validation.'
    }
    if ($metadata.source.repository -ne $Evidence.candidate.repository -or
        $metadata.source.commit -cne $Evidence.candidate.sourceCommit -or
        [long]$metadata.source.runId -ne [long]$Evidence.candidate.candidateRunId -or
        [int]$metadata.source.runAttempt -ne [int]$Evidence.candidate.candidateRunAttempt -or
        [long]$metadata.source.qualityGateRunId -ne [long]$Evidence.candidate.qualityGateRunId -or
        $metadata.package -ne $Evidence.candidate.package -or
        $metadata.versionName -ne $Evidence.candidate.versionName -or
        [int]$metadata.versionCode -ne [int]$Evidence.candidate.versionCode) {
        throw 'Release metadata source/package/version does not match device evidence.'
    }
    if ($metadata.signing.status -ne 'verified' -or
        $metadata.signing.certificateSha256 -cne $Evidence.candidate.productionApk.certificateSha256) {
        throw 'Release metadata signing identity does not match device evidence.'
    }
    $expectedArtifactName = "production-candidate-$($metadata.versionName)-$($metadata.source.commit.Substring(0, 12))"
    if ($Evidence.candidate.candidateArtifactName -cne $expectedArtifactName) {
        throw "Device evidence candidate artifact name does not match '$expectedArtifactName'."
    }

    $bindings = @(
        @{ Evidence = $Evidence.candidate.productionApk; Path = $ProductionApkPath; InMetadata = $true },
        @{ Evidence = $Evidence.candidate.productionAab; Path = $ProductionAabPath; InMetadata = $true },
        @{ Evidence = $Evidence.candidate.testApk; Path = $TestApkPath; InMetadata = $false }
    )
    foreach ($binding in $bindings) {
        $file = Get-Item -LiteralPath $binding.Path
        $actualHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($file.Name -ne $binding.Evidence.name -or $file.Length -ne [long]$binding.Evidence.bytes -or $actualHash -cne $binding.Evidence.sha256) {
            throw "Artifact bytes do not match device evidence: $($file.Name)"
        }
        if ($binding.InMetadata) {
            $digest = @($metadata.artifactDigests | Where-Object { $_.name -eq $file.Name })
            if ($digest.Count -ne 1 -or [long]$digest[0].bytes -ne $file.Length -or $digest[0].sha256 -cne $actualHash) {
                throw "Release metadata artifact digest does not match: $($file.Name)"
            }
        }
    }
    return $metadata
}

Export-ModuleMember -Function Assert-DeviceGateEvidence, Assert-DeviceGateArtifactBinding, Assert-TestApkInspection, Assert-DeviceGateWorkflowRun
