# RemoteCam Desktop — Windows 11 prototype

Receives the existing RemoteCam phone stream and exposes **RemoteCam Windows
Virtual Camera** to camera applications. This is an experimental **video-only**
companion, independent of OBS Browser Source. It does not install a virtual microphone.

## Run

1. Extract the complete package to a folder and open `RemoteCam.exe`.
2. Click **Zainstaluj kamerę** once and accept the Windows administrator prompt.
   The installer registers a Media Foundation source DLL under Program Files.
3. On the phone enable **Stream** with **H.264 + WebRTC**, or experimental H.265.
4. Enter the phone's HTTP address, e.g. `192.168.1.11:8080`, and click **Połącz**.
   This is the application port, **not** the wireless ADB port.
5. In OBS add **Video Capture Device**, selecting **RemoteCam Windows Virtual Camera**.
   Keep RemoteCam Desktop running. Choose a separate microphone in OBS.

The output is 1920×1080 NV12 at up to 30 fps. Other input sizes are fitted inside
that frame with letterboxing. A disconnected feed turns black after 1.5 seconds;
the receiver retries automatically. **Rozłącz** removes the session camera.
The phone's settings are not changed by the desktop application.

Version **0.1.1-test** uses a warm dark-brown interface and the website's yellow
accent (`#ffe15a`). The preview follows incoming frames (approximately 30 fps),
converts at most one frame at a time on a worker, and never builds a display queue.
The status bar shows separate **receive** and **preview** frame rates.
The original 0.1.0 preview was limited to four updates per second.
No camera-component reinstall is required when updating from 0.1.0 to 0.1.1.

Windows camera privacy settings must permit desktop applications. The test
binaries are not signed; there is no public Windows release yet. Compatibility
with each conferencing application must be checked individually.

To unregister the component, disconnect, close applications using the camera,
then run `Uninstall-Camera.ps1` as administrator. If Windows still has the DLL open,
retry after restarting Windows. The desktop folder can then be removed.

## Architecture

`Phone WHEP → go2rtc → localhost RTSP → FFmpeg → NV12 local pipe → Media Foundation camera`

- The native camera source runs inside Windows Frame Server. It receives only
  fixed-size raw frames through a randomly named pipe restricted to the current
  user, Local Service and Local System; network logons are denied.
- Network handling and decoding run in child processes owned by RemoteCam Desktop.
  The go2rtc control API and RTSP server listen only on loopback. WebRTC uses
  ephemeral ICE ports. All child processes are stopped when disconnecting.
- No recordings, camera frames or audio are written to disk. The last phone address
  is saved in `%LOCALAPPDATA%/RemoteCam/desktop-address.txt`. Temporary relay
  configuration is removed on normal shutdown.
- H.264 and H.265 are decoded by FFmpeg on the PC. This prototype uses software
  decoding; GPU acceleration and latency tuning are future work.
- The camera media source is adapted from Microsoft's MIT-licensed Windows-Camera
  sample; attribution is in `licenses/`. Native binaries use a static MSVC runtime.
  The UI uses the .NET Framework included in Windows 11.

## Build and validation

Requires Visual Studio 2022 C++ tools, Windows SDK 10.0.22000.0, and Windows 11 x64.
Run `prepare-dependencies.ps1`, then `build.ps1` from PowerShell.
Output: `dist/windows/RemoteCam-Desktop-0.1.1-test/`.

- `RemoteCam.exe --self-test`: address validation and NV12 preview checks.
- `RemoteCam.exe --preview-test PHONE_IP:8080 20`: exercise the actual WinForms
  preview off-screen, with the registered camera; text-only `preview-result.txt`
  reports receive/preview rates, process CPU time, pipe reads and connections.
  Its steady-stream pass threshold is 25 fps for both receive and preview.
- `RemoteCam.exe --smoke PHONE_IP:8080 15 --no-camera`: receive/decode check;
  writes only text counters to `smoke-result.txt`.
- `RemoteCam.exe --smoke PHONE_IP:8080 20`: same check with the registered camera.
- `RemoteCamHost.exe --list`: enumerate Windows cameras.
- `RemoteCamHost.exe --probe`: read 60 frames from the RemoteCam camera through
  Media Foundation and report sizes, timestamps and content checksums, not pixels.

These commands do not replace a live test in OBS or a conferencing application.
