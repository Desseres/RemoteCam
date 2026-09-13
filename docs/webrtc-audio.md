# WebRTC audio — 0.3.2

Version 0.3.2 adds optional phone microphone audio alongside H.264 video.
Audio is off by default. Older 0.3.1 APK/AAB files remain video only.
See the [0.3.2 release notes](releases/v0.3.2.md) for downloads and validation.

## Phone

1. Select **H.264 + WebRTC**.
2. Enable **Microphone audio** and grant Android microphone permission.
3. Enable **Stream**. An audio-capable receiver receives an Opus track alongside video.
4. Use **Mute microphone** to send silence without reconnecting or restarting video.

Enabling/disabling the audio track closes existing WebRTC sessions so receivers can
negotiate a new SDP. The phone camera does not restart. The built-in player reconnects;
reconnect a go2rtc producer if necessary. Disabling audio releases the microphone
capture once the existing peers close. Muting leaves the capture session available,
but substitutes silence; Android may continue showing its microphone indicator.

Audio and mute preferences are saved. An enabled Stream and microphone resume when
the user reopens the app, provided microphone permission remains granted. Denying
microphone permission does not prevent video use. Stream off, JPEG mode or stopping
the service releases audio. No audio/video recordings are saved on the phone.

## OBS and browsers

Use `http://PHONE_IP:8080/webrtc` in an OBS Browser Source and enable **Control audio
via OBS**. Check that the source is unmuted in the OBS mixer. If autoplay is blocked,
use **Interact** and click **Enable sound**. In a normal browser the same button
allows playback without blocking the video. `?muted=1` starts the receiver muted;
this does not mute the phone for other receivers.

Do not monitor the stream on speakers close to the phone microphone: use headphones
to avoid feedback. Avoid mixing the phone and computer microphones unintentionally.
WebRTC provides audio/video synchronization; verify lip sync in an OBS recording.
A separate PC microphone does not share the phone capture clock and may still need
an OBS Sync Offset. Network/receiver delay is not eliminated by adding audio.

## go2rtc

```yaml
streams:
  H6:
    - webrtc:http://PHONE_IP:8080/whep
```

Open `http://localhost:1984/stream.html?src=H6&mode=webrtc`. Enable phone audio before
connecting. The source should advertise H.264 and Opus; changing audio availability
requires a new upstream session. Prefer WebRTC playback for this pair of codecs;
support in other output/container modes depends on the receiver.

## Implementation

- Android RECORD_AUDIO permission requested only when the user enables audio.
- Camera/microphone foreground-service types promoted from the visible activity,
  before enabling capture. No background process-death restart.
- Native WebRTC JavaAudioDeviceModule, mono microphone input, up to 64 kb/s audio.
- Audio and video share the `remotecam` stream identity for receiver synchronization.
- WS and WHEP accept one receive-only video and at most one receive-only/inactive
  audio section. Incoming microphone/camera uploads and data channels are rejected.
- Legacy video-only offers remain supported. Capture errors are shown in the UI.

## Validation checklist

Check permission grant/denial, default video-only behavior, H.264+Opus through WS
and WHEP/go2rtc, microphone energy, silent mute with continuous video, audio disable,
multiple receivers, receiver close, JPEG switch, stop, preference restore and
background/return. Unit tests validate SDP restrictions; they do not replace device
tests for actual capture, audio quality or lip sync.

References: [Android microphone foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone),
[WebRTC audio](https://www.rfc-editor.org/info/rfc7874/).
