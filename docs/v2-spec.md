# Motionfit V2 — Specification

**Status:** approved 2026-10-25; implemented 2026-10-27

**Date:** 2026-10-25

**Baseline:** `v0.1.0` (`eaef9ae`), branch `v2-dev`

**Target release:** `2.0.0` (versionCode 2) — implemented on `v2-dev`; not pushed or published

**This document changes no source code.** It is the contract for the V2 plan.

**Implementation status (2026-10-27):** all 17 tasks are complete. The app builds as
`2.0.0` (versionCode 2), and Settings displays `Version 2.0.0`. Final automated
results and the manual `NOT RUN` rows are recorded in [testing.md](testing.md) and the
final-results index in [v2-plan.md](v2-plan.md). Nothing has been pushed or published.

---

## 1. Purpose

V2 has three goals and nothing else:

1. **Make step counting accurate.** Replace the foreground-only, one-shot sensor
   experiment with a clear daily contract: `TYPE_STEP_COUNTER` is the automatic
   source of truth; a manual entry is an explicit calibration of that source.
   There must be no double counting, no lost checkpoint across restarts, and no
   write that silently undoes a correction.
2. **Improve UI/UX.** Fix accessibility and layout resilience first, then add
   first-run onboarding and permission education, then visual polish.
3. **Add Running mode** as the middle destination of the floating nav. Running
   uses a monotonic timer, live steps and a rolling cadence, persists across
   ordinary lifecycle events, and saves exactly one Running workout.

Everything remains **fully offline**: no internet permission, no analytics, no
accounts, no cloud, no location permission, no GPS, no distance, no pace, no
route maps and no calorie estimation. V2 adds no third-party dependency.

---

## 2. Decisions already made

| Question | Decision |
|---|---|
| Q1 navigation | `Today / Workouts / Run / Progress / Settings`. Steps becomes a detail screen reachable from Today and Progress. |
| Q2 Running scope | No GPS. Start/Pause/Resume/Stop with confirmation, monotonic timer, live steps, rolling cadence, persistence and exactly one saved workout. |
| Q3 UI order | A: accessibility/layout resilience → B: onboarding/permissions → C: visual polish. |
| Q4 automatic steps | Opt-in, always-on foreground service with a persistent notification. If notification permission is denied, fall back to app-open-only counting with a clear status, not a hidden service. |
| Q5 manual vs sensor | **A:** sensor-authoritative daily totals with manual calibration. A manual entry becomes the new total; future sensor steps continue from that checkpoint. |

### Additional decisions proposed by this spec

These are part of the approval request, not hidden assumptions:

- The daily step checkpoint is `last_raw_sensor`, not a separate "baseline"
  column. The total is updated by deltas and the checkpoint is the concurrency
  token. This is the persisted baseline-equivalent.
- One tracking service owns both daily counting and Running state. There is one
  sensor listener and one foreground notification, never two competing ones.
- No `TYPE_STEP_DETECTOR` or accelerometer-peak fallback. Devices without a step
  counter use manual daily entry and a timer-only Run.
- Running stores exact `duration_seconds`; `duration_minutes` remains for the
  existing summary screens and is rounded **up** to at least 1.
- V2 release version is `2.0.0` / versionCode 2.
- Existing V1 users see the short onboarding once after the upgrade; it is
  skippable and does not overwrite their data.
- Fix lint **errors** in files touched by V2. Do not chase the 91 pre-existing
  unused-resource warnings or unrelated overdraw warnings.

---

## 3. Scope

### 3.1 In scope

| Area | V2 outcome |
|---|---|
| Step source | `TYPE_STEP_COUNTER` is the automatic source of truth. Manual entry calibrates the total. |
| Automatic tracking | Opt-in foreground service, persistent low-importance notification, daily database updates, recovery after process death. |
| Manual fallback | Manual daily entry always works. Auto tracking can be disabled or unavailable without breaking the app. |
| Notification-denied fallback | No always-on hidden service. Count only while a Motionfit screen is visible and show the reason. |
| Navigation | Five destinations: Today, Workouts, Run, Progress, Settings. Steps detail is not a root destination. |
| Steps detail | Back navigation, selected date, calibration-aware entry, full history, automatic-tracking status. |
| Progress | Recent step history (last 7 recorded days) and a link to the full Steps detail. |
| Running | Full-screen session: Start/Pause/Resume/Stop, confirmation, timer, steps, cadence, recovery, summary, one saved workout. |
| Database | Version 2, additive migration from version 1, data preserved, tests from a real version-1 database. |
| UI | Contrast, 48dp targets, 1.3x font at 360dp, keyboard, empty/error states, custom-view accessibility, reduced motion. |
| Onboarding | First-run explanation, goals, optional automatic tracking, permission education, skip. |
| Docs | Spec, plan, schema, architecture, testing, limitations, UI docs, README/version. |

### 3.2 Out of scope

- GPS, location permission, distance, pace, route maps, altitude, speed.
- Calories, heart rate, sleep, body measurements, accounts, sync, export/import.
- Wear OS, widgets, tablets/landscape-specific redesign, dark mode.
- New libraries, Compose, analytics, crash reporting, ads, cloud.
- Changing workout categories, goals semantics, reminder semantics, summary
  maths, or existing screen behaviour except where this document says so.
- Guaranteed background tracking on every Android/OEM configuration. The app
  does its best and documents the limits.

### 3.3 Compatibility rules

- Keep existing database file name and table names that are not changing.
- Preserve existing view IDs and public method names where the UI remains.
- Do not change `minSdk` (26), `targetSdk` (34) or add permissions unrelated to
  the approved service/permission work.
- Do not delete user data during upgrade. No destructive migration.
- Do not invent sensor readings, steps, distance or workout history.

---

## 4. Current baseline

Repository state at the time of writing:

- Branch `v2-dev`, HEAD `eaef9ae`, tag `v0.1.0`, clean working tree.
- `origin/main` is at the same commit; nothing pushed.
- Pre-interview WIP is stashed as `stash@{0}` and will not be restored blindly.

Current behaviour that V2 must correct:

- `StepCounterSensorHelper` collects `TYPE_STEP_COUNTER` only while the Steps
  screen is open; `StepsActivity.onPause()` stops it.
- The baseline is in memory and session-only. A manual entry overwrites the
  day's absolute total; the sensor button adds `existing + sessionSteps` once.
- There is no persisted daily sensor checkpoint and no background service.
- `daily_steps` has a unique date row, but no raw-counter checkpoint.
- `workouts` stores whole minutes only; no exact seconds and no step count.
- Automatic tracking is labelled experimental and is not reachable from Today.
- Steps is a bottom-nav destination; navigation has no Run.

Build/test baseline:

| Check | Expected | Actual | Result |
|---|---|---|---|
| `assembleDebug --rerun-tasks` | success | BUILD SUCCESSFUL, 35 tasks | PASS |
| `testDebugUnitTest --rerun-tasks` | all pass | 34 tests, 0 failures/errors/skips | PASS |
| `assembleDebugAndroidTest` | success | BUILD SUCCESSFUL | PASS |
| `connectedDebugAndroidTest` | run on emulator/device | not run | NOT RUN |
| `lintDebug` | no errors | 29 errors, 114 warnings | FAIL |
| Physical-device sensor/notification tests | real device | not run | NOT RUN |

`lintDebug` errors are 8 duplicate IDs in the nav, 19 `UseAppTint`, one API-27
`windowLightNavigationBar` use and one environment-only `local.properties`
escape warning. Warnings are mostly 91 unused resources. V2 fixes the source
errors in files it touches; it does not add a lint baseline.

