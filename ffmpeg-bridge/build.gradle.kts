// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.android.library)
}

val nativeOutput = layout.buildDirectory.dir("native/install/arm64-v8a")
val buildFfmpegAndroid by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the pinned FFmpeg and codec static libraries for Android arm64-v8a in WSL2."
    workingDir(rootProject.projectDir)
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
            rootProject.file("scripts/build-ffmpeg-android.ps1").absolutePath,
        )
    } else {
        commandLine("bash", rootProject.file("scripts/build-ffmpeg-android.sh").absolutePath)
    }
    inputs.files(
        rootProject.file("scripts/build-ffmpeg-android.sh"),
        rootProject.file("scripts/build-ffmpeg-android.ps1"),
        rootProject.file("native/ffmpeg/versions.env"),
    )
    outputs.dir(nativeOutput)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildFfmpegAndroid)
}

android {
    namespace = "com.transversa.ffmpeg"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DTRANSVERSA_FFMPEG_PREFIX=${nativeOutput.get().asFile.absolutePath}",
                    "-DANDROID_STL=c++_shared",
                )
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
