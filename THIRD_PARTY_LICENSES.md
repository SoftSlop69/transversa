<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Third-party notices

This inventory covers Android project tooling, UI dependencies, and the currently pinned native FFmpeg build inputs. Recheck exact transitive versions, notices, and redistribution terms before each release.

## Build tooling

| Component | Version | License | Use |
|---|---:|---|---|
| Gradle Wrapper scripts and JAR | 9.4.1 | Apache License 2.0 | Downloads and launches the pinned Gradle distribution. |
| Android Gradle Plugin | 9.2.1 | Apache License 2.0 | Android build tooling; not packaged in the app. |
| Kotlin Gradle plugin and Compose compiler plugin | 2.3.10 | Apache License 2.0 | Kotlin build tooling; not packaged in the app. |

## Android app dependencies

| Component | Version | License | Use |
|---|---:|---|---|
| Jetpack Compose BOM | 2026.09.00 | Apache License 2.0 | Pins a compatible set of Compose UI artifacts. |
| Material 3 for Compose | BOM-managed | Apache License 2.0 | Android UI components. |
| Activity Compose | 1.13.0 | Apache License 2.0 | Android activity and Compose integration. |
| Navigation Compose | 2.10.2 | Apache License 2.0 | Android Compose navigation. |
| AndroidX Core SplashScreen | 1.0.1 | Apache License 2.0 | Android 12+ system launch splash support. |
| AndroidX Media3 ExoPlayer and UI | 1.11.1 | Apache License 2.0 | Local video/audio preview playback and controls. |
| Coil Compose | 3.6.3 | Apache License 2.0 | Local image preview with constraint-aware decoding. |
| Google Play Billing Library | 9.1.0 | Android SDK License Agreement | Google Play donation product lookup and purchase flow. |
| Roboto Flex | Variable font from Google Fonts | SIL Open Font License 1.1 | High weight Transversa splash screen title; bundled at `app/src/main/res/font/roboto_flex.ttf`. License text: `licenses/RobotoFlex-OFL.txt`. |

## Native Android media engine

| Component | Version / pin | License | Use |
|---|---:|---|---|
| FFmpeg | 9.0.2 | GPL-2.0-or-later for this GPL-enabled configuration; verify upstream notices for the exact build | Demuxing, decoding, filtering, muxing, AAC/PNG, and Android MediaCodec integration. Built statically into the JNI library. |
| x264 | `b35605ace3ddf7c1a5d67a2eb553f034aef41d55` | GPL-2.0-or-later | Software H.264 encoder fallback. |
| Kvazaar | 2.3.2, commit `6040962bed5cc68c5ad01234c38c08b8b2822068` | BSD-3-Clause | Software HEVC encoder through FFmpeg's `libkvazaar` integration. |
| LAME | 4.0 | LGPL-2.0-or-later (confirm the exact upstream notice) | MP3 encoder. |
| Android NDK | 29.0.14206865 | Android SDK license | Cross-compilation toolchain; not packaged as an app dependency. |

FFmpeg is configured with `--enable-gpl` and GPL-licensed x264; Kvazaar is BSD-3-Clause, and `--enable-nonfree` is not used. GPL-3.0-or-later is retained for project-owned code and the combined application configuration. Exact source pins, build switches, copied upstream license texts, and installation instructions are maintained in `native/ffmpeg/versions.env`, `scripts/build-ffmpeg-android.sh`, and `docs/ffmpeg-android-build.md`. The build fetches pinned source and verifies archive checksums before compiling where available. This inventory is not legal advice or a distribution clearance; retain complete corresponding source and obtain the release review required by the project specifications.

Upstream project license files and transitive dependency notices remain authoritative. This file is not a release compliance review. In particular, the future iOS build and its Apple-platform distribution need a separate license and policy review.

