#ifndef AppVersion
  #define AppVersion "0.1.4"
#endif
#ifndef PackageDir
  #error PackageDir is required; run windows/build-installer.ps1
#endif
#ifndef CameraHash
  #error CameraHash is required
#endif
#define BrandDir SourcePath + "..\assets"
#define CameraDir "{commonpf}\RemoteCam Desktop\Camera"
#define CameraFile "RemoteCamSource-" + CameraHash + ".dll"
#define CameraKey "Software\Classes\CLSID\{D168A389-283B-4AD8-ACB4-1CC943368FA0}"

[Setup]
AppId={{59D138B7-650A-445F-93F6-762BECA1A748}
AppName=RemoteCam Desktop
AppVersion={#AppVersion}-test
AppVerName=RemoteCam Desktop {#AppVersion} (test)
AppPublisher=RemoteCam
AppPublisherURL=https://github.com/Desseres/RemoteCam
AppSupportURL=https://github.com/Desseres/RemoteCam/issues
AppUpdatesURL=https://github.com/Desseres/RemoteCam/releases
DefaultDirName={autopf}\RemoteCam Desktop
DefaultGroupName=RemoteCam
DisableProgramGroupPage=yes
DisableWelcomePage=no
PrivilegesRequired=admin
ArchitecturesAllowed=x64os
ArchitecturesInstallIn64BitMode=x64os
MinVersion=10.0.22000
OutputDir=..\..\dist\windows
OutputBaseFilename=RemoteCam-Desktop-{#AppVersion}-test-Setup
SetupIconFile={#BrandDir}\RemoteCam.ico
UninstallDisplayIcon={app}\RemoteCam.exe
UninstallDisplayName=RemoteCam Desktop
VersionInfoVersion={#AppVersion}.0
VersionInfoDescription=RemoteCam Desktop — instalator
VersionInfoProductName=RemoteCam Desktop
VersionInfoProductVersion={#AppVersion}
WizardStyle=modern dark includetitlebar hidebevels
WizardBackColor=#1c1815
WizardImageFile={#BrandDir}\installer-panel.png
WizardImageBackColor=#1c1815
WizardSmallImageFile={#BrandDir}\brandmark.png
WizardSmallImageBackColor=#1c1815
WizardSizePercent=110
Compression=lzma2/max
SolidCompression=yes
DiskSpanning=no
SetupLogging=yes
AppMutex=Local\RemoteCam.Desktop
CloseApplications=yes
CloseApplicationsFilter=RemoteCam.exe,RemoteCamHost.exe,go2rtc.exe,ffmpeg.exe
RestartApplications=no
UninstallLogMode=append
LicenseFile=..\..\LICENSE

[Languages]
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
polish.WelcomeLabel1=Zamień telefon w kamerę Windows
polish.WelcomeLabel2=RemoteCam Desktop odbiera obraz z telefonu i udostępnia go jako kamerę w OBS oraz innych aplikacjach.%n%nInstalator przygotuje aplikację i składnik kamery. Po instalacji włącz Stream na telefonie, wpisz jego adres i kliknij Połącz.%n%nWersja testowa {#AppVersion} · Windows 11
polish.FinishedHeadingLabel=RemoteCam jest gotowy
polish.FinishedLabel=Włącz Stream w aplikacji RemoteCam na telefonie. Następnie uruchom RemoteCam Desktop i kliknij Połącz.%n%nW OBS wybierz urządzenie do przechwytywania wideo „RemoteCam”.
english.WelcomeLabel1=Turn your phone into a Windows camera
english.WelcomeLabel2=RemoteCam Desktop receives video from your phone and makes it available as a camera in OBS and other applications.%n%nSetup installs the app and camera component. Then enable Stream on your phone, enter its address and click Connect.%n%nTest version {#AppVersion} · Windows 11
english.FinishedHeadingLabel=RemoteCam is ready
english.FinishedLabel=Enable Stream in RemoteCam on your phone. Open RemoteCam Desktop and connect.%n%nIn OBS, add a Video Capture Device and select RemoteCam.

[CustomMessages]
polish.DesktopShortcut=Utwórz skrót na pulpicie
polish.LaunchApp=Uruchom RemoteCam Desktop
english.DesktopShortcut=Create a desktop shortcut
english.LaunchApp=Launch RemoteCam Desktop

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopShortcut}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#PackageDir}\RemoteCam.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\RemoteCamHost.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\RemoteCamSource.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\RemoteCamSource.dll"; DestDir: "{#CameraDir}"; DestName: "{#CameraFile}"; Flags: onlyifdoesntexist uninsrestartdelete
Source: "{#PackageDir}\go2rtc.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\ffmpeg.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\Install-Camera.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\Uninstall-Camera.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\README.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\VALIDATION.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\build-info.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\ffmpeg-origin.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\SHA256SUMS.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\licenses\*"; DestDir: "{app}\licenses"; Flags: ignoreversion recursesubdirs createallsubdirs

[Registry]
Root: HKLM64; Subkey: "Software\Classes\CLSID\{{D168A389-283B-4AD8-ACB4-1CC943368FA0}\InprocServer32"; ValueType: string; ValueName: ""; ValueData: "{#CameraDir}\{#CameraFile}"
Root: HKLM64; Subkey: "Software\Classes\CLSID\{{D168A389-283B-4AD8-ACB4-1CC943368FA0}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Both"

[Icons]
Name: "{group}\RemoteCam Desktop"; Filename: "{app}\RemoteCam.exe"; WorkingDir: "{app}"; AppUserModelID: "RemoteCam.Desktop"
Name: "{autodesktop}\RemoteCam Desktop"; Filename: "{app}\RemoteCam.exe"; WorkingDir: "{app}"; Tasks: desktopicon; AppUserModelID: "RemoteCam.Desktop"

[Run]
Filename: "{app}\RemoteCam.exe"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent runasoriginaluser

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Registered: String;
begin
  if CurUninstallStep = usUninstall then
  begin
    { Do not unregister a different camera build installed after this package. }
    if RegQueryStringValue(HKLM64, '{#CameraKey}\InprocServer32', '', Registered) and
       (CompareText(Registered, ExpandConstant('{#CameraDir}\{#CameraFile}')) = 0) then
      RegDeleteKeyIncludingSubkeys(HKLM64, '{#CameraKey}');
  end;
end;
