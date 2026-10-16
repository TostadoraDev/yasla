# Building YASLA

Use the checked-in Gradle wrapper. Versions are declared in `gradle/libs.versions.toml`,
`gradle/wrapper/gradle-wrapper.properties` and `gradle/gradle-daemon-jvm.properties`.

The configured daemon toolchain is JDK 25; bytecode targets Java 11. Android builds
use AGP 9.4.0 and `compileSdk.release(37)` (validated as SDK platform `android-37.0`).
Install the matching SDK through Android Studio. Do not lower the SDK or substitute
dependency versions merely to work around a local configuration issue.

## Local setup

1. Open the root folder in a compatible Android Studio version.
2. Resolve the SDK and Java toolchain and perform Gradle sync.
3. Build `:app:assembleDebug`.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on API 24+.

`local.properties` contains machine-specific SDK configuration and is ignored.
No Spotify credential configuration is currently needed.

## Checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :app:lintDebug
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

For one selected test on a particular device:

```powershell
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -e class xyz.tosdev.yetasimplelyicsapp.RefinementVisualTest xyz.tosdev.yetasimplelyicsapp.test/androidx.test.runner.AndroidJUnitRunner
```

Use an unlocked dedicated test device/emulator for gesture automation. The debug
showcase uses synthetic data and mock playback actions. It does not control a real
MediaSession or persist its demo widget settings. The existing-widget smoke test
requests updates on installed instances and verifies their configuration is unchanged.

Unit tests verify model/timing contracts; captures verify appearance. Neither alone
proves sustained physical 60/120 Hz performance. Keep those claims separate.

## Before a release

Run `scripts/audit-publication.ps1`, inspect the source changes and follow
`docs/LICENSE_STATUS.md`. Generate complete transitive notices, review asset rights,
choose a project license, define supported versions and establish private reporting.

Release signing is not configured with private credentials in the source tree.
Use a maintainer-owned keystore outside the repository when a release is authorized.
Never commit it, passwords, OAuth tokens or a client secret.
Building a local unsigned release does not publish a release.
