# RemoteCam Desktop — Windows 11 prototype

Receives the existing RemoteCam phone stream and exposes **RemoteCam Windows
Virtual Camera** to camera applications. This experimental companion runs
independently of OBS Browser Source. An optional microphone module routes phone
audio through VB-CABLE as a separate Windows recording device.

## Install (recommended)

Run **RemoteCam-Desktop-0.1.7-test-Setup.exe** on Windows 11 x64 and accept
the administrator prompt. This single, offline installer includes the application,
decoder, relay and camera component. It creates a Start menu shortcut and offers
an optional desktop shortcut. No separate camera-registration script is needed.
Open RemoteCam Desktop, enable Stream on the phone and connect to its HTTP address.

Later installers update the same application directory. Choose **Zakończ** from
the RemoteCam tray menu before updating; the window's X now hides it.
Uninstall through Windows Settings → Apps → Installed apps.
Close applications using the camera first; a loaded camera DLL may require a restart
to finish removal. The saved phone address and diagnostics are retained.

Version **0.1.4-test** adds the application logo, multi-size EXE/window/taskbar icon,
matching shortcut identity and a branded Polish/English setup wizard. Streaming
behavior is unchanged from 0.1.3. The app interface is currently Polish.

## Hardware and stream recommendations (0.1.5)

Open **Sprzęt i jakość** to see the CPU model/core count, graphics adapters and
driver versions, RAM, the active GPU/CPU decoder, and the phone's actual input
resolution/codec/FPS. Hardware discovery runs asynchronously through Windows WMI;
missing hardware data does not prevent streaming. An adapter listing alone is not
proof of codec support. The active decoder reports whether D3D11VA is in use.

