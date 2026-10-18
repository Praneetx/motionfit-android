# Fitness Tracker — Native Android College Project

A **fully offline**, native Android fitness-tracking app built with **Java, Kotlin,
XML layouts and SQLite**. It records daily step totals, logs workouts, manages
personal goals, and shows daily / weekly / monthly progress summaries.

> **Excluded on purpose:** this project does **not** implement distance tracking and
> does **not** estimate calories. Cycling exists only as a workout category.

---

## 1. Problem statement

People often struggle to keep a consistent exercise habit because they have no
convenient way to record daily activity, log workouts, set personal goals, and
review their progress in one place. This app provides a simple, accessible,
**offline** tool that lets a user record steps, manage workout history, set fitness
goals, and review activity summaries.

## 2. Objectives

* Record a daily step total (one consolidated record per calendar day).
* Create, view, edit and delete workouts.
* Configure a daily step goal and a daily workout-minutes goal.
* Show progress toward both goals on a dashboard.
* Provide daily, weekly and monthly activity summaries with a chart.
* Provide optional local reminders to stay active.
* Store everything locally and work with no internet connection.
* Demonstrate native Android development with **both Java and Kotlin**.

## 3. Features

| Feature | Status |
|---|---|
| Dashboard (steps, workout count/minutes, goal progress, recent workouts) | IMPLEMENTED |
| Manual daily step entry + editing + history + duplicate prevention | IMPLEMENTED |
| Workout add / view / edit / delete + history + type filter + confirmation dialogs | IMPLEMENTED |
| Daily step goal + daily workout-minutes goal (validated) | IMPLEMENTED |
| Daily / weekly / monthly summaries + custom bar chart | IMPLEMENTED |
| Optional step-count sensor | EXPERIMENTAL (graceful fallback, not verified on real hardware) |
| Local reminders (AlarmManager + notifications) | IMPLEMENTED (timing limits documented) |
| Settings + destructive "clear all data" with confirmation | IMPLEMENTED |
| Offline operation | IMPLEMENTED and verified (no INTERNET permission) |
| Distance tracking | **NOT IMPLEMENTED (on purpose)** |
| Calorie estimation | **NOT IMPLEMENTED (on purpose)** |

## 4. Technology stack

| Layer | Technology |
|---|---|
| Language (primary) | **Java** |
| Language (secondary) | **Kotlin** |
| UI | **Android XML layouts**, native Views, RecyclerView, Material Components |
| Database | **SQLite** via `SQLiteOpenHelper` |
| Build system | Gradle 9.6.0 + Android Gradle Plugin 9.4.0 |
| Testing | JUnit 4 (JVM) + AndroidX Test / AndroidJUnit4 (instrumented) |
| SDK | `compileSdk 37`, `minSdk 26`, `targetSdk 34` |

No Firebase, no backend, no REST API, no cloud database, no Flutter, no Compose.

## 5. Java and Kotlin responsibilities

### Kotlin (small, pure, heavily tested business logic)
```
app/src/main/kotlin/com/example/fitnesstracker/logic/
├── GoalProgressCalculator.kt      # percentages, clamping, divide-by-zero safety
├── ActivitySummaryCalculator.kt   # zero-fill missing days, totals, averages
└── DayActivity.kt                 # immutable per-day data class
```
These are Kotlin `object`s whose methods are `@JvmStatic`, so Java calls them like
static methods. They are covered by the JVM tests
`GoalProgressCalculatorTest` and `ActivitySummaryCalculatorTest` (written in Java) —
this is the explicit Java↔Kotlin interoperability proof.

### Java (everything else)
```
app/src/main/java/com/example/fitnesstracker/
├── activities/    MainActivity, StepsActivity, WorkoutListActivity,
│                  WorkoutEditActivity, GoalsActivity, SummaryActivity, SettingsActivity
├── adapters/      WorkoutAdapter, StepHistoryAdapter
├── database/      DatabaseContract, FitnessDatabaseHelper
├── models/        DailySteps, Workout, FitnessGoal, AppSettings, DaySummary, DashboardData
├── repositories/  StepsRepository, WorkoutRepository, GoalRepository,
│                  SettingsRepository, SummaryRepository, DashboardRepository,
│                  DataResetRepository
├── services/      ReminderScheduler, ReminderReceiver, BootReceiver,
│                  NotificationHelper, StepCounterSensorHelper
├── utils/         DateUtils, ValidationUtils, OperationResult, FormatUtils
└── views/         BarChartView (custom Canvas bar chart, no chart library)
```

## 6. Prerequisites

* Windows 10/11 with PowerShell
* A JDK **17 or newer** (this project was built with the Android Studio JBR, JDK 25)
* Android SDK with platform **android-37** and `platform-tools`
* An Android emulator or a physical device (API 26+)

## 7. Folder structure

```
Fitness tracker/
├── build.gradle                 # root: declares AGP 9.4.0
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/              # pinned Gradle 9.6.0
├── local.properties             # SDK path (machine specific, git-ignored)
├── .gitignore
├── README.md
├── docs/
│   ├── architecture.md
│   ├── database.md
│   ├── setup.md
│   ├── testing.md
│   └── limitations.md
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/            # Java source (see section 5)
        │   ├── kotlin/          # Kotlin logic (see section 5)
        │   └── res/             # XML layouts, drawables, values, mipmaps
        ├── test/                # JVM unit tests
        └── androidTest/         # instrumented SQLite tests
```

