# Architecture

Motionfit is a native, **offline-first** Android application. There is no backend, no
cloud database, no login and no `INTERNET` permission. The UI, business logic and data
all live on the device.

## Layers

```
+--------------------------------------------------------------+
|  UI layer                                                    |
|  Java Activities + XML layouts + RecyclerView adapters       |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  Application logic                                           |
|  Java: ValidationUtils, DateUtils                            |
|  Kotlin: GoalProgressCalculator, ActivitySummaryCalculator   |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  Data access                                                 |
|  Java repositories, parameterised SQL, transactions          |
|  SQLiteOpenHelper (FitnessDatabaseHelper)                    |
+------------------------------+-------------------------------+
                               v
+--------------------------------------------------------------+
|  SQLite: daily_steps, workouts, fitness_goals, app_settings  |
+--------------------------------------------------------------+

Optional device integrations (never required for core features)
  * step-count sensor -> Steps screen (experimental)
  * local notifications -> ReminderScheduler + ReminderReceiver
```

Each layer talks only to the layer below it. Activities never contain SQL, and
repositories never touch views.

## Request flow: saving a day's steps

1. `StepsActivity` validates the number with `ValidationUtils.parseNonNegative` (Java).
2. It calls `StepsRepository.saveDailySteps(date, steps, "manual")`.
3. The repository runs an `UPDATE` and, only if no row exists, an `INSERT`, inside a
   transaction. The `daily_steps.activity_date UNIQUE` constraint is the final guard
   against duplicates.
4. On success the screen refreshes; the dashboard re-reads the database in `onResume`,
   so the new total is always shown.

Reading the dashboard is the reverse: `MainActivity` → `DashboardRepository` →
`SummaryRepository` / `GoalRepository` / `WorkoutRepository` → SQLite.

## Why Java and Kotlin

* **Java** — all Activities, adapters, the SQLite helper, every repository, and the
  `DateUtils` / `ValidationUtils` helpers.
* **Kotlin** — two small, pure, unit-tested components:
  * `GoalProgressCalculator` — percentages, clamping, divide-by-zero safety.
  * `ActivitySummaryCalculator` — zero-filling missing days, totals and averages.

Both are Kotlin `object`s with `@JvmStatic` methods, so Java calls them like static
functions, and `DayActivity` is a Kotlin `data class` read through normal getters.
The Kotlin components are covered by JVM tests written in Java, which proves the
interoperability in both directions.

Gradle note: AGP 9.x provides **built-in Kotlin**, so the project does not apply the
separate `org.jetbrains.kotlin.android` plugin; `src/main/kotlin` is added to the
source set in `app/build.gradle`.

## Local persistence

* A single `FitnessDatabaseHelper` (process-wide singleton) owns the database.
* Tables and default rows are created on first launch.
* Calendar dates are stored as fixed-width ISO text (`YYYY-MM-DD`) so SQL text
  comparisons (`BETWEEN`, `ORDER BY`) are correct.
* The database is the source of truth; the UI is rebuilt from it after writes.

## Offline behaviour

No component performs network I/O, and the manifest declares no `INTERNET`
permission. Airplane mode therefore has no effect on launching the app, entering
steps, managing workouts, changing goals or viewing summaries.

## Optional integrations

* **Step sensor** — `StepCounterSensorHelper` reads `TYPE_STEP_COUNTER` only while the
  Steps screen is open. It is marked experimental and never required.
* **Reminders** — `AlarmManager` + `BroadcastReceiver` + `NotificationManager`. The
  receiver re-schedules the next daily alarm and a boot receiver restores it after a
  restart. Limits are listed in [limitations.md](limitations.md).
