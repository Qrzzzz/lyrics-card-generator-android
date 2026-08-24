[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $EvidencePath,
    [Parameter(Mandatory = $true)][string] $ReleaseMetadataPath,
    [Parameter(Mandatory = $true)][string] $ProductionApkPath,
    [Parameter(Mandatory = $true)][string] $ProductionAabPath,
    [Parameter(Mandatory = $true)][string] $TestApkPath,
    [Parameter(Mandatory = $true)][string] $ExpectedRepository,
    [Parameter(Mandatory = $true)][string] $ExpectedSourceCommit,
    [Parameter(Mandatory = $true)][long] $ExpectedEvidenceRunId,
    [Parameter(Mandatory = $true)][int] $ExpectedEvidenceRunAttempt,
    [Parameter(Mandatory = $true)][string] $ExpectedEvidenceWorkflowPath,
    [Parameter(Mandatory = $true)][string] $ExpectedEvidenceWorkflowEvent,
    [Parameter(Mandatory = $true)][string] $ExpectedEvidenceArtifactName,
    [Parameter(Mandatory = $true)][string] $Aapt2Path,
    [Parameter(Mandatory = $true)][string] $ApkSignerPath,
    [switch] $AllowTestFixture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $EvidencePath -PathType Leaf)) {
    throw "Final device-gate evidence is missing: $EvidencePath"
}
$schemaPath = Join-Path $PSScriptRoot '..\config\device-gate-evidence.schema.json'
if (-not (Test-Path -LiteralPath $schemaPath -PathType Leaf)) { throw 'Device-gate JSON schema is missing.' }
$rawEvidence = Get-Content -LiteralPath $EvidencePath -Raw
$testJsonCommand = Get-Command Test-Json -ErrorAction SilentlyContinue
if ($testJsonCommand -and $testJsonCommand.Parameters.ContainsKey('SchemaFile')) {
    if (-not ($rawEvidence | Test-Json -SchemaFile $schemaPath -ErrorAction Stop)) {
        throw 'Device-gate evidence does not satisfy its JSON schema.'
    }
}
$evidence = $rawEvidence | ConvertFrom-Json
Import-Module (Join-Path $PSScriptRoot 'DeviceGateEvidence.psm1') -Force

$assertEvidence = @{
    Evidence = $evidence
    EvidenceRoot = Split-Path -Parent ([IO.Path]::GetFullPath($EvidencePath))
    ExpectedRepository = $ExpectedRepository
    ExpectedSourceCommit = $ExpectedSourceCommit
    ExpectedEvidenceRunId = $ExpectedEvidenceRunId
    ExpectedEvidenceRunAttempt = $ExpectedEvidenceRunAttempt
    ExpectedEvidenceWorkflowPath = $ExpectedEvidenceWorkflowPath
    ExpectedEvidenceWorkflowEvent = $ExpectedEvidenceWorkflowEvent
    ExpectedEvidenceArtifactName = $ExpectedEvidenceArtifactName
}
if ($AllowTestFixture) { $assertEvidence.AllowTestFixture = $true }
$null = Assert-DeviceGateEvidence @assertEvidence
$null = Assert-DeviceGateArtifactBinding `
    -Evidence $evidence `
    -ReleaseMetadataPath $ReleaseMetadataPath `
    -ProductionApkPath $ProductionApkPath `
    -ProductionAabPath $ProductionAabPath `
    -TestApkPath $TestApkPath

foreach ($tool in @($Aapt2Path, $ApkSignerPath)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Required Android inspection tool is missing: $tool" }
}
$badging = (& $Aapt2Path dump badging $TestApkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt2 could not inspect the test APK.' }
$manifestXmlTree = (& $Aapt2Path dump xmltree $TestApkPath --file AndroidManifest.xml 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'aapt2 could not inspect the test APK manifest.' }
$certificateOutput = (& $ApkSignerPath verify --print-certs $TestApkPath 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'apksigner could not verify the test APK.' }
Assert-TestApkInspection -Evidence $evidence -Badging $badging -ManifestXmlTree $manifestXmlTree -CertificateOutput $certificateOutput

Write-Output "Final device gate PASS: source=$ExpectedSourceCommit version=$($evidence.candidate.versionName) environments=$(@($evidence.environments).Count) gates=$(@($evidence.gates).Count)"
exit 0
