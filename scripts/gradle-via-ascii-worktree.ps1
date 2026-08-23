[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string[]] $GradleArguments = @(
        ':app:test',
        '--no-parallel',
        '--console=plain'
    ),

    [string] $StagingRoot = (Join-Path ([IO.Path]::GetTempPath()) 'lyrics-card-gradle-staging'),

    [string] $JavaHome = $env:JAVA_HOME,

    [string] $AndroidSdkRoot = '',

    [switch] $AllowDirtySource
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-AsciiPath {
    param([Parameter(Mandatory = $true)][string] $Path)

    return -not [regex]::IsMatch($Path, '[^\x00-\x7f]')
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string] $WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )

    & git -C $WorkingDirectory @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

if ($GradleArguments.Count -eq 0) {
    throw 'At least one Gradle task or argument is required.'
}

$sourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reportedRoot = (& git -C $sourceRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "The script directory is not inside a Git worktree: $sourceRoot"
}
$reportedRoot = [IO.Path]::GetFullPath($reportedRoot)
if (-not $reportedRoot.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Expected repository root '$sourceRoot', but Git reported '$reportedRoot'."
}

$commit = (& git -C $sourceRoot rev-parse --verify 'HEAD^{commit}').Trim()
if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'HEAD could not be resolved to a full commit SHA.'
}
$commit = $commit.ToLowerInvariant()

$sourceChanges = @(& git -C $sourceRoot status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect the source worktree status.'
}
if ($sourceChanges.Count -ne 0) {
    $preview = ($sourceChanges | Select-Object -First 20) -join [Environment]::NewLine
    $message = @"
The source worktree has uncommitted changes. This wrapper deliberately tests immutable HEAD
$commit, so those changes would not be included:
$preview
"@
    if (-not $AllowDirtySource) {
        throw $message
    }
    Write-Warning $message
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'JAVA_HOME is not set. Pass -JavaHome or set JAVA_HOME to a JDK installation.'
}
$JavaHome = [IO.Path]::GetFullPath($JavaHome)
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)) {
    throw "java.exe was not found under Java home '$JavaHome'."
}

if ([string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $AndroidSdkRoot = $env:ANDROID_HOME
    } elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $AndroidSdkRoot = $env:ANDROID_SDK_ROOT
    } else {
        throw 'ANDROID_HOME/ANDROID_SDK_ROOT is not set. Pass -AndroidSdkRoot or set one of those variables.'
    }
}
$AndroidSdkRoot = [IO.Path]::GetFullPath($AndroidSdkRoot)
if (-not (Test-Path -LiteralPath $AndroidSdkRoot -PathType Container)) {
    throw "Android SDK root does not exist: '$AndroidSdkRoot'."
}

$StagingRoot = [IO.Path]::GetFullPath($StagingRoot).TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
)
if (-not (Test-AsciiPath -Path $StagingRoot)) {
    throw "The staging root must contain ASCII characters only: '$StagingRoot'. Pass -StagingRoot with an ASCII path."
}
New-Item -ItemType Directory -Path $StagingRoot -Force | Out-Null

$stagingName = 'worktree-{0}-{1}' -f $commit.Substring(0, 12), ([Guid]::NewGuid().ToString('N'))
$stagingPath = [IO.Path]::GetFullPath((Join-Path $StagingRoot $stagingName))
$expectedPrefix = $StagingRoot + [IO.Path]::DirectorySeparatorChar
if (-not $stagingPath.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Computed staging path escaped its root: '$stagingPath'."
}
if (-not (Test-AsciiPath -Path $stagingPath)) {
    throw "Computed staging path is not ASCII: '$stagingPath'."
}
if (Test-Path -LiteralPath $stagingPath) {
    throw "Refusing to reuse an existing staging path: '$stagingPath'."
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $AndroidSdkRoot

$resultCode = 1
$worktreeAdded = $false
$cleanupFailed = $false

try {
    Write-Output "[gradle-ascii] source=$sourceRoot"
    Write-Output "[gradle-ascii] commit=$commit"
    Write-Output "[gradle-ascii] staging=$stagingPath"
    Write-Output "[gradle-ascii] java=$JavaHome"
    Write-Output "[gradle-ascii] androidSdk=$AndroidSdkRoot"

    Invoke-Git -WorkingDirectory $sourceRoot -Arguments @(
        'worktree',
        'add',
        '--detach',
        $stagingPath,
        $commit
    )
    $worktreeAdded = $true

    $stagedCommit = (& git -C $stagingPath rev-parse --verify HEAD).Trim().ToLowerInvariant()
    if ($LASTEXITCODE -ne 0 -or $stagedCommit -ne $commit) {
        throw "Staged worktree resolved to '$stagedCommit', expected '$commit'."
    }

    Push-Location (Join-Path $stagingPath 'renderer')
    try {
        & npm.cmd ci --no-audit --no-fund
        $npmExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($npmExitCode -ne 0) {
        Write-Error "npm ci failed with exit code $npmExitCode." -ErrorAction Continue
        $resultCode = $npmExitCode
    } else {
        Push-Location $stagingPath
        try {
            & .\gradlew.bat @GradleArguments
            $resultCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        Write-Output "[gradle-ascii] gradleExitCode=$resultCode"
    }
} catch {
    Write-Error $_ -ErrorAction Continue
    $resultCode = 1
} finally {
    if ($worktreeAdded) {
        & git -C $stagingPath -c core.longpaths=true clean -ffdx
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Could not clean generated files from staging worktree '$stagingPath'." -ErrorAction Continue
            $cleanupFailed = $true
        }

        & git -C $sourceRoot worktree remove --force $stagingPath
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Could not remove staging worktree '$stagingPath'." -ErrorAction Continue
            $cleanupFailed = $true
        }
    }

    if (Test-Path -LiteralPath $stagingPath) {
        Write-Error "Staging path still exists after cleanup: '$stagingPath'." -ErrorAction Continue
        $cleanupFailed = $true
    } else {
        Write-Output '[gradle-ascii] cleanup=complete'
    }
}

if ($cleanupFailed -and $resultCode -eq 0) {
    $resultCode = 1
}
exit $resultCode