---

## 5. Architecture overview

```
MotionfitApplication (activity visibility)
        |
        v
ActivityTrackingService  (single owner)
  - one TYPE_STEP_COUNTER listener
  - daily step engine + repository writes
  - Running session clock/state + step count + cadence
  - foreground notification (when legal)
        |
        +--> StepsRepository / RunningSessionRepository / WorkoutRepository
        |
        +--> Binder -------> RunningActivity (live timer/steps/cadence)
        |
        +--> package-scoped daily-step broadcast
                              Today / Steps detail / Progress refresh
```

### 5.1 One owner

Only the tracking service writes sensor-derived daily totals while automatic
tracking is active. This prevents the service and an Activity from calculating
different totals from the same raw counter. When the service is not active,
manual editing goes through `StepsRepository`, which clears the sensor
checkpoint for a manual day.

Manual calibration while the service is active is routed through the service
(or serialized through a repository method that the service reloads) so the
total and `last_raw_sensor` move together. A stale in-memory checkpoint must
never re-add steps that a correction removed.

### 5.2 Service modes

The service has two execution modes:

1. **Foreground mode** — used when the app may legally keep a health foreground
   service: automatic tracking is on, `ACTIVITY_RECOGNITION` is granted, a step
   counter exists, and for daily-only tracking the user allows notifications.
   A persistent low-importance notification is shown. A Running session may use
   foreground mode even if notifications are denied, because the session is
   explicitly user-started; the UI warns that Android is hiding the notice.
2. **App-visible fallback** — used when a foreground service is not legal or not
   wanted. The service only runs while a Motionfit screen is visible. It stops
   when the app leaves the foreground. No hidden always-on service. The UI says
   "Automatic counting is paused — allow notifications to count in the
   background" or "Background timing is unavailable without activity access."

Android 14+ requires a health foreground service to have
`ACTIVITY_RECOGNITION`. If that permission is denied, the app does **not**
pretend it can run a health FGS. Running still starts/pauses/resumes/stops and
computes time from `elapsedRealtime()`; the session is persisted and recovered,
but background execution is not promised. This is a documented platform limit.

### 5.3 Threading and lifecycle

- Sensor callbacks arrive on the main thread. Database work is small and
  synchronous inside short transactions; no long work on the main thread.
- All writes that update `steps` and `last_raw_sensor` are serialized.
- Daily persistence and notification updates are throttled (at most every 10
  seconds or 100 steps). A process death loses at most the unpersisted delta,
  which is recovered from the raw counter on restart; no duplicate is added.
- Flush on pause, date change, service stop and Running finish.
- Running UI ticks while visible; no background polling or wake lock.
- Activity binds/unbinds from the service for live Running state and unregisters
  receivers in `onStop`. No static listener leaks.

### 5.4 Updates to visible screens

- Daily step changes are broadcast package-scoped after a persisted write,
  throttled. Today, Steps detail and Progress refresh from SQLite on resume and
  on that broadcast.
- RunningActivity binds to the service and receives state callbacks. It must
  also reload from the repository when it resumes, because the service may have
  been restarted or the activity may have been recreated.
- No polling loop is left running in the background.

---

## 6. Step-counting contract

### 6.1 Source rules

- The automatic source is `Sensor.TYPE_STEP_COUNTER` **only**. It reports a
  cumulative value since boot, not a daily count.
- The app never sums `TYPE_STEP_DETECTOR` events and never invents steps from
  the accelerometer. Devices without a counter use manual entry.
- The app reads the sensor with `SENSOR_DELAY_NORMAL`. It does not request a
  wake lock, high sampling rate, or exact alarms.
- Raw values are rounded from the sensor float to a `long`. A non-increasing
  reading is not allowed to reduce the displayed daily total.

### 6.2 Daily state

The app stores, for each date:

- `steps` — the consolidated daily total (existing column);
- `last_raw_sensor` — the latest raw counter value observed for that day, or
  NULL when no automatic reading has been applied;
- `source` — `manual`, `sensor`, or `sensor_adjusted`.

`last_raw_sensor` is the persisted checkpoint. There is no second baseline
column. The next automatic total is the current total plus
`max(0, raw - last_raw_sensor)`, with the reboot/date rules below.

### 6.3 Applying a reading

For a reading `raw` on date `D`:

1. **No row for `D`** — create it with `steps = 0`,
   `last_raw_sensor = raw`, `source = sensor`. Steps taken before the first
   reading are not claimed.
2. **Row has no checkpoint** (a manual row, or an upgraded row) — keep
   `steps`, set `last_raw_sensor = raw`, and change `source` to
   `sensor_adjusted`. Future readings add from this point. This is the manual
   calibration rule after an upgrade.
3. **Row has a checkpoint and `raw >= checkpoint`** — add
   `raw - checkpoint` to `steps`; set checkpoint to `raw`. Never write a
   negative delta.
4. **Row has a checkpoint and `raw < checkpoint`** — a reboot or sensor-epoch
   reset is assumed. Keep `steps`, set checkpoint to `raw`, and do not add the
   difference. Steps taken between the reboot and the first new reading are
   conservatively not counted; this avoids a double count.
5. **Duplicate/zero reading** — write nothing.

The update must be atomic. The service may use
`UPDATE ... SET steps = steps + ?, last_raw_sensor = ? WHERE activity_date = ?
AND last_raw_sensor = ?`. If zero rows change, another writer calibrated the
row; reload and recompute rather than overwriting it.

### 6.4 Manual calibration

When the user enters a total `T`:

- If automatic tracking is **off**: save `steps = T`, `last_raw_sensor = NULL`,
  `source = manual`. Manual mode is authoritative; the sensor is not read.
- If automatic tracking is **on** and a live raw value is available: save
  `steps = T`, `last_raw_sensor = rawNow`, `source = sensor_adjusted`.
  The next reading adds from `rawNow`, so the correction is not undone.
- If automatic tracking is **on** but no raw value is available: save
  `steps = T`, `last_raw_sensor = NULL`, `source = manual`. The next sensor
  reading applies rule 6.3.2 and preserves `T`.

If the tracking service is active, calibration is routed through the service
(or through a repository operation that makes the service reload) so the
in-memory checkpoint cannot race the correction. Manual editing of a past date
never receives future sensor updates and clears that date's checkpoint.

### 6.5 Day rollover

- While the service is continuously tracking, the first reading on a new local
  date starts a new day with the steps observed since the previous reading:
  `newTotal = max(0, raw - previousCheckpoint)`, with the new checkpoint set to
  `raw`. The previous day's row is not modified.
- If a row already exists for the new date (for example a manual total), keep
  that total and set the checkpoint to `raw`; future steps add from there.
- If the service was not running across midnight, the app does **not** split
  the unknown gap between the two days. The first reading of the new day starts
  a new checkpoint. This is conservative and is documented as a limitation.
- A backwards local-date change follows the same conservative rule: keep any
  existing row for that date, set the checkpoint to the current raw value, and
  do not subtract steps.
- Time-zone changes can move the local day boundary. The app never fabricates
  history to cover a boundary it did not observe.

### 6.6 Reboot, process death, and gaps

- **Process death, same boot:** the row's `last_raw_sensor` checkpoint survives.
  The first new reading is `raw >= checkpoint`; the delta since the last write
  is recovered. No duplicate is added.
- **Reboot:** if the raw counter decreased, rule 6.3.4 preserves the total and
  restarts the checkpoint. The first reading after boot is the new epoch.
