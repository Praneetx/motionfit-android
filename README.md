# Motionfit

**An offline-first Android fitness tracker for daily steps, workouts, running and personal goals.**

Motionfit keeps a simple, private record of your movement: count steps, log workouts,
run with a timer and live step count, set daily goals, and see how you are doing across
the day, week and month. Everything is stored on your phone — there is no account, no
server and no internet permission.

> **Status:** `2.0.0` (versionCode 2) — V2 build on `v2-dev`; not pushed or published
> on Google Play yet.

---

## Features

| Feature | Status |
|---|---|
| Five-tab navigation: Today, Run, Workouts, Progress, Settings | Available |
| Today dashboard: steps ring, workout time, workout count, recent workouts | Available |
| Steps detail: selected-day total, manual entry/calibration, history, tracking status | Available |
| Optional automatic daily step counting from the device step counter, with a foreground service | Available on devices with a step-counter sensor |
| Running mode: monotonic timer, live steps, rolling cadence, pause/resume, stop confirmation, recovery | Available; timer-only when the sensor is unavailable |
| Workout logging: add, view, edit, delete, filter by type | Available |
| Exact Running duration and step metadata on saved workouts | Available |
| Daily step goal and daily workout-minutes goal, with progress and percentages | Available |
| Progress summaries for day / week / month, with charts and a seven-day step history | Available |
| Skippable first-run onboarding with privacy and permission education | Available |
| Optional daily reminder (local notification) | Available |
| Settings, including automatic-tracking status and a confirmed "clear all data" action | Available |
| Accessibility/layout checks at 360dp and 1.3× font scale | Available; full TalkBack audit is not run |
| GPS, routes, distance, pace or calorie estimation | **Not included** |
| Accounts, cloud sync, analytics or network requests | **Not included** |

## Screenshots

Screenshots are not checked into this repository yet. The layouts are native XML and can
be viewed by installing the debug APK.

## Tech stack

* **Java** — Activities, adapters, custom views, repositories, SQLite helper, services,
  validation and date utilities.
* **Kotlin** — pure logic for step accounting, Running clock/controller, cadence,
  duration conversion, goal progress and activity summaries (called from Java).
* **XML layouts** — native Android views, no Compose.
* **SQLite** via `SQLiteOpenHelper` — local-only storage, schema version 2.
* **Material Components** + a custom design system (see [docs/ui-design.md](docs/ui-design.md)).
* Gradle 9.6.0 / Android Gradle Plugin 9.4.0, `compileSdk 37`, `minSdk 26`,
  `targetSdk 34`, version `2.0.0` (versionCode 2).

## Offline-first and privacy

* The app declares **no `INTERNET` permission** — it cannot send your data anywhere.
* No accounts, no sign-in, no analytics, no advertising SDKs, no crash-reporting SDKs.
* All data lives in a local SQLite database on the device.
* Reminders are scheduled locally with `AlarmManager`; notifications are generated
  on-device.
* The only permissions are for optional features: notifications, activity recognition
  (step counter), boot-completed reminder rescheduling, and the foreground service used
  by optional automatic tracking/Running. There is no location permission.

Read [PRIVACY.md](PRIVACY.md) for the draft privacy policy.

## Requirements

* Windows, macOS or Linux with PowerShell/Bash
* **JDK 17 or newer**
* Android SDK with platform **android-37** and `platform-tools`
* Android 8.0 (API 26) or newer on a device/emulator
* A step-counter sensor for automatic steps; the app still works without one

## Build and run

```powershell
# From the project folder
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'   # any JDK 17+ works

# Build the debug APK
.\gradlew.bat assembleDebug

# Install and launch (with an emulator or device connected)
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n io.github.praneetx.motionfit/.activities.MainActivity
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

On macOS/Linux use `./gradlew assembleDebug` instead.

## Automatic step counting

Automatic counting is opt-in from onboarding or Settings. It uses only
`Sensor.TYPE_STEP_COUNTER`; the app does **not** sum step-detector events or
accelerometer peaks and has no detector fallback.

* The daily total is stored once per calendar date with a raw sensor checkpoint.
  Sensor readings add only the delta from that checkpoint.
* A manual total is an explicit calibration: it replaces the total; when automatic
  tracking is on and a raw reading is available, that reading becomes the new
  checkpoint, so the next sensor reading cannot silently undo the correction.
* When automatic counting is enabled, activity access is granted, notifications are
  allowed, and the platform accepts the foreground service, a low-importance persistent
  notification stays visible while tracking. The Settings/Steps status shows `Active`
  only when that service actually starts.
* If notifications are denied or the foreground service is rejected, the truthful
  fallback is `App-open only`: steps count while a Motionfit screen is open.
* If activity access is denied or no step counter exists, automatic counting is
  `Unavailable`; manual entry and workouts still work.
* There is no guaranteed background tracking on every Android/OEM configuration. A
  force-stop, reboot, battery optimisation or OEM policy can stop or delay tracking.
  See [docs/limitations.md](docs/limitations.md).

## Running mode

Running records only a timer, live steps and a rolling cadence. It has **no GPS,
location, routes, distance, pace or calorie data**.

* Start, pause, resume and stop are explicit; stop asks for confirmation before saving.
* The timer uses monotonic `elapsedRealtime()` and pauses correctly. The in-progress
  session is persisted so ordinary backgrounding, rotation and process death do not
  duplicate the workout.
* If the process or device restarts, the run is marked interrupted. Recovery offers
  Resume, Finish and save, or Discard; downtime is never added to the timer.
* Finish inserts exactly one `Running` workout and deletes the session in one SQLite
  transaction. A second finish cannot create a second workout.
* If activity access is denied or the device has no step counter, the timer still works
  as a timer-only run. Steps and cadence show `—` rather than fabricated values.
* Cadence is a rolling 30-second estimate and shows `—` until enough valid sensor data
  exists.
* The Running notification has exactly Pause/Resume and Stop actions. Stop opens the
  Running screen's confirmation instead of silently saving.

## Tests

```powershell
# JVM unit tests (no device required)
.\gradlew.bat testDebugUnitTest

