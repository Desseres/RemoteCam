$ErrorActionPreference = 'Stop'
$deps = Join-Path (Split-Path $PSScriptRoot) 'build/windows-deps/audio'
New-Item -ItemType Directory -Force $deps | Out-Null
$packages = @(
    @('naudio.core','https://api.nuget.org/v3-flatcontainer/naudio.core/2.2.1/naudio.core.2.2.1.nupkg','794645DBFD30E4880663D52D9D9224D55C301FA54228FF623C95D572C7887347'),
    @('naudio.wasapi','https://api.nuget.org/v3-flatcontainer/naudio.wasapi/2.2.1/naudio.wasapi.2.2.1.nupkg','C8396B8DBAB86A2619EDFD9158BEAF18C21CB86677099B5594950FD8651B9D1A'),
    @('vbcable','https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack45.zip','B950E39F01AF1D04EA623C8F6D8EB9B6EA5C477C637295FABF20631C85116BFB')
)
foreach ($package in $packages) {
    $zip = Join-Path $deps ($package[0] + '.zip')
    if (!(Test-Path $zip)) { Invoke-WebRequest $package[1] -OutFile $zip }
    if ((Get-FileHash $zip).Hash -ne $package[2]) { throw ('Checksum mismatch: ' + $package[0]) }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $deps $package[0]) -Force
}
$signature = Get-AuthenticodeSignature (Join-Path $deps 'vbcable/VBCABLE_Setup_x64.exe')
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'BUREL VINCENT') { throw 'Invalid VB-CABLE installer signature.' }
Write-Output 'Audio dependencies verified.'