- **Service restarted after a long gap on the same day:** the delta since the
  checkpoint is recovered. If the gap crosses midnight, the conservative
  day-rollover rule applies.
- **Force-stop / app killed:** tracking resumes when the app or a legal
  foreground service next starts. No claim is made that Android will restart a
  force-stopped app.

### 6.7 Double-counting rules

- Running steps are a snapshot of the same raw counter. They are **never added**
  to the daily total as a second write.
- A Running workout's step count is metadata for that workout. Daily steps are
  already updated by the service when automatic tracking is on.
- If automatic tracking is off, a Running session can still show and save its
  own steps, but the daily total remains manual. The UI says this explicitly.
- No "Add N sensor steps" button exists in V2. All sensor writes are automatic
  or an explicit absolute calibration.
- Clearing a correction, re-enabling auto tracking, or restarting the service
  must not re-add steps that were already included in the corrected total.

### 6.8 Effective automatic-tracking status

The UI must show one of these states, not a vague "on/off" alone:

| State | Meaning |
|---|---|
| `OFF` | User has not enabled automatic counting. Manual entry only. |
| `ACTIVE` | Foreground service is running; automatic daily steps are being written. |
| `APP_OPEN_ONLY` | Enabled, but notifications are blocked, so no always-on service. Counting happens while a screen is visible. |
| `UNAVAILABLE` | No step counter, or `ACTIVITY_RECOGNITION` denied, or the service failed to start. Manual entry still works. |

`ACTIVE` requires a persistent notification. The app must not run an always-on
automatic tracker with its notification hidden. A running session is different:
it is explicit, user-started and visible in the UI, so it may use foreground
mode while notifications are denied, with an in-app warning.

### 6.9 When permission or sensor is missing

- **No step counter:** automatic counting is `UNAVAILABLE`; manual daily entry
  works. Running is timer-only; steps/cadence show "—"; saved workout step count
  is NULL.
- **`ACTIVITY_RECOGNITION` denied:** same manual/running fallback. On Android
  14+, a health FGS is not started; Running uses the app-visible fallback and
  says background timing is not available.
- **Notification permission denied:** automatic counting uses
  `APP_OPEN_ONLY`; no always-on FGS. Running may still use FGS if activity
  access is granted, with a visible in-app warning that Android is hiding the
  notification.
- **FGS start rejected by the OS:** catch it, log without user data, fall back
  to app-visible mode, and show the status. Do not crash or silently pretend
  tracking is active.

---

## 7. Running mode

### 7.1 Destination and screen

- Running is the middle tab: `Today | Workouts | Run | Progress | Settings`.
- `RunningActivity` is full-screen and does **not** show the floating nav.
- It is the same screen before, during and after a session:
  - **Ready:** title, "Timer and steps only. No GPS, distance or pace.",
    permission/status card, Start button.
  - **Running:** large timer, steps, cadence, Pause and Stop.
  - **Paused:** frozen timer, cadence "—", Resume and Stop.
  - **Finishing/Summary:** progress state, then duration/steps saved summary.
  - **Recovery:** interrupted-session explanation with Resume/Finish/Discard.
- The system Back button leaves the screen; an active session continues in the
  background (or app-visible fallback). The Run tab reopens it.
- While a session is active and the screen is visible, keep the screen on with
  `FLAG_KEEP_SCREEN_ON`. Clear it when paused, finished or stopped. This is not
  a wake lock.

### 7.2 Timer

- Use `SystemClock.elapsedRealtime()` only. Never use wall-clock time for
  elapsed duration.
- The pure clock state is `startedElapsedMs`, `accumulatedMs`,
  `lastResumedElapsedMs`, and `status`.
- Start: `startedElapsed = now`, `accumulated = 0`, `lastResumed = now`.
- Pause: `accumulated += now - lastResumed`; `lastResumed = null`.
- Resume: `lastResumed = now`.
- Stop: if active, `accumulated += now - lastResumed`; final duration is
  `accumulated`.
- The UI ticks about once per second while visible. The timer is not a countdown
  and not a repeating alarm.
- `durationSeconds = max(1, round(durationMs / 1000.0))`.
- `durationMinutes = max(1, ceil(durationSeconds / 60.0))` for the existing
  `duration_minutes > 0` constraint and summary queries.
- A run may cross midnight. It is saved as one workout on its **start date**.
  Daily step rows handle the boundary separately; the workout is not split.

### 7.3 Session persistence and recovery

A single-row table holds the in-progress session. Only one session can exist.
State transitions are persisted on start, pause, resume, periodic updates,
finish and discard.

- **Rotation / activity recreation:** bind/reload; timer continues.
- **Background / screen off:** foreground service continues when legal. The
  clock is derived from monotonic time, so returning shows the correct elapsed
  duration even if UI updates were suspended.
- **Process death, same boot:** service restart / next open loads the row; timer
  is recomputed from `startedElapsedMs`/`lastResumedElapsedMs`; steps recover
  from the raw counter when possible.
- **Reboot / `elapsedRealtime` invalid:** mark the session interrupted, freeze
  the last recorded duration and steps, and offer:
  - Resume — starts a new active segment; downtime is excluded and labelled.
    Steps taken during the reboot gap are not reconstructed.
  - Finish and save — saves the last recorded values.
  - Discard — deletes the session with no workout.
- **Service cannot run:** persist the row on every UI transition. On return,
  recompute; if the process was killed, recover the same way. This is the
  app-visible fallback.

### 7.4 Steps and cadence

- Running steps use the same `TYPE_STEP_COUNTER` raw values as daily tracking:
  `sessionSteps = max(0, rawNow - startRawSteps)` while the same sensor epoch is
  valid. A raw decrease freezes the count and marks the sensor epoch lost; it
  must not go negative.
- If no sensor/permission, steps show "—" and the saved `step_count` is NULL.
  A real zero is stored as 0 and shown as 0.
- Cadence is a rolling 30-second estimate from step deltas:
  `round(stepsInWindow * 60000 / windowDurationMs)`.
- Show "—" until there are at least two samples and at least 5 seconds of
  window data. After that, show the numeric cadence. When there are no steps for
  30 seconds, show 0 (valid data, no recent steps).
- Pause resets the cadence window; resume starts a fresh window. Do not show a
  stale pre-pause cadence.
- Do not claim cadence is medically accurate; it is an estimate from the
  device's step counter.

### 7.5 Save exactly once

Stop always asks for confirmation. The confirmation shows final duration and
steps. On confirm, the service performs one transaction:

1. Re-read the session row inside the transaction. If absent, treat it as
   already finished and do **not** insert a workout.
2. Compute the final duration and step snapshot.
3. Insert exactly one `workouts` row:
   - type `Running`;
   - date = start date;
   - `duration_minutes` = rounded-up whole minutes;
   - `duration_seconds` = exact seconds;
   - `step_count` = number or NULL;
   - no distance fields (none exist).
4. Delete the session row.
5. Commit.

If any step fails, roll back; the session remains and the UI offers retry. If
the process dies after commit, no session exists and the workout is already
saved. A second Stop cannot create a second workout. Discard deletes the session
without inserting.

### 7.6 Notification while running

- One persistent notification, low importance, no sound/vibration, ongoing.
- Foreground mode: title "Run in progress"; text includes elapsed time and
  steps; actions Pause/Resume and Stop.
- Stop in the notification opens `RunningActivity` with a confirm-stop request;
  it never finishes the run directly.
- Notification content is private on the lock screen (public version says only
  that Motionfit is tracking a run). Use immutable `PendingIntent`s.
