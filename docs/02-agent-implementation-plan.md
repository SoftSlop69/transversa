# Transversa — Detailed Implementation Plan for Coding Agent

## 0. Agent instructions and definition of done

Implement a native, offline-first Transversa media conversion app in Kotlin/Jetpack Compose using an embedded FFmpeg engine. Follow this specification rather than inventing product behavior. Use maintained dependencies, pin versions, write tests, and document decisions/limitations. Avoid shipping placeholder conversion functionality. If an FFmpeg build or license constraint blocks a codec, surface the limitation clearly and propose a compliant alternative rather than silently changing the required output.

**Definition of done:** On a supported physical Android device, a user can receive or select a video, image, or audio file; preview it; optionally choose/edit/save a preset; tap **Save** or **Share** (no Convert button); watch conversion progress; and receive a valid MP4/H.264/AAC, PNG, or MP3 file respectively. Conversions work offline, cancel safely, and use hardware acceleration when compatible with software fallback through bundled libx264/libx265 when compatible.

## Open-source licensing policy (mandatory)

**Project license: GNU GPL version 3 or later (`GPL-3.0-or-later`).** All project-owned application code, JNI integration code, and build scripts should be released under GPL-3.0-or-later (subject to any clearly documented third-party exceptions). Bundled FFmpeg is built with `--enable-gpl` and includes both **libx264** and **libx265**; this is a GPL build, **not** an LGPL-only build. Check the pinned FFmpeg, x264, and x265 versions and their actual license notices before each release. Do not enable `--enable-nonfree` or include incompatible nonfree libraries.

**Required video encoders:** `h264_mediacodec` and `hevc_mediacodec` where supported by the device and build, plus `libx264` and `libx265` as software encoders. Default video output remains **MP4/H.264/AAC**; H.265/HEVC is a selectable video option, not the default. In automatic mode prefer a validated hardware encoder and fall back to `libx264` for H.264 or `libx265` for HEVC if the device or hardware conversion fails. Respect user-selected software-only mode. Hardware and software encoders have different quality options: use bitrate/codec-specific controls for MediaCodec, CRF/preset for x264/x265. Test fallback on physical devices.

**Release and compliance requirements:**

1. Ship `LICENSE` with the full GPLv3 text and `SPDX-License-Identifier: GPL-3.0-or-later` for project-owned code, plus `THIRD_PARTY_LICENSES.md`, relevant FFmpeg/x264/x265 licenses, copyright notices, and an in-app About/Licenses screen.
2. Publish complete corresponding source for **the exact distributed app and native binaries**, including pinned FFmpeg/x264/x265 sources or compliant source delivery, patches, JNI code, Gradle/CMake/configure flags, NDK/toolchain versions, and installation/build instructions. Do not rely solely on upstream links or a generic source repository.
3. Review every third-party dependency (including Android libraries and any binary SDKs) for GPLv3 compatibility. Do not incorporate proprietary or incompatible components into the combined GPL work without a documented legal basis. Android system APIs may be used under applicable system-library exceptions; evaluate distribution-specific details.
4. Archive release artifacts, license inventory, source revision, native binary hashes, FFmpeg build configuration and encoder inventory. Verify `--enable-gpl` and `--enable-libx264` / `--enable-libx265` where appropriate, and verify `--enable-nonfree` is absent.
5. Check Google Play terms and current policies for GPL distribution, and ensure recipients can obtain corresponding source and exercise GPL rights. Review codec patent issues separately from copyright licensing.
6. Reference authoritative guidance: https://ffmpeg.org/legal.html , https://www.gnu.org/licenses/gpl-3.0.html , and https://www.gnu.org/licenses/gpl-faq.html . Obtain a distribution-focused license review before release.

**Licensing nuance:** GPLv3-or-later is the recommended uniform project license, not a claim that FFmpeg itself is automatically relicensed as GPLv3-or-later. The bundled FFmpeg/x264/x265 components retain their own applicable GPL notices and versions. Confirm the exact combination is compatible.

## 1. Scope

### Required MVP

