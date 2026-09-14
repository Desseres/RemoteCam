# Windows prototype validation — 14 September 2026

## 0.1.1 preview and performance fix

The user reported stuttering specifically in the desktop preview. Code inspection
found a 250 ms UI timer (a four-fps ceiling), a fixed delay after each camera pipe
write, and an eight-second timer created for every small decoder pipe read.

The preview now consumes the latest frame at most once, converts outside the UI
thread with one conversion in flight, and polls at 15 ms to follow a 30 fps input.
Camera pipe writers wake on frame publication rather than adding a delay after
each transfer. Decoder reading uses one worker instead of scheduling tasks/timers
per chunk. FFmpeg uses one decoder/output thread and timestamp passthrough to
avoid frame-thread queues and synthesized catch-up frames.

Using the same physical phone and H.265 stream, the real WinForms preview ran
off-screen through its normal message loop:

| Steady measurement | Receive | Preview updates | Desktop process CPU time |
| --- | --- | --- | --- |
| 15.00 s | 30.00 fps | 29.93 fps | 0.84 s |
| 20.01 s | 30.03 fps | 29.78 fps | 1.67 s |

The 15-second run made 42,750 raw pipe reads, demonstrating why a timer per read
was unnecessary overhead. Preview counts are image presentations by the UI code,
not a measurement of physical monitor scanout or phone-to-screen latency.

A separate 30-second fault-injection run killed only its own go2rtc child;
the receiver established two successful connections and resumed preview. Its
averages, including the forced outage, were 23.73 fps received / 23.67 displayed.
Media Foundation also returned changing NV12 camera samples during the test.
One earlier start attempt returned Windows `MF_E_INVALIDREQUEST`; a subsequent
run succeeded without reinstalling the component. The startup error message now
distinguishes an unregistered component from other camera-start failures.

The source DLL is unchanged and its hash matches the already installed component.
The warm brown / yellow interface was rendered separately without camera images
to inspect layout and contrast. Hardware decoding remains future work.

## 0.1.0 initial validation

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
