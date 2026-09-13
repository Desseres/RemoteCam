# Audio validation — development build 0.3.2 (17)

Date: 2026-09-12. Phone: Honor BVL-N49, Android 16. Package: `pl.remotecam.app`.

## Completed

- `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`: successful; 24 tests,
  zero failures/errors, lint reports no issues.
- SDP tests cover legacy video-only clients, video+audio in either order, inherited
  session direction, inactive/rejected audio, and rejection of sending clients,
  duplicate media, data channels and conflicting directions. WS and WHEP share validation.
- Direct browser receive: H.264 and Opus, increasing audio bytes and nonzero audio
  energy. No audio/video recordings were made. One temporary UI screenshot was removed.
- Phone mute: receiver audio energy stopped increasing and audio level reached zero;
  video advanced from 734 to 1183 decoded frames over 15 seconds, with the camera
  generation unchanged. The same stream also remained active during background/return.
- go2rtc H6: producer and consumer report H.264 plus OPUS/48000/2. A roughly 39-second
  sample decoded 1159 video frames and received 1940 audio packets, zero reported losses.
  Opus RTP advertises two channels even though microphone input is configured mono.
- Enabled audio continued with the app in the background during the go2rtc test.
- Audio off: browser negotiated video only; phone reported audioCapturing=false,
  and the camera generation was unchanged.
- Revoking RECORD_AUDIO and reopening the app: video capture restarted, audio stayed
  off and no camera error was reported. Permission and the user's enabled audio setting
  were restored for subsequent testing.
- Final build: installed; saved camera 1, 1920x1080, 40 Mb/s, rotation 270 degrees,
  Stream on, audio on and mute off restored. Browser playback works; `?muted=1`
  starts silently and the Enable sound button starts audio without interrupting video.
- Final WHEP SDP checked through ADB forwarding while the LAN was unavailable:
  POST returned 201 with send-only Opus plus H.264, the browser accepted the answer,
  and DELETE released the test session.
- After the user exited Standby, final installed build 0.3.2 passed real LAN go2rtc
  playback again: H.264 and Opus, 150 additional video frames over five seconds,
  increasing audio bytes/energy and zero reported packet losses in the sample.

## Limits and follow-up

- One direct test had startup packet loss; later samples were stable. These checks
  do not promise zero network loss, zero latency or measured lip-sync accuracy.
- Listening quality and end-to-end lip sync still require an OBS listening/clap test.
- The Honor system Standby screen repeatedly interrupted new LAN connections late in
  testing: HTTP over ADB forwarding still answered while Wi-Fi HTTP timed out; waking
  the device restored Wi-Fi HTTP. This is separate from the tested normal background
  behavior. Locked-screen/Standby streaming is not claimed as validated.
- Public 0.3.1 website downloads and Play materials were not replaced by this debug build.
  Before a 0.3.2 release, update store audio/privacy declarations, public policy, listings,
  website version history and generate signed release artifacts.

APK: `dist/testing/0.3.2/RemoteCam-0.3.2-debug.apk` (development signature, not a Play upload).
Detailed temporary build/device/statistics logs are in ignored `build/audio-*` files.

## Website release

Later on 2026-09-12, a separate release-signed APK and AAB were built in
`dist/google-play/pl.remotecam.app/0.3.2/`. The APK was published on
https://remotecam.kasztelan.me/ with updated PL/EN text and privacy policy.
Public APK SHA-256: `aeff09300c84fb9221ffa806fa59d2696e1712fa2d4e516a8e9d7abd0911b6f9`.
Release certificate, APK alignment, public download hash, PL/EN, gallery, clipboard,
mobile layout and exact policy match were verified. No Play Console upload was performed.