- Single input file and one active conversion at a time.
- Android system file picker and incoming `ACTION_SEND` / `ACTION_VIEW` intents.
- Media-type detection by probing content, not trusting filename extensions alone.
- Preview: Media3 video/audio playback and image display; show filename, dimensions, duration, size where available.
- Defaults: video `MP4 + H.264 + AAC`; image `PNG`; audio `MP3`.
- Settings screen per media type; choose output format/codec and applicable quality, resize, bitrate, resolution, frame rate, audio channels, etc., subject to engine support.
- Built-in **Default** preset per media type; reset to factory defaults. User presets support create, load, rename, edit, duplicate, delete.
- Automatic hardware H.264 encoding through FFmpeg MediaCodec where supported; platform software fallback where available; otherwise clear unsupported-device error. Do not equate hardware decode availability with hardware encode availability.
- Save: launch system `ACTION_CREATE_DOCUMENT` first, then convert and commit to selected URI.
- Share: convert to private cache, then open native Android share sheet using `FileProvider` and MIME-aware `ACTION_SEND`.
- After successful conversion, make both Save and Share available without re-encoding if the output still exists.
- Foreground service for long-running user-initiated conversion, progress, cancellation, cleanup, and understandable error reporting.
- Unit/instrumentation tests and reproducible native build instructions.

### Explicitly out of scope for MVP

Batch conversion, trimming/editing, cloud processing, cloud storage integrations, conversion history UI, account/login, advertising, watermarking, media library indexing, remote APIs, and conversion queues. Do not add a separate Convert action.

## 2. Nonfunctional requirements

- **Offline/private:** no network access required for normal operation; no user media uploaded.
- **Robust:** no full-file buffering in Java/Kotlin memory; stage seek-dependent inputs to private disk.
- **Responsive:** all probes and conversions off main thread; UI remains interactive.
- **Correct:** never claim conversion success until FFmpeg exits successfully and output is validated.
- **Lifecycle-safe:** conversion survives Activity recreation/backgrounding; foreground service maintains user-visible notification as required by Android version.
- **Recoverable:** after process death, mark interrupted jobs as interrupted and offer retry; do not claim native work resumed automatically.
- **Accessible:** Material 3, screen-reader labels, contrast, large text, accessible progress.
- **Internationalizable:** string resources and locale-safe numeric formatting.
- **Licensing:** GPL-3.0-or-later for project-owned code; GPL-enabled FFmpeg with libx264 and libx265 required; prohibit --enable-nonfree. Publish corresponding sources, licenses, and build flags.

## 3. Technical architecture

### Modules

```text
:app                 Compose UI, navigation, Activity, intents, foreground service
:core:model          media descriptors, settings, job/result models
:core:media          SAF URI access, probing, thumbnails/previews
:core:presets        Room persistence, factory defaults, validation/migration
:core:conversion     orchestration, command planning, job state, output handling
:ffmpeg-bridge       JNI, embedded FFmpeg runtime, progress/cancel/capability APIs
```

Use MVVM with unidirectional UI state, Kotlin coroutines/Flow, repositories and constructor injection (Hilt or a simple DI composition root; choose one and document it). UI must not construct raw FFmpeg commands. Core conversion logic must not reference Compose.

### Component contracts (illustrative Kotlin)

```kotlin
interface MediaInspector {
    suspend fun inspect(input: Uri): MediaDescriptor
}

interface ConversionEngine {
    suspend fun capabilities(): EngineCapabilities
    suspend fun execute(
        plan: ConversionPlan,
        onProgress: (ConversionProgress) -> Unit
    ): ConversionResult
    suspend fun cancel(jobId: String)
}

interface ConversionPlanner {
    fun plan(
        input: MediaDescriptor,
        settings: ConversionSettings,
        capabilities: EngineCapabilities,
        destination: File
    ): ConversionPlan
}

interface PresetRepository {
    fun observe(mediaType: MediaType): Flow<List<Preset>>
    suspend fun save(preset: Preset)
    suspend fun delete(id: String)
    suspend fun restoreDefault(mediaType: MediaType)
}
```

