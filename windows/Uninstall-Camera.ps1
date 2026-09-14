$ErrorActionPreference = 'Stop'
try {
    $admin = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (!$admin.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Uruchom skrypt jako administrator.' }
    $targetDir = [IO.Path]::GetFullPath((Join-Path $env:ProgramFiles 'RemoteCam Desktop\Camera'))
    $targetDll = Join-Path $targetDir 'RemoteCamSource.dll'
    $key = 'HKLM:\SOFTWARE\Classes\CLSID\{D168A389-283B-4AD8-ACB4-1CC943368FA0}'
    $inproc = $key + '\InprocServer32'
    if (Test-Path -LiteralPath $inproc) {
        $registered = (Get-Item -LiteralPath $inproc).GetValue('')
        if ([IO.Path]::GetDirectoryName($registered) -ne $targetDir -or [IO.Path]::GetFileName($registered) -notmatch '^RemoteCamSource(?:-[0-9a-f]{12})?\.dll$') { throw 'Rejestr wskazuje inną lokalizację. Nie usunięto tej instalacji.' }
        # Remove only the two exact keys owned by this component, without recursion.
        Remove-Item -LiteralPath $inproc
        Remove-Item -LiteralPath $key
    }
    Get-ChildItem -LiteralPath $targetDir -File | Where-Object Name -match '^RemoteCamSource(?:-[0-9a-f]{12})?\.dll$' | ForEach-Object {
        if ([IO.Path]::GetDirectoryName($_.FullName) -ne $targetDir) { throw 'Unexpected uninstall target.' }
        Remove-Item -LiteralPath $_.FullName
    }
    Write-Output 'Komponent odinstalowany. Kamera sesyjna znika po zamknięciu RemoteCam.'
    exit 0
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show($_.Exception.Message + "`nZamknij RemoteCam i aplikacje korzystające z kamery; spróbuj ponownie.", 'RemoteCam — odinstalowanie') | Out-Null
    exit 1
}
