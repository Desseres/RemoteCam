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
- Release APKs are no longer silently signed with a debug key. No native
  libraries are packaged in the debug APK (the historical unused AARs in libs/
  are not dependencies).

## Validation

- Debug and unsigned release builds passed. All seven JVM tests passed;
  Android Lint reported no issues (0 errors, 0 warnings).
- Tests cover MJPEG framing/reconnect and disabled streaming, independent latest
  frame queues, decimal units, feedback expiry/health, WebSocket ACK gating,
  skipping stale frames, byte-for-byte JPEG preservation and invalid ACK closure.
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

- Android 17 is compiled/targeted, but runtime tests here use Android 16 hardware.
  Test Android 17 permission grant/denial (especially LAN), Android 9–15,
  rotation, screen lock, OEM battery restrictions and prolonged streaming before
  a public release. No claim of comprehensive device compatibility.
- The stream is unauthenticated, unencrypted HTTP for a trusted LAN. Do not
  expose port 8080 to the internet. Authentication/TLS remain future work.
- Only cameras exposed by the vendor through CameraManager.cameraIdList are
  selectable. Hidden physical telephoto/ultrawide sensors are not guessed or
  bypassed; supporting those requires logical-camera output routing and testing.
- The legacy utils module still contains unused sample helpers, including a
  deprecated RenderScript converter; the actual JPEG streaming path does not use it.
- JPEG hardware capture and Wi-Fi determine frame rate and bandwidth; high
  resolutions and JPEG quality 100 can use substantial bandwidth.
- This is a locally tested debug build. Release signing and distribution are
  intentionally not configured.

## Sources

- [Android 17 changes](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Camera foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#camera)
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Gradle 9.7.1](https://docs.gradle.org/9.7.1/release-notes.html)
- [Ktor engines](https://ktor.io/docs/server-engines.html)
