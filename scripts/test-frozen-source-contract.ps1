[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'ProductionReleasePolicy.psm1') -Force

# Exercise the real API comparison consumer, including a lying status paired
# with the wrong merge base. All responses are local fixtures.
& (Get-Module ProductionReleasePolicy) {
    $source = '1' * 40
    $later = '2' * 40
    foreach ($case in @(
        @{ Status = 'identical'; MergeBase = $source; Accept = $true },
        @{ Status = 'ahead'; MergeBase = $source; Accept = $true },
        @{ Status = 'behind'; MergeBase = $source; Accept = $false },
        @{ Status = 'diverged'; MergeBase = $source; Accept = $false },
        @{ Status = 'ahead'; MergeBase = $later; Accept = $false }
    )) {
        function Invoke-RestMethod {
            param($Headers, $Method, $Uri)
            if ($Method -ne 'Get' -or $Uri -ne "https://api.github.com/repos/owner/repo/compare/$source...$later") {
                throw 'Unexpected ancestry request.'
            }
            return @{ status = $case.Status; merge_base_commit = @{ sha = $case.MergeBase } }
        }
        $accepted = $false
        try {
            Assert-CommitAncestor -AncestorCommit $source -DescendantCommit $later -Repository 'owner/repo' -GitHubToken 'fixture'
            $accepted = $true
        } catch {
            if ($_.Exception.Message -notmatch 'not in the approved main history') { throw }
        }
        if ($accepted -ne $case.Accept) { throw "Wrong ancestry outcome for $($case.Status)." }
    }
}

function Get-WorkflowScript([string] $Path, [string] $Name) {
    $workflow = [IO.File]::ReadAllText((Join-Path $repositoryRoot $Path))
    $step = [regex]::Match($workflow, '(?ms)^      - name: ' + [regex]::Escape($Name) + '\r?\n(?<step>.*?)(?=^      - name: |\z)').Groups['step'].Value
    $code = [regex]::Match($step, '(?ms)^        run: \|\r?\n(?<code>.*)\z').Groups['code'].Value
    if (-not $code) { throw "Workflow step not found: $Name" }
    return [scriptblock]::Create(($code -replace '(?m)^          ', ''))
}

$capture = Get-WorkflowScript '.github/workflows/capture-device-gate-evidence.yml' 'Validate immutable capture inputs'
$final = Get-WorkflowScript '.github/workflows/final-device-gate.yml' 'Validate immutable gate inputs'
$binding = Get-WorkflowScript '.github/workflows/final-device-gate.yml' 'Bind candidate and evidence runs to allowed workflow identities'
$values = @{
    SOURCE_COMMIT = '1' * 40; WORKFLOW_SHA = '3' * 40; TRIGGER_SHA = '3' * 40
    WORKFLOW_REF = 'refs/heads/main'; WORKFLOW_EVENT = 'workflow_dispatch'
    CANDIDATE_RUN_ID = '1001'; CANDIDATE_ARTIFACT_NAME = 'production-candidate-2.0.0-111111111111'
    EVIDENCE_RUN_ID = '2002'; EVIDENCE_ARTIFACT_NAME = 'final-device-evidence-fixture'
    REPOSITORY = 'owner/repo'; GH_TOKEN = 'fixture'; API_URL = 'https://api.contract.invalid'
    VALIDATOR_COMMIT = '3' * 40
}
$saved = @{}
foreach ($name in $values.Keys) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    foreach ($name in $values.Keys) { [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process') }
    # Capture input reader/output are mocked without touching a device or a file.
    function Get-Content { return '{"inputs":{"physical_device_serial":"fixture-device"}}' }
    $savedEvent = $env:GITHUB_EVENT_PATH
    $savedOutput = $env:GITHUB_OUTPUT
    $output = [IO.Path]::GetTempFileName()
    $env:GITHUB_EVENT_PATH = 'unused-fixture-event'
    $env:GITHUB_OUTPUT = $output
    foreach ($gate in @($capture, $final)) {
        $null = & $gate
        $env:TRIGGER_SHA = '4' * 40
        $rejected = $false
        try { $null = & $gate } catch { $rejected = $true }
        if (-not $rejected) { throw 'Mismatched validator/trigger SHA was accepted.' }
        $env:TRIGGER_SHA = '3' * 40
        $env:WORKFLOW_REF = 'refs/heads/unmerged'
        $rejected = $false
        try { $null = & $gate } catch { $rejected = $true }
        if (-not $rejected) { throw 'Unmerged validator was accepted.' }
        $env:WORKFLOW_REF = 'refs/heads/main'
    }
    # Execute Final's wiring with source S, capture E and validator F distinct.
    $ancestryCalls = [Collections.Generic.List[string]]::new()
    function Import-Module { }
    function Assert-CommitAncestor {
        param($AncestorCommit, $DescendantCommit, $Repository, $GitHubToken, $ApiBaseUrl)
        $ancestryCalls.Add("$AncestorCommit/$DescendantCommit")
    }
    function Invoke-RestMethod {
        param($Headers, $Uri)
        $candidate = $Uri.EndsWith('/1001')
        return @{ id = $(if ($candidate) { 1001 } else { 2002 }); run_attempt = 1
            head_sha = $(if ($candidate) { '1' * 40 } else { '2' * 40 }) }
    }
    function Assert-DeviceGateWorkflowRun {
        param($Run, $ExpectedRepository, $ExpectedSourceCommit, $ExpectedPath, $ExpectedEvent)
        if ($Run.head_sha -ne $ExpectedSourceCommit) { throw 'Run was bound to the wrong SHA.' }
    }
    $null = & $binding
    $expected = @("$('1' * 40)/$('2' * 40)", "$('2' * 40)/$('3' * 40)")
    if ($ancestryCalls.Count -ne 2 -or (Compare-Object $expected $ancestryCalls)) {
        throw 'Final must bind source -> evidence workflow -> trusted final validator ancestry.'
    }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    if (Get-Variable savedEvent -ErrorAction SilentlyContinue) { $env:GITHUB_EVENT_PATH = $savedEvent }
    if (Get-Variable savedOutput -ErrorAction SilentlyContinue) { $env:GITHUB_OUTPUT = $savedOutput }
    if (Get-Variable output -ErrorAction SilentlyContinue) { Remove-Item -LiteralPath $output -Force }
}
Write-Output 'Frozen-source contract PASS: ancestry rejection, later trusted validators, input rejection and Final SHA-chain wiring.'
