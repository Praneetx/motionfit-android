# Architecture

The Fitness Tracker is a **native, offline-first Android application**. There is no
backend server, no cloud database, no login and no internet permission. Everything
the app needs lives on the phone: the UI, the business logic and a local SQLite
database.

## 1. High-level layers

```
+--------------------------------------------------------------+
|                        Android UI layer                       |
|  Java Activities + XML layouts + RecyclerView adapters        |
|  (MainActivity, StepsActivity, WorkoutListActivity, ...)      |
+------------------------------+-------------------------------+
                               |  calls (Java)
                               v
+--------------------------------------------------------------+
|                     Application logic layer                   |
|  * Java: ValidationUtils, DateUtils                           |
|  * Kotlin: GoalProgressCalculator, ActivitySummaryCalculator  |
|    (goal percentages, zero-filling, totals, averages)         |
+------------------------------+-------------------------------+
                               |  calls (Java)
                               v
+--------------------------------------------------------------+
|                     Data access layer                         |
|  Java Repositories: StepsRepository, WorkoutRepository,       |
|  GoalRepository, SettingsRepository, SummaryRepository,       |
|  DashboardRepository, DataResetRepository                     |
|  Parameterised SQL + transactions                             |
+------------------------------+-------------------------------+
                               |  SQLiteOpenHelper
                               v
+--------------------------------------------------------------+
|                       SQLite database                         |
|  fitness_tracker.db : daily_steps, workouts,                  |
|                       fitness_goals, app_settings             |
+--------------------------------------------------------------+

Optional native integrations (never required for core features)
  * Step-count sensor -> StepsActivity (EXPERIMENTAL)
  * Local notifications -> ReminderScheduler + ReminderReceiver
```

Each layer only talks to the layer directly below it. Activities never contain
raw SQL, and repositories never touch Android views.

## 2. Request flow example - "save today's steps"

1. `StepsActivity` reads the text field and calls
   `ValidationUtils.parseNonNegative(...)` (Java) to validate the number.
2. `StepsActivity` calls `StepsRepository.saveDailySteps(date, steps, "manual")`.
3. `StepsRepository` runs an `UPDATE` and, only if no row exists, an `INSERT`
   inside a transaction. The `daily_steps.activity_date UNIQUE` constraint is the
   final guard against duplicates.
4. When the write succeeds the Activity finishes and the dashboard's `onResume()`
   re-reads the database, so the new total is always visible immediately.

Reading the dashboard is the reverse: `MainActivity` -> `DashboardRepository` ->
`SummaryRepository`/`GoalRepository`/`WorkoutRepository` -> SQLite, and then the
values travel back up into the XML views.

## 3. Why the business logic is split between Java and Kotlin

* **Java** is the primary language: all Activities, adapters, the SQLite helper,
  every repository, and the `DateUtils`/`ValidationUtils` helpers are Java. This
  matches the "Java for most of the app" requirement.
* **Kotlin** owns two small, pure, thoroughly unit-tested components:
  * `GoalProgressCalculator` - percentage maths, divide-by-zero safety, clamping.
  * `ActivitySummaryCalculator` - zero-filling missing days and summing activity.

Both are Kotlin `object`s whose methods are annotated with `@JvmStatic`, so Java
can call them like static functions:

```java
// Java calling Kotlin
int percent = GoalProgressCalculator.clampedPercent(steps, goal);
List<DayActivity> series = ActivitySummaryCalculator.zeroFill(dates, recorded);
```

`DayActivity` is a Kotlin `data class`; Java reads it through normal getters
(`day.getSteps()`). The Kotlin unit tests (`GoalProgressCalculatorTest`,
`ActivitySummaryCalculatorTest`) are written in Java, which proves the
Java-to-Kotlin direction works both at compile time and at run time.

Gradle note: AGP 9.x has **built-in Kotlin** support, so the project does not
apply the old `org.jetbrains.kotlin.android` plugin. The `:app` module simply adds
`src/main/kotlin` to its source set and AGP compiles it.

## 4. Local persistence

* One `SQLiteOpenHelper` subclass (`FitnessDatabaseHelper`) is shared process-wide.
* Tables are created automatically on first launch and defaults are seeded.
* All dates are stored as fixed-width ISO text (`YYYY-MM-DD`) so text
  comparisons in SQL (`BETWEEN`, `ORDER BY`) are correct.
* The database is the single source of truth; the UI is always rebuilt from it
  after a write rather than being patched in place.

## 5. Offline behaviour

No component performs network I/O. The manifest contains **no INTERNET
permission**, and the merged manifest was checked to confirm no dependency adds
one. Airplane mode therefore has no effect on starting the app, entering steps,
managing workouts, changing goals or viewing summaries.

## 6. Optional integrations

* **Step sensor** (`StepCounterSensorHelper`) reads `TYPE_STEP_COUNTER` while the
  activity is in the foreground. It is explicitly labelled EXPERIMENTAL and is
  never required; manual entry always works.
* **Reminders** use `AlarmManager` + a `BroadcastReceiver` +
  `NotificationManager`. The receiver re-schedules the next daily alarm, and a
  `BOOT_COMPLETED` receiver restores the schedule after a reboot. Limits are
  documented in `docs/limitations.md`.
