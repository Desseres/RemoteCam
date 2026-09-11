[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$BuildRoot = 'C:\Temp\RemoteCamBuild',
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($BuildRoot -match '[^\x00-\x7F]' -or $BuildRoot -match '\s') {
    throw 'BuildRoot must be an ASCII path without spaces (Java socket workaround).'
}
New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null
function Ensure-Junction([string]$Path, [string]$Target) {
    $resolvedTarget = [IO.Path]::GetFullPath($Target)
    if (Test-Path -LiteralPath $Path) {
        $existing = Get-Item -LiteralPath $Path
        if ($existing.LinkType -ne 'Junction' -or [IO.Path]::GetFullPath($existing.Target) -ne $resolvedTarget) {
            throw "Existing path $Path does not point to $resolvedTarget. Choose a different BuildRoot."
        }
    } else {
        New-Item -ItemType Junction -Path $Path -Target $resolvedTarget | Out-Null
    }
}
$cache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
New-Item -ItemType Directory -Force -Path $cache | Out-Null
Ensure-Junction (Join-Path $BuildRoot 'project') $project
Ensure-Junction (Join-Path $BuildRoot 'gradle-home') $cache
$previousJava = $env:JAVA_HOME
$previousGradle = $env:GRADLE_USER_HOME
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath "$studioJava\bin\java.exe") { $env:JAVA_HOME = $studioJava }
    $env:GRADLE_USER_HOME = Join-Path $BuildRoot 'gradle-home'
    $env:JAVA_TOOL_OPTIONS = "$previousOptions -Djdk.net.unixdomain.tmpdir=$BuildRoot".Trim()
    Push-Location (Join-Path $BuildRoot 'project')
    try {
        & .\gradlew.bat --no-daemon '-Pandroid.overridePathCheck=true' @Tasks
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:GRADLE_USER_HOME = $previousGradle
    $env:JAVA_TOOL_OPTIONS = $previousOptions
}
