[CmdletBinding()]
param([string]$KeyDirectory = (Join-Path $env:LOCALAPPDATA 'RemoteCam\signing'))
$ErrorActionPreference = 'Stop'
$keyDirectoryPath = [IO.Path]::GetFullPath($KeyDirectory)
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($keyDirectoryPath.StartsWith($projectPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $keyDirectoryPath -eq $projectPath) {
    throw 'Keep release signing keys outside the repository.'
}
$keystorePath = Join-Path $keyDirectoryPath 'remotecam-release.p12'
$passwordPath = Join-Path $keyDirectoryPath 'remotecam-release.password.dpapi'
if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $passwordPath)) {
    throw 'Signing material already exists. Reuse it for updates; this script never replaces keys.'
}
$javaPath = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if (!(Test-Path -LiteralPath "$javaPath\bin\keytool.exe")) { $javaPath = $env:JAVA_HOME }
$keytoolPath = Join-Path $javaPath 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $keytoolPath)) { throw 'A JDK with keytool is required.' }
New-Item -ItemType Directory -Force -Path $keyDirectoryPath | Out-Null
$acl = Get-Acl -LiteralPath $keyDirectoryPath
$acl.SetAccessRuleProtection($true, $false)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$rule = [Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $keyDirectoryPath -AclObject $acl
$randomBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
$password = [Convert]::ToBase64String($randomBytes)
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
$securePassword | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordPath -Encoding ascii
$previousPassword = $env:REMOTECAM_SIGNING_PASSWORD
try {
    $env:REMOTECAM_SIGNING_PASSWORD = $password
    & $keytoolPath -genkeypair -keystore $keystorePath -storetype PKCS12 -alias remotecam-release -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=RemoteCam Release' -storepass:env REMOTECAM_SIGNING_PASSWORD -keypass:env REMOTECAM_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Inspect the signing directory before retrying.' }
} finally {
    $env:REMOTECAM_SIGNING_PASSWORD = $previousPassword
    $password = $null
    $securePassword.Dispose()
}
Write-Output "Release keystore: $keystorePath"
Write-Output "Password protected by Windows DPAPI: $passwordPath"
Write-Output 'Back up the keystore AND its recovered password in secure storage before publishing. See docs/releases.md.'