# Instrumented tests (requires a working emulator or device)
.\gradlew.bat connectedDebugAndroidTest

# Lint
.\gradlew.bat lintDebug
```

Final V2 result on 2026-10-27:

* **95 JVM unit tests**, 0 failures, 0 errors.
* **104 instrumented tests**, 0 failures, 0 errors on the API 30 emulator.
* **Lint:** 0 errors, 105 warnings; no lint baseline was added.
* `assembleDebug` and `assembleDebugAndroidTest` succeeded; the debug APK reports
  `versionCode 2`, `versionName 2.0.0`.

Physical-device sensor accuracy, real notification/FGS behaviour, process death,
reboot, OEM battery policies, TalkBack and a real V1→V2 APK upgrade remain **NOT RUN**.
Details and the full manual matrix: [docs/testing.md](docs/testing.md).

## Project structure

```
.
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/io/github/praneetx/motionfit/     # activities, adapters,
│       │   │                                          # database, models,
│       │   │                                          # repositories, services,
│       │   │                                          # utils, views
│       │   ├── kotlin/io/github/praneetx/motionfit/   # pure logic (Kotlin)
│       │   └── res/                                   # layouts, values, drawables,
│       │                                              # fonts, animations
│       ├── test/                                      # JVM unit tests
│       └── androidTest/                               # instrumented SQLite/UI/service tests
├── docs/
│   ├── architecture.md
│   ├── database.md
│   ├── setup.md
│   ├── testing.md
│   ├── limitations.md
│   ├── ui-design.md
│   ├── v2-spec.md
│   └── v2-plan.md
├── PRIVACY.md
├── README.md
├── build.gradle
├── settings.gradle
└── gradle/                                            # wrapper
```

## Data model (summary)

Five tables in a single local database (`fitness_tracker.db`):

| Table | Purpose |
|---|---|
| `daily_steps` | one consolidated step total per calendar date, plus the raw sensor checkpoint |
| `workouts` | individual workouts, including exact Running duration/step fields |
| `fitness_goals` | the single active goal record (step goal + workout-minutes goal) |
| `app_settings` | reminder preferences, automatic tracking opt-in and onboarding state |
| `running_sessions` | at most one in-progress/paused Running session (singleton row) |

Full schema, constraints and migration notes: [docs/database.md](docs/database.md).

## Roadmap

Everything below is **PLANNED** and not implemented yet.

* PLANNED — physical-device validation of sensor accuracy, notifications, FGS and OEM
  battery behaviour.
* PLANNED — data export and backup (CSV/JSON) and restore.
* PLANNED — encrypted local storage.
* PLANNED — full TalkBack and localisation/RTL audit.
* PLANNED — tablet and landscape layouts.
* PLANNED — continuous integration and a broader Espresso UI suite.
* PLANNED — home-screen widget.
* PLANNED — Play Store release: signing config, store listing, data-safety form and a
  reviewed privacy policy.

## Deliberately not included

Distance tracking, pace, routes, GPS and calorie-expenditure estimation are **not** part
of Motionfit. Cycling exists only as a workout category; no distance is recorded or
calculated. There is no network, account or cloud feature.

## Before publishing to Google Play

The application id is currently **`io.github.praneetx.motionfit`** (a temporary
identifier because this project has no domain yet). An application id is permanent
once published, so **change it before the first release** to an id you control
(for example `com.yourdomain.motionfit`), and generate a release signing key.

## Contributing

Issues and pull requests are welcome.

1. Keep the app **offline-first**: no `INTERNET` permission, no network calls, no
   analytics.
2. Do not add a dependency without explaining why the platform cannot do the job.
3. Run `.\gradlew.bat assembleDebug`, `.\gradlew.bat testDebugUnitTest` and
   `.\gradlew.bat lintDebug` before opening a pull request.
4. Keep location/GPS, distance, pace and calorie features out of the product.

## License

No license has been granted. This repository is **all rights reserved** — the source
is public for viewing, but no permission is given to copy, modify or redistribute it.
If you later want to allow reuse, add MIT or Apache-2.0.
