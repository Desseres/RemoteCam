# RemoteCam modernization — 2026-09-11

## Scope and baseline

Reviewed commit `375ed00` (Android 13 / API 33 target, Kotlin 1.6.21,
AGP 8.1.0, Ktor 2.3.3 and several AndroidX libraries from 2020).
The modernization preserves the application ID and MJPEG endpoint.

## Changes

- Compile/target API 37 (Android 17), minimum API 28. AGP 9.4.0 with built-in
  Kotlin, Gradle 9.7.1 with distribution checksum, Java 17 bytecode.
- Updated AndroidX and coroutines; Ktor 3.5.2 CIO replaces Netty. Removed
  unused Glide, kapt, Safe Args, navigation and explicit Kotlin plugin setup.
- Required camera foreground-service permission and private service.
  Start only after runtime permissions, while the activity is visible.
  Request notifications only on API 33+; declining notifications does not
  block the camera. Request ACCESS_LOCAL_NETWORK on API 37+.
- Bound service and StateFlow replace exported broadcasts and parcelled
  commands. Configuration survives activity recreation and returning from Home.
  Camera work is serialized off the main thread. Generation checks discard
  callbacks from closed sessions. Images, sessions and devices are closed on
  errors and shutdown. No automatic camera restart after process death.
- Enumerate advertised, openable camera IDs, including logical cameras;
  handle missing metadata and select a supported JPEG resolution.
- Nonblocking per-viewer MJPEG distribution, correct CRLF boundaries and
  Content-Length, no replay of old images, HTTP 503 when streaming is disabled.
- Browser receiver at `/view`, using `/live` WebSocket binary messages (8-byte
  big-endian sequence followed by the original JPEG). Text ACK after decode and
  canvas draw permits the next frame; each viewer retains one latest waiting
  frame. Two-second send/ACK deadline disconnects stalled viewers. This bounds
  application backlog but does not guarantee zero latency or every camera frame.
- Decimal Mb/s camera production and total receiver output, feedback-based
  green/orange/red/gray status, and a bandwidth planning table based on measured
  JPEG size at the selected resolution/quality. Suggested capacity is explicitly
  a 2× planning heuristic. Legacy MJPEG cannot acknowledge playback; its socket
  throughput is not presented as a successful end-to-end latency measurement.
- Stop through an explicit service notification action, lifecycle-aware
  preview surface handling, system-bar insets and correctly proportioned preview.
- Persist camera ID, pixel dimensions, quality, Preview and Stream in private
  phone storage. Commit changes on the camera worker before publishing the
  applied configuration; restore and validate camera/resolution on a fresh start.
  Avoid repeated disk writes when only the preview surface changes.
- Release APKs are no longer silently signed with a debug key. The historical
  unused AARs in libs/ remain outside the dependency graph.
- Version 0.2.0 adds a separate H.264 + WebRTC mode using WebRTC SDK
  150.7871.01. Camera2 feeds SurfaceTextureHelper/VideoSource textures directly
  into a hardware-only H.264 Baseline encoder factory, with no JPEG intermediate.
  Camera texture sizes are intersected with hardware H.264 size/rate support.
  RTP senders maintain resolution, target at most 30 fps, and expose a 2–40 Mb/s
  bitrate ceiling per receiver. This is adaptive, lossy coding, not guaranteed
  constant quality. The existing JPEG mode and its quality controls are retained.
- WebRTC uses `/webrtc` for playback and `/signal` for bounded video-only SDP/ICE
  signaling. It uses host ICE candidates on the LAN, without public STUN/TURN
  services. No microphone track or microphone permission is introduced. Up to
  four signaling sessions are admitted; hardware encoder capacity may be lower.
  Disconnects/configuration changes dispose peer resources; clients reconnect.
- All three addresses are visible and copyable. Settings also retain mode,
  bitrate and remembered resolutions per camera/mode. The guide is now a native
  tabbed dialog with cards, colored explanations, capacity tables and copy buttons.
- WebRTC SDK and third-party notices ship in resources/licenses and are served
  at `/licenses`; the application remains MIT-licensed.
- Version 0.2.1 corrects Camera2 texture geometry before WebRTC encoding: undo
  the sensor texture rotation/front-camera mirror and report the sensor/display
  rotation in VideoFrame metadata. This follows upstream Camera2Session's texture
  handling. Previously, upright portrait pixels were squeezed into a landscape
  buffer. The browser continues to fit the picture without stretching; portrait
  1920×1080 capture is displayed as 1080×1920. The guide and README explain OBS sizing.

