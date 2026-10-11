<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Third-party notices

This initial inventory covers the Android Milestone A project tooling and UI dependencies. Recheck exact transitive versions, notices, and redistribution terms before each release.

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

Upstream project license files and transitive dependency notices remain authoritative. This file is an initial inventory, not a release compliance review. It does not cover a future FFmpeg, x264, x265, JNI, Media3, Room, or DataStore integration.

