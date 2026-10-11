# Transversa — Windows Development, Debugging and Publishing

## Open-source licensing policy (mandatory)

**Project license: GNU GPL version 3 or later (`GPL-3.0-or-later`).** All project-owned application code, JNI integration code, and build scripts should be released under GPL-3.0-or-later (subject to clearly documented third-party notices). AndroidX dependencies are Apache-2.0; GPLv3 is compatible with Apache-2.0. The bundled FFmpeg build enables GPL components through `libx264` (GPL-2.0-or-later) and uses Kvazaar (BSD-3-Clause) for software HEVC. Kvazaar replaces x265, whose GPL-2-only terms are unsuitable for this combined GPLv3/Apache-2.0 configuration. Check exact pinned sources and notices before each release. Do not enable `--enable-nonfree` or include incompatible nonfree libraries.

**Required video encoders:** `h264_mediacodec` and `hevc_mediacodec` where supported by the device and build, plus `libx264` and `libkvazaar` as software encoders. Default video output remains **MP4/H.264/AAC**; H.265/HEVC is a selectable video option, not the default. In automatic mode prefer a validated hardware encoder and fall back to `libx264` for H.264 or `libkvazaar` for HEVC if the device or hardware conversion fails. Respect user-selected software-only mode. Use bitrate/codec-specific controls for MediaCodec and x264 CRF/preset controls; configure Kvazaar with bitrate or its documented encoder parameters. Test fallback on physical devices.

**Release and compliance requirements:**

1. Ship `LICENSE` with the full GPLv3 text and `SPDX-License-Identifier: GPL-3.0-or-later` for project-owned code, plus `THIRD_PARTY_LICENSES.md`, relevant FFmpeg/x264/Kvazaar licenses, copyright notices, and an in-app About/Licenses screen.
2. Publish complete corresponding source for **the exact distributed app and native binaries**, including pinned FFmpeg/x264/Kvazaar sources or compliant source delivery, patches, JNI code, Gradle/CMake/configure flags, NDK/toolchain versions, and installation/build instructions. Do not rely solely on upstream links or a generic source repository.
3. Review every third-party dependency (including Android libraries and any binary SDKs) for GPLv3 compatibility. Do not incorporate proprietary or incompatible components into the combined GPL work without a documented legal basis. Android system APIs may be used under applicable system-library exceptions; evaluate distribution-specific details.
4. Archive release artifacts, license inventory, source revision, native binary hashes, FFmpeg build configuration and encoder inventory. Verify `--enable-gpl` and `--enable-libx264` / `--enable-libkvazaar` where appropriate, and verify `--enable-nonfree` is absent.
5. Check Google Play terms and current policies for GPL distribution, and ensure recipients can obtain corresponding source and exercise GPL rights. Review codec patent issues separately from copyright licensing.
6. Reference authoritative guidance: https://ffmpeg.org/legal.html , https://www.gnu.org/licenses/gpl-3.0.html , and https://www.gnu.org/licenses/gpl-faq.html . Obtain a distribution-focused license review before release.

**Licensing nuance:** GPLv3-or-later is the project license, not a claim that FFmpeg itself is automatically relicensed as GPLv3-or-later. FFmpeg and x264 retain their applicable GPL notices; Kvazaar retains its BSD-3-Clause notice. The combined Android dependency selection is intended to be compatible with GPLv3 and Apache-2.0 AndroidX libraries. Review the exact shipped combination and distribution obligations before release.

## 1. Purpose

This guide describes how to build, run, debug, test, sign, and publish the Transversa Android application on a Windows 11 workstation. The companion `02-agent-implementation-plan.md` is the authoritative product and engineering specification.

**Application:** Kotlin, Jetpack Compose, Android Media3, Room, DataStore, and a native FFmpeg integration with Android MediaCodec support. The application works offline. Saving or sharing initiates conversion; there is no separate Convert button.

