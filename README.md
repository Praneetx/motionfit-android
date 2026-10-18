# Motionfit

**An offline-first Android fitness tracker for daily steps, workouts and personal goals.**

Motionfit keeps a simple, private record of your movement: log a daily step total, add
workouts, set daily goals, and see how you are doing across the day, week and month.
Everything is stored on your phone — there is no account, no server and no internet
permission.

> **Status:** `0.1.0` — pre-release, not published on Google Play yet.

---

## Features

| Feature | Status |
|---|---|
| Today dashboard: steps ring, workout time, workout count, recent workouts | Available |
| Manual daily step entry, editing and history (one record per calendar day) | Available |
| Workout logging: add, view, edit, delete, filter by type | Available |
| Daily step goal and daily workout-minutes goal, with progress and percentages | Available |
| Progress summaries for day / week / month, with charts | Available |
| Optional daily reminder (local notification) | Available |
| Settings, including a confirmed "clear all data" action | Available |
| Automatic step counting from the device sensor | **Experimental** (needs a physical device with a step counter) |
| Distance tracking | **Not included** |
| Calorie estimation | **Not included** |

## Screenshots

_Placeholder — add screenshots of the Today, Progress, Goals and Settings screens here._

```
docs/screenshots/today.png
docs/screenshots/progress.png
docs/screenshots/goals.png
docs/screenshots/settings.png
```

## Tech stack

* **Java** — Activities, adapters, repositories, SQLite helper, validation and date utilities.
* **Kotlin** — goal-progress maths and activity-summary aggregation (called from Java).
* **XML layouts** — native Android views, no Compose.
* **SQLite** via `SQLiteOpenHelper` — local-only storage.
* **Material Components** + a custom design system (see [docs/ui-design.md](docs/ui-design.md)).
* Gradle 9.6.0 / Android Gradle Plugin 9.4.0, `compileSdk 37`, `minSdk 26`, `targetSdk 34`.

## Offline-first and privacy

* The app declares **no `INTERNET` permission** — it cannot send your data anywhere.
* No accounts, no sign-in, no analytics, no advertising SDKs, no crash-reporting SDKs.
* All data lives in a local SQLite database on the device.
* Reminders are scheduled locally with `AlarmManager`; notifications are generated on-device.

Read [PRIVACY.md](PRIVACY.md) for the draft privacy policy.

## Requirements

* Windows, macOS or Linux with PowerShell/Bash
* **JDK 17 or newer**
* Android SDK with platform **android-37** and `platform-tools`
* Android 8.0 (API 26) or newer on a device/emulator

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

## Tests

```powershell
# JVM unit tests (no device required)
.\gradlew.bat testDebugUnitTest

# Instrumented SQLite tests (requires a working emulator or device)
.\gradlew.bat connectedDebugAndroidTest
```

Current status: **34 JVM unit tests pass**, and the app assembles successfully.
The instrumented tests compile but have **not** been executed in a working device
environment yet, and the UI has not been visually verified. Details and honest
results: [docs/testing.md](docs/testing.md).

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
│       │   ├── kotlin/io/github/praneetx/motionfit/   # logic (Kotlin)
│       │   └── res/                                   # layouts, values, drawables,
│       │                                              # fonts, animations
│       ├── test/                                      # JVM unit tests
│       └── androidTest/                               # instrumented SQLite tests
├── docs/
│   ├── architecture.md
│   ├── database.md
│   ├── setup.md
│   ├── testing.md
│   ├── limitations.md
│   └── ui-design.md
├── PRIVACY.md
├── README.md
├── build.gradle
├── settings.gradle
└── gradle/                                            # wrapper
```

## Data model (summary)

Four tables in a single local database (`fitness_tracker.db`):

| Table | Purpose |
|---|---|
| `daily_steps` | one consolidated step total per calendar date |
| `workouts` | individual workouts (type, date, duration, optional notes) |
| `fitness_goals` | the single active goal record (step goal + workout-minutes goal) |
| `app_settings` | reminder preferences |

Full schema, constraints and migration notes: [docs/database.md](docs/database.md).

## Roadmap

Everything below is **PLANNED** and not implemented yet.

* PLANNED — background step tracking via a foreground service (current sensor support is foreground-only).
* PLANNED — data export and backup (CSV/JSON) and restore.
* PLANNED — encrypted local storage.
* PLANNED — full Espresso UI test suite and continuous integration.
* PLANNED — home-screen widget.
* PLANNED — localisation and a full accessibility pass.
* PLANNED — Play Store release: signing config, store listing, data-safety form.

## Deliberately not included

Distance tracking and calorie-expenditure estimation are **not** part of Motionfit.
Cycling exists only as a workout category; no distance is recorded or calculated.

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
3. Run `.\gradlew.bat assembleDebug` and `.\gradlew.bat testDebugUnitTest` before
   opening a pull request.
4. Keep distance tracking and calorie estimation out of the product.

## License

No license has been granted. This repository is **all rights reserved** — the source
is public for viewing, but no permission is given to copy, modify or redistribute it.
If you later want to allow reuse, add MIT or Apache-2.0.