Keep URI access, file staging, output commits, and share grants outside the native FFmpeg bridge.

## 4. Data models

Use a sealed model or tagged serializable DTOs, with explicit schema versions. Illustrative shape:

```kotlin
enum class MediaType { VIDEO, IMAGE, AUDIO }
enum class DestinationAction { SAVE, SHARE }
enum class AccelerationMode { AUTO, PREFER_HARDWARE, SOFTWARE_ONLY }

data class MediaDescriptor(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val type: MediaType,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val hasAudio: Boolean,
    val videoCodec: String?,
    val audioCodec: String?,
    val rotationDegrees: Int?
)

sealed interface ConversionSettings {
    val schemaVersion: Int
    val mediaType: MediaType
}

data class VideoSettings(
    override val schemaVersion: Int = 1,
    override val mediaType: MediaType = MediaType.VIDEO,
    val container: String = "mp4",
    val videoCodec: String = "h264",
    val audioCodec: String = "aac",
    val acceleration: AccelerationMode = AccelerationMode.AUTO,
    val softwareCrf: Int = 23,
    val hardwareBitrateKbps: Int? = null,
    val audioBitrateKbps: Int = 192,
    val resolution: ResolutionChoice = ResolutionChoice.Original,
    val frameRate: FrameRateChoice = FrameRateChoice.Original
) : ConversionSettings

data class ImageSettings(
    override val schemaVersion: Int = 1,
    override val mediaType: MediaType = MediaType.IMAGE,
    val format: String = "png",
    val resize: ResizeChoice = ResizeChoice.Original,
    val preserveAlpha: Boolean = true
) : ConversionSettings

data class AudioSettings(
    override val schemaVersion: Int = 1,
    override val mediaType: MediaType = MediaType.AUDIO,
    val format: String = "mp3",
    val bitrateKbps: Int = 192,
    val sampleRateHz: Int? = null,
    val channels: Int? = null
) : ConversionSettings
```

Define `ResolutionChoice`, `FrameRateChoice`, and `ResizeChoice` as serializable sealed types or enums plus validated parameters. Never persist FFmpeg CLI fragments supplied by users.

### Room entities

`PresetEntity(id, mediaType, name, isBuiltIn, settingsSchemaVersion, settingsJson, createdAt, updatedAt)` with unique `(mediaType, name)` constraint. `ConversionJobEntity(id, inputUri, mediaType, action, state, presetSnapshotJson, outputCachePath, destinationUri, startedAt, updatedAt, errorCode)` if durable job recovery is implemented. Do not persist transient URI grants as if they were permanent.

`Default` is a reserved built-in preset for each media type. Users may modify its active settings, but **Restore factory defaults** restores canonical defaults. Built-in presets cannot be renamed/deleted. Custom presets can. Edits are tracked as dirty until saved; selecting another preset prompts to discard/save when needed. Preset settings are snapshotted at conversion start.

## 5. Screen specifications

### 5.1 Main conversion screen

States: `Empty`, `Inspecting`, `Ready`, `Converting`, `Completed`, `Failed`, `Cancelled`.

- Empty: prominent **Select file**; explain Android Share/Open with integration.
- Inspecting: spinner, file name if known, error recovery.
- Ready: large preview, metadata, current preset summary, **Settings** button, two equally prominent **Save** and **Share** buttons. **No Convert button**.
- Converting: preview remains; progress percentage when measurable, elapsed time, ETA if reliable, destination action label, **Cancel**; prevent duplicate job launches.
- Completed: success indication, output size/format, **Save** and **Share** actions both available without re-encoding, plus **Choose another file**.
- Failed: concise actionable error and retry; no fake success or share sheet.
- Cancelled: return to Ready with prior settings retained; clean partial output.

### 5.2 Preview

- Video: Media3 PlayerView/Compose integration, playback controls, aspect-ratio fit, no autoplay by default.
- Audio: play/pause, seek and duration; optional simple waveform later, not required.
- Image: Coil decode with memory-aware downsampling; preserve orientation and fit bounds.
- If a source cannot be previewed by platform decoders but FFmpeg can convert it, show thumbnail/metadata and a preview-unavailable explanation; conversion remains available.