## Validation

- Version 0.2.8 adds `/whep` HTTP/SDP signaling for go2rtc. `/webrtc` remains an
  HTML player, explaining go2rtc's former `unsupported header: 3c21646f` error.
  POST accepts bounded complete receive-only offers, gathers ICE once, responds
  with 201/application-sdp and Location, and retains the peer until DELETE or
  connection failure. Optional receive-only audio is inactive; no microphone
  track is created. Native peers that never connect or remain disconnected are
  cleaned up, and the session registry is bounded. Browser WebSocket signaling
  retains continual ICE gathering. This HTTP endpoint does not implement trickle
  ICE/PATCH or ICE restarts. The main screen has a copyable go2rtc source address;
  the guide and README explain its distinct role.
  Debug build, lint and 21 JVM tests passed, including HTTP response type, completed
  SDP, Location/DELETE, shutdown/failure cleanup, invalid input and payload bounds.
  On the Android 16 phone with go2rtc 1.9.14, the source was identified as H.264 and
  go2rtc's MP4 output delivered 16,023,584 bytes in 6.55 seconds with an MP4 `ftyp`
  header and `video/mp4; codecs="avc1.42E01F"` response. The user's H6 configuration
  uses `webrtc:http://192.168.1.11:8080/whep`; no relay transcoder was added.
  A follow-up late-join test exposed MP4 viewers waiting indefinitely for a
  keyframe while a WebRTC viewer was already connected. The encoder wrapper now
  requests a keyframe about every two seconds, preserves receiver keyframe requests,
  and retries a rejected request on the next input frame, within the existing bitrate limit.
  After the fix, an additional MP4 consumer joined the active H6 WebRTC relay:
  HTTP 200/MP4 headers arrived in 0.11 seconds and 1 MiB in 0.30 seconds, while the
  existing browser consumer remained connected and the phone reported 30 fps.

- Version 0.2.7 separates the activity renderer from the camera's preview output.
  A service-owned SurfaceTexture is always drained during capture; hiding the
  preview skips rendering instead of closing the camera or WebRTC peers. The same
  applies when the activity goes into the background or its surface is recreated.
  JPEG and WebRTC retain their original streaming paths; local preview uses a
  supported texture size up to 720p where available and fits the full frame.
  Turning Preview off removes its container and expands settings to the full
  available screen. This keeps a camera preview output active while streaming,
  trading some device-side processing for uninterrupted session ownership.
  On-device regression passed six toggles per mode (WebRTC and JPEG), expanded
  settings bounds, background and return: capture generation stayed unchanged
  and frame counts continued increasing. The opt-in instrumentation command is
  `adb shell am instrument -w -e previewContinuity true com.samsung.android.scan3d.test/com.samsung.android.scan3d.JpegRotationInstrumentation`
  after installing the debug test APK, with an unlocked phone and active camera.
  It temporarily switches modes and restores the starting configuration.
  Debug compilation, 17 JVM tests and lint passed. This is a capture-continuity
  check, not a guarantee against packet loss or an end-to-end latency measurement.

- Version 0.2.5 adds camera-supported zoom (Camera2 ratio or legacy centered crop),
  automatic focus, one-shot Focus & lock, Refocus and manual lens distance. The
  manual slider starts at the measured lens position; limits and mode availability
  are taken from the selected camera, and tuning is saved per camera. Capture
  results provide AF state, lens distance and zoom diagnostics. Triggers are sent
  once, not repeated; changing tuning keeps the capture session and stream alive.
  Camera tuning can be inspected locally with
  `adb shell dumpsys activity service com.samsung.android.scan3d/.serv.Cam`.
  Initial on-device diagnostics for camera 1 reported manual focus support,
  1–8× zoom, continuous AF state 2 and 30 fps. Focus calibration is approximate;
  this device can report an AF distance outside its advertised manual range, so
  manual requests are clamped and distance labels must not be treated as exact.