- When the run ends and automatic tracking remains active, the same service
  notification returns to the daily "counting steps" state. If neither applies,
  the service stops and removes the notification.
- Notification updates are throttled; do not rebuild it per step.

### 7.7 Permissions before/during Running

- Explain activity recognition and notifications before requesting them.
- If activity recognition is denied, the user may still start a timer-only run.
  The screen clearly states that background timing is unavailable.
- If no sensor exists, the user may still start; steps/cadence are unavailable.
- If notification permission is denied but activity recognition is granted, the
  run can use foreground mode; show "Notifications are off, so Android may hide
  the tracking notice."
- No location permission is ever requested.

---

## 8. Database changes and migration

### 8.1 Version

`DatabaseContract.DATABASE_VERSION` changes from `1` to `2`. The database file
name and existing table names stay the same. `onCreate` creates the full V2
schema. `onUpgrade` performs a real numbered `1 -> 2` migration and must not
recreate tables or delete rows. No destructive fallback is allowed for a normal
upgrade.

### 8.2 `daily_steps` additions

| Column | Type | Meaning |
|---|---|---|
| `last_raw_sensor` | INTEGER, nullable | Latest raw `TYPE_STEP_COUNTER` reading applied to this date. NULL for a purely manual day or a migrated row that has not seen a sensor reading. |

`steps`, `source`, `activity_date`, `created_at`, `updated_at` and the UNIQUE
date constraint remain. New source value: `sensor_adjusted`. Existing rows are
not rewritten; their `last_raw_sensor` is NULL.

### 8.3 `workouts` additions

| Column | Type | Meaning |
|---|---|---|
| `duration_seconds` | INTEGER, nullable | Exact duration for Running-mode workouts; NULL for existing/manual workouts. |
| `step_count` | INTEGER, nullable | Steps recorded for a Running workout; NULL when unavailable. |

Existing `duration_minutes` and its `CHECK (> 0)` remain. V2 validates the new
fields in the repository rather than rebuilding the table. Existing workouts
keep NULL for both new columns.

### 8.4 `running_sessions` table

Only one in-progress session row can exist. It is deleted on finish or discard,
so the table is a durable state holder, not a workout history.

```sql
CREATE TABLE IF NOT EXISTS running_sessions (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    status TEXT NOT NULL CHECK (status IN ('active', 'paused')),
    started_at TEXT NOT NULL,              -- local ISO timestamp, start date
    started_elapsed_ms INTEGER NOT NULL,   -- elapsedRealtime at start
    accumulated_ms INTEGER NOT NULL DEFAULT 0 CHECK (accumulated_ms >= 0),
    duration_ms INTEGER NOT NULL DEFAULT 0 CHECK (duration_ms >= 0),
    last_resumed_elapsed_ms INTEGER,       -- NULL when paused
    step_count INTEGER NOT NULL DEFAULT 0 CHECK (step_count >= 0),
    start_raw_steps INTEGER,               -- NULL when no sensor
    last_raw_steps INTEGER,
    boot_count INTEGER NOT NULL DEFAULT -1,
    interrupted INTEGER NOT NULL DEFAULT 0 CHECK (interrupted IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

`duration_ms` is the last persisted duration snapshot. It is what recovery can
show after a reboot, when `elapsedRealtime` no longer belongs to the same boot.
`started_elapsed_ms` and `last_resumed_elapsed_ms` are used for exact live
calculation on the same boot. `boot_count` comes from
`Settings.Global.BOOT_COUNT` where available (-1 otherwise).

### 8.5 Settings keys

Add to `app_settings` with defaults for new and upgraded installs:

| Key | Default | Meaning |
|---|---|---|
| `auto_steps_enabled` | `false` | User opted into automatic daily counting. |
| `onboarding_completed` | `false` | The short first-run setup has been shown/finished. |

Existing reminder keys and defaults remain. `clearAllData` resets these to
their defaults and deletes `running_sessions`.

### 8.6 Migration SQL

The version-1-to-2 migration is additive:

```sql
ALTER TABLE daily_steps ADD COLUMN last_raw_sensor INTEGER;
ALTER TABLE workouts ADD COLUMN duration_seconds INTEGER;
ALTER TABLE workouts ADD COLUMN step_count INTEGER;
CREATE TABLE IF NOT EXISTS running_sessions (...); -- schema above
```

Then ensure the default goal/settings rows exist, including the two new
settings keys. The migration must preserve all rows in `daily_steps`,
`workouts`, `fitness_goals` and `app_settings`. `onCreate` must not be called as
a shortcut after `onUpgrade`, because that hides migration mistakes and makes
the upgrade test meaningless.

`onDowngrade` remains unsupported and destructive; it must drop
`running_sessions` too and document the data loss. A normal V2 install never
takes that path.

### 8.7 Data reset

`DataResetRepository.clearAllData` becomes a single transaction that deletes
`running_sessions`, `daily_steps` and `workouts`, resets goals and reminder
settings, and resets the new auto/onboarding settings. Before clearing, the
Settings screen stops the tracking service and discards any active run. The
confirmation text must mention that an in-progress run will be discarded.

### 8.8 Migration tests

Instrumented tests must build a genuine version-1 database, insert sample rows
in every table, open it with the V2 helper, and assert:

- the schema version is 2;
- every old row is present with the same values;
- new columns are NULL for old rows;
- `running_sessions` exists and accepts one row;
- a second `running_sessions` insert with id 1 fails;
- new daily/workout fields round-trip;
- a fresh install creates the same V2 schema;
- opening the upgraded database again does not re-run migrations or lose data;
- clear-all removes the session and resets the new settings.

A separate manual test installs V1 on a device, adds real data, upgrades with the
V2 APK, and verifies the data. That manual row stays NOT RUN until executed.

---

## 9. Permissions, privacy and security

### 9.1 Manifest permissions

Existing: `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`,
`RECEIVE_BOOT_COMPLETED`. Add only:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
```

