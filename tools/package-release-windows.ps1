[CmdletBinding()]
param(
    [string]$KeyDirectory = (Join-Path $env:LOCALAPPDATA 'RemoteCam\signing'),
    [string]$BuildRoot = 'C:\Temp\RemoteCamBuild'
)
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$keystorePath = Join-Path $KeyDirectory 'remotecam-release.p12'
$passwordPath = Join-Path $KeyDirectory 'remotecam-release.password.dpapi'
if (!(Test-Path -LiteralPath $keystorePath) -or !(Test-Path -LiteralPath $passwordPath)) {
    throw 'Release signing material is missing. See docs/releases.md.'
}
$status = & git -C $projectPath status --porcelain
if ($LASTEXITCODE -ne 0 -or $status) { throw 'Commit all source changes before packaging a release.' }
$commit = & git -C $projectPath rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the source commit.' }
$tag = & git -C $projectPath describe --exact-match --tags HEAD
if ($LASTEXITCODE -ne 0) { throw 'Tag the release commit before packaging.' }

& (Join-Path $PSScriptRoot 'build-windows.ps1') -BuildRoot $BuildRoot ':app:assembleRelease'
$apkDirectory = Join-Path $projectPath 'app\build\outputs\apk\release'
$metadata = Get-Content -LiteralPath (Join-Path $apkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.elements.Count -ne 1) { throw 'Expected one universal release APK.' }
$version = $metadata.elements[0].versionName
$versionCode = $metadata.elements[0].versionCode
if ($tag -ne "v$version") { throw 'Git tag and Android versionName do not match.' }
$unsignedApk = Join-Path $apkDirectory $metadata.elements[0].outputFile
$outputDirectory = Join-Path $projectPath "dist\$tag"
$apkName = "RemoteCam-$version.apk"
$apkPath = Join-Path $outputDirectory $apkName
if (Test-Path -LiteralPath $outputDirectory) { throw "Output directory already exists: $outputDirectory. Preserve published artifacts; choose a new version for changes." }
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Join-Path $sdkPath 'build-tools\37.0.0'
$javaPath = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if (!(Test-Path -LiteralPath "$javaPath\bin\java.exe")) { $javaPath = $env:JAVA_HOME }
$javaExe = Join-Path $javaPath 'bin\java.exe'
$apksigner = Join-Path $buildTools 'lib\apksigner.jar'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$aapt = Join-Path $buildTools 'aapt.exe'
foreach ($toolPath in @($javaExe, $apksigner, $zipalign, $aapt)) {
    if (!(Test-Path -LiteralPath $toolPath)) { throw "Required tool missing: $toolPath" }
}
New-Item -ItemType Directory -Path $outputDirectory | Out-Null
$alignedApk = Join-Path $apkDirectory 'app-release-aligned.apk'
& $zipalign -P 16 -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
$securePassword = Get-Content -LiteralPath $passwordPath -Raw | ConvertTo-SecureString
$previousPassword = $env:REMOTECAM_SIGNING_PASSWORD
try {
    $env:REMOTECAM_SIGNING_PASSWORD = [Net.NetworkCredential]::new('', $securePassword).Password
    & $javaExe -jar $apksigner sign --ks $keystorePath --ks-key-alias remotecam-release --ks-pass env:REMOTECAM_SIGNING_PASSWORD --key-pass env:REMOTECAM_SIGNING_PASSWORD --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $apkPath $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
} finally {
    $env:REMOTECAM_SIGNING_PASSWORD = $previousPassword
    $securePassword.Dispose()
}
$verification = & $javaExe -jar $apksigner verify --verbose --print-certs $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
& $zipalign -c -P 16 4 $apkPath
if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment check failed.' }
$badging = & $aapt dump badging $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed.' }
if ($badging -match '^application-debuggable') { throw 'Refusing to distribute a debuggable APK.' }
if (!($badging -match "versionCode='$versionCode' versionName='$version'")) { throw 'APK version check failed.' }
if (!($badging -match "sdkVersion:'28'") -or !($badging -match "targetSdkVersion:'37'")) { throw 'Unexpected SDK requirements.' }
$certificate = ($verification | Select-String '^Signer #1 certificate SHA-256 digest: (.+)$').Matches.Groups[1].Value
if (!$certificate) { throw 'Cannot read signing certificate fingerprint.' }
$notesPath = Join-Path $projectPath "docs\releases\$tag.md"
Copy-Item -LiteralPath $notesPath -Destination (Join-Path $outputDirectory 'release-notes.md')
$buildInfo = [ordered]@{
    version = $version
    versionCode = $versionCode
    tag = $tag
    commit = $commit
    applicationId = $metadata.applicationId
    minSdk = 28
    targetSdk = 37
    buildType = 'release'
    debuggable = $false
    apk = $apkName
    apkBytes = (Get-Item -LiteralPath $apkPath).Length
    apkSha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    signingCertificateSha256 = $certificate
    buildTools = '37.0.0'
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
}
$buildInfo | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDirectory 'build-info.json') -Encoding utf8
$checksums = foreach ($name in @($apkName, 'release-notes.md', 'build-info.json')) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $outputDirectory $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $name"
}
$checksums | Set-Content -LiteralPath (Join-Path $outputDirectory 'SHA256SUMS.txt') -Encoding ascii
$verification
Write-Output "GitHub Release assets: $outputDirectory"
