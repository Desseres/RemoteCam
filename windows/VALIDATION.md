# Windows prototype validation — 14 September 2026

Environment: Windows 11 25H2 x64 (build 26200), Honor BVL-N49 with Android 16,
RemoteCam Android 0.3.3-h265.1. The phone used H.265 WebRTC, front camera,
1920×1080 and a 40 Mb/s configured limit. No phone settings were changed by the
desktop application. These results are for this machine, not all Windows PCs.

## Confirmed

- Native source DLL and camera host compile with MSVC 14.35 and SDK 10.0.22000.0;
  desktop UI compiles with Roslyn 4.8 against .NET Framework.
- Address normalization/rejection and the NV12 black-frame preview self-test pass.
- Windows registers and enumerates **RemoteCam (Wirtualny aparat fotograficzny
  systemu Windows)**. The suffix is supplied and localized by Windows.
- A two-minute run received **3444 H.265 frames**, including recovery after the
  test's go2rtc child process was deliberately terminated. The receiver reported
  disconnection, retried, and resumed H.265 without recreating the camera.
- Media Foundation Source Reader consumed 3,110,400-byte NV12 samples from the
  registered 1920×1080 camera. Changing content checksums before and after the
  reconnect confirmed real phone frames, not just successful device enumeration.
- A separate 90-second run received **2632 H.265 frames**.
- DirectShow enumerated and captured the virtual device at **1920×1080 / 30 fps**.
  A three-second FFmpeg DirectShow capture consumed **89 frames**, approximately
  29–30 fps; Windows exposed YUY2 on that compatibility path.
- Normal disconnect removed the session camera and left no receiver child
  processes running. No camera images or recordings were saved by the tests.
- Forcibly terminating the desktop test process also removed the camera and
  killed all **three** owned child processes through the Windows job object;
  no child processes survived. The installed DLL hash matches the package.
- The app's own window was rendered off-screen for layout inspection.

## Scope and remaining checks

This is a video-only prototype. DirectShow capture is useful evidence for OBS's
device capture path, but an actual OBS scene, Teams/Zoom/Discord call, microphone
synchronization, end-to-end latency, sustained thermal/load behavior and GPU
decoding have not been validated here. H.264 reception is implemented using the
same decoder pipeline; this physical-phone run used H.265.

The Windows binaries are local, unsigned test artifacts. Public distribution
also needs preservation of the corresponding third-party source/build materials
described in `licenses/THIRD-PARTY.md`.