Do **not** add `INTERNET`, `ACCESS_FINE_LOCATION`,
`ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `WAKE_LOCK`,
`BODY_SENSORS`, `HIGH_SAMPLING_RATE_SENSORS` or `SCHEDULE_EXACT_ALARM`.

The service is declared `android:exported="false"`,
`android:foregroundServiceType="health"`, `android:stopWithTask="false"`. Pass
the health type to `startForeground` on API 34+; use the two-argument form on
older APIs. A catch around foreground start handles OS restrictions without
crashing.

### 9.2 Foreground-service truthfulness

- Automatic counting only runs foreground with a visible persistent
  notification. If notifications are blocked, it uses app-open-only mode.
- Running may use foreground mode with notification permission denied only
  because the user explicitly started it; the app warns that the notice is
  hidden. It is not an always-on tracker in that state.
- If the OS rejects the FGS, show app-visible fallback status. Never claim
  background tracking that is not actually running.
- Do not request battery-optimization exemption automatically. A Settings link
  to the system battery screen is informational only.

### 9.3 Notification privacy

- Tracking notifications use `VISIBILITY_PRIVATE` with a generic public version
  that does not show step counts or run duration on the lock screen.
- No step values, dates or session details in logs. Log only error classes and
  generic messages.
- Notification channel is low importance, no sound/vibration, and is separate
  from the existing reminder channel.
- PendingIntents are immutable and explicit; the service/receiver are not
  exported.

### 9.4 Data and SQL

- The database remains app-private and unencrypted, as documented in V1. V2 does
  not add encryption, export or network sync.
- All user input continues through bound SQL parameters.
- Database upgrades are additive and transactional.
- "Clear all local data" remains confirmed and irreversible.
- Android backup behaviour is unchanged; the privacy/limitations docs must
  continue to disclose that system backup may copy the database if enabled.

### 9.5 Boot and background limits

- `BootReceiver` continues to restore reminders. If automatic tracking is
  enabled and legal, it makes a best-effort attempt to start the service. If the
  OS blocks a background FGS start, catch it and wait for the app to open.
- A force-stopped app is not restarted by the app. Aggressive OEM battery
  management can stop sensors, services or notifications. These limits are
  documented; no guaranteed tracking claim is made.

---

## 10. Onboarding and settings

### 10.1 First-run onboarding

A short, skippable onboarding appears once for new installs and once for
existing V1 users after upgrade. It must not overwrite existing goals or data.

Content:

1. **Welcome / privacy:** offline, no account, no internet, step/workout data
   stays on the device. Existing goals are shown, not reset.
2. **Daily goals:** step goal and workout-minutes goal, using the existing goal
   controls and validation.
3. **Automatic counting (optional):** explain that counting starts **from the
   moment it is enabled** (use a manual total to include earlier steps), the
   step sensor, the persistent notification, battery considerations, and what
   happens if permission is denied. Request `ACTIVITY_RECOGNITION` and
   `POST_NOTIFICATIONS` only after the user chooses to enable it. Always offer
   "Not now" / "Skip".
4. Finish or Skip sets `onboarding_completed = true`. Manual entry and workouts
   remain available without any permission.

No permission dialog may appear before the explanation. No dark patterns, no
blocking the user from the app, no fake urgency. TalkBack, 1.3x font and 360dp
must work.

### 10.2 Settings

Add an **Automatic step counting** card:

- switch for `auto_steps_enabled`;
- effective status: Active / App-open only / Unavailable / Off;
- explanation of the current state and what to do;
- actions: allow activity access, allow notifications, open battery settings
  (informational), and a note that background tracking depends on Android/OEM;
- no "test sensor" one-shot add button.

Existing reminder, goals, clear-data and version sections remain. Replace the
hardcoded `Version 0.1.0` string with the build version. The clear-data
confirmation includes the active-run warning and stops tracking first.

### 10.3 Permission-denied education

Every denial path must explain the consequence in plain language and keep the
rest of the app usable:

- activity denied → automatic steps unavailable; manual entry works; Running
  timer-only;
- notifications denied → automatic steps app-open-only; Running warning;
- sensor missing → manual entry; Running timer-only;
- FGS blocked → app-visible fallback, with status;
- permanently denied → link/instructions to system settings; no repeated
  surprise prompts.

---

## 11. UI/UX audit and remediation

The order is deliberate: **A. accessibility and layout resilience**, then
**B. onboarding and permission education**, then **C. visual polish**. New
Running UI must meet the A criteria from its first implementation.

### 11.1 Global findings (A)

| ID | Finding | Evidence | Required fix |
|---|---|---|---|
| G1 | Caption text fails AA contrast | `mute` `#868685` on sage `#E8EBE6` is **3.03:1**; on white it is **3.64:1** | Darken the caption/mute token to at least `#5F615F` (5.19:1 on sage, 6.25:1 on white), or use `body` for captions. Keep decorative lines separate. |
| G2 | Touch targets below 48dp | `icon_button_small` = 40dp, used for back/action buttons; `Widget.Wise.Segment` minHeight = 40dp | Make every clickable target at least 48x48dp. Row actions become 48dp; segments 48dp high. |
| G3 | Nav labels can clip at 1.3x | 12sp labels, `maxLines="1"`, five equal tabs, 64dp nav; "Workouts" exceeds a 360dp tab at 1.3x | Autosize 10–12sp or reduce to 11sp, keep one line, test 360dp/1.3x. Do not rely on `maxLines` alone. |
| G4 | Lint source errors | 8 duplicate `navIcon`/`navLabel` IDs; 19 `android:tint` instead of `app:tint`; API-27 theme attribute | Give nav items unique IDs (or an include pattern that lint accepts), use `app:tint`, and guard/move the API-27 item. No lint baseline. |
| G5 | Keyboard can cover pinned actions | Goals and Workout editor pin Save at the bottom; manifest has no `windowSoftInputMode` | Add `adjustResize` to input screens and test with the keyboard open on a small screen. |
| G6 | Custom views are silent to TalkBack | `BarChartView` and `StepsRingView` draw data but expose no semantics | Mark purely decorative drawing as not important and expose a text summary; or set a useful `contentDescription` from the data. Charts need a summary, not a fake clickable. |
| G7 | Load failures look like empty/zero data | Repositories catch `SQLiteException` and return empty lists/zero summaries; several Activities cannot distinguish failure from "no records" | Add a small `LoadResult<T>` (success/data/error) or equivalent for the read paths used by Progress, Workouts, Steps and Today; show an inline error with Retry. Do not silently show fake zeros. |
| G8 | Large text can clip key numbers | 64sp hero totals on Progress and Steps have no autosize; period/date labels have no overflow policy | Add autosize/ellipsize/weight rules and verify at 360dp with 1.3x font. Dates must remain fully available to accessibility services. |
| G9 | Hardcoded/concatenated text | Workout row metadata is concatenated in Java (`SetTextI18n`); version is a fixed `0.1.0` string | Use string resources/placeholders; use `BuildConfig.VERSION_NAME`. |
| G10 | Unused touch helper has an accessibility warning | `UiAnimations.attachPressFeedback` uses `OnTouchListener` without `performClick` and is currently unused | Remove the dead helper or implement `performClick`; do not leave a lint warning. |
| G11 | Mixed start/end padding | RTL symmetry warnings on several headings | Use matching start/end padding or deliberately asymmetric end padding. |
| G12 | Root screens hide 40dp back buttons | Steps/Settings/Progress/Workouts declare a gone back button at 40dp | Remove dead root-screen back buttons; Steps detail gets a visible 48dp back. |
| G13 | No loading/working states for actions | Starting/saving Running is synchronous-looking | Disable and label Start/Save while starting/saving; show a short status. No network spinner is needed. |

### 11.2 Today (A/B/C)

Confirmed issues:

- The steps hero is display-only. Manual entry is reachable only through the
  old Steps nav item; after Q1 it would become unreachable unless wired.
- `dashboardDate` uses the low-contrast Caption style and has no overflow rule.
- The two-column workout card can crowd at large font; labels and values need
  weights/ellipsize.
- A load failure shows a Toast and leaves stale values; no inline retry.
- The step ring has no accessibility summary; the numeric text is readable but
  the progress relationship is not.
- The recent empty state is low-contrast text with no direct action.
- Bottom nav has 104dp content padding, which is correct for the 64dp pill plus
  margins; verify after the five-tab change.

Required changes:

- Make the steps hero a single, clearly discoverable **Edit steps** target
  (visible affordance, 48dp, content description including the selected date).
  Keep `stepsValue`, `stepsProgress`, `stepsPercent`, `stepsGoal` IDs.
- Use the accessible caption token and wrap/ellipsize the date.
- Show an inline error card with Retry instead of stale silent values.
- Keep the ring decorative or give the hero one combined accessibility summary.
- Add a real empty state with a "Log steps" or "Add workout" action.
- Nav starts Today; no behavior change to the date arrows, quick-add, recent
  list or workout editor routing.

### 11.3 Steps detail (A/B/C)

Confirmed issues:

