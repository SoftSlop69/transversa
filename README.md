# Transversa

The ultimate converter.

Transversa is an offline-first Android media converter. This repository currently contains the Android Milestone A project shell: a multi-module Gradle build, a native Material 3 Compose entry screen, portable domain models, and a CI debug-build workflow.

## Android development

1. Open this repository folder in Android Studio.
2. Let Gradle sync with the checked-in Gradle wrapper.
3. Select the app run configuration and a device or the Transversa_API_37 emulator.
4. Run the app. The current screen can select a media file, but preview and conversion are not implemented yet.

The package ID com.example.transversa and minimum Android API 26 are provisional development choices. Choose the permanent app ID and supported minimum Android version before the first Play upload.

## Module boundaries

- app: Android entry point, Compose Material 3 UI, navigation, Android intents and app wiring.
- core:model: platform-independent conversion profiles and job state.
- core:media: Android media selection, inspection and preview adapters.
- core:presets: Android preset persistence and validation adapters.
- core:conversion: conversion planning, orchestration and output handling.
- ffmpeg-bridge: Android JNI and embedded FFmpeg boundary.

Media3, Room, DataStore, foreground conversion, and native FFmpeg dependencies belong in their owning modules when those milestones are implemented. No conversion is presented as functional until a real engine and output validation exist.

## Scope

This checkout is Android-first. It does not contain an iOS app, Xcode project, iOS targets, signing configuration, or iOS-native dependencies. Keep portable business rules in core:model; a later macOS task can extract shared logic into Kotlin Multiplatform and add native SwiftUI without changing the Android UI to Compose Multiplatform.

