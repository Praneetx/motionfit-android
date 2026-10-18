# Setup

How to build and run Motionfit from a clean machine.

## Tools

| Tool | Version used |
|---|---|
| JDK | 17 or newer (built with the Android Studio JBR, JDK 25) |
| Gradle | 9.6.0 (via the bundled wrapper — do not install Gradle by hand) |
| Android Gradle Plugin | 9.4.0 |
| Android SDK platform | `android-37` (`compileSdk 37`) |
| Android build-tools | 36.0.0 |
| `minSdk` / `targetSdk` | 26 / 34 |

## JDK

Install a JDK 17 or newer. If `java` is not on your `PATH`, point Gradle at one:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

Kotlin is compiled by AGP's built-in Kotlin support, so no separate Kotlin plugin is
applied.

## Android SDK

The SDK location is read from `local.properties` (machine-specific, not committed):

```
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

Alternatively set `ANDROID_HOME`. You need platform `android-37` and `platform-tools`.

## Emulator or device

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

Start an emulator from Android Studio's Device Manager, or use a physical device with
USB debugging enabled. Android 8.0 (API 26) or newer is required.

## Build and run

```powershell
# Debug APK
.\gradlew.bat assembleDebug

# Install and launch
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n io.github.praneetx.motionfit/.activities.MainActivity
```

Output APK: `app\build\outputs\apk\debug\app-debug.apk`.

On macOS/Linux, use `./gradlew assembleDebug`.

## Tests

```powershell
.\gradlew.bat testDebugUnitTest          # JVM unit tests
.\gradlew.bat connectedDebugAndroidTest  # instrumented tests (needs a device)
```

Report: `app\build\reports\tests\testDebugUnitTest\index.html`.

## Release build (not configured yet)

A release build currently reuses the debug signing config; there is no production
keystore in this repository. Before publishing:

1. Create a keystore and a `keystore.properties` (already git-ignored).
2. Add a `release` signing config in `app/build.gradle`.
3. Consider enabling R8 (`minifyEnabled true`) and testing the shrunk build.

## Opening in Android Studio

**File → Open** → choose the project folder → let Gradle sync → select the `app`
configuration and Run.