### 5.3 Settings screen

- Show active preset selector at top and relevant fields only.
- Video: container, codec, hardware mode, quality/bitrate, resolution, frame rate, audio codec/bitrate, audio inclusion toggle. Hide/disable unsupported combinations with explanation.
- Image: format, dimensions/resize mode, transparency behavior; only show quality/compression controls where meaningful.
- Audio: format, bitrate/quality, sample rate, channel mode.
- Actions: Save changes, Save as new preset, Duplicate, Rename/Delete for custom presets, Restore factory defaults for built-in preset.
- Persist changes intentionally; do not overwrite saved preset on every slider movement.

## 6. Save and Share workflows

### 6.1 Save — picker first

1. User taps Save in Ready.
2. Construct proposed output filename and MIME type from selected settings.
3. Launch `ACTION_CREATE_DOCUMENT` (or Compose Activity Result contract `CreateDocument`) with a suggested filename.
4. If picker is cancelled, remain Ready; do not convert.
5. On URI return, create a job snapshot and start the foreground conversion service.
6. Stage input if necessary and encode to a private temporary output file. This avoids leaving partial/corrupt data in the destination document.
7. Validate output (probe expected streams/container and check nonzero file size), then copy to the destination URI with `ContentResolver.openOutputStream`.
8. On success, retain output cache temporarily for reuse and show Completed. On write failure, report error and attempt cleanup where possible; do not assert the destination is valid.

If the result was already converted with identical settings/input, skip encoding and simply copy the cached result to the chosen destination.

### 6.2 Share — conversion first

1. User taps Share in Ready.
2. Snapshot settings and start conversion immediately to private cache.
3. On success, validate output and obtain a `content://` URI via `FileProvider`.
4. Launch Android Sharesheet with `ACTION_SEND`, MIME type, `EXTRA_STREAM`, `ClipData` where needed, and `FLAG_GRANT_READ_URI_PERMISSION`.
5. Retain the cached result for a bounded period so receiving apps can read it; do not delete it as soon as the chooser closes.
6. Returning to Completed permits Save or Share again without recomputation.

Do not claim the receiving app accepted or stored the file merely because the chooser was launched.

### 6.3 Cache and reuse

Use app-private `cacheDir` or a dedicated private files area with bounded quota. Associate a completed output with an input identity, settings snapshot/hash, and job ID. Check file existence before reuse. Clean old outputs via explicit cleanup and scheduled maintenance; do not remove files while an active share may still read them. Prefer conservative retention (e.g., 24 hours, adjustable after testing) and size-based eviction. Handle revoked URI access and missing input gracefully.

## 7. FFmpeg engine and command planning

### 7.1 Integration

Implement an in-process JNI bridge that links to FFmpeg libraries. Expose version, capabilities, probe, encode, progress, and cancellation. Never rely on a shell binary as the default Android integration. Implement safe native thread ownership, JNI reference handling, cancellation flags, and cleanup of FFmpeg contexts. If using a third-party wrapper, pin and audit it and verify MediaCodec **encoding** support.

Build `arm64-v8a` first; optionally `x86_64` for emulator. Use reproducible NDK/CMake scripts. Include codec licensing inventory and Android native page-size compatibility tests.

### 7.2 Conversion plans

The planner generates a typed `ConversionPlan` with explicit input/output paths, selected streams, filters, encoder names, options, and fallback candidates. The bridge maps the plan to FFmpeg APIs or a well-tested embedded CLI runner. Never interpolate filenames into a shell string.

**Illustrative CLI-equivalent commands (not an instruction to spawn a desktop executable):**

Software fallback is mandatory: use `libx264` for H.264 and `libx265` for H.265/HEVC. Hardware MediaCodec remains the preferred AUTO path where validated. If hardware fails, retry using software with a fresh output file and correctly mapped quality settings.

Video software H.264 fallback:

```bash
ffmpeg -i input.mov -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 192k output.mp4
```

Optional H.265/HEVC software output:

