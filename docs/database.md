# Database design

The app stores everything in one local SQLite database, `fitness_tracker.db`,
managed by `FitnessDatabaseHelper` (a `SQLiteOpenHelper`). The schema is version 1.

All table and column names are defined once in
`com.example.fitnesstracker.database.DatabaseContract`.

## Date & timestamp formats

| Purpose | Format | Example |
|---|---|---|
| Calendar day (`activity_date`, `workout_date`) | `YYYY-MM-DD` | `2026-07-11` |
| Record timestamp (`created_at`, `updated_at`) | `YYYY-MM-DDTHH:mm:ss` | `2026-07-11T18:30:05` |

Fixed-width ISO text keeps SQLite `BETWEEN`/`ORDER BY` comparisons correct without
any extra parsing. The app uses the **device's local time zone** when it decides
what "today" is.

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

* Exactly **one row per calendar day**: `activity_date` is UNIQUE and the
  repository uses update-then-insert.
* Negative totals are rejected twice: by validation in
  `StepsRepository.saveDailySteps` and by the `CHECK (steps >= 0)` constraint.

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

Indexes: `idx_workouts_date` on `workout_date`, `idx_workouts_type` on
`workout_type`. There is **no distance and no calorie column** - those features are
intentionally excluded.

### `fitness_goals`

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, CHECK (`id` = 1) |
| `daily_step_goal` | INTEGER | NOT NULL, CHECK (> 0) |
| `daily_workout_minutes_goal` | INTEGER | NOT NULL, CHECK (> 0) |
| `updated_at` | TEXT | NOT NULL |

`CHECK (id = 1)` enforces a single active goal row for the MVP. Defaults on
first launch: **8000 steps / 30 minutes**.

### `app_settings`

| Column | Type | Constraints |
|---|---|---|
| `setting_key` | TEXT | PRIMARY KEY |
| `setting_value` | TEXT | nullable |

Keys currently used: `reminder_enabled` (`true`/`false`),
`reminder_hour` (`0`-`23`), `reminder_minute` (`0`-`59`).
Defaults: reminders **disabled**, time **18:00**.

## Relationships

All data belongs to a single local user, so there are no foreign keys. Steps and
workouts are independent tables that are combined by date when building summaries.

## CRUD operations

| Operation | Table | Where |
|---|---|---|
| Upsert daily total | `daily_steps` | `StepsRepository.saveDailySteps` |
| Read a day's steps | `daily_steps` | `StepsRepository.getForDate` |
| List all step history | `daily_steps` | `StepsRepository.getAll` |
| Insert / update / delete workout | `workouts` | `WorkoutRepository.insert` / `update` / `delete` |
| Filter workouts | `workouts` | `WorkoutRepository.getWorkouts(type, date, limit)` |
| Read / save goals | `fitness_goals` | `GoalRepository.getGoal` / `saveGoal` |
| Read / save reminder settings | `app_settings` | `SettingsRepository.getSettings` / `saveReminder` |
| Day / week / month aggregates | `daily_steps` + `workouts` | `SummaryRepository` |
| Clear all data | all tables | `DataResetRepository.clearAllData` |

Every statement that includes user input uses bound parameters (`?`); no SQL is
built by concatenating user text.

## Versioning and migrations

`DatabaseContract.DATABASE_VERSION` is `1`. `FitnessDatabaseHelper.onUpgrade`
contains a numbered migration switch for future versions and re-runs the
idempotent `CREATE TABLE IF NOT EXISTS` / `INSERT OR IGNORE` steps as a safety
net for additive changes. `onDowngrade` recreates the schema (destructive, and
documented in `docs/limitations.md`).

## Data deletion behaviour

"Clear all data" (`SettingsActivity` -> `DataResetRepository.clearAllData`) runs
inside **one transaction**:

1. delete every row from `daily_steps`;
2. delete every row from `workouts`;
3. reset `fitness_goals` to the defaults (8000 / 30);
4. reset reminder settings to disabled at 18:00;
5. the Activity cancels any scheduled reminder.

The user must confirm in a dialog before any of this happens. Uninstalling the app
or clearing app data from Android settings also removes the database file.
