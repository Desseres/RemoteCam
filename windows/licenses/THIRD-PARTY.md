# Third-party components

RemoteCam code uses the repository's MIT license. These components retain their
own licenses; they are not relicensed by RemoteCam.

- **Microsoft Windows-Camera**, MIT, revision
  `d348797d2f5632ff3cb250960638091c4a1a75fb`:
  <https://github.com/microsoft/Windows-Camera/tree/d348797d2f5632ff3cb250960638091c4a1a75fb/Samples/VirtualCamera/VirtualCameraMediaSource>.
  `SimpleMediaSource`, `SimpleMediaStream`, activation forwarding, `winrtCommon`
  and `pch.h` are adapted from this source. RemoteCam removes physical-camera
  wrapping and example controls, changes the CLSID and output size, receives
  real frames through a pipe, and adds shutdown, validation and pacing changes.
  See `Microsoft-Windows-Camera.txt`.
- **Windows Implementation Library** 1.0.240803.1, MIT, header-only;
  <https://github.com/microsoft/wil>. See `WIL.txt`.
- **go2rtc** v1.9.14 (`b5948cfb25404cc5cb37b166ecaa2dca20b11d4b`), MIT,
  <https://github.com/AlexxIT/go2rtc/tree/v1.9.14>, built with the repository's
  `patches/go2rtc-1.9.14-nack.patch`. See `go2rtc.txt`. The Windows package runs it
  as a separate local process, not as an installed service.
- **FFmpeg** n8.1, BtbN win64 LGPL static executable (separate process),
  <https://github.com/BtbN/FFmpeg-Builds/releases/tag/autobuild-2026-09-14-13-17>.
  See `FFmpeg.txt`, `ffmpeg-origin.json` and `ffmpeg.exe -buildconf`.
  Build recipes and source acquisition scripts: <https://github.com/BtbN/FFmpeg-Builds>;
  FFmpeg source: <https://git.ffmpeg.org/ffmpeg.git>.
  Before publishing Windows binaries, preserve the exact corresponding source
  and dependency build materials required by the distributed FFmpeg build.
- **Microsoft.Net.Compilers.Toolset** 4.8.0 is a build-time dependency only,
  <https://www.nuget.org/packages/Microsoft.Net.Compilers.Toolset/4.8.0>.
  It is not shipped with the desktop application.
