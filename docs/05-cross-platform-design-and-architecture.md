# Transversa — Cross-Platform Architecture and Design System

**Brand:** Transversa  
**Tagline:** The ultimate converter  
**Objective:** One coherent product with native Android and iOS presentation.

## 1. Decision record

- **Share logic:** Kotlin Multiplatform, compiling common Kotlin to JVM/Android and Kotlin/Native for iOS.
- **Android UI:** Jetpack Compose with Material Design 3.
- **iOS UI:** SwiftUI using Apple Human Interface Guidelines.
- **Do not share visual controls:** no mandate to use Compose Multiplatform UI on iOS; use platform-native components.
- **Share design intent:** design tokens, semantic styles, iconography, copy, information hierarchy, feature contracts, validation and test cases.
- **Native FFmpeg engines:** separately compiled for Android and iOS; MediaCodec vs VideoToolbox hardware paths.

## 2. Recommended architecture

```text
                   Transversa design tokens / product spec
                                   |
           +-----------------------+-----------------------+
           |                                               |
    Android application                              iOS application
    Compose / Material 3                             SwiftUI / Apple HIG
    Android ViewModels                               Swift observable adapters
           |                                               |
           +------------ Kotlin Multiplatform -------------+
                        common domain / use cases
                        profile + preset schema
                        validation + job state
                        engine/storage interfaces
           |                                               |
    Android platform adapters                       iOS platform adapters
    SAF, Media3, Room, Service                       FileImporter, AVKit, storage
    FFmpeg + MediaCodec                              FFmpeg + VideoToolbox
```

Keep common code free of platform UI and native file-handle types. Define `ConversionEngine`, `MediaProbe`, `PresetRepository`, `MediaSourceResolver`, `OutputDelivery`, and `CapabilityProvider` contracts. Platform adapters implement them. For Swift interop, expose simple DTOs and state snapshots; provide explicit cancellation tokens and threading rules.

## 3. Shared UX specification

Both platforms use the same flow: **Select media → Preview original → Choose format/settings/preset → Save or Share → Progress → Result**. There is **no separate Convert button**. Save asks for the destination/name before conversion begins. Share converts privately before invoking the system share UI. After success, Save and Share may reuse an existing output.

Default formats: video MP4/H.264/AAC; image PNG; audio MP3. HEVC is optional. Profiles and validation are semantically equivalent across platforms even where encoder-specific knobs differ.

## 4. Design tokens and native mapping

Maintain a versioned design-token source (e.g. JSON in `design/tokens/`) containing semantic brand colors, spacing scale, corner radius intent, typography roles, icon semantic names and status colors. Generate or manually maintain platform mappings with tests; avoid treating exact pixel values as cross-platform guarantees.

| Shared intent | Android | iOS |
|---|---|---|
| Primary action | Material 3 button | SwiftUI prominent button |
| Select format | Material 3 single-choice segmented buttons | SwiftUI Picker/Menu/sheet |
| Preset settings | Material lists and switches | SwiftUI Form and Toggle |
| Navigation | Android Navigation + system back | NavigationStack + native back |
| Share | Android sharesheet | UIActivityViewController/ShareLink |
| Save | Storage Access Framework | Native document destination/export flow |
| Progress | Material progress indicator | SwiftUI ProgressView |
| Dark mode | Material color scheme | SwiftUI color scheme |
| Accessibility | TalkBack + font scaling | VoiceOver + Dynamic Type |

Use system fonts by default (Roboto/platform typography on Android; San Francisco on iOS). Preserve platform motion, touch targets, scrolling, sheets, keyboard behavior, safe areas and back gestures. Don't override iOS native controls to imitate Material, or Android controls to imitate iOS.

## 5. Portable schema sketch

```kotlin
@Serializable
data class ConversionProfile(
    val schemaVersion: Int,
    val mediaKind: MediaKind,
    val container: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val encoderPreference: EncoderPreference = EncoderPreference.AUTO,
    val options: Map<String, String> = emptyMap(),
)

enum class EncoderPreference { AUTO, HARDWARE, SOFTWARE }
```

Treat this as an illustrative contract, not a substitute for strongly typed settings and schema migrations. Never store native Android `Uri` or iOS security-scoped `URL` in the portable preset schema. Common settings validation should consult capability information provided by each platform.

## 6. Conversion lifecycle contract

Use a documented state machine: `Idle → Probing → Ready → Preparing → Running → Finalizing → Completed`, with `Failed`, `Cancelled` and `Interrupted` exits. Each job has an ID, selected output action, input identity, profile snapshot, progress and output ownership. Cancel must stop the native encoder and clean partial outputs. An output is reusable only while the file remains valid and accessible. Both UI adapters must render consistent state meanings.

**Platform divergence is intentional:** Android can use a foreground service for user-initiated long-running work subject to current Android restrictions. iOS background execution is more limited; expose clear UX and retry semantics rather than guaranteeing uninterrupted background conversion.

## 7. Source, compliance and test strategy

GPL-3.0-or-later is the project license; FFmpeg with x264 is GPL-enabled and Kvazaar is BSD-3-Clause. Audit the exact combination per platform. Before App Store distribution, explicitly review GPL obligations against Apple distribution and signing conditions, all third-party licenses, and codec patents. Do not claim legal clearance from this architecture plan.

- Shared unit tests for models, defaults, presets, migration, validation and job state.
- Android UI/integration tests for Material interactions, SAF, MediaCodec and foreground behavior.
- iOS UI/integration tests for SwiftUI, security-scoped files, VideoToolbox and interruption behavior.
- Golden conversion fixtures checked by FFprobe for semantic output equivalence.
- Accessibility, dark mode, localization and screen-size reviews on both platforms.

## 8. Development workflow

Windows remains supported for Android and common Kotlin development. macOS/Xcode is required for iOS builds, simulator/device testing, signing and App Store distribution. Use separate platform CI jobs with a shared Kotlin test gate. Keep independent platform release readiness checks and avoid merging untested cross-platform changes.

## References

- https://kotlinlang.org/docs/multiplatform/multiplatform-get-started.html
- https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html
- https://m3.material.io/
- https://developer.apple.com/design/human-interface-guidelines/
- https://ffmpeg.org/legal.html
