# Android Play testing release

The release variant uses the Play package name `com.slopsoft.transversa`;
the debug variant uses the `.debug` suffix. This release is version code 6,
version name 0.6.0.

## Configure the upload key

Copy `keystore.properties.example` to the repository root as
`keystore.properties`, then enter the local keystore path, password, alias,
and key password. The real properties file is ignored by Git. Do not commit it
or share its secret values. Confirm that the keystore certificate matches the
upload certificate registered for the app in Play Console.

## Build the upload bundle

From PowerShell at the repository root, run:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user'
.\gradlew.bat :app:bundleRelease
```

The release variant uses R8 and resource shrinking. The Android Gradle Plugin
produces full native debug symbols, and the build copies the archive beside
the AAB as `app-release-native-debug-symbols.zip`.

On success, upload `app/build/outputs/bundle/release/app-release.aab` to the
Internal testing track. Enroll in Play App Signing if prompted, select the
tester list, then save and roll out the release. Each upload needs a version
code higher than every bundle already uploaded for this app.
