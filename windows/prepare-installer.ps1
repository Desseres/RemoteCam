$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$deps = Join-Path $repoRoot 'build/windows-deps'
$download = Join-Path $deps 'innosetup-6.7.3.exe'
$destination = Join-Path $deps 'inno'
New-Item -ItemType Directory -Force -Path $deps | Out-Null
if (!(Test-Path -LiteralPath $download)) {
    Invoke-WebRequest 'https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe' -OutFile $download
}
if ((Get-FileHash $download -Algorithm SHA256).Hash -ne '9C73C3BAE7ED48D44112A0F48E66742C00090BDB5BEF71D9D3C056C66E97B732') { throw 'Inno Setup checksum mismatch.' }
$signature = Get-AuthenticodeSignature $download
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Pyrsys B.V.') { throw 'Inno Setup publisher verification failed.' }
if (!(Test-Path (Join-Path $destination 'ISCC.exe'))) {
    $arguments = @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CURRENTUSER','/NOICONS',('/DIR="' + $destination + '"'),'/MERGETASKS="!fileassoc"')
    $process = Start-Process -FilePath $download -ArgumentList $arguments -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode) { throw 'Inno Setup compiler installation failed.' }
}
Write-Output "Inno Setup compiler ready: $destination"
