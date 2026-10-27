# Architecture

Motionfit is a native, **offline-first** Android application. There is no backend, no
cloud database, no login and no `INTERNET` permission. The UI, business logic, service
and data all live on the device.

## Layers

```
+------------------------------------------------------------------+
|  UI layer                                                        |
|  Java Activities + XML layouts + RecyclerView adapters           |
|  Custom views: FloatingNavView, StepsRingView                    |
+------------------------------+-----------------------------------+
                               v
+------------------------------------------------------------------+
|  Application logic                                               |
|  Java: ValidationUtils, DateUtils, FormatUtils                   |
|  Kotlin: StepCountEngine, RunningSessionClock/Controller,        |
|          CadenceTracker, WorkoutDuration, GoalProgressCalculator, |
|          ActivitySummaryCalculator                               |
+------------------------------+-----------------------------------+
                               v
+------------------------------------------------------------------+
|  Tracking / notification layer                                   |
|  ActivityTrackingService + StepCounterSource + NotificationHelper |
|  ReminderScheduler + ReminderReceiver + BootReceiver             |
+------------------------------+-----------------------------------+
                               v
+------------------------------------------------------------------+
|  Data access                                                     |
|  Java repositories, parameterised SQL, transactions              |
|  SQLiteOpenHelper (FitnessDatabaseHelper)                        |
+------------------------------+-----------------------------------+
                               v
+------------------------------------------------------------------+
|  SQLite: daily_steps, workouts, fitness_goals, app_settings,     |
|          running_sessions                                        |
+------------------------------------------------------------------+
```

Activities never contain SQL, and repositories never touch views. The service owns the
step sensor, the Running clock and the Running session lifecycle; the Running screen is
a thin observer that renders immutable snapshots and sends commands.

## Request flow: automatic daily steps

1. `ActivityTrackingService` starts only when automatic counting is enabled, activity
   access is granted, the sensor exists and Android permits the foreground service (or
   the app is visible for the app-open-only fallback).
2. `StepCounterSource` registers `TYPE_STEP_COUNTER`. No step-detector or
   accelerometer-peak source is used.
3. A raw reading is passed to the pure `StepCountEngine`, which compares it with the
   persisted `daily_steps.last_raw_sensor` checkpoint and returns a delta, an absolute
   calibration, or no write.
4. `StepsRepository` applies the write inside a transaction. Sensor writes are
   conditional on the checkpoint that was read; a concurrent manual calibration wins,
   and the service reloads and reapplies the reading instead of overwriting it.
5. The service broadcasts a daily-steps-changed signal; visible screens re-read the
   database.

A manual total goes through `StepsActivity` → `StepsRepository.calibrate`. When a raw
reading is available it becomes the new checkpoint, so the correction is not undone by
the next sensor reading. The sensor remains the automatic source of truth; manual entry
is an explicit calibration.

## Request flow: Running

1. `RunningActivity` binds to `ActivityTrackingService` and receives
   `RunningSnapshot` callbacks on the main thread.
2. The service owns the monotonic clock (`SystemClock.elapsedRealtime()`), the
   `CadenceTracker`, and a singleton `running_sessions` row through
   `RunningSessionRepository`.
3. Start creates the singleton row. Pause/resume update the clock snapshot. A periodic
   snapshot persists duration and steps so ordinary backgrounding or process death does
   not lose the run.
4. If the process/device restarts, the persisted boot count and elapsed values mark the
   run interrupted. Recovery freezes the last persisted duration and offers Resume,
   Finish and save, or Discard. Downtime is never added.
5. Finish runs in one transaction: re-read the session, compute exact duration, insert
   one `Running` workout, delete the session, commit. Any failure rolls back and leaves
   the session for retry. A second finish cannot insert a second workout.
6. The Running notification (when the service is foreground) has exactly Pause/Resume
   and Stop actions. Stop opens `RunningActivity` with a confirmation extra; it never
   silently saves.

## Lifecycle and tracking fallbacks

`MotionfitApplication` tracks whether at least one Motionfit Activity is started. That
signal lets the service run its app-visible fallback only while a screen is present.

The effective daily-tracking state is resolved by `TrackingModeResolver`:

| State | Meaning |
|---|---|
| `OFF` | automatic counting is disabled |
| `ACTIVE` | foreground service actually started and its notification is visible |
| `APP_OPEN_ONLY` | notifications are blocked or Android rejected the service; counting only while a screen is open |
| `UNAVAILABLE` | no step counter or activity permission denied |

`ACTIVE` is never claimed merely because the user flipped a switch. A rejected
foreground-service start falls back to app-visible counting and reports that truthfully.

Running is deliberately not promoted to a hidden background timer. If activity access
is denied, the timer-only fallback stays app-visible. If notifications are blocked, live
updates may stop when the screen is left; the timer is saved when the user returns.

## Data and persistence

* A single `FitnessDatabaseHelper` (process-wide singleton) owns the SQLite database.
* Schema version 2 has five tables; see [database.md](database.md).
* Calendar dates are stored as fixed-width ISO text (`YYYY-MM-DD`) so SQL text
  comparisons (`BETWEEN`, `ORDER BY`) are correct.
* The database is the source of truth; the UI is rebuilt from it after writes.
* Running finish is transaction-safe and exactly-once from the app's perspective.

## Notifications and reminders

* The reminder is a local `AlarmManager` alarm plus `ReminderReceiver`; a
  `BootReceiver` restores the reminder schedule after reboot.
* The optional tracking/Running notification uses a separate low-importance channel
  (`automatic_step_tracking`) with a generic public lock-screen version.
* Notification actions are local `PendingIntent`s. No remote push or cloud service is
  involved.

## Offline behaviour

No component performs network I/O, and the manifest declares no `INTERNET` permission.
Airplane mode therefore has no effect on launching the app, entering steps, managing
workouts, changing goals, running the timer, viewing summaries or reading the database.

## Why Java and Kotlin

* **Java** — Activities, adapters, custom views, the SQLite helper, every repository,
  the tracking service, notification/reminder helpers and the `DateUtils` /
  `ValidationUtils` / `FormatUtils` helpers.
* **Kotlin** — small, pure, unit-tested components:
  * `StepCountEngine` — checkpoint/delta/calibration/day-rollover rules.
  * `RunningSessionClock` and `RunningSessionController` — monotonic clock, pause/resume,
    recovery and command validation.
  * `CadenceTracker` — rolling 30-second cadence with a valid-data gate.
  * `WorkoutDuration` — exact seconds and rounded-up minutes.
  * `GoalProgressCalculator` and `ActivitySummaryCalculator` — progress and aggregates.

Both are Kotlin `object`s with `@JvmStatic` methods, so Java calls them like static
functions. The Kotlin components are covered by JVM tests written in Java, which proves
the interoperability in both directions.

Gradle note: AGP 9.x provides **built-in Kotlin**, so the project does not apply the
separate `org.jetbrains.kotlin.android` plugin; `src/main/kotlin` is added to the source
set in `app/build.gradle`.

## Platform limits

Android and device makers do not guarantee indefinite foreground-service execution.
Notification permission, foreground-service start rules, force-stop, reboot and OEM
battery policies can all change the effective mode. The app reports the mode it actually
achieved and documents these limits in [limitations.md](limitations.md). It does not
claim guaranteed background tracking.