## 8. Installation and build (exact PowerShell commands)

```powershell
# 1. Go to the project folder
cd 'C:\Users\PRANIT DESHMUKH\Desktop\Fitness tracker'

# 2. Point Gradle at a JDK (required on this machine because `java` is not on PATH)
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

# 3. Build the debug APK
.\gradlew.bat assembleDebug

# 4. Check that a device/emulator is connected
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
#    (if ANDROID_HOME is not set, use the full path:
#     & "C:\Users\PRANIT DESHMUKH\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices)

# 5. Install the app
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# 6. Launch it
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n io.github.praneetx.motionfit/.activities.MainActivity
```

The APK is written to:

```
app\build\outputs\apk\debug\app-debug.apk
```

## 9. Testing instructions

```powershell
# JVM unit tests (no device needed) -- 34 tests
.\gradlew.bat testDebugUnitTest

# Instrumented SQLite tests (needs a working emulator/device)
.\gradlew.bat connectedDebugAndroidTest
```

See **`docs/testing.md`** for the full test table and the honest results from this
environment.

## 10. Database structure

Four tables in `fitness_tracker.db`:

| Table | Purpose |
|---|---|
| `daily_steps` | one row per calendar day (`activity_date` is **UNIQUE**), `steps >= 0` |
| `workouts` | one row per workout; `duration_minutes > 0`; optional notes |
| `fitness_goals` | exactly one row (`CHECK (id = 1)`) with both daily goals |
| `app_settings` | key/value reminder preferences |

Full schema, constraints and CRUD mapping in **`docs/database.md`**.

## 11. Architecture

```
+--------------------------------------------------------------+
|  UI layer: Java Activities, XML layouts, RecyclerView        |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  Application logic: Java ValidationUtils/DateUtils           |
|  + Kotlin GoalProgressCalculator / ActivitySummaryCalculator |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  Data access: Java repositories, parameterised SQL,          |
|  transactions, SQLiteOpenHelper                              |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  SQLite database: daily_steps, workouts, fitness_goals,      |
|  app_settings                                                |
+--------------------------------------------------------------+
```

## 12. Why it works offline

There is **no INTERNET permission** in the manifest, and the merged manifest was
inspected to confirm no dependency adds one. The app only reads and writes the local
SQLite file, so airplane mode does not affect launching, step entry, workouts,
goals or summaries. The only optional system features are the local step sensor and
local notifications — neither uses a network.

## 13. Demonstration scenarios

1. **Log steps** — Dashboard → *Log steps* → type `6500` → save → the dashboard
   step total and goal percentage update immediately.
2. **Edit the same day** — Log steps again with `8000`; the history still shows one
   record for that date (no duplicates).
3. **Add a workout** — *Add workout* → choose *Running*, duration `30`, save; it
   appears in *Recent workouts* and in the dashboard's minutes total.
4. **Edit / delete** — open the workout, change the duration, or delete it after the
   confirmation dialog.
5. **Goals** — Goals → set 9000 steps / 40 minutes → the dashboard progress bars and
   percentages recalculate, but past records are unchanged.
6. **Progress** — Progress → switch between Day / Week / Month and move between
   periods; totals come from SQLite and zero-activity days show as zero-height bars.
7. **Reminders** — Settings → enable the reminder, choose a time, press *Send test
   notification* to confirm local notifications work.
8. **Clear data** — Settings → *Clear all local data* → confirm → all records are
   gone and goals/settings are back to defaults.
9. **Offline** — enable airplane mode and repeat steps 1–6.

## 14. Actual test results (summary)

| Check | Result |
|---|---|
| `assembleDebug` (app APK) | **PASS** — BUILD SUCCESSFUL |
| `testDebugUnitTest` (34 JVM tests) | **PASS** — 34 tests, 0 failures |
| `assembleDebugAndroidTest` (test APK compiles) | **PASS** |
| APK installs on emulator | **PASS** — `Success` |
| App process starts on emulator with no app crash | **PASS** |
| Visual dashboard verification on emulator | **NOT RUN** — emulator framebuffer stayed black / system timed out (host RAM limit) |
| `connectedDebugAndroidTest` | **NOT RUN** — emulator too resource-starved; run it on a working device |

Full details, commands and environment limitations: **`docs/testing.md`**.

## 15. Known limitations

Local-only unencrypted storage; data is lost on uninstall or *Clear storage*; no
cloud sync or accounts; the step sensor is experimental and only reads while the
screen is open; reminders are inexact and stop after a force-stop; no physical-device
testing was possible. Read **`docs/limitations.md`** for the complete list.

## 16. Future improvements

* Optional background step tracking with a foreground service.
* Encrypted local storage (SQLCipher) and an export/backup feature.
* Full Espresso UI test suite and CI.
* Accessibility and localisation pass (the UI is English-only today).
* Exact-alarm scheduling with a per-OEM battery-optimisation guide.
* Dark-theme polish and tablet layouts.

---

**Bottom line:** a beginner-friendly, runnable, offline native Android app using
Java + Kotlin + XML + SQLite, with step tracking, workout logging, goals and
progress summaries — and no distance or calorie features.