- Version 0.2.4 moves Latency & bandwidth to the bottom of the configuration screen
  beside Info, removing the repository link from that screen. Info presents the
  installed version, Thomas SIMON/Ruddle credits, original Ruddle/RemoteCam link,
  MIT/free-app details and modernization notes in scrollable cards.
  Debug build and lint passed with no issues; the footer, guide opening and Info
  layout were visually verified on the phone after the in-place installation.

- Version 0.2.3 shares the rotation selector across WebRTC, JPEG Browser and MJPEG,
  migrating rtc_rotation to stream_rotation. JPEG requests Camera2 orientation;
  a display listener updates automatic orientation and manual changes update the
  repeating request without reopening the camera. Physically rotated JPEGs are
  passed through. EXIF-only rotation is normalized with Android Bitmap at quality
  100 before both endpoints: same dimensions (swapped for quarter turns), no stale
  EXIF rotation, but the fallback is lossy and can increase CPU/bandwidth/frame time.
- Device instrumentation uses synthetic colored quadrants, not camera images.
  All eight EXIF orientations passed corner-color and dimension checks, stale
  metadata checks and byte identity for already-oriented JPEGs. Build its test APK
  with `:app:assembleDebugAndroidTest`, install it and run
  `adb shell am instrument -w com.samsung.android.scan3d.test/com.samsung.android.scan3d.JpegRotationInstrumentation`.
  Check the explicit PASS/FAIL result. The test APK was removed after validation.
- Live JPEG validation on the rear camera at Full HD/quality 80: a 90-frame MJPEG
  sample at manual 90 degrees delivered 1920×1080 at 21.3 fps (58.8 MB); a later
  60-frame sample at manual 0 degrees delivered 1080×1920 at 18.0 fps (32.18 MB),
  with the Browser viewer also connected. `/view` displayed the changed portrait
  dimensions with aspect fit. These scene/network-specific observations show that
  JPEG rotation can cost throughput; no unchanged-latency/quality claim is made.
  The additional test browser was closed; the user's JPEG mode/rotation was retained.

- Version 0.2.2 adds a WebRTC-only Stream rotation selector: automatic display
  orientation or fixed clockwise 0/90/180/270 degrees relative to upright portrait.
  Rotation changes update frame metadata live without restarting the camera or
  peer connection, and are saved in camera_settings. Local preview is independent.
  On the phone, the saved 90-degree choice was verified and the existing Chromium
  receiver changed from 1080×1920 to 1920×1080 at about 30 fps. The choice was left
  as selected on the phone. Unit tests cover manual rotation independence from
  display rotation/lens facing and invalid-value fallback to automatic mode.

- Debug and unsigned release builds passed. All seventeen JVM tests passed;
  Android Lint reported no issues (0 errors, 0 warnings).
- Tests cover MJPEG framing/reconnect and disabled streaming, independent latest
  frame queues, decimal units, feedback expiry/health, WebSocket ACK gating,
  skipping stale frames, byte-for-byte JPEG preservation and invalid ACK closure.
- Signaling tests cover lossless SDP transport, ICE parsing, malformed/oversized
  offers, rejection of extra/audio tracks, offer/answer routing and peer cleanup
  when a signaling socket closes.
- In the initial 0.2.0 check on the Android 16 phone, Chromium confirmed `video/H264`, 1920×1080 and
  approximately 28–31 received fps. With a 12 Mb/s limit, sampled reception was
  approximately 10–11 Mb/s. These are observations for the tested scene and LAN,
  not a claim of equal JPEG quality or end-to-end latency. The phone also reported
  two connected WebRTC receivers at 30 encoded fps. Changing the bitrate ceiling
  during transmission and reconnecting after preview changes worked.
- The 0.2.1 geometry regression was reproduced in Chromium before the fix:
  portrait content was visibly squeezed into 1920×1080. After the in-place APK
  update, the same front-camera scene displayed upright at 1080×1920 with correct
  proportions and side bars in a landscape browser viewport, at approximately
  30 fps. The existing viewer reconnected automatically. Unit tests also cover
  front/rear rotation directions at all four display rotations; other physical
  camera/orientation combinations still need device testing.
- Also checked the live picture directly in OBS 32.2.2 after the correction:
  its 1920×1080 Browser Source displayed the upright portrait picture with correct
  proportions and side bars. An inactive JPEG Browser Source above WebRTC was
  covering it with a waiting screen; hiding that source revealed the working feed.
