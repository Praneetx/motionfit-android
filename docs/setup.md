# Setup guide

This document describes exactly how the project was set up and built on the
Windows machine it was developed on, so you can reproduce it.

## 1. Tools that were actually used

| Tool | Version found / used |
|---|---|
| Operating system | Windows 10 (10.0.19044) |
| JDK | OpenJDK **25.0.3** (the JetBrains runtime bundled with Android Studio) |
| Gradle | **9.6.0** (via the project Gradle wrapper) |
| Android Gradle Plugin | **9.4.0** |
| Kotlin | provided by AGP's built-in Kotlin support (Kotlin 2.2.x) |
| Android SDK platform | **android-37** (`compileSdk 37`) |
| Android build-tools | 36.0.0 |
| Android platform-tools (adb) | 37.0.1 |
| Emulator | AVD `Medium_Phone`, Android API 37, x86_64 |

### Important note about JAVA_HOME

On this machine `java` is **not on the PATH**, so Gradle must be told where the JDK
is. Every build command therefore starts with:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

If you install a normal JDK 17 or newer and put it on your PATH, you can skip that
line, but JDK 17+ is required.

## 2. Android SDK

The SDK is installed at:

```
C:\Users\<your-user>\AppData\Local\Android\Sdk
```

The project points at it through `local.properties` (this file is machine specific
and intentionally not committed):

```
sdk.dir=C:/Users/<your-user>/AppData/Local/Android/Sdk
```

If you move the project to another computer, either edit `local.properties` or set
`ANDROID_HOME` / `ANDROID_SDK_ROOT` to your SDK folder.

## 3. Gradle

The project ships a Gradle **wrapper** (`gradlew` / `gradlew.bat`) pinned to
Gradle 9.6.0, so you do not need to install Gradle manually. AGP 9.4.0 is declared
once in the root `build.gradle` and applied in `app/build.gradle`.

Why these versions are compatible:

* AGP 9.4.0 requires a recent Gradle 9.x and JDK 17+.
* `compileSdk 37` matches the installed `android-37` platform.
* AGP 9 uses **built-in Kotlin**, so the old `org.jetbrains.kotlin.android` plugin
  is intentionally not applied; `src/main/kotlin` is added to the source set.

## 4. Emulator or physical device

Check that a device is connected:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

Start the bundled emulator (headless example used during development):

```powershell
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd Medium_Phone -no-snapshot -no-audio
```

Or open Android Studio → **Device Manager** → start an emulator.

## 5. Build and run

```powershell
cd 'C:\Users\PRANIT DESHMUKH\Desktop\Fitness tracker'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# Build the debug APK
.\gradlew.bat assembleDebug

# Install it on the connected device/emulator
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# Launch the dashboard
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n io.github.praneetx.motionfit/.activities.MainActivity
```

The debug APK is written to:

```
app\build\outputs\apk\debug\app-debug.apk
```

## 6. Tests

```powershell
# Pure JVM unit tests (no device needed)
.\gradlew.bat testDebugUnitTest

# Instrumented database tests (needs a running emulator/device)
.\gradlew.bat connectedDebugAndroidTest
```

The HTML unit-test report is generated at:

```
app\build\reports\tests\testDebugUnitTest\index.html
```

## 7. Opening the project in Android Studio

1. **File → Open** and choose the project folder.
2. Let Gradle sync (it will download nothing remote at run time - the app itself
   is offline; Gradle only needs the internet the first time to fetch dependencies).
3. Select the `app` run configuration and press Run.