```bash
ffmpeg -i input.mov -c:v libx265 -crf 28 -preset medium -c:a aac -b:a 192k output.mp4
```

Video hardware:

```bash
ffmpeg -i input.mov -map 0:v:0 -map 0:a? \
  -c:v h264_mediacodec -b:v 5M \
  -c:a aac -b:a 192k -movflags +faststart output.mp4
```

Image:

```bash
ffmpeg -i input.jpg -frames:v 1 output.png
```

Audio:

```bash
ffmpeg -i input.wav -vn -c:a libmp3lame -b:a 192k output.mp3
```

These examples assume the named encoders are actually compiled into the binary and compatible with the input. The planner must account for source rotation/orientation, alpha, pixel format, audio-less video, optional streams, resize dimensions, even-dimension requirements, and format-specific encoder options. Prefer `-map 0:a?` or equivalent optional mapping for silent video. Do not add invalid encoder flags to MediaCodec encoders (e.g., `-crf`).

### 7.3 Hardware acceleration selection

- `AUTO`: check engine support, device codec capabilities, input/output constraints, and attempt hardware encode. If hardware initialization or encoding fails, clean partial output and retry once with a known-good software encoder, with clear progress reset/notification.
- `PREFER_HARDWARE`: attempt hardware first; fall back only if product semantics explicitly allow it (document chosen behavior). Recommended MVP: allow fallback and show that it occurred.
- `SOFTWARE_ONLY`: use software encoder directly.
- Keep decoding and encoding choices independent. Hardware decode may not interoperate with filter/encode pipeline without pixel-buffer transfer; begin with default decode and hardware encode, then optimize.
- For hardware mode, expose bitrate/quality controls supported by the device; for any separately approved software encoder, its supported quality controls. Avoid mapping CRF 23 to an arbitrary hardware bitrate without a documented heuristic.
- Hardware capability discovery is advisory; the real encode attempt determines success.

### 7.4 Progress and cancellation

Compute progress using processed media timestamp / known input duration where reliable; clamp 0–100 and treat as estimated. Images/short audio may use indeterminate progress. Native engine must periodically check cancellation and release codec/FFmpeg resources promptly. Cancellation deletes incomplete temp outputs, does not delete input, and must not accidentally share or save partial files.

## 8. Foreground service and Android lifecycle

Use a user-initiated foreground service with a notification, correct `foregroundServiceType`/permissions for media processing according to the targeted Android API level, and stop/cancel action. Check Android 15+ media-processing foreground-service limits and background-start restrictions when selecting the final target SDK. Start from the visible user action. Do not use WorkManager as a substitute for immediate foreground conversion without validating its foreground execution semantics.

Persist minimal job metadata for crash recovery. On restart, detect interrupted jobs, clean orphaned partial files, and present retry; native FFmpeg state cannot generally resume mid-encode. Activity/ViewModel observes service job state through repository Flow. Rotation and UI recreation must not restart a conversion.

## 9. Android intent and storage integration

- Use `ACTION_OPEN_DOCUMENT` (and persist read permission only when actually needed/available) for selection; receive `ACTION_SEND` and `ACTION_VIEW` with correctly declared MIME intent filters.
- Detect real media type via probe; validate MIME and file metadata rather than trusting external names.
- Use ContentResolver streams and ParcelFileDescriptor where supported. For non-seekable providers, stage to private disk with progress and free-space checks.
- Use `ACTION_CREATE_DOCUMENT` for Save. Avoid `MANAGE_EXTERNAL_STORAGE` and broad storage permissions.
- Share with FileProvider and URI read grants; never expose `file://` URIs.
- Normalize output filename extensions, sanitize names, and handle collision via Android document picker.
- Prevent path traversal, malformed filenames, oversized metadata, and invalid preset values.

## 10. Error taxonomy and UX

