param([string]$GoExe)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$depsDir = Join-Path $repoRoot 'build/windows-deps'
New-Item -ItemType Directory -Force $depsDir | Out-Null
function Get-VerifiedZip($name, $url, $sha256, $destination) {
    $zip = Join-Path $depsDir ($name + '.zip')
    if (!(Test-Path $zip)) { Invoke-WebRequest $url -OutFile $zip }
    if ((Get-FileHash $zip -Algorithm SHA256).Hash -ne $sha256) { throw "Checksum mismatch for $name. Do not use this download." }
    if (!(Test-Path $destination)) { Expand-Archive $zip $destination }
}
Get-VerifiedZip 'wil' 'https://api.nuget.org/v3-flatcontainer/microsoft.windows.implementationlibrary/1.0.240803.1/microsoft.windows.implementationlibrary.1.0.240803.1.nupkg' 'FBC8F63269C99BC551E41E48D258B9F011BBF4A5C3FA3F706307D5EBCF70B087' "$depsDir/wil"
Get-VerifiedZip 'compiler' 'https://api.nuget.org/v3-flatcontainer/microsoft.net.compilers.toolset/4.8.0/microsoft.net.compilers.toolset.4.8.0.nupkg' '37333F4F1E2CE55E621355D6DA651DC23D4CB5F94A8F76B9478816E87F110AD9' "$depsDir/compiler"
$ffmpegUrl = 'https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-09-14-13-17/ffmpeg-n8.1.2-52-g5a03dfa0f6-win64-lgpl-8.1.zip'
Get-VerifiedZip 'ffmpeg-pinned' $ffmpegUrl '398BE5FB6E09FF3AE419AD62436D566C62705B1CC59BD4F98E8CD79646D42708' "$depsDir/ffmpeg-pinned"
@{ url = $ffmpegUrl; sha256 = '398be5fb6e09ff3ae419ad62436d566c62705b1cc59bd4f98e8cd79646d42708'; license = 'LGPL'; version = 'n8.1.2-52-g5a03dfa0f6' } | ConvertTo-Json | Set-Content "$depsDir/ffmpeg-origin.json"

if (!$GoExe) {
    $goCommand = Get-Command go -ErrorAction SilentlyContinue
    if ($goCommand) { $GoExe = $goCommand.Source }
    elseif (Test-Path "$repoRoot/build/go-toolchain/go/bin/go.exe") { $GoExe = "$repoRoot/build/go-toolchain/go/bin/go.exe" }
    else { throw 'Install Go compatible with go2rtc go.mod or pass -GoExe.' }
}
$goSource = Join-Path $depsDir 'go2rtc-source'
if (!(Test-Path "$goSource/.git")) {
    git clone --depth 1 --branch v1.9.14 https://github.com/AlexxIT/go2rtc.git $goSource
    if ($LASTEXITCODE) { throw 'go2rtc checkout failed.' }
}
$commit = git -C $goSource rev-parse HEAD
if ($commit -ne 'b5948cfb25404cc5cb37b166ecaa2dca20b11d4b') { throw 'Unexpected go2rtc source revision.' }
$patchPath = Join-Path $repoRoot 'patches/go2rtc-1.9.14-nack.patch'
git -C $goSource apply --reverse --check $patchPath 2>$null
if ($LASTEXITCODE) {
    git -C $goSource apply --check $patchPath
    if ($LASTEXITCODE) { throw 'go2rtc patch check failed.' }
    git -C $goSource apply $patchPath
    if ($LASTEXITCODE) { throw 'go2rtc patch failed.' }
}
Push-Location $goSource
try {
    & $GoExe test ./pkg/webrtc -count=1
    if ($LASTEXITCODE) { throw 'go2rtc tests failed.' }
    & $GoExe build -trimpath -ldflags '-s -w' -o "$repoRoot/build/go2rtc-remotecam.exe" .
    if ($LASTEXITCODE) { throw 'go2rtc build failed.' }
} finally { Pop-Location }
Copy-Item "$goSource/LICENSE" "$PSScriptRoot/licenses/go2rtc.txt" -Force
Copy-Item "$depsDir/wil/LICENSE" "$PSScriptRoot/licenses/WIL.txt" -Force
Copy-Item "$depsDir/ffmpeg-pinned/ffmpeg-n8.1.2-52-g5a03dfa0f6-win64-lgpl-8.1/LICENSE.txt" "$PSScriptRoot/licenses/FFmpeg.txt" -Force
