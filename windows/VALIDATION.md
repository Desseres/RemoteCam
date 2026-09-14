# Windows prototype validation — 14 September 2026

## 0.1.10 address field alignment

The single-line address editor is vertically centered in a 32 px frame matching
the adjacent connection buttons, with inset text and a focus border. The normal
window render (`--render-ui`, exit 0) was visually checked: top and bottom edges
align and the address remains legible. No connection logic changed.

## 0.1.9 microphone layout

Reproduced the overlapping device selector and buttons with `--render-audio`.
The table now uses an explicit full-width column and auto-sized rows; width
constraints apply only to wrapping labels, not fixed-height input controls.
The selector, volume slider and level meter occupy their own full-width rows.
Disabled button captions and device names use readable muted text on the dark
background. The expanded device list accommodates longer endpoint names.
Visually checked rendered default and minimum-size windows (exit 0): no overlap,
the complete installed VB-CABLE name is visible, and lower content scrolls.

## 0.1.8 information tab

Built the desktop application and visually checked the information tab at the
default and minimum window sizes using `--render-about` and
`--render-about-small` (both exit 0). The minimum size scrolls the content;
navigation and all three primary links fit horizontally. The preview tab was
also rendered to check navigation layout. The website returned HTTP 200 and
contained both the download anchor and APK links. Download navigation uses the
website section rather than a pinned APK version. Local license targets are
included in the package. This UI update does not change the receiver pipeline.

## 0.1.7 tray and suspended preview; optional audio module

The live tray probe exercised the real window X path with the physical phone.
While hidden, Media Foundation returned changing 1920×1080 NV12 samples from
the RemoteCam camera (`RemoteCamHost --probe`, exit 0). Over the hidden interval
the receiver delivered 213 video frames and the audio module received 2,726,400
PCM bytes; the local preview started **zero** conversions. Restore resumed the
preview without restarting the receiver. Manual preview disable and minimizing
also suspended it. Explicit exit completed and no media child processes remained.
This tests the system camera API, not an OBS recording or end-to-end latency.

The separate VB-CABLE audio capture test returned 814,314 nonzero live samples,
zero nonzero samples during mute, 204,806 after unmute, and zero after stop.
It captured 48 kHz stereo IEEE float through the Windows recording endpoint;
only counters were retained. AudioBufferTests verifies queued-data mute, gain,
underrun silence, bounded overflow and clearing. HardwareAdviceTests verifies
that disabling preview does not imply decoder overload.

VB-CABLE Pack45 was downloaded from its official site and its SHA-256 and valid
VB-Audio installer signature verified. Its installer changed this host's default
devices; with the user's choice, 7.1 Surround Sound and HF-50 were restored.
The current endpoint name remains CABLE Output. A proposed automatic rename was
removed after Windows denied the property write; use Windows sound settings.
No automatic device-name/default changes are performed by the application.
The original optional driver archive and NAudio MIT notices ship with the app.

## 0.1.5 hardware and quality panel

WMI discovery returned the host's Intel i9-12900KS (16 cores / 24 threads),
Intel UHD 770, NVIDIA RTX 3090 Ti, driver versions and 64 GB RAM. The new
Sprzęt i jakość panel was rendered and visually checked with those real values.
Hardware queries run off the UI thread and have a bounded UI wait; failed
discovery does not disable the receiver. Names do not imply codec support.

HardwareAdviceTests passes input parsing (including explicitly estimated RTSP
frame rates), warmup/full observation timing, reconnect invalidation, unknown
and variable FPS, portrait resolution reduction, stalls despite burst catchup,
and packet-loss/corruption precedence. New loss immediately invalidates an older
healthy result. Self-test and receiver watchdog tests also pass.

The physical phone sent 4096×2304 H.265, decoded through D3D11VA. A 40.00 s
WinForms test measured receive 29.75 fps / preview 29.40 fps, with 2.39 s UI
process CPU time and no decoder errors. The first 30-second advisory window
measured 30.0 / 29.8 fps and reported the profile as sustained for that window.
Loss occurred later in the run; this prompted immediate invalidation of the
previous result on new packet loss, covered by the regression test above.
RTSP supplied a 30 tbr estimate, not explicit input FPS; the panel labels it
as estimated and asks the user to compare the phone setting.

These observations do not measure glass-to-glass latency or maximum hardware
capability. Software-decoder resolution recommendations are conservative starting
profiles, not CPU-model benchmarks. No phone settings are changed automatically.

## 0.1.4 branding and single EXE installer

The WinForms app embeds the RemoteCam logo and a nine-size Windows icon
(16–256 px). The application and installer shortcuts share the explicit
`RemoteCam.Desktop` AppUserModelID. The rendered window and installed app were
visually inspected: logo, wordmark, version badge and title-bar icon are visible.
`--self-test` and `--render-ui` both exited 0; file version is 0.1.4.0.

Inno Setup 6.7.3 compiled the branded, offline Windows 11 x64 installer.
A real installation completed in `C:\Program Files\RemoteCam Desktop`, without
a restart, and launched the app as the original user. The installed app, host,
camera DLL, go2rtc and FFmpeg matched the build's SHA-256 hashes. The Start menu
shortcut targets the installed app, and Windows' uninstall entry reports
0.1.4-test. The camera registration points at the expected content-addressed DLL,
whose hash also matches, with `ThreadingModel=Both`.