> Versions and Google Play policies change. Install the latest stable compatible Android Studio/SDK/NDK/Gradle versions, pin the exact tested versions in the repository, and verify current Play Console requirements immediately before release. Avoid hardcoding an API-level target here that may be obsolete.

## 2. Prerequisites

- Windows 11 x64, preferably 16 GB RAM or more and 30–50 GB free disk space. Enable CPU virtualization for emulator use.
- Git for Windows, PowerShell 7 (recommended), Android Studio stable, and an Android device or emulator.
- Android Studio's bundled JDK (typically JDK 17 or the JDK required by the selected Android Gradle Plugin). Avoid competing `JAVA_HOME` values.
- Android SDK Platform for the selected compile SDK, Android SDK Build-Tools, Platform-Tools (`adb`), Emulator, CMake, and a pinned NDK (side-by-side).
- A Google Play developer account for publishing; access to Play Console and a private place to store signing keys.
- Optional: WSL2 Ubuntu for reproducible native FFmpeg cross-compilation. Native Windows development is fine; building FFmpeg itself is typically easier in Linux/WSL2.

## 3. Install and verify the Android toolchain

1. Install Git for Windows and Android Studio stable.
2. Start Android Studio and run its first-run setup wizard.
3. In **Settings → Languages & Frameworks → Android SDK**, install the selected Android SDK Platform, Build-Tools, Platform-Tools, Emulator, NDK (side-by-side), and CMake. Record exact versions in the repository.
4. Under **Device Manager**, create an emulator (prefer a recent Google APIs image; use an arm64 physical device for final native-library testing). Enable Windows Hypervisor Platform or the virtualization backend supported by your machine.
5. Open the project in Android Studio and allow Gradle sync. Use the Gradle wrapper checked into the repository; do not rely on a global Gradle install.
6. Configure the SDK path through Android Studio or an untracked `local.properties` containing `sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk` (Android Studio usually creates this automatically). Never commit machine-specific paths.

In PowerShell, from the repository root:

```powershell
.\gradlew.bat --version
.\gradlew.bat tasks
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

If `adb` is not on `PATH`, call it by its full path as shown. If the build fails, inspect the AGP/Gradle/JDK compatibility matrix and the NDK version pinned in Gradle.

## 4. FFmpeg and native-code setup

### 4.1 Integration strategy

The project should contain a dedicated `:ffmpeg-bridge` Android library module exposing a typed Kotlin interface to a native JNI adapter. The bridge loads the bundled FFmpeg libraries and runs conversions in-process; **do not assume a desktop `ffmpeg` executable can simply be packaged and invoked as a process on Android**. Prefer a maintained, auditable Android FFmpeg integration or a reproducible self-built FFmpeg + JNI runner. The old FFmpegKit distribution is retired; do not depend on unmaintained binaries without review.

The FFmpeg build must include the demuxers, muxers, codecs, filters, parsers, and protocols needed for the supported media types. Include `h264_mediacodec` where supported, the native `aac` encoder, PNG encoding, and a working MP3 encoder (`libmp3lame` if licensed/bundled). Bundle `libx264` and `libkvazaar` as mandatory software encoding fallbacks; keep MediaCodec hardware encoding where supported.

### 4.2 Reproducible native build in this repository

The repository contains a pinned Android build recipe in [`docs/ffmpeg-android-build.md`](ffmpeg-android-build.md). Android Gradle builds invoke it through `:ffmpeg-bridge:buildFfmpegAndroid` in WSL2; it cross-compiles `arm64-v8a` static libraries and links them into the JNI bridge. The Kotlin bridge currently exposes in-process local-path conversions for PNG images, 192 kb/s MP3 audio, and MP4/H.264/AAC video (AAC is included when the source has audio). Converted files are checked for a non-empty expected stream before their destination is committed. JPEG-to-PNG, WAV-to-MP3, and video-to-H.264/AAC MP4 have passed a smoke check on the Android emulator. The app layer remains responsible for staging `content://` inputs and running conversion off the UI thread. Progress/cancellation, device hardware encoder selection/fallback, end-to-end app-flow coverage, and physical-device testing are still required before these paths satisfy the full conversion specification.

