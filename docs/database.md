# Database

Motionfit stores everything in one local SQLite database, **`fitness_tracker.db`**,
managed by `FitnessDatabaseHelper` (a `SQLiteOpenHelper`). The schema is **version 2**.

Table and column names are defined once in
`io.github.praneetx.motionfit.database.DatabaseContract`.

> The database file name and table names are intentionally independent of the Java
> package, so renaming the app or its package does not change the schema or lose data.

## Date and timestamp formats

| Purpose | Format | Example |
|---|---|---|
| Calendar day (`activity_date`, `workout_date`) | `YYYY-MM-DD` | `2026-07-11` |
| Record timestamp (`created_at`, `updated_at`, `started_at`) | `YYYY-MM-DDTHH:mm:ss` | `2026-07-11T18:30:05` |
| Running clock snapshots | monotonic milliseconds (`elapsedRealtime`) | `123456789` |

Fixed-width ISO text keeps SQLite comparisons correct without parsing. "Today" is the
device's local date.

## Tables

### `daily_steps`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `activity_date` | TEXT | NOT NULL, **UNIQUE** |
| `steps` | INTEGER | NOT NULL, DEFAULT 0, CHECK (`steps` >= 0) |
| `source` | TEXT | NOT NULL, DEFAULT `'manual'` |
| `last_raw_sensor` | INTEGER | nullable |
| `created_at` | TEXT | NOT NULL |
| `updated_at` | TEXT | NOT NULL |

One row per calendar day, enforced twice: by the repository's conditional update/insert
logic and by the UNIQUE constraint. Negative totals are rejected by validation and by
the CHECK constraint.

`source` values:

* `manual` — a user-entered total with no raw checkpoint;
* `sensor` — a total built from the step counter;
* `sensor_adjusted` — a manual calibration that now has a raw checkpoint, or a sensor
  total that was adjusted by the user.

`last_raw_sensor` is the **only** step checkpoint. It is NULL for manual-only rows and
for migrated V1 rows that never had a sensor reading. Sensor updates are conditional on
the checkpoint that was read; if another writer calibrated the row, the update is
rejected and the caller reloads and reapplies the reading instead of overwriting the
correction.

### `workouts`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `workout_type` | TEXT | NOT NULL |
| `workout_date` | TEXT | NOT NULL |
| `duration_minutes` | INTEGER | NOT NULL, CHECK (`duration_minutes` > 0) |
| `duration_seconds` | INTEGER | nullable |
| `step_count` | INTEGER | nullable |
| `notes` | TEXT | nullable |
| `created_at` | TEXT | NOT NULL |
| `updated_at` | TEXT | NOT NULL |

Indexes: `idx_workouts_date` on `workout_date`, `idx_workouts_type` on `workout_type`.
There is no distance or calorie column.

* Manual workouts store whole `duration_minutes`; `duration_seconds` and `step_count`
  are NULL.
* Running workouts store the exact `duration_seconds` (rounded to the nearest second,
  minimum 1) and the rounded-up `duration_minutes` (minimum 1), plus `step_count` when a
  step-counter baseline existed. If the run had no sensor, `step_count` is NULL and the
  UI shows "steps unavailable".
* Running workout rows are created by the finish transaction in
  `RunningSessionRepository`, not by the manual editor.

### `fitness_goals`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, CHECK (`id` = 1) |
| `daily_step_goal` | INTEGER | NOT NULL, CHECK (> 0) |
| `daily_workout_minutes_goal` | INTEGER | NOT NULL, CHECK (> 0) |
| `updated_at` | TEXT | NOT NULL |

`CHECK (id = 1)` keeps a single active goal row. Defaults on first launch: 8000 steps
and 30 minutes.

### `app_settings`

| Column | Type | Constraints |
|---|---|---|
| `setting_key` | TEXT | PRIMARY KEY |
| `setting_value` | TEXT | nullable |

Keys and defaults:

| Key | Values | Default |
|---|---|---|
| `reminder_enabled` | `true` / `false` | `false` |
| `reminder_hour` | `0`–`23` | `18` |
| `reminder_minute` | `0`–`59` | `0` |
| `auto_steps_enabled` | `true` / `false` | `false` |
| `onboarding_completed` | `true` / `false` | `false` |

Missing or malformed values fall back to the documented defaults where possible.
Automatic tracking is opt-in, so both `auto_steps_enabled` and `onboarding_completed`
default to false.

### `running_sessions`

