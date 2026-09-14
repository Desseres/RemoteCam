param([string]$Iscc, [switch]$SkipAppBuild)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$release = Get-Content (Join-Path $PSScriptRoot 'version.json') -Raw | ConvertFrom-Json
if (!$SkipAppBuild) { & (Join-Path $PSScriptRoot 'build.ps1'); if ($LASTEXITCODE) { throw 'Application build failed.' } }
$package = Join-Path $repoRoot ('dist/windows/RemoteCam-Desktop-' + $release.version + '-' + $release.channel)
if (!$Iscc) { $Iscc = Join-Path $repoRoot 'build/windows-deps/inno/ISCC.exe' }
if (!(Test-Path -LiteralPath $Iscc)) { throw 'Run windows/prepare-installer.ps1 or pass -Iscc pointing to Inno Setup 6.7.3+.' }
$cameraHash = (Get-FileHash (Join-Path $package 'RemoteCamSource.dll') -Algorithm SHA256).Hash.ToLower().Substring(0,12)
& $Iscc "/DAppVersion=$($release.version)" "/DPackageDir=$package" "/DCameraHash=$cameraHash" (Join-Path $PSScriptRoot 'installer/RemoteCam.iss')
if ($LASTEXITCODE) { throw 'Installer compilation failed.' }
$setup = Join-Path $repoRoot ('dist/windows/RemoteCam-Desktop-' + $release.version + '-test-Setup.exe')
$hash = (Get-FileHash $setup -Algorithm SHA256).Hash.ToLower()
[IO.File]::WriteAllText($setup + '.sha256', $hash + '  ' + [IO.Path]::GetFileName($setup) + "`n")
Write-Output "Installer: $setup"