Start with H.264 / 1920×1080 / 30 fps; the software-decoder starting recommendation
is 1280×720 / 30 fps. These are conservative starting profiles, not model-specific
hardware limits. After a three-second warmup, each 30-second observation compares
receive and preview throughput with the input FPS (capped at the camera's 30 fps).
Below 90% of the target, or more than three seconds below that threshold, suggests
a lower input resolution. Unrecovered RTP packets and decoder errors instead
produce transmission/corruption guidance. Unknown input FPS cannot pass the check.
For RTSP inputs that omit FPS, the demuxer's `tbr` estimate is explicitly labelled
as estimated and the user is asked to compare it with the phone's setting.
Disconnect, retry or input-profile replacement discards the previous assessment.

The panel recommends settings; change them on the phone. A passing profile is
only evidence for that observation, not the physical maximum or a latency test.
4K input still has to be decoded before scaling to the fixed 1080p camera output.
Neither CPU/GPU names nor a short live test can guarantee a maximum resolution.

## Background operation (0.1.7)

Closing the window with X or minimizing it hides RemoteCam in the Windows tray.
The receiver, virtual camera and enabled phone microphone continue working.
Double-click the tray icon or choose **Pokaż RemoteCam** to restore the window.
The tray menu also offers microphone mute, disconnect, and **Zakończ — zatrzymaj
transmisję**. Exit stops the media processes and removes the session camera.
This is a user-session application, not a Windows service; it does not survive
logout, shutdown or an explicit exit, and it does not start automatically at login.

Video is decoded once. The local preview converts the resulting NV12 frames into
a bitmap. Hidden/minimized windows, other tabs and the unchecked **Podgląd lokalny**
option skip that conversion and drawing. In-flight conversions are discarded;
the preview bitmap is released and UI polling drops to once per second. The
background receiver and its recovery logic remain active. Quality advice excludes
preview FPS while preview is off, rather than reporting it as a performance failure.

## Optional phone microphone (0.1.6/0.1.7)

Open **Mikrofon**, install VB-CABLE if absent, connect the phone and enable its
microphone. Select **CABLE Input (VB-Audio Virtual Cable)** and click **Włącz mikrofon**.
In OBS add an Audio Input Capture source using **CABLE Output**. Windows sound
settings can rename that input to **RemoteCam Microphone (VB-CABLE)**. Leave the
OBS source's monitoring off and Windows **Listen to this device** unchecked.
Mute the browser phone player to avoid capturing that playback a second time.
Keep the physical microphone as its own OBS source.

The module starts off, has its own mute, 0–100% gain and level meter, and never
renders to speakers. It uses the existing relay session with an audio-only decoder
(48 kHz stereo float PCM), a bounded 100 ms queue and an explicit VB-CABLE WASAPI
endpoint. There is no default-device fallback. Decoder stalls/source restarts retry;
disconnect/exit clears queued audio and stops audio processing. Microphone enable
and gain/mute settings are not persisted across application restarts.

VB-CABLE is a separate VB-Audio donationware product; the package carries its
original unmodified installer archive and licensing notice. Its installation is
optional and explicitly initiated by the user. Its installer can change Windows'
default input/output and may require a restart: restore your normal speakers and
physical microphone afterward. RemoteCam does not uninstall this shared driver.
See `licenses/VB-CABLE.txt` and https://vb-audio.com/Services/licensing.htm.

## Run the portable package

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

Version **0.1.3-test** uses D3D11VA hardware decoding on Windows, with a four-thread
software fallback if hardware initialization fails. The connection status shows
**GPU** or **CPU**. The previous single-thread CPU decoder could not sustain the
phone's high-bitrate 4K H.265 stream and overflowed the local packet queue.

The receiver repairs reordered/missing video packets before decoding.
It waits up to 150 ms for retransmission when a gap occurs; complete frames are
forwarded immediately. If repair fails, it drops the damaged reference chain
and requests a fresh keyframe. A brief pause can still occur on a lossy network.
Changing codec/restarting the phone stream automatically renews the receiver;
a four-second no-frame watchdog also catches decoders that remain connected
but stop producing images. Initial connection has a 12-second grace period.
The Windows camera stays registered throughout this recovery. A local RTSP queue
overflow also renews the decoder session instead of continuing with missing fragments.

The interface uses warm dark brown and the website's yellow
accent (`#ffe15a`). The preview follows incoming frames (approximately 30 fps),
converts at most one frame at a time on a worker, and never builds a display queue.
The status bar shows separate **receive** and **preview** frame rates.
The original 0.1.0 preview was limited to four updates per second.
No camera-component reinstall is required when updating from 0.1.0–0.1.2 to 0.1.3.

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
- Bounded text diagnostics (last 256 lines, including packet repair counters and
  decoder errors) are saved every five seconds and on retry/disconnect to
  `%LOCALAPPDATA%/RemoteCam Desktop/receiver.log`. They contain no camera images.
- H.264 and H.265 are decoded by FFmpeg on the PC using D3D11VA when available.
  Hardware frames are downloaded as NV12 and scaled to the camera's fixed output.
  Setting `REMOTECAM_SOFTWARE_DECODER=1` before launch forces the CPU path for diagnostics.
- The camera media source is adapted from Microsoft's MIT-licensed Windows-Camera
  sample; attribution is in `licenses/`. Native binaries use a static MSVC runtime.
  The UI uses the .NET Framework included in Windows 11.

## Build and validation

Requires Visual Studio 2022 C++ tools, Windows SDK 10.0.22000.0, and Windows 11 x64.
Run `prepare-dependencies.ps1`, then `build.ps1` from PowerShell.
Also run `prepare-audio.ps1` once for the verified NAudio/VB-CABLE dependencies.
Output: `dist/windows/RemoteCam-Desktop-0.1.7-test/`.

For the single EXE installer, run `prepare-installer.ps1` once to download and
verify the pinned Inno Setup 6.7.3 compiler, then run `build-installer.ps1`.
Output: `dist/windows/RemoteCam-Desktop-0.1.7-test-Setup.exe`, with a SHA-256 sidecar.
Pass `-Iscc PATH` to use an existing compiler, or `-SkipAppBuild` to package an
already built app. Version and channel are in `version.json` (installer is test-only).
The installer uses Windows' normal elevation prompt to register the camera in
HKLM64. A content-addressed DLL filename permits updates while an older camera
component is loaded. Uninstall removes its registration only if it still points
to that package's component.

Brand assets are checked in under `assets/`, derived from the repository's
RemoteCam icon. To regenerate PNG/ICO files after SVG edits, run
`node windows/generate-brand-assets.cjs` with `sharp` available to Node.js.

The Windows dependency build uses the cumulative
`patches/go2rtc-1.9.14-video-repair.patch` against the pinned upstream commit.
It includes the previous outgoing NACK fix. Incoming video repair and tuned
NACK feedback are enabled only in the desktop child via `REMOTECAM_RTP_REPAIR=1`.
The separately installed go2rtc used with OBS is not replaced.

- `RemoteCam.exe --self-test`: address validation and NV12 preview checks.
- `RemoteCam.exe --tray-test PHONE_IP:8080 ABSOLUTE_LOG_PATH`: live hidden-window
  camera API probe, zero preview conversions while hidden, preview resume/manual
  disable/minimize, optional audio continuity through installed VB-CABLE, and exit.
- `RemoteCam.exe --audio-test PHONE_IP:8080 ABSOLUTE_LOG_PATH`: reads the real
  VB-CABLE recording endpoint; verifies live/muted/resumed/stopped signal. Saves
  only counters, never audio samples. Requires the phone microphone to be enabled.
- `tests/AudioBufferTests.cs`: verifies queued-audio mute, gain, silence on
  underrun, bounded overflow and buffer clearing.
- `tests/HardwareAdviceTests.cs`: compile with `app/HardwareInfo.cs` and
  `System.Management.dll`; checks input parsing, measurement windows, reconnects,
  variable source FPS, packet-loss/corruption priority and resolution guidance.
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
- `tests/ReceiverWatchdogTests.cs`: compile as a .NET Framework console executable
  and pass the absolute `RemoteCam.exe` path. Uses a local HTTP fixture and its
  own idle child processes to verify frame stall/source replacement/cancellation.

These commands do not replace a live test in OBS or a conferencing application.