Define stable error codes: `UNSUPPORTED_INPUT`, `PROBE_FAILED`, `PREVIEW_UNAVAILABLE`, `UNSUPPORTED_OUTPUT`, `ENCODER_UNAVAILABLE`, `HARDWARE_INIT_FAILED`, `CONVERSION_FAILED`, `CANCELLED`, `LOW_STORAGE`, `INPUT_PERMISSION_REVOKED`, `DESTINATION_WRITE_FAILED`, `SHARE_FAILED`, `NATIVE_CRASH_OR_INTERRUPTION`.

Show friendly messages with Retry/Change settings/Choose file where applicable. Preserve diagnostic codes in logs. A preview failure must not automatically block conversion. A hardware failure in AUTO mode must retry with bundled `libx264` (or `libx265` for HEVC), report the fallback, and preserve cancellation semantics.

## 11. Testing strategy

### Unit tests

- Factory defaults are exactly MP4/H.264/AAC, PNG, and MP3.
- Built-in presets cannot be deleted/renamed; reset restores factory values.
- Custom preset create/update/rename/duplicate/delete and schema migration.
- Dirty settings and snapshot isolation.
- Encoder capability validation and hardware/software selection.
- ConversionPlan construction: audio-less video, rotation, resize, codecs, optional streams, and file extensions.
- Job state machine: legal transitions, duplicate tap prevention, cancellation, retries, completion.
- Cache reuse only for identical input/settings and valid existing output.

### Instrumentation/UI tests

- Picker cancellation does not start Save conversion.
- Tapping Save opens destination picker; after confirmation conversion begins.
- Tapping Share starts conversion immediately and opens chooser only on success.
- No Convert button exists anywhere in the normal workflow.
- Incoming Share/Open with populates preview and metadata.
- Rotation and backgrounding do not restart conversion.
- Completed output can be saved and shared without re-encoding.
- URI permissions and FileProvider grants work with external apps.

### Real device/media tests

Use small licensed fixtures for MP4/MOV/MKV, MP3/WAV/FLAC, JPEG/PNG/WebP, video with no audio, odd dimensions, rotation, corrupted input, large media, low storage, hardware-unsupported format, and non-seekable cloud/document provider. Verify resulting streams with FFprobe and play them in a second app. Include at least one physical arm64 Android device for MediaCodec encoding and fallback tests.

## 12. Implementation sequence for agent

### Milestone A — Skeleton and architecture

**Tasks:** Create multi-module Gradle project, Compose navigation, theme, models, DI, unit-test scaffolding, permissions/manifest, CI build.

**Acceptance:** Debug APK launches on emulator/device; tests and lint run; empty/select screen visible.

### Milestone B — Input and preview

**Tasks:** SAF selection, incoming intents, MIME/probe, descriptor model, Media3 video/audio, Coil image, error states.

**Acceptance:** All three media types can be selected and previewed or show explicit preview-unavailable state without crashing.

### Milestone C — FFmpeg engine

**Tasks:** Pin/build native FFmpeg, JNI bridge, codec capability query, conversion planner, basic default conversions, conditional compliant software fallback, progress/cancel, native diagnostics.

**Acceptance:** Real valid MP4/H.264/AAC, PNG, and MP3 outputs produced on arm64 device; hardware encoding attempted when supported; libx264/libx265 fallback tested; license inventory generated.

### Milestone D — Settings and presets

**Tasks:** Typed settings, Room entities/DAO, factory defaults, custom presets, settings validation and conditional UI, schema migrations.

**Acceptance:** Settings persist; presets can be managed; reset is deterministic; settings change resulting FFmpeg output.

### Milestone E — Save and Share integration

**Tasks:** Save picker-first flow, Share convert-first flow, FileProvider, cache, output validation, reuse, destination copy, errors.

**Acceptance:** Both primary actions convert without a Convert button; cancel picker does not encode; chooser appears only after success; completed output can be reused.

### Milestone F — Background robustness

**Tasks:** Foreground service, notifications, cancellation, lifecycle observation, interruption recovery, cleanup, low-storage checks.

**Acceptance:** Background/rotation works, progress is observable, cancellation removes partial files, interrupted jobs do not falsely show Completed.

### Milestone G — Release readiness

**Tasks:** UI polish/accessibility, complete test matrix, performance, ABI/page-size validation, signed release, Play Console metadata/policy checks, internal testing.