- It is currently a root destination with the floating nav and a hidden 40dp
  back button. Q1 changes this to a detail screen.
- `stepsSelectedValue` is 64sp, single-line, with no autosize; large totals can
  clip at 1.3x.
- The "EXPERIMENTAL" sensor card is a one-shot add flow. It is the main source
  of double-count risk and contradicts the new single-owner model.
- The manual dialog helper says only "Enter the total"; it does not explain
  calibration when automatic counting is on.
- History labels only Manual/Sensor; no Adjusted state.
- Repository errors are indistinguishable from zero/empty.
- Empty state uses low-contrast Caption text.

Required changes:

- Remove the floating nav; use a visible 48dp back button and a 24dp bottom
  padding. Keep `EXTRA_DATE` and the selected-date behavior.
- Remove the experimental sensor card and the one-shot add button. Replace it
  with a read-only status card: Active / App-open only / Off / Unavailable,
  plus an action to manage tracking.
- Keep `btnEditSteps` at 48dp, autosize the selected value, and make the dialog
  explain: "This becomes today's total. Future automatic steps continue from
  here." When auto is off, say the sensor is not used.
- Show source as Manual / Automatic / Adjusted, with text, not color alone.
- Add inline error/retry for load failures.
- Keep the full history here; Progress gets a recent slice and a "View all"
  link to this screen.

### 11.4 Workouts (A/C)

Confirmed issues:

- Row edit/delete actions are 40dp; below the 48dp target.
- Row metadata concatenates date and duration in Java, triggering
  `SetTextI18n`, and is awkward to localize.
- A database error and "no workouts" both render as an empty list.
- The filter spinner uses platform styling, which is visually inconsistent
  with the Wise controls.
- The root header still declares a hidden 40dp back button.

Required changes:

- 48dp row actions with content descriptions; keep row-click details.
- Use a string resource for date/duration/exact-running metadata.
- Distinguish empty and error states; add Retry on error.
- Style the spinner consistently if it can be done without changing filter
  behaviour; otherwise leave it and note the visual difference.
- Remove the dead back button. Keep the 104dp bottom padding and nav.
- Running workouts show their exact duration and step count when available;
  manual workouts retain the existing whole-minute display.

### 11.5 Progress (A/C)

Confirmed issues:

- The empty state is a single Caption line at the bottom, after cards and charts
  that still show zeros. It is not a real empty state.
- `totalSteps` is 64sp with no autosize; monthly totals can clip.
- `periodLabel` is 24sp with no maxLines/autosize; long ranges at 1.3x can wrap
  or push the arrows.
- The segmented control is 40dp high.
- `BarChartView` has no accessible summary.
- `bestDay` can collide with its label at large font.
- Repository failures collapse to zero-filled series; the user cannot tell
  "no data" from "database error".
- The step-history primary surface is missing; Steps is still a root screen.

Required changes:

- Show a proper empty card near the top when the period has no activity; hide
  or de-emphasise zero-only charts. Keep the period controls usable.
- Autosize `totalSteps`; set `periodLabel` maxLines=1 with autosize/ellipsize
  and keep the arrows 48dp.
- Make segments 48dp.
- Add a textual chart summary for TalkBack, e.g. "Steps for 1–31 July: total
  120,000; best day 12 July with 8,000." Mark the drawing decorative if the
  summary is adjacent.
- Add a "Step history" card with the last seven recorded days and "View all"
  to Steps detail. Rows must be 60dp minimum, show date, steps and source.
- Add inline error/retry for series load failure.
- Keep the day/week/month selector, arrows, best-day and active-day behaviour.
- Remove the hidden 40dp root back button.

### 11.6 Settings (A/B/C)

Confirmed issues:

- No automatic-tracking controls or permission status exist.
- The version is a hardcoded `Version 0.1.0` string.
- Reminder time, value and Change time can crowd at large font.
- Caption-heavy notes fail contrast.
- The reminder switch has a content description that may duplicate its visible
  label for TalkBack.
- The root header declares a hidden 40dp back button.
- Clear data does not know about an active Running session.

Required changes:

- Add the automatic-tracking card and status described in section 10.2.
- Replace hardcoded version with `BuildConfig.VERSION_NAME`.
- Let the reminder row wrap/stack at large font; keep all targets 48dp.
- Use accessible captions; associate the switch label instead of duplicating
  it if TalkBack repeats it.
- Remove the dead back button.
- On clear data: confirm, stop tracking, discard the active session, clear all
  tables, reset settings, cancel reminders, and update status. Show a clear
  active-run warning in the confirmation.
- Keep the existing reminder and test-notification behavior.

### 11.7 Goals (A)

Confirmed issues:

- The pinned Save button can be covered by the keyboard.
- The large bare-number inputs have labels as separate TextViews without
  `labelFor`, so TalkBack may not associate them.
- Caption help text has low contrast.
- Seven-digit values at 1.3x should be verified between the 48dp steppers.

Required changes:

- Add `adjustResize` (or equivalent) and test Save above the keyboard.
- Add `android:labelFor` or content descriptions; keep steppers 48dp.
- Use the accessible caption token; test 360dp/1.3x and a 1,000,000 goal.
- Preserve validation, save behavior and existing IDs.

### 11.8 Workout editor / details (A/C)

Confirmed issues:

- Pinned Save can be covered by the keyboard.
- Labels are not associated with inputs for TalkBack.
- The spinner uses platform styling.
- Running-mode workouts need to show exact duration and steps; the current
  screen only knows whole minutes and notes.
- The hidden 40dp root back button is not relevant here; this is a pushed screen
  and should keep a visible 48dp back.

Required changes:

- Add `adjustResize`, label associations and large-font testing.
- For a Running workout, show a read-only "Recorded duration" (`mm:ss`) and
  "Steps" row. Preserve exact `duration_seconds` and `step_count` when editing
  other fields. If the user changes the whole-minute duration, set seconds to
  the new whole-minute value; if unchanged, keep the exact seconds. Preserve
  steps unless a future explicit step-edit feature is approved.
- For manual workouts, continue to save NULL seconds/steps.
- Keep delete confirmation, date picker, validation and existing IDs.
- Keep the spinner functional even if styling is deferred.

### 11.9 Running (new; must meet A from day one)

The screen must define and test these states: Ready, Running, Paused,
Finishing, Summary, Recovery, Error.

Requirements:

- 48dp controls; large timer with autosize; steps and cadence cards; no
  distance/pace/calories.
- Start label "Start run"; Pause/Resume are explicit; Stop is destructive and
  always confirms with duration and steps.
- Cadence "—" with helper text until enough data; numeric value after.
- Permission/status card with actions; timer-only path is always available.
- Back leaves the screen; active session continues or clearly says background
  timing is unavailable.
- Keep screen on only while active and visible; clear on pause/finish.
- TalkBack: no per-second live-region spam; state changes are announced; timer
  and metrics have labels; buttons have action labels.
- Rotation, 1.3x font, 360dp, keyboard-free layout, reduced motion.
- No bottom nav. Summary says "Saved to Workouts" and does not create a second
  record.

### 11.10 Nav, list rows and dialogs (A/B/C)

- `view_floating_nav.xml`: replace Steps with Run, use five tabs, fix duplicate
  IDs, keep the highlight animation and reduced-motion support.
- Nav item labels: Today, Workouts, Run, Progress, Settings. Keep the nav 64dp
  high, 48dp items, safe bottom inset, and 104dp content padding on root
  screens.