The recipe is Android-only. Shared codec source pins are recorded for future iOS work, but iOS requires its own build, bridge, and license review on macOS.

### 4.3 Native build requirements and release checks

1. Install WSL2 and Ubuntu (`wsl --install -d Ubuntu`) if you plan to build FFmpeg yourself.
2. Install Ubuntu build dependencies (e.g., `git`, `make`, `ninja-build`, `cmake`, `pkg-config`, `nasm`, `yasm`, `python3`, `clang` as needed by the chosen recipe).
3. Use the **Windows-installed Android NDK** from WSL via `/mnt/c/...` or install the same pinned NDK inside WSL. Verify the NDK clang toolchain and host path handling.
4. Check out a pinned FFmpeg release/tag and any codec libraries at pinned revisions; record source URLs, checksums, build flags, and licenses.
5. Cross-compile for `arm64-v8a` first. Optionally add `x86_64` for emulator testing; add other ABIs only if supported and required.
6. Enable Android MediaCodec/JNI integration in the build, ensuring relevant headers and NDK libraries are available. Actual encoder availability must be checked at runtime on each device.
7. Build the JNI adapter with CMake and Gradle's `externalNativeBuild`, package `.so` libraries in the appropriate ABI directories, and verify all transitive `.so` dependencies are included.
8. Check Android's current native-library page-size requirements (including 16 KB compatibility), ABI support, and Play distribution rules. Validate on a compatible device or emulator.
9. Produce an attribution/license report and corresponding source/build scripts where the selected licenses require it.

**Critical distinction:** Android's MediaCodec APIs can be available even when the particular FFmpeg build does not expose MediaCodec **encoding**. Check both build capabilities and device hardware. Do not promise hardware encoding for every codec/device.

### 4.4 Verify the bundled engine

Provide an in-app developer diagnostics screen or instrumentation test that enumerates:

- FFmpeg build configuration/version and available encoders/decoders.
- `h264_mediacodec` encoder and decoder presence (where compiled).
- `aac`, `png`, `mp3`/`libmp3lame`, and software H.264 fallback availability.
- Android `MediaCodecList` codec capabilities, supported profiles, resolutions, frame rates, and color formats.
- A tiny hardware encode smoke test, because an advertised codec may still fail for a specific configuration.

Do not use `ffmpeg -encoders` as a desktop shell command unless the project explicitly ships an executable for diagnostics; query the embedded FFmpeg libraries through the bridge.

## 5. Running the app

### 5.1 Emulator

1. Start an Android emulator in Device Manager.
2. Confirm it is listed by `adb devices -l`.
3. Run the `app` configuration in Android Studio, or:

```powershell
.\gradlew.bat :app:installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.mediaconverter/.MainActivity
```

Replace the package and activity with the actual values. Emulators may lack representative hardware encoding; test real hardware before declaring acceleration working.

### 5.2 Physical Android device

1. Enable Developer Options and USB debugging.
2. Connect the phone via USB and accept its RSA debugging prompt.
3. Run `adb devices -l`; the device should show as `device`, not `unauthorized`.
4. Select it in Android Studio and click Run, or use `:app:installDebug`.
5. Exercise file selection from Files and Gallery, preview, Save, Share, cancel, and background conversion. Test different media sizes and codec combinations.

### 5.3 Useful Gradle commands

```powershell
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:bundleRelease
```

Run `connectedDebugAndroidTest` with an emulator/device attached. The release bundle requires signing configuration before it can be distributed through Play.

## 6. Debugging

### Kotlin / Compose

- Set breakpoints in ViewModels, repositories, intent handling, preset validation, and conversion coordination.
- Use Android Studio's Debug action, Logcat, Layout Inspector, and Compose tooling.
- Observe state changes, cancellation, lifecycle transitions, configuration changes, and process recreation.
- Run unit tests for preset migration, argument construction, and job-state transitions.

