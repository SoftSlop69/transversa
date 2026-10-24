// SPDX-License-Identifier: GPL-3.0-or-later
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val releaseKeystoreFile = releaseSigningProperties.getProperty("storeFile")
    ?.takeIf(String::isNotBlank)
    ?.let(::file)
    ?: rootProject.file("keystore.properties")

android {
    namespace = "com.example.transversa"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.slopsoft.transversa"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 7
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The in-process FFmpeg bridge is currently built for arm64-v8a only.
        // Keep the app package on the same ABI so Android Studio/emulators do
        // not select x86_64 and install an app without its native converter.
        ndk {
            abiFilters += "arm64-v8a"
            debugSymbolLevel = "FULL"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("playUpload") {
            storeFile = releaseKeystoreFile
            storePassword = releaseSigningProperties.getProperty("storePassword").orEmpty()
            keyAlias = releaseSigningProperties.getProperty("keyAlias").orEmpty()
            keyPassword = releaseSigningProperties.getProperty("keyPassword").orEmpty()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("playUpload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Keep the native symbols bundle beside the release AAB so both Play Console
// upload files are easy to find together.
val copyReleaseNativeDebugSymbols by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/native-debug-symbols/release/native-debug-symbols.zip"))
    into(layout.buildDirectory.dir("outputs/bundle/release"))
    rename { "app-release-native-debug-symbols.zip" }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy(copyReleaseNativeDebugSymbols)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:media"))
    implementation(project(":core:presets"))
    implementation(project(":core:conversion"))
    implementation(project(":ffmpeg-bridge"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.google.play.billing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