- `FloatingNavView`: add `DEST_RUN`; remove the Steps destination from the
  nav. Keep `bind`, `setActiveDestination` and the reorder-to-front behavior.
  Update every root screen's active destination.
- `item_workout.xml`: actions 48dp; row still 60dp minimum; exact running meta
  when present.
- `item_step_history.xml`: date/steps/source readable; 60dp minimum; no color-
  only meaning.
- `dialog_steps.xml`: keep the input layout, add the calibration helper and
  label association; preserve validation and max length.
- Any image button gets 48dp, a content description and `app:tint` where
  appropriate. Decorative icons are `importantForAccessibility="no"`.

---

## 12. Acceptance criteria

### 12.1 Step accuracy

- **AC-STEP-1:** First automatic reading starts today's automatic total at 0
  and stores the raw checkpoint. It does not claim steps taken before tracking
  started.
- **AC-STEP-2:** A later reading adds exactly the raw delta; a duplicate reading
  writes nothing; a decreasing reading never lowers the total.
- **AC-STEP-3:** After a manual calibration of 5,000 at raw 10,000, the next
  reading at raw 10,010 makes the total 5,010. It does not revert to 10 or
  double-count the prior 5,000.
- **AC-STEP-4:** With automatic counting off, manual totals remain manual; no
  sensor listener writes to the database.
- **AC-STEP-5:** Process death and restart on the same boot recover the delta
  since the persisted checkpoint without duplicating it.
- **AC-STEP-6:** Reboot/raw decrease preserves the total and starts a new
  checkpoint; no negative or double count.
- **AC-STEP-7:** A continuously running service at midnight creates the new
  day's row and leaves the previous day unchanged. A service that was not
  running does not retroactively split an unknown gap.
- **AC-STEP-8:** Running steps are not added to the daily total a second time.
  With auto off, the workout can have steps while the daily total stays manual,
  and the UI says so.
- **AC-STEP-9:** Notification-denied automatic tracking is app-open-only; no
  always-on hidden service remains after the app leaves the foreground.
- **AC-STEP-10:** Missing sensor or denied activity permission leaves manual
  entry fully usable and shows a clear status.

### 12.2 Running mode

- **AC-RUN-1:** Navigation has `Today | Workouts | Run | Progress | Settings`;
  Run opens `RunningActivity`; no bottom nav is shown there.
- **AC-RUN-2:** Start/Pause/Resume produce the expected elapsed durations using
  `elapsedRealtime()`; pause and resume do not accumulate double time.
- **AC-RUN-3:** A session survives rotation, backgrounding where the legal
  service exists, and process death on the same boot.
- **AC-RUN-4:** A reboot marks the session interrupted; the user can resume
  (downtime excluded), finish with the last recorded values, or discard.
- **AC-RUN-5:** Stop always confirms. Confirming saves exactly one Running
  workout with start date, exact seconds, rounded-up minutes and steps/NULL;
  the session row is removed. A second Stop adds nothing.
- **AC-RUN-6:** Discard creates no workout.
- **AC-RUN-7:** Cadence is "—" until at least 5 seconds/two samples; then a
  numeric 30-second rolling estimate; 0 after 30 seconds of no steps; reset on
  pause.
- **AC-RUN-8:** With no sensor/permission, the timer still works, steps/cadence
  show "—", and the saved step count is NULL.
- **AC-RUN-9:** Running creates no location fields, requests no location
  permission, and stores no distance/pace/calories.
- **AC-RUN-10:** A notification-denied run either uses FGS with a clear in-app
  warning that the notice is hidden, or falls back with an explicit "background
  timing unavailable" state. It never silently claims background tracking.

### 12.3 UI/UX and accessibility

- **AC-UI-1:** All interactive targets are at least 48x48dp (nav items, row
  actions, back buttons, segments, steppers, switches, dialog buttons).
- **AC-UI-2:** Normal text contrast is at least 4.5:1; large text at least 3:1.
  The caption token passes on both sage and white. No information is conveyed by
  color alone.
- **AC-UI-3:** At 360dp width and 1.3x font, no required text or control is
  clipped, overlapping, or unreachable. Screens scroll; pinned buttons remain
  above the keyboard.
- **AC-UI-4:** TalkBack can identify every control, its state, and the meaning of
  charts/ring/steps; no duplicate announcements from nav labels; custom views
  are either summarised or marked decorative.
- **AC-UI-5:** Every list/series has distinguishable empty, error and loaded
  states. Load errors offer Retry or a clear next action; fake zero data is not
  shown.
- **AC-UI-6:** Reduced-motion setting suppresses count-ups/highlight slides;
  final values are still shown.
- **AC-UI-7:** First-run onboarding is skippable, requests permissions only
  after explanation, preserves existing data, and never blocks manual use.
- **AC-UI-8:** The five-tab nav labels fit at 360dp/1.3x; Steps detail has a
  visible 48dp back affordance; Today and Progress can reach step editing and
  history.

### 12.4 Database, offline and regression

- **AC-DB-1:** Database version is 2; a real version-1 database upgrades with all
  rows preserved; new columns are NULL for old rows; `running_sessions` exists.
- **AC-DB-2:** Fresh install, upgrade, reopen and clear-all are tested. Clear-all
  deletes the session and resets new settings. Downgrade remains documented as
  destructive.
- **AC-OFFLINE-1:** The manifest still has no `INTERNET` permission, no location
  permission, no analytics/cloud SDK and no new third-party dependency.
- **AC-OFFLINE-2:** No new database field stores location, distance, pace, route
  or calories.
- **AC-REG-1:** Existing manual workout CRUD, filtering, goals, reminders,
  day/week/month summaries, clear-data confirmation and the 104dp nav padding
  still work.
- **AC-REG-2:** Existing view IDs and public methods remain where the screen
  still exists; intentional exceptions (Steps nav item, experimental sensor
  controls, new workout fields) are documented.
- **AC-REG-3:** `assembleDebug`, unit tests, instrumented tests (when run) and
  lint source errors are clean. Physical-device rows stay NOT RUN until the user
  runs them.

---

## 13. Test plan

### 13.1 JVM unit tests

Add pure Kotlin/Java tests with no Android runtime:

| Test class | Coverage |
|---|---|
| `StepCountEngineTest` | first reading; delta; duplicate; decrease/reboot; midnight; manual calibration; manual-before-sensor; no sensor; auto off; large gap; never negative; source transitions; stale checkpoint conflict. |
| `CadenceTrackerTest` | insufficient data; steady cadence; decay; pause reset; duplicate readings; window boundaries. |
| `RunningSessionClockTest` | start/pause/resume/stop; repeated transitions; zero/negative guards; duration formatting inputs; recovery snapshot. |
| `WorkoutDurationTest` | exact seconds; rounded-up minutes; one-second minimum; NULL steps; manual workout defaults. |
| Existing tests | all existing date/validation/goal/summary tests must continue to pass. |

### 13.2 Instrumented tests

Use the real SQLite database on an emulator/device:

- `DatabaseMigrationTest` — build a true V1 database, fill every table, open V2,
  assert preservation, new columns, new table, version, fresh-install schema,
  reopen and clear-all.
- `StepsRepositoryTest` — checkpoint update, calibration, source transitions,
  unique date, concurrent/stale checkpoint reload, manual clears checkpoint.
- `RunningSessionRepositoryTest` — singleton, start/pause/resume persistence,
  interrupted recovery, finish transaction, rollback on failure, duplicate
  finish, discard, exact workout fields, no duplicate workout.
- `WorkoutRepositoryTest` — new fields round-trip; old rows NULL; update
  preserves fields; validation.