### FFmpeg / JNI

- Build native code with debug symbols for debug variants and attach Android Studio's native debugger to the process.
- Forward FFmpeg `av_log` output into Android Logcat with job IDs, while redacting potentially sensitive filenames/paths.
- Report structured progress via FFmpeg callbacks or a supported progress interface; don't parse arbitrary human-readable stderr as the only progress source.
- Capture FFmpeg return codes, codec failures, native exceptions, and process-death recovery states.
- Test hardware encoder initialization, fallback to software, and cancellation under load.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -c
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -v time
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell dumpsys media.codec
```

`dumpsys media.codec` output varies by Android release and device; rely on application-level capability tests for definitive support.

### File and intent debugging

- Use Storage Access Framework (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`) and `content://` URIs; do not request broad storage permissions unnecessarily.
- Verify the app can read non-seekable document-provider streams. Stage inputs to private cache if FFmpeg needs seeking.
- Test receiving `ACTION_SEND` and `ACTION_VIEW` from other apps; ensure URI grants are propagated and never assume raw filesystem paths.
- Test sharing via `FileProvider`, correct MIME type, `ClipData` where appropriate, and read-grant flags.
- Verify cancellation removes partial outputs and leaves no corrupted saved document.

## 7. Test matrix before release

| Area | Minimum checks |
|---|---|
| Input | MP4/MOV/MKV, MP3/WAV/FLAC, JPEG/PNG/WebP; malformed and unsupported files |
| Video | H.264/AAC MP4 output, source without audio, variable frame rate, rotation metadata, hardware/software fallback |
| Image | PNG output, alpha preservation, EXIF orientation, large images |
| Audio | MP3 output, mono/stereo, variable source sample rates |
| UX | Preview, edit settings, save/reset presets, Save and Share start conversion |
| Lifecycle | Rotate, background, lock screen, kill/relaunch, cancellation |
| Storage | Low storage, no permissions, cloud document provider, URI revoked, large files |
| Devices | Multiple Android versions, at least one physical arm64 device, different hardware codecs |
| Release | Signed AAB, ABI libraries, page-size compatibility, no debug logs/secrets |

For Play distribution, validate the AAB using Android Studio's **Analyze APK/App Bundle**, Play Console pre-launch reports, and internal testing tracks.

## 8. Prepare the application for Google Play

1. Choose a permanent application ID (for example, `com.yourcompany.mediaconverter`); it is difficult or impossible to change after publication.
2. Set app name, icon, adaptive icon, version code/version name, minimum and target SDK, supported ABIs, and app theme.
3. Check the current Play target API requirement and any special requirements for foreground services, Android 13+ notifications, and media-related permissions. Declare only the permissions actually needed.
4. Review GPLv3 compatibility and obligations for the complete distribution, including FFmpeg, libx264, Kvazaar and other libraries. Fulfill source, notice and redistribution obligations and obtain a release licensing review.
5. Prepare a privacy policy that accurately describes offline processing, local file access, temporary cache retention, and whether telemetry/crash reporting is enabled.
6. Prepare screenshots, feature graphic, short/full descriptions, category, support contact, content rating, target audience, Data safety answers, and required policy declarations.
7. Verify that the app does not upload user media unless explicitly added and disclosed.
8. Implement user-accessible cache cleanup and a defensible retention policy for shareable temporary files.

## 9. Signing and release builds

1. In Android Studio, use **Build → Generate Signed Bundle / APK** to create an Android App Bundle (`.aab`).
2. Create an upload keystore and strong passwords; store them in an encrypted password manager/secure backup, never in Git.
3. Enable Play App Signing (recommended/required depending on distribution route) and securely register the upload key.
4. Keep signing configuration in environment variables or local untracked properties, not committed source.
5. Build and validate the release AAB, including native `.so` packaging, crash-free startup, and correct version codes.
6. Upload first to an **internal testing** track. Test installation, incoming share intents, conversion, save/share, and upgrades.
7. Complete Play Console store listing, privacy/data safety, content rating, app access, and policy forms.
8. Use closed testing if required for the developer account, then request production access if applicable. Requirements differ by account type and enrollment history.
9. Roll out production gradually, monitor Android vitals/crashes, and maintain release notes and rollback/hotfix procedures.