- After the WebRTC update, switching back to JPEG passed another physical-device
  regression check: `/cam.mjpeg` delivered 60 valid 1920×1080 frames in 2.03 s
  (29.5 fps), and `/view` displayed a 1920×1080 canvas without a connection error.
  Test viewers were closed and the phone was returned to WebRTC afterward.
- Native WebRTC ARM64 ELF load segments have 16,384-byte alignment. Actual
  runtime testing used this phone, not a separate 16 KB page-size device.
- Visually checked all three address labels and the guide's Formats, Bandwidth
  and OBS setup tabs on the phone; shortened the summary to avoid splitting units.
- Updated APK installed in place on the phone; `/view` decoded and displayed
  1920×1080 JPEGs in Chromium, including a page reload after updating the app.
  Bandwidth table and colored feedback visually checked on the phone. The
  receiver draws immediately without waiting for requestAnimationFrame, avoiding
  an additional browser refresh wait in the ACK loop. Test browser closed after
  verification to release its extra bandwidth. OBS output/audio sync is not yet
  measured with a recorded clap test.
- Physical device: BVL_N49, Android 16, API 36. Installation, camera permission
  flow, live preview and Wi-Fi streaming verified.
- Two simultaneous viewers: 90 correctly framed JPEGs each at 1920×1080,
  about 27.2 fps each. A longer single-viewer run received 900 frames in
  30.34 seconds (29.7 fps). Values are observations, not performance guarantees.
- Background service observed with camera type 0x40, foreground notification,
  no activity binding and working Full HD output. One connection reset occurred
  during the transition; reconnect and the longer run succeeded.
- The stream validator reads image bytes only in memory; it saves no camera images.
- Switching to the front camera produced 30 valid JPEG frames at 3840×1644.
  Stop closed the HTTP port and removed the service; a fresh launch succeeded.
- Persistence checked on the phone after force-stop/relaunch and an in-place
  APK update. The selected camera, resolution, JPEG quality and switch states
  were retained. Uninstall/clear-storage intentionally removes these preferences.

## Remaining limits

The go2rtc 1.9.14 relay needed a separate RTCP/NACK fix after Firefox playback
exposed packet-loss freezes. The local relay patch and regression test are in
[patches/README.md](../patches/README.md), including before/after measurements
and the user's confirmation of smooth playback. This does not change the APK;
the patched executable is a local build, not an official go2rtc release.

- Android 17 is compiled/targeted, but runtime tests here use Android 16 hardware.
  Test Android 17 permission grant/denial (especially LAN), Android 9–15,
  rotation, screen lock, OEM battery restrictions and prolonged streaming before
  a public release. No claim of comprehensive device compatibility.
- JPEG and WebRTC signaling use unauthenticated, unencrypted HTTP for a trusted
  LAN; WebRTC media uses DTLS-SRTP. Do not expose port 8080 to the internet.
  Authentication/TLS and cross-network STUN/TURN support remain future work.
- WebRTC video-only LAN mode is an initial implementation. Test more hardware
  encoders, high resolutions, receiver browsers/OBS versions, prolonged sessions
  and network loss. No measured total camera-to-OBS latency or audio sync claim.
- Only cameras exposed by the vendor through CameraManager.cameraIdList are
  selectable. Hidden physical telephoto/ultrawide sensors are not guessed or
  bypassed; supporting those requires logical-camera output routing and testing.
- The legacy utils module still contains unused sample helpers, including a
  deprecated RenderScript converter; the actual JPEG streaming path does not use it.
- JPEG hardware capture and Wi-Fi determine frame rate and bandwidth; high
  resolutions and JPEG quality 100 can use substantial bandwidth.
- Earlier physical-device checks used local debug builds. Version 0.3.0 adds a
  separately signed release APK and a documented packaging/verification workflow;
  see [release signing](releases.md). It does not establish additional device coverage.

## Sources

- [Android 17 changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Camera foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#camera)
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Gradle 9.7.1](https://docs.gradle.org/9.7.1/release-notes.html)
- [Ktor engines](https://ktor.io/docs/server-engines.html)
- [WebRTC Android SDK distribution](https://github.com/webrtc-sdk/android)
- [WebRTC hardware encoder factory](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/HardwareVideoEncoderFactory.java)
- [WebRTC Camera2 texture and orientation handling](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/src/java/org/webrtc/Camera2Session.java)