- `DataResetRepositoryTest` — running session deletion and new settings reset.
- Service tests are limited to deterministic helpers; actual FGS/sensor
  behaviour is manual because it depends on the device and OS.

The existing instrumented tests currently clear app data; run them on a
dedicated emulator or accept the reset. Do not claim they passed until
`connectedDebugAndroidTest` output is shown.

### 13.3 Build and static checks

Required commands (from the project root, with the existing Android Studio JBR):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --rerun-tasks
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat lintDebug
```

`lintDebug` must not add a baseline and must be clean for source errors touched
by V2. The local `local.properties` escape warning is environmental and must not
be committed. Pre-existing unrelated unused-resource warnings may remain, but
must be reported honestly.

### 13.4 Manual and physical-device matrix

These rows are **NOT RUN** until executed and reported with actual results:

| ID | Test | Expected |
|---|---|---|
| M1 | Fresh install, onboarding skip | App opens; manual entry/workouts work; no forced permission. |
| M2 | Enable auto with activity + notification permission | Persistent notification; steps update while backgrounded/screen off. |
| M3 | Deny notification permission | No always-on service; app-open-only status; steps reconcile while app is visible. |
| M4 | Deny activity permission | Manual entry works; Run timer-only; clear explanation. |
| M5 | Device/emulator without step counter | Auto unavailable; manual works; Run timer/steps "—". |
| M6 | Calibrate after walking | Correction holds; future steps add; no reset/double count. |
| M7 | Close/reopen app, force-stop, process death | Checkpoint recovers; no duplicate/no negative. |
| M8 | Reboot while auto tracking | Service/app recovers; total preserved; notification correct. |
| M9 | Midnight rollover | New day row starts; previous day unchanged; no fabricated split. |
| M10 | Run start/pause/resume/stop | Timer correct; pause freezes; stop confirms; one workout. |
| M11 | Run in background/screen off | Timer/steps continue where FGS is legal; notification visible. |
| M12 | Run recovery after process death/reboot | Resume/finish/discard works; no duplicate workout. |
| M13 | Cadence | "—" initially; numeric after data; decays to 0; reset on pause. |
| M14 | Run without sensor/permission | Timer works; steps NULL; no crash; clear warning. |
| M15 | Stop twice / tap quickly | Exactly one workout. |
| M16 | Upgrade real V1 app with data | All records preserved; new fields NULL; no loss. |
| M17 | 360dp, 1.3x font, landscape | No clipped required content; pinned actions above keyboard. |
| M18 | TalkBack/Accessibility Scanner | Controls labelled; charts summarised; no color-only meaning. |
| M19 | Reduced motion | No count-up/slide; values immediate. |
| M20 | Clear data during active run | Confirmation; run discarded; all data cleared; service stopped. |
| M21 | Notification privacy | Lock screen does not show step counts/duration. |
| M22 | Battery/OEM behaviour | No wake lock; service survives where allowed; limits documented. |
| M23 | Permission denial permanently | Settings guidance; no repeated surprise prompts. |
| M24 | No location/GPS | No location permission; no distance/pace UI or fields. |

At the end of every task, results are reported as:

```
Test name | Expected | Actual | PASS / FAIL / NOT RUN
```

Physical-device results stay NOT RUN until the user runs them. Emulator results
must name the emulator/API level. No "PASS" is inferred from code inspection.

---

## 14. Risks and limitations

| Risk | Impact | Mitigation |
|---|---|---|
| Step-counter hardware accuracy varies | Automatic total may under/overcount; calibration is essential | Document sensor limitations; manual calibration; no claim of medical accuracy. |
| Reboot/epoch handling | Steps in the gap are conservatively skipped | Preserve total, restart checkpoint; test; document. |
| Day-boundary gap | Steps across midnight cannot be split exactly if the service was off | Continuous FGS minimizes gaps; conservative new-day checkpoint; document. |
| FGS restrictions (Android 12/14, boot, OEM) | Background tracking may not start/continue | Catch failures; app-visible fallback; no guaranteed claim; physical testing. |
| Notification permission denied | No always-on service allowed by design | App-open-only fallback with clear status. |
| Activity permission denied | Health FGS cannot run on API 34+ | Timer-only in-app fallback; persist state; document background limit. |
| Concurrent manual/sensor writes | Double count or correction undone | Single owner; atomic `steps + last_raw_sensor`; stale-checkpoint reload; tests. |
| Migration bug | Data loss on upgrade | Additive v1→v2 migration; real v1 fixture test; manual upgrade test; no destructive fallback. |
| Running duplicate save | Two workouts | Singleton session row; transaction; delete on finish; idempotent finish test. |
| Large font/locale/nav overflow | Unusable controls | 48dp, autosize/ellipsize, 360dp/1.3x tests, string resources. |
| Timer-only permission path | Background timing not guaranteed | Explicit warning; monotonic time still correct on return; docs. |
| Battery drain | User uninstalls/disables | Normal sensor delay; no polling/wake lock; throttled writes/notifications; battery screen link. |
| Local DB unencrypted / backup | Privacy exposure if device backup is on | Existing limitation documented; no network; private notifications. |
| Scope creep | Delays/corruption of the three goals | Non-goals list; no new dependencies; small atomic tasks. |

---

## 15. Documentation and release deliverables

After implementation and verification:

- `docs/v2-spec.md` (this document) and `docs/v2-plan.md`.
- Update `docs/database.md` to version 2, new columns/tables and migration.
- Update `docs/architecture.md` with the single service, step engine, Running
  state machine and fallback modes.
- Update `docs/testing.md` with actual command output and honest device status.
- Update `docs/limitations.md`: sensor accuracy, reboot/day-boundary, FGS/OEM,
  notification/activity permission fallbacks, no guaranteed tracking.
- Update `docs/ui-design.md`: five-tab nav, Run screen, 48dp/contrast decisions,
  onboarding.
- Update `README.md` status/features/roadmap/version; update `PRIVACY.md` if the
  service/notification wording needs it.
- Version `2.0.0` (versionCode 2) in `app/build.gradle`; Settings shows the build
  version.
- No push until explicitly told. Work stays on `v2-dev`; `main` is untouched.

---

## 16. Approval checklist

Please approve or amend these points before I write the plan:

1. The V2 scope and non-goals in sections 3 and 12.
2. The delta-based step contract using `last_raw_sensor` as the persisted
   checkpoint (no separate baseline column).
3. One tracking service owning daily counting and Running, with one notification.
4. No `TYPE_STEP_DETECTOR`/accelerometer fallback.
5. Automatic tracking fallback: no always-on service when notifications are
   denied; app-open-only with clear status.
6. Running on API 34+ with `ACTIVITY_RECOGNITION` denied: app-visible timer-only
   fallback, with background timing explicitly not guaranteed.
7. The v2 schema: `last_raw_sensor`, `workouts.duration_seconds`,
   `workouts.step_count`, `running_sessions`, new settings keys.
8. The UI audit and A → B → C order, including the 48dp/contrast/font/error-state
   fixes.
9. Step history: last seven recorded days on Progress, full history in Steps
   detail; Today and Progress both link to step editing.
10. Onboarding shown once to existing V1 users, skippable, permissions only
    after explanation.
11. Version `2.0.0` / versionCode 2.
12. Lint source-error cleanup only; no chase of unrelated unused-resource
    warnings.

If any item is wrong, say which number and what to change. After approval I will
write `docs/v2-plan.md` with small ordered tasks, files, tests and dependencies,
then stop for plan approval. No source changes before that.
