Set-StrictMode -Version Latest

function Assert-FullCommitSha {
    param(
        [Parameter(Mandatory = $true)][string] $Value,
        [Parameter(Mandatory = $true)][string] $Name
    )

    if ($Value -notmatch '^[0-9a-f]{40}$') {
        throw "$Name must be a full lowercase 40-character commit SHA."
    }
}

function Assert-ProductionCandidatePolicy {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string] $CandidateCommit,
        [Parameter(Mandatory = $true)][string] $ExpectedVersion,
        [Parameter(Mandatory = $true)][string] $RepositoryVersion,
        [Parameter(Mandatory = $true)][string] $Repository,
        [Parameter(Mandatory = $true)][string] $RemoteMainCommit,
        [Parameter(Mandatory = $true)][string] $WorkflowEvent,
        [Parameter(Mandatory = $true)][string] $WorkflowRef,
        [Parameter(Mandatory = $true)][string] $WorkflowSha,
        [Parameter(Mandatory = $true)][string] $TriggerSha,
        [Parameter(Mandatory = $true)][bool] $TagExists,
        [Parameter(Mandatory = $true)][bool] $ReleaseExists,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]] $QualityGateRuns
    )

    $candidate = $CandidateCommit.ToLowerInvariant()
    $remoteMain = $RemoteMainCommit.ToLowerInvariant()
    $workflowCommit = $WorkflowSha.ToLowerInvariant()
    $triggerCommit = $TriggerSha.ToLowerInvariant()

    Assert-FullCommitSha -Value $candidate -Name 'Candidate commit'
    Assert-FullCommitSha -Value $remoteMain -Name 'origin/main commit'
    Assert-FullCommitSha -Value $workflowCommit -Name 'Workflow commit'
    Assert-FullCommitSha -Value $triggerCommit -Name 'Trigger commit'

    if ($ExpectedVersion -notmatch '^\d+\.\d+\.\d+$') {
        throw 'Version must be a production x.y.z version.'
    }
    if ($RepositoryVersion -ne $ExpectedVersion) {
        throw "Requested version '$ExpectedVersion' does not match Gradle version '$RepositoryVersion'."
    }
    if ($WorkflowEvent -ne 'workflow_dispatch') {
        throw "Production candidates require workflow_dispatch, not '$WorkflowEvent'."
    }
    if ($WorkflowRef -ne 'refs/heads/main') {
        throw "Production candidates must be dispatched from refs/heads/main, not '$WorkflowRef'."
    }
    if ($candidate -ne $remoteMain) {
        throw "Candidate '$candidate' is not the current origin/main tip '$remoteMain'."
    }
    if ($candidate -ne $workflowCommit -or $candidate -ne $triggerCommit) {
        throw 'Candidate, workflow definition, and workflow_dispatch trigger must resolve to the same main commit.'
    }
    if ($TagExists) {
        throw "Tag 'v$ExpectedVersion' already exists."
    }
    if ($ReleaseExists) {
        throw "GitHub Release 'v$ExpectedVersion' already exists."
    }

    $matchingRuns = @($QualityGateRuns | Where-Object {
        $_.name -eq 'Android Quality Gate' -and
        $_.head_sha -eq $candidate -and
        $_.event -eq 'push' -and
        $_.head_branch -eq 'main' -and
        $_.status -eq 'completed' -and
        $_.conclusion -eq 'success' -and
        $_.head_repository.full_name -eq $Repository
    })
    if ($matchingRuns.Count -eq 0) {
        throw "No successful Android Quality Gate push run on main exists for exact candidate '$candidate'."
    }

    return $matchingRuns |
        Sort-Object -Property @{ Expression = { [datetime]$_.run_started_at }; Descending = $true }, @{ Expression = { [long]$_.id }; Descending = $true } |
        Select-Object -First 1
}

Export-ModuleMember -Function Assert-ProductionCandidatePolicy