## 10. Suggested repository structure

```text
android-media-converter/
  README.md
  docs/
    01-windows-development-and-publishing.md
    02-agent-implementation-plan.md
    THIRD_PARTY_LICENSES.md
  app/
    src/main/
    src/test/
    src/androidTest/
  core/model/
  core/media/
  core/presets/
  core/conversion/
  ffmpeg-bridge/
    src/main/cpp/
    src/main/java/
  native-build/
    README.md
    build-ffmpeg.sh
    patches/
  gradle/
  gradlew
  gradlew.bat
  settings.gradle.kts
  build.gradle.kts
```

## 11. Release checklist

- [ ] All unit, integration, UI, and device tests pass.
- [ ] Software H.264 fallback works if hardware encoding is unavailable.
- [ ] Video, image, and audio defaults produce valid playable outputs.
- [ ] Save opens destination picker before conversion; Share converts then opens chooser.
- [ ] Conversion runs in a compliant foreground service when needed and can be cancelled.
- [ ] Presets persist and factory defaults can be restored.
- [ ] Temporary files are cleaned without breaking URI shares.
- [ ] Native libraries support selected ABIs and current Android page-size requirements.
- [ ] License obligations and attribution are satisfied.
- [ ] Privacy policy and Play Console declarations match actual behavior.
- [ ] Signed AAB installs and upgrades successfully through internal testing.


## Cross-platform roadmap: Kotlin Multiplatform and iOS

**Product name:** Transversa — *The ultimate converter*. This document remains the Windows/Android development guide. The project is now designed for an eventual iOS release, with a Mac available for iOS development. See `03-macos-ios-development-and-publishing.md`, `04-ios-agent-implementation-plan.md`, and `05-cross-platform-design-and-architecture.md`.

**Architecture decision:** Use Kotlin Multiplatform (KMP) for platform-independent domain models, preset schemas, validation, conversion-job state and orchestration contracts. Use **Jetpack Compose + Material 3 on Android** and **SwiftUI following Apple Human Interface Guidelines on iOS**. Do **not** assume Compose Multiplatform UI is the desired iOS presentation layer. Maintain shared brand tokens and product workflows, not identical controls.

**Migration rule:** Existing Android-only code remains valid during an incremental KMP extraction. Move only portable pure Kotlin into `shared/commonMain`, use `androidMain` and `iosMain` for platform-specific adapters, and keep Android `Activity`, services, Room integration and SAF in Android-specific modules. Use Kotlin/Native framework exports for Swift interop, with a thin Swift adapter to expose observable state to SwiftUI. The iOS application requires Xcode/macOS to build, sign and distribute; Windows can continue to build Android and develop/test common Kotlin logic.

**Platform-specific media:** Android uses MediaCodec and Android storage/share APIs; iOS uses VideoToolbox where supported, FFmpeg built for iOS, document import/export, QuickLook/AVKit preview, and `ShareLink`/`UIActivityViewController`. Hardware encoder selection and progress/cancel must be implemented and tested separately on each platform. Any GPL-enabled FFmpeg configuration including libx264 remains a compliance consideration on iOS; separately validate Kvazaar's iOS build and licensing, and consult `04-ios-agent-implementation-plan.md` before publishing, especially regarding GPL distribution, code signing, App Store terms and codec patents.

**Android acceptance additions:** Common Kotlin code must compile and pass common tests without Android dependencies. Android UI must preserve Material 3 conventions and continue to pass existing conversion and sharing tests after extraction. Never block the Android release on completion of iOS work unless shared-code changes break Android behavior.
