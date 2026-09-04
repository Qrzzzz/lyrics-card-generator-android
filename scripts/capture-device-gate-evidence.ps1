[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $CandidateRoot,
    [Parameter(Mandatory = $true)][string] $TestApkPath,
    [Parameter(Mandatory = $true)][string] $OutputRoot,
    [Parameter(Mandatory = $true)][string] $SourceCommit,
    [Parameter(Mandatory = $true)][long] $CandidateRunId,
    [Parameter(Mandatory = $true)][string] $CandidateArtifactName,
    [Parameter(Mandatory = $true)][long] $EvidenceRunId,
    [Parameter(Mandatory = $true)][int] $EvidenceRunAttempt,
    [Parameter(Mandatory = $true)][string] $EvidenceArtifactName,
    [Parameter(Mandatory = $true)][string] $PhysicalDeviceSerial,
    [Parameter(Mandatory = $true)][string] $AuthorizationReference,
    [Parameter(Mandatory = $true)][string] $AndroidSdkRoot,
    [Parameter(Mandatory = $true)][string] $AvdRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptStartedAt = [datetimeoffset]::UtcNow
$repository = 'Qrzzzz/lyrics-card-generator-android'
$workflowPath = '.github/workflows/capture-device-gate-evidence.yml'
$runnerName = if ($env:RUNNER_NAME) { $env:RUNNER_NAME } else { $env:COMPUTERNAME }
$adb = Join-Path $AndroidSdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $AndroidSdkRoot 'emulator\emulator.exe'
$avdManager = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkRoot 'cmdline-tools') -Filter 'avdmanager.bat' -Recurse -File | Select-Object -First 1
$aapt2 = Join-Path $AndroidSdkRoot 'build-tools\36.1.0\aapt2.exe'
$apkSigner = Join-Path $AndroidSdkRoot 'build-tools\36.1.0\apksigner.bat'
foreach ($tool in @($adb, $emulator, $avdManager.FullName, $aapt2, $apkSigner)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Required Android tool is missing: $tool" }
}

$candidateRootPath = [IO.Path]::GetFullPath($CandidateRoot)
$outputRootPath = [IO.Path]::GetFullPath($OutputRoot)
$avdRootPath = [IO.Path]::GetFullPath($AvdRoot)
New-Item -ItemType Directory -Path $outputRootPath -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputRootPath 'logs') -Force | Out-Null
trap {
    if (-not (Get-Variable outputRootPath -ErrorAction SilentlyContinue) -or -not (Test-Path -LiteralPath $outputRootPath -PathType Container)) { throw }
    [ordered]@{
        status = 'FAILED'
        sourceCommit = $SourceCommit
        workflowRunId = $EvidenceRunId
        startedAt = $scriptStartedAt.ToString('o')
        failedAt = [datetimeoffset]::UtcNow.ToString('o')
        message = $_.Exception.Message
        completedGateIds = if (Get-Variable gates -ErrorAction SilentlyContinue) { @($gates | ForEach-Object { $_.id }) } else { @() }
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputRootPath 'capture-failure.json') -Encoding utf8
    throw
}
New-Item -ItemType Directory -Path $avdRootPath -Force | Out-Null
$env:ANDROID_AVD_HOME = $avdRootPath

$metadataPath = Join-Path $candidateRootPath 'release-metadata.json'
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) { throw 'Candidate metadata is missing.' }
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ($metadata.source.repository -ne $repository -or $metadata.source.commit -cne $SourceCommit -or
    [long]$metadata.source.runId -ne $CandidateRunId -or [int]$metadata.source.runAttempt -le 0) {
    throw 'Candidate metadata does not match the authorized capture inputs.'
}
$expectedCandidateName = "production-candidate-$($metadata.versionName)-$($SourceCommit.Substring(0, 12))"
if ($CandidateArtifactName -cne $expectedCandidateName) { throw 'Candidate artifact name is not bound to source/version.' }
$productionApkName = "lyrics-card-generator-android-$($metadata.versionName).apk"
$productionAabName = "lyrics-card-generator-android-$($metadata.versionName).aab"
$productionApkPath = Join-Path $candidateRootPath $productionApkName
$productionAabPath = Join-Path $candidateRootPath $productionAabName
foreach ($artifact in @($productionApkPath, $productionAabPath, $TestApkPath)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) { throw "Required artifact is missing: $artifact" }
}

