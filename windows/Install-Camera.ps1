$ErrorActionPreference = 'Stop'
try {
    $admin = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (!$admin.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Uruchom instalator jako administrator (lub użyj przycisku w RemoteCam).' }
    $build = [int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion').CurrentBuild
    if ($build -lt 22000) { throw 'RemoteCam Desktop wymaga Windows 11.' }
    $targetDir = Join-Path $env:ProgramFiles 'RemoteCam Desktop\Camera'
    $sourceDll = Join-Path $PSScriptRoot 'RemoteCamSource.dll'
    if (!(Test-Path -LiteralPath $sourceDll)) { throw 'Brak RemoteCamSource.dll obok instalatora.' }
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    # Versioned filenames allow updates while Frame Server still has the old DLL loaded.
    $sourceHash = (Get-FileHash $sourceDll -Algorithm SHA256).Hash.ToLower()
    $targetDll = Join-Path $targetDir ('RemoteCamSource-' + $sourceHash.Substring(0, 12) + '.dll')
    if (!(Test-Path -LiteralPath $targetDll) -or (Get-FileHash $sourceDll).Hash -ne (Get-FileHash $targetDll).Hash) {
        Copy-Item -LiteralPath $sourceDll -Destination $targetDll -Force
    }
    $key = 'HKLM:\SOFTWARE\Classes\CLSID\{D168A389-283B-4AD8-ACB4-1CC943368FA0}\InprocServer32'
    New-Item -Path $key -Force | Out-Null
    Set-Item -LiteralPath $key -Value $targetDll
    New-ItemProperty -LiteralPath $key -Name ThreadingModel -Value Both -PropertyType String -Force | Out-Null
    Write-Output 'Zainstalowano komponent kamery RemoteCam. Uruchom RemoteCam.exe i kliknij Połącz.'
    exit 0
} catch {
    $_.Exception.Message | Set-Content (Join-Path $env:ProgramData 'RemoteCam-install-error.txt')
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'RemoteCam — instalacja') | Out-Null
    exit 1
}
