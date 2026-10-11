# Transversa — iOS Implementation Plan for Coding Agent

**Platform:** iOS, SwiftUI, Kotlin Multiplatform shared logic, native FFmpeg, VideoToolbox.  
**Product:** Transversa — *The ultimate converter*.  
**Dependencies:** Read `02-agent-implementation-plan.md` for the authoritative product requirements, `03-macos-ios-development-and-publishing.md` for tooling/release, and `05-cross-platform-design-and-architecture.md` for cross-platform contracts.

## 0. Agent mandate

Build a fully functional, offline-first iOS companion with native SwiftUI presentation. Do not use Compose Multiplatform UI. Reuse platform-neutral Kotlin domain logic through KMP, but implement iOS platform adapters and SwiftUI screens natively. Do not replace mandatory FFmpeg x264/Kvazaar software encoders without an explicit project decision. Do not claim background conversion is guaranteed after iOS suspension. Keep licensing and Apple distribution compatibility as a hard release gate.

## 1. MVP behavior parity

- Import one video/audio/image through native document pickers or an incoming supported share/open-in flow; probe actual content.
- Preview original video/audio with AVKit and images with native image presentation. Display metadata.
- Default video: MP4/H.264/AAC; default image: PNG; default audio: MP3. HEVC/H.265 is optional.
- Configure output codec/container, resolution/quality/bitrate and relevant media-specific settings, constrained by tested capabilities.
- Support editable/resettable factory **Simple** presets plus create/edit/duplicate/delete user presets; use the same shared preset schema as Android.
- **No Convert button.** Save requests destination/filename before starting conversion. Share initiates conversion to a private temporary file and opens native share sheet.
- After successful conversion, Save or Share existing output without re-encoding if still available; show progress, cancel, errors and cleanup.
- No network requirement, accounts, ads, watermarks or cloud upload.

## 2. Module and interface deliverables

1. Create KMP shared targets and pure Kotlin models/use cases: `MediaKind`, `MediaMetadata`, `OutputProfile`, `EncoderPreference`, `EncoderCapabilities`, `ConversionRequest`, `ConversionProgress`, `ConversionResult`, `ConversionError`, `PresetRepository` and `ConversionEngine` interfaces.
2. Expose Swift-friendly entrypoints with explicit ownership, cancellation and immutable snapshots. Bridge state into `@MainActor` observable SwiftUI models. Avoid holding unmanaged pointers across Swift/Kotlin boundaries.
3. Provide iOS adapters for native persistence (SwiftData/SQLite or a documented compatible store), file security-scoped URLs, FFmpeg bridge, VideoToolbox probing and output delivery.
4. Add schema versioning and migrations for presets; cross-platform parity means same meaning and defaults, not necessarily shared database files.
5. Keep Android code working and retain unit tests as KMP extraction proceeds.

## 3. iOS native engine implementation

1. Pin FFmpeg, x264, Kvazaar, MP3 encoder dependencies and Apple toolchain versions. Build device/simulator-compatible libraries and package them correctly.
2. Implement a safe C/Objective-C/Swift bridging layer around FFmpeg APIs or an embedded executable approach only if technically and policy compliant; avoid brittle shell-process assumptions on iOS.
3. Implement input staging, probing, stream mapping, output validation, atomic file handling, progress callbacks, cancellation and error classification.
4. Probe `h264_videotoolbox`/`hevc_videotoolbox` per device; prefer tested hardware encoding in Auto mode and retry with `libx264`/`libkvazaar` where supported. Respect software-only mode. Ensure video defaults to MP4/H.264/AAC and HEVC is explicit.
5. Provide encoder-specific setting translations: VideoToolbox bitrate/quality versus x264 CRF/preset and Kvazaar bitrate or documented encoder parameters. Avoid promising unsupported knobs.
6. Verify actual FFmpeg configure flags and codecs. Build must not contain `--enable-nonfree`; keep complete source/build provenance.

## 4. SwiftUI screen deliverables

- **Import/preview:** system picker, original media preview, metadata, media-kind detection and unsupported-file state.
- **Conversion settings:** platform-native forms, pickers, toggles, sheets and contextual help; per-kind options, hardware/software capability messaging.
- **Presets:** choose, edit, save, rename, duplicate, delete, reset default with confirmation where destructive.
- **Save:** destination/name chooser first, then conversion with progress; support reusing existing converted output.
- **Share:** conversion then `UIActivityViewController`/native share UI; ensure temp output survives share completion.
- **Progress:** accessible progress indicators, cancellation, success/failure feedback and interruption messaging.
- **About:** licenses, complete corresponding source link, offline/privacy description, build version.

Design requirements: native navigation and sheets, Dynamic Type, VoiceOver, high contrast, safe areas, reduced motion, dark mode. Keep shared Transversa colors/iconography and user-facing terminology aligned with Android.

## 5. Lifecycle and security

- Never hold security-scoped URL access beyond need; avoid assuming cloud-backed documents are seekable or permanently available.
- Use app-owned temporary storage and streamed copying. Detect low space and clean partial results on failure/cancel.
- Handle app backgrounding and suspension honestly. If conversion cannot finish in background, preserve job metadata and allow retry when foregrounded; don't misreport success.
- Avoid network permissions/analytics by default; keep all processing on device.
- Share only user-selected output. Use system privacy permissions and UTTypes correctly.

## 6. Test matrix

- Shared Kotlin tests: profile defaults, preset reset/migrations, option validation, state transitions, cancellation, output planning.
- Swift unit/UI tests: file import, Save/Share triggers, destination/name selection, accessibility, preset lifecycle, result reuse.
- Native integration tests on iPhone: MP4/MOV/MKV inputs; H.264/H.265 software/hardware; MP3/WAV/FLAC; JPEG/PNG/WebP; no-audio video, rotation, odd dimensions, corruption, low space, nonseekable input.
- Cross-platform golden tests: same logical profile maps to semantically equivalent output settings on Android/iOS; verify with FFprobe, allow encoder-specific byte differences.
- Interruption tests: phone lock, background, memory pressure, app termination, document provider revocation and cancellation.
- Real-device performance/battery/memory tests; no simulator-only release acceptance.

## 7. Delivery milestones

**A — KMP foundation:** shared models, validation, preset contracts, unit tests and Swift bridge. Acceptance: Android remains green; iOS target compiles.

**B — SwiftUI shell:** import/preview, native settings, preset CRUD and accessibility. Acceptance: UI works on simulator with mocked engine.

**C — Native iOS FFmpeg:** reproducible GPL-enabled build, probe, codecs, software and VideoToolbox encoder paths, cancellation and validation. Acceptance: valid MP4/PNG/MP3 on physical iPhone.

**D — File workflow:** Save destination selection, Share, progress, temp retention, cleanup and interruption handling. Acceptance: end-to-end user workflow without Convert button.

**E — QA/release:** automated tests, license/source inventory, policy/legal review, signing, TestFlight and App Store submission package. Acceptance: no unreviewed GPL/App Store distribution conflict; release quality gates met.

## 8. Agent deliverables

Provide source, native build scripts, pinned dependency versions, reproducible instructions, unit/UI/integration tests, accessibility checks, supported codec/format matrix, limitations, complete corresponding source archive process, third-party notices, and a release readiness checklist. Flag any inability to meet a mandatory requirement before claiming completion.
