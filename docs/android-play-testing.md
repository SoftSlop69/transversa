# Android Play testing release

The release variant uses the Play package name `com.slopsoft.transversa`; only
the debug variant has the `.debug` suffix. Release builds target API 36 and use
version code 6 / version name 0.6.0.

## Configure the upload key

The existing upload keystore is at `D:/Projects/slopsoft/transversa`. Copy
`keystore.properties.example` to the repository root as `keystore.properties`
and enter the keystore password, alias, and key password locally. That file is
ignored by Git. Do not commit it or share its secret values.

Before uploading, confirm the certificate in this keystore matches the upload
certificate registered for this app in Play Console. A signed bundle from an
earlier build is not enough to prove the local keystore is the same key.

## Build the upload bundle

From PowerShell at the repository root, run:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user'
.\gradlew.bat :app:bundleRelease
```

Release builds use this signing configuration, so Android Gradle Plugin signing
validation will stop the build if the local configuration or keystore is
missing or invalid. On success, upload
`app/build/outputs/bundle/release/app-release.aab` to the Internal testing
track. The native symbols archive is copied beside it as
`app-release-native-debug-symbols.zip` for crash and ANR diagnostics.

In Play Console, enroll in Play App Signing if prompted, upload the AAB to
Internal testing, select the tester list, and save/roll out the release. Play
requires each new upload to have a higher version code. New app submissions
must target API 36 or higher as of August 31, 2026.

