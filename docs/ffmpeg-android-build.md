<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Android FFmpeg build

This repository builds FFmpeg and its codec dependencies from pinned source and links them into `:ffmpeg-bridge` through a small in-process JNI library. It does not package or run a standalone `ffmpeg` executable. iOS will use the same source pins from `native/ffmpeg/versions.env` with a separate macOS/Apple-platform build recipe; Android binaries and flags are not reused on iOS.

## What is built

- **ABI:** `arm64-v8a`; **minimum Android API:** 26.
- **NDK:** pinned to `29.0.14206865`; its official Linux archive is checked by SHA-1 before extraction.
- **FFmpeg:** static libraries, GPL enabled; programs, network protocols, devices, autodetection, shared FFmpeg libraries, and docs are disabled.
- **PNG support:** Android system zlib is enabled explicitly because FFmpeg autodetection is disabled; this is required for PNG encoding.
- **Encoders:** FFmpeg native AAC and PNG, `libmp3lame`, `libx264`, `libkvazaar`; Android MediaCodec integration is enabled. Runtime hardware encoder availability varies by device and is reported by the bridge.
- **Formats:** FFmpeg's built-in format/codec/filter set is retained for broad local media input support. No extra demuxer/muxer allowlist is applied yet.
- **Native packaging:** FFmpeg and codec archives are linked into `libtransversa_ffmpeg.so`; 16 KB maximum ELF page alignment is requested for the JNI library. NDK 29 also supports current Android page-size requirements.

The Kotlin API exposes native FFmpeg version, build configuration, encoder inventory, and in-process local-file conversions: PNG still images, 192 kb/s MP3 audio, and MP4/H.264/AAC video (including AAC when an audio track is present). Each output is written to a temporary file, checked for a non-empty expected media stream, then committed without replacing an existing destination. The Android emulator smoke check passed for JPEG-to-PNG, WAV-to-MP3, and video-to-H.264/AAC MP4. Android URI staging and destination handling remain outside the native bridge. The current video path uses software `libx264`; automatic MediaCodec selection/fallback, progress callbacks, cancellation, end-to-end app-flow coverage, and physical-device smoke tests are still outstanding.

**Encoder and license choice:** Transversa retains its GPL-3.0-or-later project license, which is compatible with the AndroidX dependencies' Apache-2.0 terms. The Android FFmpeg build enables GPL components for `libx264` and uses BSD-3-Clause Kvazaar for software HEVC; Kvazaar replaces GPL-2-only x265. This avoids the x265/GPLv3 incompatibility in the existing combined configuration. It does not clear codec patents or remove GPL source, notice, and redistribution duties. Review all exact shipped components before release. The FFmpeg `libkvazaar` wrapper supports target bitrate and Kvazaar-specific parameters; do not send x264 CRF or preset flags to it.

## Linux build prerequisites

The Linux build script is `scripts/build-ffmpeg-android.sh`. It is used directly on Linux and by the Windows wrapper under WSL2. Before running it, install the Linux packages below and use CMake 3.25 or newer:

```bash
sudo apt update
sudo apt install -y build-essential curl git make nasm ninja-build pkg-config python3 unzip xz-utils yasm
```

The GitHub Actions Android workflow runs on Ubuntu and installs these packages before invoking Gradle, which in turn runs the same Linux FFmpeg build script. The script checks for its required commands and fails with the missing package name when a prerequisite is absent.

## Windows + WSL2 build

Install/start a WSL2 Linux distribution and install the packages from [Linux build prerequisites](#linux-build-prerequisites) once.

Install CMake **3.25 or newer** in WSL and ensure that version is on `PATH`. The Ubuntu 20.04 package, for example, is too old for the pinned Kvazaar CMake project; Android Studio's Windows CMake installation cannot configure Linux/NDK build tools in WSL. For Ubuntu's system Python, one supported user-local installation is `python3 -m pip install --user cmake==3.25.2`; add `$HOME/.local/bin` to the login `PATH` if needed. The build script checks the minimum and prepends this user-local tools directory when it runs.

Open the repository in Android Studio and run an Android build, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The bridge module's `preBuild` task invokes `scripts/build-ffmpeg-android.ps1` on Windows; that runs `scripts/build-ffmpeg-android.sh` under the default WSL distribution. The first build downloads the approximately 784 MB pinned Linux NDK archive plus FFmpeg/codec sources and builds the static libraries. Allow free disk space for the downloads, extracted sources, build trees, and outputs. WSL downloads, sources, the NDK, and compilation intermediates live in `$HOME/.cache/transversa/ffmpeg-android` on the Linux filesystem for faster native builds; set `TRANSVERSA_WSL_BUILD_CACHE` to change that location. Previously cached repository inputs under the ignored `ffmpeg-bridge/build/native/` directory are copied into this WSL cache without removing the originals. Final install libraries and license files stay under that ignored repository directory; generated binaries and cache files are not committed.

The native build can also be invoked directly with `:ffmpeg-bridge:buildFfmpegAndroid`. Set `TRANSVERSA_BUILD_JOBS` in WSL to limit parallel compilation if needed. On Linux, Gradle invokes the same shell script directly.

## Pins and output

`native/ffmpeg/versions.env` is the shared source-pin manifest for the Android and future iOS recipes. Archive checksums are verified before extraction; x264 and Kvazaar are checked out at fixed commits. Updating a pin requires checking FFmpeg compatibility, licenses, configure options, checksum, Android build, and encoder inventory together. Never use moving tags or branches for a release build.

The install tree is `ffmpeg-bridge/build/native/install/arm64-v8a/`. `THIRD_PARTY_LICENSES.md` records the selected versions and terms; upstream license files are copied beside the generated native outputs for each build. A release must also publish complete corresponding source, exact build scripts/patches/toolchain versions, and required notices under the GPL policy documented in `docs/01-windows-development-and-publishing.md`.

## Later iOS work

Use these shared FFmpeg, x264, Kvazaar, and LAME pins as a starting point, after revalidating that each release builds for the selected Apple deployment targets. Add a distinct macOS script and iOS JNI/Objective-C/Swift bridge, target/device capability reporting, and license/distribution review. iOS uses VideoToolbox rather than Android MediaCodec and requires macOS/Xcode; no iOS artifacts are produced by this Android task.

## References

- [FFmpeg download and source releases](https://ffmpeg.org/download.html)
- [Android NDK downloads](https://developer.android.com/ndk/downloads)
- [Kvazaar releases and source](https://github.com/ultravideo/kvazaar/releases)
- [LAME source releases](https://lame.sourceforge.io/download.php)
