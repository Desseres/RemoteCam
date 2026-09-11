# RemoteCam

**Your Android camera on your computer. Free, without ads or paid feature unlocks. Open source under the MIT license.**

RemoteCam streams your phone's camera over your local network so you can use it
in OBS or another compatible viewer. This fork brings the original application
up to date with modern Android SDKs, build tools and libraries while keeping its
simple purpose and free, ad-free approach.

In a world full of ads, subscriptions and paywalls, we want this project to
remain freely available to everyone. Download the source, build it, use it and
make it your own. Contributions and improvements are welcome.

## Thanks to the original author

Thank you to **Thomas SIMON ([Ruddle](https://github.com/Ruddle))**, the author of
the [original RemoteCam](https://github.com/Ruddle/RemoteCam), for the application,
the inspiration and the decision to share it as free, open-source software.
This project builds on that work and carries its spirit forward with an updated
Android implementation.

## Updated for modern Android

The current development version is **0.1.0**:

- **Android 17 / API 37** compile and target SDK, with Android 9 / API 28 as the minimum.
- Updated build tools: **Android Gradle Plugin 9.4.0**, **Gradle 9.7.1** and Java 17 bytecode.
- Updated AndroidX libraries, Kotlin coroutines and **Ktor 3.5.2**.
- Camera foreground service and permission handling for modern Android versions.
- Camera and resolution selection, aspect-ratio labels and adjustable JPEG quality.
- Saved camera, resolution, quality, Preview and Stream settings between launches.
- A browser receiver that skips stale frames to limit accumulated delay.
- Bandwidth in **Mb/s**, receiver feedback colors and a bandwidth planning table.

Tested on a physical **Android 16** phone. Android 17 is the build target;
runtime behavior on Android 17 and other devices still needs testing.
See the [modernization and validation report](docs/modernization.md) for details.

## Build and test

Get the source:

```sh
git clone https://github.com/Desseres/RemoteCam.git
cd RemoteCam
```

Install JDK 17+ and Android SDK Platform 37.0; set `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, especially with non-ASCII characters in the account/project path:

```powershell
.\tools\build-windows.ps1
```

The helper creates checked directory junctions under `C:\Temp\RemoteCamBuild`
to avoid Java socket and Gradle worker encoding problems. It changes no global
settings and does not move the repository. Use `-BuildRoot` to choose another
ASCII path if that directory is already used by another checkout.

Build and install your own development version using Android Studio or Android
SDK tools. Release builds require your own signing configuration.

## Use with OBS

Open RemoteCam, grant camera access (and local network access on Android 17),
then enable **Stream**. Copy the displayed `http://PHONE_IP:8080/view`
address into an OBS **Browser Source**, set width/height to the camera resolution
and custom FPS to 30. Close the previous camera source to avoid duplicate traffic.
The compatible MJPEG endpoint remains `http://PHONE_IP:8080/cam.mjpeg`;
for OBS Media Source, start with Network Buffering at 0 MB.
**Preview** is independent of streaming;
**Stop** in the app or notification releases the camera and server.
Camera, resolution, JPEG quality, Preview and Stream are saved on the phone and
restored when you next open the app, including after an app update. Uninstalling
the app or clearing its storage deletes these settings. If the saved camera or
resolution is no longer available, RemoteCam selects a supported fallback.

Validate the live stream without saving images (Python 3.9+):

```sh
python tools/check_stream.py http://PHONE_IP:8080/cam.mjpeg --frames 60 --clients 2
```

Streaming uses unauthenticated, unencrypted HTTP/WebSocket connections; use a trusted local network.

### Latency and bandwidth

The app currently streams **JPEG frames**, including through the `/view` browser
receiver. H.264, H.265 and WebRTC streaming are not implemented in this version.

The displayed camera rate uses decimal **Mb/s** (megabits per second). The old
counter was **kB/s** (kilobytes per second): 16,000 kB/s equals 128 Mb/s.
Camera production and total receiver output are displayed separately.
**Latency & bandwidth** opens a table calculated from the current resolution,
JPEG quality and measured average frame size. The suggested 2× capacity is a
planning allowance for variation, not a measured LAN speed or a guarantee.

`/view` decodes the original JPEG bytes into a canvas and acknowledges each draw.
The server permits one frame in flight and retains only the latest waiting frame
per receiver. A slow receiver skips stale frames without changing JPEG quality or
resolution. Green/orange/red feedback uses recent acknowledgement cycles and
skipped frames; gray means insufficient feedback, including legacy MJPEG clients.
This does not measure total camera-to-OBS latency. Legacy MJPEG clients may still
buffer internally; a successful socket write cannot confirm playback.

A larger buffer increases delay and cannot fix a sustained bandwidth shortage.
Prefer Ethernet for the PC and a strong 5/6 GHz Wi-Fi connection for the phone.
For a separate PC microphone, record a clap test in OBS. If sound consistently
precedes the image, apply that measured positive Sync Offset to the microphone
in Advanced Audio Properties. Fix variable video delay before setting an offset.

## License

RemoteCam remains open source under the **[MIT License](LICENSE)**. The original
author's copyright and license notice are preserved.

You are welcome to download, use, modify and share the project under those terms.
This fork remains free, without ads, subscriptions or paid feature unlocks.

The original author also asked that the app not be uploaded to the Play Store.
Please respect that request.