The installed app connected to the physical phone with H.265/GPU and displayed
30 fps for both reception and preview during inspection. This was a launch and
packaging check, not a new stream-quality benchmark. The installer license and
destination screens were visually inspected with the brown theme and yellow logo.

Automatic uninstall and upgrade execution have not been exercised on this host;
the user's installed, connected app was left running. The uninstall hook checks
component ownership before removing its COM registration; locked component files
are scheduled for removal on restart. The package is unsigned. Testing on a clean
Windows 11 machine and at additional DPI settings remains pending.

## 0.1.3 high-resolution H.265 GPU decoding

The user's live codec-switch test exposed a second failure after packet repair:
H.265 input reached go2rtc without incoming loss, but the RTSP sender reported
thousands of dropped packets. A one-thread CPU decoder could not sustain this
4096×2304, 40 Mb/s target input. Two 35 s same-source probes with/without
`low_delay` both delivered about 23 fps and had reference-frame errors. A D3D11VA
probe sustained 30 fps. Secondary probes initially joined between keyframes;
their startup parameter-set messages are distinct from the continuing CPU errors.

The desktop now uses explicit D3D11VA hardware output, downloads NV12 frames and
scales them to 1920×1080. GPU setup failure switches subsequent attempts to four
CPU decoder threads. An RTSP sequence-gap guard after the local sender queue
closes an overrun session so the receiver reconnects rather than continue to
decode missing fragments. This guard is enabled only for the desktop child.

| Physical phone / H.265 / real WinForms | Receive | Preview | Process CPU time | Decoder errors |
| --- | --- | --- | --- | --- |
| 45.00 s, D3D11VA | 30.33 fps | 30.02 fps | 2.73 s | 0 |

The run produced 1370 frames. It included packet reordering and a later burst
with three unrecovered packets; dependent frames were suppressed and playback
resumed without decoder corruption. This establishes recovery in that test,
not loss-free Wi-Fi or a guarantee against pauses. Physical display latency and
subjective artifact quality still need user assessment.

A forced software-path 15 s smoke test produced 356 frames with zero decoder
errors (includes startup and a network-loss recovery). It is not a 30 fps CPU
performance claim. Tests also verify detecting a GPU setup failure from stderr.
The process is fully drained on exit before deciding whether to fall back.
The RTSP sequence guard test covers wraparound and stopping on a missing packet.
WebRTC, RTSP, and watchdog test suites pass after the changes.

A separate camera smoke run returned changing Media Foundation sample checksums
at 1920×1080 NV12 (3,110,400 bytes each), confirming the GPU path reaches the
Windows virtual camera. The native DLL hash is unchanged from 0.1.1.

The user's H.264/H.265 switching in 0.1.2 caused automatic receiver renewals while
the same camera-host process remained alive. The same monitor is used in 0.1.3.
Five-second bounded diagnostic snapshots now make current failures inspectable
without disconnecting or recording camera images.

## 0.1.2 packet repair and automatic recovery

The existing receiver reproduced H.264 decoder errors (`error while decoding MB`,
`corrupt decoded frame`, missing PPS) and RTP sequence discontinuities. Its WHEP
track reader forwarded arrival-order RTP directly into RTSP without reordering or
waiting for NACK repairs. This was distinct from the preview's earlier 4 fps cap.

The desktop-only repair path now assembles complete access units, waits up to
150 ms for a missing packet, discards duplicates/late repairs, and requests a new
IDR after an unrecoverable gap. Dependent frames are withheld until that IDR.
Packet history for NACK is 8192 (previous default 512), with a 20 ms request
interval and at most five requests per packet. RTSP sequence numbers are rebuilt
after intentional drops. FFmpeg retains probed keyframes and requests video only.
The original SSRC/sequence space is retained on the WebRTC feedback path.

Physical phone, H.264, high-resolution input (phone settings observed at
4096×2304, 40 Mb/s target, 30 fps), desktop output 1920×1080:

| Real WinForms test | Receive | Preview | Process CPU time | Decoder errors |
| --- | --- | --- | --- | --- |
| 40.00 s | 30.32 fps | 29.50 fps | 2.25 s | 0 |

The receive average includes a small startup burst. Final diagnostic count was
1221 decoded frames. A separate 40 s FFmpeg diagnostic run encountered a burst
with six unrecovered RTP packets; the repair layer suppressed dependent frames
and resumed without H.264 corruption messages. Timestamp rounding warnings in
the null/rawvideo muxer were present; these are not decoder corruption reports.

Go tests cover reordering across sequence wrap, duplicates, missing fragments,
dependent-frame suppression, new-IDR recovery, padding, missing markers, H.265
fragment/aggregation key detection, and bounded storage. Existing outgoing NACK
integration tests still pass. The cumulative patch's reverse check and the
dependency preparation script both pass on the updated checkout.

Process-level C# tests use a loopback HTTP fixture and disposable decoder children:
no complete frames causes a restart; producer ID replacement causes a restart;
an empty transient producer list does not; cancellation terminates the monitor.
The monitor uses one task per pipeline, not one timer per raw pipe read.
The address/preview self-test passes. Native camera binaries are unchanged.

Limits: the 40 s phone run does not establish long-term Wi-Fi reliability or
absolute end-to-end latency. Subsequent live H.265 testing exposed CPU overload,
addressed above in 0.1.3. No phone settings were changed automatically by these tests.

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
