[CmdletBinding()]
param(
    [string]$ApkPath,
    [switch]$Upload
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Join-Path $projectRoot 'website'
$staging = Join-Path $projectRoot 'build\website-preview'
# Publish the selected public APK, even when the checkout is developing a newer version.
$versions = @(Get-Content (Join-Path $source 'versions.json') -Raw | ConvertFrom-Json)
$version = $versions[0].version
$appId = $versions[0].applicationId
if ($appId -ne 'pl.remotecam.app' -or $version -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected application ID or version.' }
if (!$ApkPath) { $ApkPath = "dist\google-play\$appId\$version\RemoteCam-$version.apk" }
$apk = if ([IO.Path]::IsPathRooted($ApkPath)) { [IO.Path]::GetFullPath($ApkPath) } else { [IO.Path]::GetFullPath((Join-Path $projectRoot $ApkPath)) }
if (!(Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Missing signed APK: $apk. Run tools/package-play-windows.ps1 first." }
$metadataPath = Join-Path (Split-Path $apk) 'build-info.json'
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $appId -or $metadata.version -ne $version) { throw 'APK metadata does not match this app version.' }
$checksumPath = Join-Path (Split-Path $apk) 'SHA256SUMS.txt'
$apkName = [IO.Path]::GetFileName($apk)
$expected = @(Get-Content -LiteralPath $checksumPath | Where-Object { $_ -match ('^[0-9a-fA-F]{64}  ' + [regex]::Escape($apkName) + '$') })
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected.Count -ne 1 -or $expected[0].Substring(0,64).ToLowerInvariant() -ne $hash) { throw 'APK checksum mismatch.' }
$downloadName = "RemoteCam-$appId-$version.apk"
$versions[0].apk = "downloads/$downloadName"

# Validate the source list before staging. Only these public files can be uploaded.
$siteFiles = @('index.php','bootstrap.php','config.php','translations.php','privacy.php','styles.css','app.js','.htaccess')
foreach ($name in $siteFiles) { if (!(Test-Path -LiteralPath (Join-Path $source $name) -PathType Leaf)) { throw "Missing website/$name" } }
New-Item -ItemType Directory -Force -Path $staging,(Join-Path $staging 'assets'),(Join-Path $staging 'downloads') | Out-Null
foreach ($name in $siteFiles) { Copy-Item -LiteralPath (Join-Path $source $name) -Destination (Join-Path $staging $name) }
$assets = @(Get-ChildItem (Join-Path $source 'assets') -File | Where-Object { $_.Extension -in '.png','.jpg','.webp','.svg' })
foreach ($file in $assets) { Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $staging "assets/$($file.Name)") }
# The web privacy policy must always match the text shipped in the app.
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/src/main/resources/privacy-policy.txt') -Destination (Join-Path $staging 'privacy-policy.txt')
$versions | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $staging 'versions.json') -Encoding utf8NoBOM
Copy-Item -LiteralPath $apk -Destination (Join-Path $staging "downloads/$downloadName")
"$hash  $downloadName" | Set-Content (Join-Path $staging "downloads/$downloadName.sha256") -Encoding ascii

# Assets and APK first; entry point last. Do not remove any existing server files.
$files = @($assets | ForEach-Object { "assets/$($_.Name)" }) +
    @("downloads/$downloadName", "downloads/$downloadName.sha256", 'privacy-policy.txt') +
    @($siteFiles | Where-Object { $_ -ne 'index.php' }) + @('versions.json','index.php')
Write-Output "Prepared $($files.Count) public files in $staging"
if (!$Upload) {
    Write-Output 'Local preview only. To publish after configuring .env.deploy, run this script with -Upload.'
    Write-Output "php -S 127.0.0.1:8092 -t `"$staging`""
    return
}
$envPath = Join-Path $projectRoot '.env.deploy'
if (!(Test-Path -LiteralPath $envPath)) { throw 'Create a local .env.deploy from .env.deploy.example.' }
$config = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
    if (!$line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
    $parts = $line -split '=',2
    if ($parts.Count -ne 2) { throw 'Invalid line in .env.deploy.' }
    $config[$parts[0].Trim()] = $parts[1]
}
foreach ($key in @('FTP_HOST','FTP_PORT','FTP_USER','FTP_PASSWORD','FTP_REMOTE_PATH')) {
    if (!$config[$key]) { throw "Missing $key in .env.deploy." }
}
if ($config.FTP_HOST -notmatch '^[a-zA-Z0-9.-]+$' -or $config.FTP_PORT -notmatch '^\d{1,5}$' -or [int]$config.FTP_PORT -notin 1..65535) { throw 'Invalid FTP host or port.' }
$remote = $config.FTP_REMOTE_PATH.TrimEnd('/')
if (!$remote.StartsWith('/') -or $remote -eq '' -or $remote -match '(^|/)\.\.?(/|$)' -or $remote.Contains('\')) { throw 'Use an explicit, non-root absolute FTP_REMOTE_PATH for RemoteCam.' }
$remote = ($remote -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
$baseUrl = "ftp://$($config.FTP_HOST):$($config.FTP_PORT)$remote/"
function Curl-Quoted([string]$Value) {
    if ($Value -match "[\r\n\x00]") { throw 'Invalid control character in curl configuration.' }
    return '"' + $Value.Replace('\','\\').Replace('"','\"') + '"'
}
Get-Command curl.exe -ErrorAction Stop | Out-Null
Write-Output "Uploading RemoteCam to $($config.FTP_HOST)$($config.FTP_REMOTE_PATH) using explicit FTPS."
foreach ($name in $files) {
    $url = $baseUrl + (($name -split '/' | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/')
    # Credentials are passed through stdin, never printed or put in the process command line.
    $curlConfig = @('fail','silent','show-error','ssl-reqd','ftp-create-dirs','connect-timeout = 20','max-time = 600',
        ('user = ' + (Curl-Quoted "$($config.FTP_USER):$($config.FTP_PASSWORD)")),
        ('upload-file = ' + (Curl-Quoted (Join-Path $staging $name))), ('url = ' + (Curl-Quoted $url))) -join "`n"
    $curlConfig | & curl.exe --config -
    if ($LASTEXITCODE -ne 0) { throw "Upload failed: $name. Earlier files may have been uploaded; fix the problem and rerun." }
    Write-Output "Uploaded: $name"
}
Write-Output 'Publication completed. Check the public homepage, privacy policy and APK download.'
