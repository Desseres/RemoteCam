param([switch]$NativeOnly, [switch]$SkipNative)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$release = Get-Content (Join-Path $PSScriptRoot 'version.json') -Raw | ConvertFrom-Json
$outDir = Join-Path $repoRoot ('dist/windows/RemoteCam-Desktop-' + $release.version + '-' + $release.channel)
$objDir = Join-Path $repoRoot 'build/windows-native'
$depsDir = Join-Path $repoRoot 'build/windows-deps'
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$vsDir = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (!$vsDir) { throw 'Install Visual Studio 2022 C++ build tools and Windows SDK 10.0.22000.0 or newer.' }
if (!(Test-Path "$depsDir/wil/include/wil/result.h")) { throw 'Run windows/prepare-dependencies.ps1 first.' }
New-Item -ItemType Directory -Force $outDir,$objDir | Out-Null
$nativeDir = Join-Path $PSScriptRoot 'native'
$vcvars = Join-Path $vsDir 'VC/Auxiliary/Build/vcvars64.bat'
$compile = @"
@echo off
chcp 65001 >nul
call "$vcvars" 10.0.22000.0 >nul
if errorlevel 1 exit /b 1
cd /d "$objDir"
cl /nologo /std:c++17 /EHsc /O2 /MT /W3 /utf-8 /DUNICODE /D_UNICODE /I"$depsDir\wil\include" /LD "$nativeDir\SimpleMediaSource.cpp" "$nativeDir\SimpleMediaStream.cpp" "$nativeDir\SimpleFrameGenerator.cpp" "$nativeDir\VirtualCameraMediaSourceActivate.cpp" "$nativeDir\winrtCommon.cpp" "$nativeDir\Exports.cpp" /link /Brepro /DEF:"$nativeDir\RemoteCamSource.def" /OUT:"$outDir\RemoteCamSource.dll"
if errorlevel 1 exit /b 1
cl /nologo /std:c++17 /EHsc /O2 /MT /W3 /utf-8 /DUNICODE /D_UNICODE "$nativeDir\CameraHost.cpp" /link /Brepro mf.lib mfplat.lib mfuuid.lib mfreadwrite.lib mfsensorgroup.lib ole32.lib /OUT:"$outDir\RemoteCamHost.exe"
exit /b %errorlevel%
"@
[IO.File]::WriteAllText((Join-Path $objDir 'compile.cmd'), ($compile -replace "`r?`n", "`r`n"), [Text.UTF8Encoding]::new($false))
if (!$SkipNative) {
    & $env:ComSpec /d /c (Join-Path $objDir 'compile.cmd')
    if ($LASTEXITCODE) { throw 'Native build failed.' }
}
if ($NativeOnly) { return }
$csc = Join-Path $depsDir 'compiler/tasks/net472/csc.exe'
$desktopSource = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'app/RemoteCam.cs'))
$desktopExe = [IO.Path]::GetFullPath((Join-Path $outDir 'RemoteCam.exe'))
$manifestPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'app/RemoteCam.manifest'))
$iconPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'assets/RemoteCam.ico'))
$logoPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'assets/brandmark.png'))
$versionSource = Join-Path $objDir 'BuildInfo.cs'
$versionCode = @"
using System.Reflection;
[assembly: AssemblyTitle("RemoteCam Desktop")]
[assembly: AssemblyProduct("RemoteCam Desktop")]
[assembly: AssemblyCompany("RemoteCam")]
[assembly: AssemblyDescription("Phone camera for Windows - test version")]
[assembly: AssemblyVersion("$($release.version).0")]
[assembly: AssemblyFileVersion("$($release.version).0")]
[assembly: AssemblyInformationalVersion("$($release.version)-$($release.channel)")]
namespace RemoteCamDesktop { static class BuildInfo { public const string Version = "$($release.version)"; } }
"@
[IO.File]::WriteAllText($versionSource, $versionCode, [Text.UTF8Encoding]::new($false))
& $csc /nologo /target:winexe /platform:x64 /optimize+ /unsafe+ /deterministic+ /codepage:65001 "/out:$desktopExe" "/win32manifest:$manifestPath" "/win32icon:$iconPath" "/resource:$iconPath,RemoteCam.Icon" "/resource:$logoPath,RemoteCam.Logo" /reference:System.Management.dll /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Net.Http.dll /reference:System.Core.dll /reference:System.Web.Extensions.dll $desktopSource (Join-Path $PSScriptRoot 'app/HardwareInfo.cs') $versionSource
if ($LASTEXITCODE) { throw 'Desktop build failed.' }
Copy-Item "$PSScriptRoot/Install-Camera.ps1","$PSScriptRoot/Uninstall-Camera.ps1","$PSScriptRoot/README.md","$PSScriptRoot/VALIDATION.md" $outDir
Copy-Item (Join-Path $repoRoot 'LICENSE') (Join-Path $outDir 'LICENSE.txt')
Copy-Item "$PSScriptRoot/licenses" $outDir -Recurse -Force
$ffmpeg = Get-ChildItem "$depsDir/ffmpeg-pinned" -Filter ffmpeg.exe -Recurse | Select-Object -First 1
if (!$ffmpeg) { throw 'FFmpeg is missing. Run prepare-dependencies.ps1.' }
Copy-Item $ffmpeg.FullName "$outDir/ffmpeg.exe"
Copy-Item "$depsDir/ffmpeg-origin.json" "$outDir/ffmpeg-origin.json"
Copy-Item "$repoRoot/build/go2rtc-remotecam.exe" "$outDir/go2rtc.exe"
@{
    application = 'RemoteCam Desktop'; version = ($release.version + '-' + $release.channel); platform = 'Windows 11 x64'
    sourceCommit = (git -C $repoRoot rev-parse HEAD)
    sourceDirty = [bool](git -C $repoRoot status --porcelain)
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
    go2rtc = 'v1.9.14 + patches/go2rtc-1.9.14-video-repair.patch'
    ffmpeg = 'n8.1.2-52-g5a03dfa0f6, BtbN LGPL build'
} | ConvertTo-Json | Set-Content "$outDir/build-info.json"
Get-ChildItem $outDir -File | Where-Object Name -ne SHA256SUMS.txt | ForEach-Object {
  '{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
} | Set-Content "$outDir/SHA256SUMS.txt"
Write-Output "Built: $outDir/RemoteCam.exe"