At most one in-progress or paused Running session exists. The row is a singleton with
`id = 1`.

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, CHECK (`id` = 1) |
| `status` | TEXT | NOT NULL, CHECK (`status` IN (`'active'`, `'paused'`)) |
| `started_at` | TEXT | NOT NULL |
| `started_elapsed_ms` | INTEGER | NOT NULL |
| `accumulated_ms` | INTEGER | NOT NULL, DEFAULT 0, CHECK (`>= 0`) |
| `duration_ms` | INTEGER | NOT NULL, DEFAULT 0, CHECK (`>= 0`) |
| `last_resumed_elapsed_ms` | INTEGER | nullable |
| `step_count` | INTEGER | NOT NULL, DEFAULT 0, CHECK (`>= 0`) |
| `start_raw_steps` | INTEGER | nullable |
| `last_raw_steps` | INTEGER | nullable |
| `boot_count` | INTEGER | NOT NULL, DEFAULT -1 |
| `interrupted` | INTEGER | NOT NULL, DEFAULT 0, CHECK (`IN (0, 1)`) |
| `created_at` | TEXT | NOT NULL |
| `updated_at` | TEXT | NOT NULL |

`status` describes the clock state. `interrupted` is a separate recovery flag: it means
the process or device lost the live clock and the UI must offer Resume, Finish and save,
or Discard. `start_raw_steps` and `last_raw_steps` are the Running-specific sensor
baseline/delta checkpoint; they are separate from the daily `last_raw_sensor` checkpoint.
`boot_count` lets recovery detect that `elapsedRealtime` is no longer comparable.

The session is deleted when a run is finished (after exactly one Running workout is
inserted) or discarded, and by "Clear all local data".

## Relationships

Single local user, so there are no foreign keys. Steps, workouts and the optional
Running session are independent tables combined by date when summaries are built.

## CRUD and transaction map

| Operation | Table | Where |
|---|---|---|
| Upsert a manual day total / calibration | `daily_steps` | `StepsRepository.saveDailySteps`, `calibrate` |
| Apply a sensor reading with checkpoint guard | `daily_steps` | `StepsRepository.applySensorReading` |
| Read a day's steps / step history | `daily_steps` | `StepsRepository.getForDate`, `getAll` |
| Insert / update / delete workout | `workouts` | `WorkoutRepository` |
| Filter workouts | `workouts` | `WorkoutRepository.getWorkouts(type, date, limit)` |
| Save exactly one Running workout and delete its session | `workouts` + `running_sessions` | `RunningSessionRepository.finish` (one transaction) |
| Start / pause / resume / recover / discard a run | `running_sessions` | `RunningSessionRepository` |
| Read / save goals | `fitness_goals` | `GoalRepository` |
| Read / save reminder, automatic-tracking and onboarding settings | `app_settings` | `SettingsRepository` |
| Day / week / month aggregates | both | `SummaryRepository` |
| Clear all data | all tables | `DataResetRepository.clearAllData` |

Every statement that includes user input uses bound `?` parameters; no SQL is built by
concatenating user text.

## Versioning and migrations

`DATABASE_VERSION` is `2`. `onCreate` creates all five tables, indexes and default
goal/settings rows.

`onUpgrade` performs the additive **1 → 2** migration:

1. add `daily_steps.last_raw_sensor INTEGER` (NULL for existing rows);
2. add `workouts.duration_seconds INTEGER` (NULL for existing rows);
3. add `workouts.step_count INTEGER` (NULL for existing rows);
4. create `running_sessions`;
5. fill in missing goal/settings rows, including `auto_steps_enabled` and
   `onboarding_completed`.

Existing steps, workouts, goals and reminder settings are preserved. The migration does
not call `onCreate` as a shortcut. It is covered by
`app/src/androidTest/java/io/github/praneetx/motionfit/database/DatabaseMigrationTest.java`,
which builds a genuine version-1 database, upgrades it through the real helper, checks
the resulting version and schema, verifies the old rows survive with NULL new columns,
and reopens the upgraded database.

`onDowngrade` recreates the schema, which destroys data. Downgrades are not supported;
see [limitations.md](limitations.md).

## Data deletion

"Clear all data" (`SettingsActivity` → `DataResetRepository.clearAllData`) runs inside
one transaction:

1. delete any in-progress Running session;
2. delete every row from `daily_steps`;
3. delete every row from `workouts`;
4. reset `fitness_goals` to the defaults (8000 / 30);
5. reset reminder settings to off at 18:00;
6. reset `auto_steps_enabled` and `onboarding_completed` to false;
7. the Activity stops the tracking service and cancels any scheduled reminder.

The user confirms in a dialog first. Uninstalling the app or clearing its storage also
removes the database.
