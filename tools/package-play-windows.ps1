[CmdletBinding()]
param([string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'RemoteCam\signing'),
      [string]$BuildRoot = 'C:\Temp\RemoteCamBuild')
$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$config = Get-Content -LiteralPath (Join-Path $projectPath 'app\build.gradle') -Raw
$version = [regex]::Match($config, "versionName = '([^']+)'").Groups[1].Value
$code = [int][regex]::Match($config, 'versionCode = (\d+)').Groups[1].Value
$appId = [regex]::Match($config, "applicationId = '([^']+)'").Groups[1].Value
if ($appId -notmatch '^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$') { throw 'Invalid applicationId.' }
if (!$version -or $code -lt 1) { throw 'Missing app version.' }
$output = Join-Path $projectPath "dist\google-play\$appId\$version"
if ((Test-Path -LiteralPath $output) -and (Get-ChildItem -LiteralPath $output).Count) {
    throw "Output exists: $output. Preserve previous packages before rebuilding."
}
$key = Join-Path $SigningDirectory 'remotecam-release.p12'
$passwordFile = Join-Path $SigningDirectory 'remotecam-release.password.dpapi'
foreach ($file in @($key,$passwordFile)) { if (!(Test-Path -LiteralPath $file)) { throw "Missing $file" } }
& (Join-Path $PSScriptRoot 'build-windows.ps1') -BuildRoot $BuildRoot :app:bundleRelease :app:assembleRelease
$java = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin'
if (!(Test-Path -LiteralPath "$java\java.exe")) { $java = Join-Path $env:JAVA_HOME 'bin' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Join-Path $sdk 'build-tools\37.0.0'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$aab = Join-Path $output "RemoteCam-$version.aab"
$apk = Join-Path $output "RemoteCam-$version.apk"
$unsigned = Join-Path $projectPath 'app\build\outputs\apk\release\app-release-unsigned.apk'
$aligned = Join-Path $projectPath 'app\build\outputs\apk\release\app-release-play-aligned.apk'
& "$buildTools\zipalign.exe" -P 16 -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
$secret = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$previous = $env:REMOTECAM_SIGNING_PASSWORD
try {
    $env:REMOTECAM_SIGNING_PASSWORD = [Net.NetworkCredential]::new('', $secret).Password
    & "$java\jarsigner.exe" -keystore $key -storetype PKCS12 -storepass:env REMOTECAM_SIGNING_PASSWORD -keypass:env REMOTECAM_SIGNING_PASSWORD -signedjar $aab (Join-Path $projectPath 'app\build\outputs\bundle\release\app-release.aab') remotecam-release
    if ($LASTEXITCODE -ne 0) { throw 'AAB signing failed' }
    & "$java\java.exe" -jar "$buildTools\lib\apksigner.jar" sign --ks $key --ks-key-alias remotecam-release --ks-pass env:REMOTECAM_SIGNING_PASSWORD --key-pass env:REMOTECAM_SIGNING_PASSWORD --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $apk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
} finally { $env:REMOTECAM_SIGNING_PASSWORD = $previous; $secret.Dispose() }
& "$java\jarsigner.exe" -verify $aab
if ($LASTEXITCODE -ne 0) { throw 'AAB verification failed' }
& "$java\java.exe" -jar "$buildTools\lib\apksigner.jar" verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
& "$buildTools\zipalign.exe" -c -P 16 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment invalid' }
$metadata = Get-Content (Join-Path $projectPath 'app\build\outputs\apk\release\output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $appId) { throw 'Built applicationId does not match requested package.' }
$info = [ordered]@{ version=$version; versionCode=$code; applicationId=$metadata.applicationId;
    commit=(git -C $projectPath rev-parse HEAD); uncommittedChanges=[bool](git -C $projectPath status --porcelain);
    builtAtUtc=[DateTime]::UtcNow.ToString('o'); purpose='Google Play preparation; review PLAY-CONSOLE.md before uploading' }
$info | ConvertTo-Json | Set-Content (Join-Path $output 'build-info.json') -Encoding utf8
Get-ChildItem -LiteralPath $output -File | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($_.Name)"
} | Set-Content (Join-Path $output 'SHA256SUMS.txt') -Encoding ascii
Write-Output "Prepared Play review artifacts: $output"