function Get-LowerSha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextSha256([string] $Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [Security.Cryptography.SHA256]::Create()
    try { return (($hash.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '') } finally { $hash.Dispose() }
}

function Get-CertificateSha256([string] $ApkPath) {
    $output = (& $apkSigner verify --print-certs $ApkPath 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed: $ApkPath" }
    $match = [regex]::Match($output, '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$')
    if (-not $match.Success) { throw "Certificate SHA-256 was not found: $ApkPath" }
    return ($match.Groups[1].Value -replace ':', '').ToLowerInvariant()
}

$testBadging = (& $aapt2 dump badging $TestApkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt2 could not inspect the release test APK.' }
$testManifestXmlTree = (& $aapt2 dump xmltree $TestApkPath --file AndroidManifest.xml 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt2 could not inspect the release test APK manifest.' }
$testPackageMatch = [regex]::Match($testBadging, "(?m)^package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'")
$testInstrumentationMatch = [regex]::Match($testManifestXmlTree, '(?ms)E: instrumentation.*?:name\([^)]*\)="([^"]+)".*?:targetPackage\([^)]*\)="([^"]+)"')
if (-not $testPackageMatch.Success -or -not $testInstrumentationMatch.Success) { throw 'Release test APK metadata is incomplete.' }
if ($testInstrumentationMatch.Groups[1].Value -cne 'com.qrzzzz.lyricscard.ui.ReleaseEvidenceTestRunner') {
    throw 'Release evidence requires the test runner that removes console credentials before framework logging.'
}
$testVersionCode = if ([string]::IsNullOrEmpty($testPackageMatch.Groups[2].Value)) { 0 } else { [int]$testPackageMatch.Groups[2].Value }
$testApkName = 'app-production-release-androidTest.apk'
$capturedTestApk = Join-Path $outputRootPath $testApkName
Copy-Item -LiteralPath $TestApkPath -Destination $capturedTestApk -Force

$productionApkSha = Get-LowerSha256 $productionApkPath
$productionAabSha = Get-LowerSha256 $productionAabPath
$testApkSha = Get-LowerSha256 $capturedTestApk
$productionCertificate = [string]$metadata.signing.certificateSha256
$testCertificate = Get-CertificateSha256 $capturedTestApk
if ($productionCertificate -cne (Get-CertificateSha256 $productionApkPath) -or $testCertificate -cne $productionCertificate) {
    throw 'Production and release-test APK certificates do not match the audited signing identity.'
}

$installedArtifacts = [ordered]@{
    sourceCommit = $SourceCommit
    package = [string]$metadata.package
    versionName = [string]$metadata.versionName
    versionCode = [int]$metadata.versionCode
    productionApkSha256 = $productionApkSha
    testApkSha256 = $testApkSha
    certificateSha256 = $productionCertificate
    testCertificateSha256 = $testCertificate
}
$environments = [Collections.Generic.List[object]]::new()
$gates = [Collections.Generic.List[object]]::new()

function Invoke-Adb([string] $Serial, [string[]] $Arguments) {
    $output = (& $adb -s $Serial @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "adb failed for ${Serial}: $($Arguments -join ' ')`n$output" }
    return $output
}

function Get-DeviceProperty([string] $Serial, [string] $Name) {
    return (Invoke-Adb $Serial @('shell', 'getprop', $Name)).Trim()
}

function Get-WebView([string] $Serial) {
    $dump = Invoke-Adb $Serial @('shell', 'dumpsys', 'webviewupdate')
    $current = [regex]::Match($dump, '(?m)^\s*Current WebView package \(name, version\):\s*\(([A-Za-z0-9_.]+),\s*([^\s)]+)\)')
    if (-not $current.Success) { throw "No active WebView provider was found on $Serial." }
    return [ordered]@{ package = $current.Groups[1].Value; version = $current.Groups[2].Value }
}

function Install-Candidate([string] $Serial) {
    $installProduction = Invoke-Adb $Serial @('install', '-r', $productionApkPath)
    if ($installProduction -notmatch 'Success') { throw "Production APK installation failed on ${Serial}: $installProduction" }
    $installTest = Invoke-Adb $Serial @('install', '-r', '-t', $capturedTestApk)
    if ($installTest -notmatch 'Success') { throw "Test APK installation failed on ${Serial}: $installTest" }
}

function Assert-DeviceIdentity([string] $Serial, [string] $Kind, [int] $ApiLevel = 0, [string] $AvdName = '') {
    $actualApi = [int](Get-DeviceProperty $Serial 'ro.build.version.sdk')
    $isEmulator = $Serial -match '^emulator-' -or
        (Get-DeviceProperty $Serial 'ro.kernel.qemu') -eq '1' -or
        (Get-DeviceProperty $Serial 'ro.boot.qemu') -eq '1'
    if ($ApiLevel -gt 0 -and $actualApi -ne $ApiLevel) { throw "Device $Serial API $actualApi differs from requested API $ApiLevel." }
    if (($Kind -eq 'AVD') -ne $isEmulator) { throw "Device $Serial does not match required device kind $Kind." }
    if ($Kind -eq 'AVD') {
        $actualName = ((Invoke-Adb $Serial @('emu', 'avd', 'name')) -split "\r?\n")[0].Trim()
        if ($actualName -cne $AvdName) { throw "Device $Serial is not the expected AVD $AvdName." }
    }
}

function Get-InstalledApkSha256([string] $Serial, [string] $Package) {
    $paths = @((Invoke-Adb $Serial @('shell', 'pm', 'path', $Package)) -split "\r?\n" | Where-Object { $_ -match '^package:' })
    if ($paths.Count -ne 1) { throw "Expected one installed base APK for $Package on $Serial." }
    $path = $paths[0].Substring('package:'.Length).Trim()
    if ($path -notmatch '^/data/app/[A-Za-z0-9_+/=.-]+/base\.apk$') { throw 'Unexpected installed APK path.' }
    $digest = Invoke-Adb $Serial @('shell', 'sha256sum', $path)
    if ($digest -notmatch '^([0-9a-f]{64})\s+') { throw "Could not hash installed APK for $Package on $Serial." }
    return $Matches[1]
}

function Add-Environment(
    [string] $Id,
    [string] $Serial,
    [string] $Kind,
    [int] $ApiLevel,
    [int] $RamMiB,
    [string] $SystemImage,
    [string] $AvdName
) {
    Assert-DeviceIdentity $Serial $Kind $ApiLevel $AvdName
    $actualProductionSha = Get-InstalledApkSha256 $Serial ([string]$metadata.package)
    $actualTestSha = Get-InstalledApkSha256 $Serial ([string]$testPackageMatch.Groups[1].Value)
    if ($actualProductionSha -cne $productionApkSha -or $actualTestSha -cne $testApkSha) {
        throw "Installed APK bytes on $Serial do not match the frozen host artifacts."
    }
    $devicePackage = Invoke-Adb $Serial @('shell', 'dumpsys', 'package', [string]$metadata.package)
    $deviceVersionName = [regex]::Match($devicePackage, '(?m)^\s*versionName=([^\r\n]+)').Groups[1].Value.Trim()
    $deviceVersionCode = [regex]::Match($devicePackage, '(?m)^\s*versionCode=(\d+)').Groups[1].Value
    if ($deviceVersionName -cne [string]$metadata.versionName -or $deviceVersionCode -ne [string]$metadata.versionCode) {
        throw "Installed package version on $Serial differs from the frozen candidate."
    }
    $deviceArtifacts = [ordered]@{}
    foreach ($key in $installedArtifacts.Keys) { $deviceArtifacts[$key] = $installedArtifacts[$key] }
    $deviceArtifacts.productionApkSha256 = $actualProductionSha
    $deviceArtifacts.testApkSha256 = $actualTestSha
    $memInfo = Invoke-Adb $Serial @('shell', 'cat', '/proc/meminfo')
    $actualRamMiB = [int]([int64]([regex]::Match($memInfo, '(?m)^MemTotal:\s+(\d+)\s+kB').Groups[1].Value) / 1024)
    if ($Kind -eq 'AVD' -and ($actualRamMiB -lt ($RamMiB * 0.70) -or $actualRamMiB -gt ($RamMiB * 1.05))) {
        throw "Device $Serial measured RAM is inconsistent with its requested AVD memory configuration."
    }
    $environment = [ordered]@{
        id = $Id
        kind = $Kind
        apiLevel = [int](Get-DeviceProperty $Serial 'ro.build.version.sdk')
        isEmulator = ($Kind -eq 'AVD')
        androidVersion = Get-DeviceProperty $Serial 'ro.build.version.release'
        buildFingerprint = Get-DeviceProperty $Serial 'ro.build.fingerprint'
        systemImage = $SystemImage
        deviceIdSha256 = Get-TextSha256 $Serial
        ramMiB = $RamMiB
        actualRamMiB = $actualRamMiB
        webView = Get-WebView $Serial
        installedArtifacts = $deviceArtifacts
    }
    if ($Kind -eq 'AVD') {
        $environment.avdName = $AvdName
    } else {
        $environment.manufacturer = Get-DeviceProperty $Serial 'ro.product.manufacturer'
        $environment.model = Get-DeviceProperty $Serial 'ro.product.model'
        $environment.securityPatch = Get-DeviceProperty $Serial 'ro.build.version.security_patch'
        $environment.authorizationReference = $AuthorizationReference
    }
    $environments.Add([pscustomobject]$environment)
}

function Invoke-Instrumentation(
    [string] $Serial,
    [string] $LogStem,
    [string] $Selector,
    [int] $TalkBackConsolePort = 0,
    [string] $TalkBackConsoleToken = ''
) {
    if ($TalkBackConsolePort -gt 0 -and ($Serial -cne "emulator-$TalkBackConsolePort" -or $TalkBackConsoleToken -notmatch '^[A-Za-z0-9_-]+$')) {
        throw 'TalkBack console credentials do not match the authorized emulator.'
    }
    $instrumentationRelative = "logs/$LogStem-instrumentation.log"
    $logcatRelative = "logs/$LogStem-logcat.txt"
    $instrumentationPath = Join-Path $outputRootPath ($instrumentationRelative -replace '/', '\')
    $logcatPath = Join-Path $outputRootPath ($logcatRelative -replace '/', '\')
    $logcatErrorPath = "$logcatPath.stderr"
    $startedAt = [datetimeoffset]::UtcNow
    $logcatProcess = Start-Process -FilePath $adb `
        -ArgumentList @('-s', $Serial, 'logcat', '-v', 'threadtime', '-T', '1', 'LCG_QUALITY:I', 'LCG_ATF:I', 'LCG_RELEASE:I', '*:S') `
        -RedirectStandardOutput $logcatPath `
        -RedirectStandardError $logcatErrorPath `
        -PassThru `
        -WindowStyle Hidden
    $arguments = @('shell', 'am', 'instrument', '-w', '-r')
    if (-not [string]::IsNullOrWhiteSpace($Selector)) { $arguments += @('-e', 'class', $Selector) }
    if ($TalkBackConsolePort -gt 0) {
        if ($env:GITHUB_ACTIONS -eq 'true') { Write-Host "::add-mask::$TalkBackConsoleToken" }
        $arguments += @('-e', 'lcgTalkBackConsolePort', [string]$TalkBackConsolePort, '-e', 'lcgTalkBackConsoleToken', $TalkBackConsoleToken)
    }
    $arguments += 'com.qrzzzz.lyricscard.test/com.qrzzzz.lyricscard.ui.ReleaseEvidenceTestRunner'
    try {
        Start-Sleep -Milliseconds 750
        if ($logcatProcess.HasExited) {
            $logcatError = if (Test-Path -LiteralPath $logcatErrorPath) { Get-Content -LiteralPath $logcatErrorPath -Raw } else { '' }
            throw "Streaming logcat exited before instrumentation on ${Serial}: $logcatError"
        }
        $null = Invoke-Adb $Serial @('shell', 'log', '-t', 'LCG_RELEASE', "gate-start-$LogStem")
        $instrumentationOutput = (& $adb -s $Serial @arguments 2>&1 | Out-String)
        $instrumentationExit = $LASTEXITCODE
    } finally {
        if (-not $logcatProcess.HasExited) {
            Stop-Process -Id $logcatProcess.Id
            $null = $logcatProcess.WaitForExit(5000)
        }
        if (Test-Path -LiteralPath $logcatErrorPath) {
            $logcatError = Get-Content -LiteralPath $logcatErrorPath -Raw
            if (-not [string]::IsNullOrWhiteSpace($logcatError)) {
                [IO.File]::AppendAllText($logcatPath, "`n[adb logcat stderr]`n$logcatError", [Text.UTF8Encoding]::new($false))
            }
            Remove-Item -LiteralPath $logcatErrorPath -Force
        }
    }
    [IO.File]::WriteAllText($instrumentationPath, $instrumentationOutput, [Text.UTF8Encoding]::new($false))
    if (-not (Test-Path -LiteralPath $logcatPath -PathType Leaf) -or (Get-Item -LiteralPath $logcatPath).Length -eq 0) {
        throw "Streaming logcat produced no evidence for ${Serial}."
    }
    $completedAt = [datetimeoffset]::UtcNow
    if ($instrumentationExit -ne 0 -or $instrumentationOutput -notmatch '(?m)^OK \([1-9][0-9]* tests?\)' -or $instrumentationOutput -match '(?m)^FAILURES!!!') {
        throw "Instrumentation failed on $Serial selector='$Selector'. See $instrumentationPath"
    }
    return [pscustomobject]@{
        selector = if ([string]::IsNullOrWhiteSpace($Selector)) { 'connectedProductionReleaseAndroidTest' } else { $Selector }
        startedAt = $startedAt.ToString('yyyy-MM-ddTHH:mm:ssZ')
        completedAt = $completedAt.ToString('yyyy-MM-ddTHH:mm:ssZ')
        logs = @(
            [ordered]@{ kind = 'instrumentation'; path = $instrumentationRelative; sha256 = Get-LowerSha256 $instrumentationPath },
            [ordered]@{ kind = 'logcat'; path = $logcatRelative; sha256 = Get-LowerSha256 $logcatPath }
        )
    }
}

function Add-Gate([string] $Id, [string] $EnvironmentId, [object] $Run, [string] $SelectorOverride = '') {
    $gates.Add([pscustomobject][ordered]@{
        id = $Id
        environmentId = $EnvironmentId
        status = 'PASS'
        attempts = 1
        testSelector = if ($SelectorOverride) { $SelectorOverride } else { $Run.selector }
        startedAt = $Run.startedAt
        completedAt = $Run.completedAt
        logs = $Run.logs
    })
}

function New-Avd([string] $Name, [string] $Package, [int] $RamMiB) {
    if (Test-Path -LiteralPath (Join-Path $avdRootPath "$Name.ini")) { throw "Capture AVD already exists; use a fresh run-specific AvdRoot: $Name" }
    "no" | & $avdManager.FullName create avd --name $Name --package $Package --device 'pixel_2' 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not create AVD $Name from $Package." }
    $configPath = Join-Path $avdRootPath "$Name.avd\config.ini"
    [IO.File]::AppendAllText($configPath, "`nhw.ramSize=$RamMiB`ndisk.dataPartition.size=6G`nhw.gpu.enabled=yes`nhw.gpu.mode=swiftshader`n")
}

function Start-Avd([string] $Name, [int] $Port, [int] $RamMiB) {
    $serial = "emulator-$Port"
    $existingDevices = (& $adb devices 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $existingDevices -match "(?m)^$([regex]::Escape($serial))\s") { throw "Capture emulator port is already in use: $Port" }
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object { $_.Port -eq $Port -or $_.Port -eq ($Port + 1) }).Count -gt 0) { throw "Capture console/ADB port is already in use: $Port" }
    $process = Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $Name, '-port', [string]$Port, '-no-window', '-no-audio', '-no-boot-anim',
        '-no-snapshot', '-wipe-data', '-gpu', 'swiftshader', '-memory', [string]$RamMiB
    ) -RedirectStandardOutput (Join-Path $outputRootPath "logs/$Name-emulator.log") -RedirectStandardError (Join-Path $outputRootPath "logs/$Name-emulator.stderr.log") -PassThru -WindowStyle Hidden
    $handle = [pscustomobject]@{ serial = $serial; process = $process; name = $Name }
    try {
        $deadline = [datetimeoffset]::UtcNow.AddMinutes(5)
        do {
            if ($process.HasExited) { throw "AVD $Name exited before boot completed." }
            Start-Sleep -Seconds 2
            $booted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        } while ($booted -ne '1' -and [datetimeoffset]::UtcNow -lt $deadline)
        if ($booted -ne '1') { throw "AVD $Name did not boot within five minutes." }
        $null = Invoke-Adb $serial @('shell', 'settings', 'put', 'global', 'window_animation_scale', '0')
        $null = Invoke-Adb $serial @('shell', 'settings', 'put', 'global', 'transition_animation_scale', '0')
        $null = Invoke-Adb $serial @('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
        return $handle
    } catch {
        Stop-Avd $handle
        throw
    }
}

function Stop-Avd([object] $Handle) {
    if ($null -eq $Handle) { return }
    if ($Handle.process.HasExited) { return }
    $name = ((& $adb -s $Handle.serial emu avd name 2>$null | Out-String) -split "\r?\n")[0].Trim()
    if ($name -ceq $Handle.name) { & $adb -s $Handle.serial emu kill 2>$null | Out-Null }
    if (-not $Handle.process.WaitForExit(15000)) { Stop-Process -Id $Handle.process.Id -Force }
}

$avdMatrix = @(
    [pscustomobject]@{ Id = 'api26'; Api = 26; Ram = 3072; Name = 'lcg-final-api26'; Package = 'system-images;android-26;google_apis;x86_64'; Port = 5554 },
    [pscustomobject]@{ Id = 'api30'; Api = 30; Ram = 4096; Name = 'lcg-final-api30'; Package = 'system-images;android-30;google_apis_playstore;x86_64'; Port = 5556 },
    [pscustomobject]@{ Id = 'api33'; Api = 33; Ram = 4096; Name = 'lcg-final-api33'; Package = 'system-images;android-33;google_apis_playstore;x86_64'; Port = 5558 },
    [pscustomobject]@{ Id = 'api36'; Api = 36; Ram = 4096; Name = 'lcg-final-api36'; Package = 'system-images;android-36;google_apis_playstore;x86_64'; Port = 5560 }
)

Assert-DeviceIdentity $PhysicalDeviceSerial 'PHYSICAL'
foreach ($entry in $avdMatrix) {
    $handle = $null
    try {
        New-Avd $entry.Name $entry.Package $entry.Ram
        $handle = Start-Avd $entry.Name $entry.Port $entry.Ram
        Assert-DeviceIdentity $handle.serial 'AVD' $entry.Api $entry.Name
        Install-Candidate $handle.serial
        Add-Environment $entry.Id $handle.serial 'AVD' $entry.Api $entry.Ram $entry.Package $entry.Name
        switch ($entry.Api) {
            26 {
                $core = Invoke-Instrumentation $handle.serial 'api26-core' 'com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest'
                Add-Gate 'api26-core' $entry.Id $core
                $renderer = Invoke-Instrumentation $handle.serial 'api26-renderer-webview' 'com.qrzzzz.lyricscard.renderer.RendererUiLifecycleTest'
                Add-Gate 'api26-renderer-webview' $entry.Id $renderer
                $recovery = Invoke-Instrumentation $handle.serial 'api26-recovery-atf' 'com.qrzzzz.lyricscard.ui.ArchitectureRestorationTest,com.qrzzzz.lyricscard.ui.AccessibilityFrameworkTest'
                Add-Gate 'api26-recovery-atf' $entry.Id $recovery
            }
            30 {
                $serif = Invoke-Instrumentation $handle.serial 'api30-serif' 'com.qrzzzz.lyricscard.quality.QualityStressTest#a_serifOneXThenTwoXProbeUsesTheSameRendererLifecycle'
                Add-Gate 'api30-serif-measure-spec-1x-2x' $entry.Id $serif
                $exports = Invoke-Instrumentation $handle.serial 'api30-export-20x' 'com.qrzzzz.lyricscard.quality.QualityStressTest#a_twentyConsecutiveTwoXExportsReturnToTheWarmedMemoryEnvelope'
                Add-Gate 'api30-export-20x' $entry.Id $exports
                Add-Gate 'api30-memory' $entry.Id $exports 'QualityStressTest#a_twentyConsecutiveTwoXExportsReturnToTheWarmedMemoryEnvelope memory envelope'
                $cleanup = Invoke-Instrumentation $handle.serial 'api30-cancel-retry-cleanup' 'com.qrzzzz.lyricscard.quality.QualityStressTest#a_cancelledExportRemovesPartialAndRetryProducesValidPng'
                Add-Gate 'api30-cancel-retry-temp-cleanup' $entry.Id $cleanup
                $fourGb = Invoke-Instrumentation $handle.serial 'api30-four-gb' 'com.qrzzzz.lyricscard.quality.QualityStressTest#c_largeCoverImportDownsamplesPreviewsAndExportsOnFourGbDevice'
                Add-Gate 'four-gb-2x-memory' $entry.Id $fourGb
                $core = Invoke-Instrumentation $handle.serial 'api30-core' 'com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest'
                Add-Gate 'api30-core' $entry.Id $core
                $recovery = Invoke-Instrumentation $handle.serial 'api30-recovery-atf' 'com.qrzzzz.lyricscard.ui.ArchitectureRestorationTest,com.qrzzzz.lyricscard.ui.AccessibilityFrameworkTest'
                Add-Gate 'api30-recovery-atf' $entry.Id $recovery
            }
            33 {
                $core = Invoke-Instrumentation $handle.serial 'api33-core' 'com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest'
                Add-Gate 'api33-core' $entry.Id $core
                $accessibility = Invoke-Instrumentation $handle.serial 'api33-accessibility' 'com.qrzzzz.lyricscard.ui.ArchitectureRestorationTest,com.qrzzzz.lyricscard.ui.AccessibilityFrameworkTest'
                Add-Gate 'api33-recovery-atf' $entry.Id $accessibility
                $font = Invoke-Instrumentation $handle.serial 'api33-font-scale-200' 'com.qrzzzz.lyricscard.ui.HomeSettingsProductionTest#compactWideLandscapeAndTwoHundredPercentFontKeepKeyTargetsReachable'
                Add-Gate 'api33-font-scale-200' $entry.Id $font
                $previousServices = Invoke-Adb $handle.serial @('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
                $previousAccessibility = Invoke-Adb $handle.serial @('shell', 'settings', 'get', 'secure', 'accessibility_enabled')
                try {
                    $null = Invoke-Adb $handle.serial @('shell', 'pm', 'grant', 'com.google.android.marvin.talkback', 'android.permission.POST_NOTIFICATIONS')
                    $null = Invoke-Adb $handle.serial @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService')
                    $null = Invoke-Adb $handle.serial @('shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1')
                    Start-Sleep -Seconds 5
                    $consoleTokenPath = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.emulator_console_auth_token'
                    $consoleToken = [IO.File]::ReadAllText($consoleTokenPath).Trim()
                    $talkback = Invoke-Instrumentation $handle.serial 'api33-talkback' 'com.qrzzzz.lyricscard.ui.TalkBackReleaseTest' $entry.Port $consoleToken
                    Add-Gate 'api33-talkback' $entry.Id $talkback
                } finally {
                    $null = Invoke-Adb $handle.serial @('shell', 'settings', 'put', 'secure', 'enabled_accessibility_services', $previousServices)
                    $null = Invoke-Adb $handle.serial @('shell', 'settings', 'put', 'secure', 'accessibility_enabled', $previousAccessibility)
                }
            }
            36 {
                $connected = Invoke-Instrumentation $handle.serial 'api36-connected-production' ''
                Add-Gate 'api36-connected-production' $entry.Id $connected
                Add-Gate 'api36-export-20x' $entry.Id $connected 'QualityStressTest#a_twentyConsecutiveTwoXExportsReturnToTheWarmedMemoryEnvelope'
                Add-Gate 'endurance-30m' $entry.Id $connected 'QualityStressTest#b_thirtyMinuteEditingEndurancePreservesAutosaveRendererAndRestorationState'
            }
        }
    } finally {
        Stop-Avd $handle
    }
}

$physicalApi = [int](Get-DeviceProperty $PhysicalDeviceSerial 'ro.build.version.sdk')
Assert-DeviceIdentity $PhysicalDeviceSerial 'PHYSICAL' $physicalApi
Install-Candidate $PhysicalDeviceSerial
$physicalMemKb = [int64]([regex]::Match((Invoke-Adb $PhysicalDeviceSerial @('shell', 'cat', '/proc/meminfo')), '(?m)^MemTotal:\s+(\d+)\s+kB').Groups[1].Value)
Add-Environment 'physical' $PhysicalDeviceSerial 'PHYSICAL' $physicalApi ([int]($physicalMemKb / 1024)) 'physical-device' ''
$physicalRun = Invoke-Instrumentation $PhysicalDeviceSerial 'physical-core-save-share' 'com.qrzzzz.lyricscard.ui.AvdMatrixSmokeTest#productionMainActivitySavesAndSharesExportedBytes'
Add-Gate 'physical-core-save-share' 'physical' $physicalRun

$productionApkInfo = Get-Item -LiteralPath $productionApkPath
$productionAabInfo = Get-Item -LiteralPath $productionAabPath
$testApkInfo = Get-Item -LiteralPath $capturedTestApk
$evidence = [ordered]@{
    schemaVersion = 1
    evidenceType = 'lyrics-card-final-device-gate'
    testFixture = $false
    status = 'READY'
    candidate = [ordered]@{
        repository = $repository
        sourceCommit = $SourceCommit
        candidateRunId = $CandidateRunId
        candidateRunAttempt = [int]$metadata.source.runAttempt
        candidateArtifactName = $CandidateArtifactName
        qualityGateRunId = [long]$metadata.source.qualityGateRunId
        releaseMetadataSha256 = Get-LowerSha256 $metadataPath
        package = [string]$metadata.package
        versionName = [string]$metadata.versionName
        versionCode = [int]$metadata.versionCode
        productionApk = [ordered]@{ name = $productionApkName; bytes = $productionApkInfo.Length; sha256 = $productionApkSha; certificateSha256 = $productionCertificate }
        productionAab = [ordered]@{ name = $productionAabName; bytes = $productionAabInfo.Length; sha256 = $productionAabSha; certificateSha256 = $productionCertificate }
        testApk = [ordered]@{
            name = $testApkName
            bytes = $testApkInfo.Length
            sha256 = $testApkSha
            certificateSha256 = $testCertificate
            package = $testPackageMatch.Groups[1].Value
            targetPackage = $testInstrumentationMatch.Groups[2].Value
            versionName = $testPackageMatch.Groups[3].Value
            versionCode = $testVersionCode
        }
    }
    run = [ordered]@{
        workflowRunId = $EvidenceRunId
        workflowRunAttempt = $EvidenceRunAttempt
        workflowPath = $workflowPath
        workflowEvent = 'workflow_dispatch'
        artifactName = $EvidenceArtifactName
        startedAt = $scriptStartedAt.ToString('yyyy-MM-ddTHH:mm:ssZ')
        completedAt = [datetimeoffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        runner = $runnerName
        authorizationReference = $AuthorizationReference
        stopOnFirstFailure = $true
    }
    environments = @($environments)
    gates = @($gates)
}
$evidence | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $outputRootPath 'device-gate-evidence.json') -Encoding utf8
Write-Output "Device evidence captured: environments=$($environments.Count) gates=$($gates.Count) source=$SourceCommit"
