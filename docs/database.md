# Database

Motionfit stores everything in one local SQLite database, **`fitness_tracker.db`**,
managed by `FitnessDatabaseHelper` (a `SQLiteOpenHelper`). The schema is version 1.

Table and column names are defined once in
`io.github.praneetx.motionfit.database.DatabaseContract`.

> The database file name and table names are intentionally independent of the Java
> package, so renaming the app or its package does not change the schema or lose data.

## Date and timestamp formats

| Purpose | Format | Example |
|---|---|---|
| Calendar day (`activity_date`, `workout_date`) | `YYYY-MM-DD` | `2026-07-11` |
| Record timestamp (`created_at`, `updated_at`) | `YYYY-MM-DDTHH:mm:ss` | `2026-07-11T18:30:05` |

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
| `created_at` | TEXT | NOT NULL |
| `updated_at` | TEXT | NOT NULL |

One row per calendar day, enforced twice: by the repository's update-then-insert logic
and by the UNIQUE constraint. Negative totals are rejected by validation and by the
CHECK constraint.

### `workouts`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `workout_type` | TEXT | NOT NULL |
| `workout_date` | TEXT | NOT NULL |
| `duration_minutes` | INTEGER | NOT NULL, CHECK (`duration_minutes` > 0) |
| `notes` | TEXT | nullable |
| `created_at` | TEXT | NOT NULL |
| `updated_at` | TEXT | NOT NULL |

Indexes: `idx_workouts_date` on `workout_date`, `idx_workouts_type` on `workout_type`.
There is no distance or calorie column.

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

Keys: `reminder_enabled` (`true`/`false`), `reminder_hour` (`0`-`23`),
`reminder_minute` (`0`-`59`). Defaults: reminders off at 18:00.

## Relationships

Single local user, so there are no foreign keys. Steps and workouts are independent
tables combined by date when summaries are built.

## CRUD map

| Operation | Table | Where |
|---|---|---|
| Upsert a day's total | `daily_steps` | `StepsRepository.saveDailySteps` |
| Read a day's steps | `daily_steps` | `StepsRepository.getForDate` |
| Step history | `daily_steps` | `StepsRepository.getAll` |
| Insert / update / delete workout | `workouts` | `WorkoutRepository` |
| Filter workouts | `workouts` | `WorkoutRepository.getWorkouts(type, date, limit)` |
| Read / save goals | `fitness_goals` | `GoalRepository` |
| Read / save reminder settings | `app_settings` | `SettingsRepository` |
| Day / week / month aggregates | both | `SummaryRepository` |
| Clear all data | all tables | `DataResetRepository.clearAllData` |

Every statement that includes user input uses bound `?` parameters; no SQL is built by
concatenating user text.

## Versioning and migrations

`DATABASE_VERSION` is `1`. `FitnessDatabaseHelper.onUpgrade` holds the numbered
migration switch for future versions and re-runs the idempotent
`CREATE TABLE IF NOT EXISTS` / `INSERT OR IGNORE` steps as a safety net for additive
changes. `onDowngrade` recreates the schema (destructive, documented in
[limitations.md](limitations.md)).

## Data deletion

"Clear all data" (`SettingsActivity` → `DataResetRepository.clearAllData`) runs inside
one transaction:

1. delete every row from `daily_steps`;
2. delete every row from `workouts`;
3. reset `fitness_goals` to the defaults (8000 / 30);
4. reset reminder settings to off at 18:00;
5. the Activity cancels any scheduled reminder.

The user confirms in a dialog first. Uninstalling the app or clearing its storage also
removes the database.