**Acceptance:** Signed AAB passes internal testing and release checklist; no unreviewed native dependency/license issues; complete corresponding source published.

## 13. Suggested task tracking format

For each milestone, create small implementation tasks with:

```text
ID:
Goal:
Dependencies:
Files/modules affected:
Implementation notes:
Tests:
Acceptance criteria:
Known risks:
```

Commit at milestone boundaries, keep CI green, and provide a brief status report listing implemented behavior, passing tests, unresolved risks, and any changes to this specification. If a decision is unclear, prefer the simplest offline-first implementation consistent with the UX and document the decision.

## 14. Open decisions to resolve before shipping

1. **App identity:** final name, package/application ID, icon, and minimum supported Android version.
2. **Licensing:** GPL-3.0-or-later application plus GPL-enabled FFmpeg with mandatory libx264 and libx265. Verify all third-party licenses and include full corresponding source with releases.
3. **Native FFmpeg distribution:** maintained wrapper vs self-built JNI engine; verify hardware encoder availability, source/build reproducibility, and ABI coverage.
4. **Settings breadth:** MVP may expose only tested output formats, even though architecture supports additional formats later.
5. **Cache policy:** finalize quota/retention based on real file sizes and share-target behavior.
6. **Analytics:** default to none; if added later, update privacy/data safety declarations.

**Do not block initial development on branding decisions.** Use a placeholder application ID only until the first Play upload; use a configurable namespace and document the required rename steps.


## Cross-platform extension: authoritative implementation decision

The application is **Transversa — The ultimate converter**. Android remains the initial delivery platform, but the codebase must support iOS through **Kotlin Multiplatform shared business logic + native SwiftUI iOS presentation**, not a shared Compose UI. Read `05-cross-platform-design-and-architecture.md` for module boundaries and data contracts and `04-ios-agent-implementation-plan.md` for iOS implementation tasks. The existing Android specification remains authoritative for product behavior; translate the same behavior to platform-native interaction patterns.

### Required module boundaries

- `shared/commonMain`: media-type/format catalog, conversion profile/preset models, default/reset logic, validation, encoder capability abstraction, output planning, conversion state machine, error taxonomy, cancellation contract and pure use cases; no Android SDK, UIKit, AVFoundation or JNI types.
- `shared/androidMain` / Android app: Android storage URIs, Room/DataStore repositories or adapters, Media3 preview, MediaCodec capability probing, FFmpeg Android bridge, foreground service, Compose Material 3 UI, share/save intents.
- `shared/iosMain` / `iosApp`: Kotlin/Native adapters where appropriate; SwiftUI screens, security-scoped URLs, document pickers, AVKit/QuickLook, iOS FFmpeg bridge, VideoToolbox capability probing, background/foreground lifecycle, iOS sharing/export and native persistence adapters.
- Platform boundaries must not leak `Uri`, `URL`, `UIView`, `Context`, `Activity`, `AVAsset` or JNI handles into common code. Use serializable logical file references and platform-local opaque handles.

### Cross-platform acceptance

1. Shared unit tests run on JVM/Android and iOS targets; default profile semantics and validation match exactly.
2. Both platforms preserve input preview, user presets, default reset, Save/Share as conversion triggers, cancellation, offline processing, and output reuse when available.
3. Each platform handles permission failures, insufficient space, interruptions and encoder fallback using native mechanisms.
4. iOS uses SwiftUI/HIG and Android uses Material 3; shared design tokens and UX requirements are documented, but controls and navigation remain native.
5. iOS release is gated on independent license, codec-patent, App Store policy, physical-device, signing and privacy review. No assertion that Android-specific background services or MediaCodec behavior directly transfers to iOS.

### Implementation order

Keep Android functional, extract pure Kotlin logic with tests, define interfaces and Swift-facing adapters, create native SwiftUI shell on macOS, implement iOS FFmpeg and file lifecycle, then test and distribute via TestFlight before App Store submission. Do not attempt to reuse Android binaries or Android JNI on iOS.
