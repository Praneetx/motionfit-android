package io.github.praneetx.motionfit.database;

/**
 * One single place that defines the SQLite table names, column names and SQL
 * statements. Keeping all SQL text here makes the database schema easy to read,
 * document and change (see docs/database.md).
 */
public final class DatabaseContract {

    private DatabaseContract() {
        // Utility class: never instantiate.
    }

    public static final String DATABASE_NAME = "fitness_tracker.db";
    public static final int DATABASE_VERSION = 1;

    // ------------------------------------------------------------------
    // daily_steps : exactly one consolidated step total per calendar date
    // ------------------------------------------------------------------
    public static final String TABLE_DAILY_STEPS = "daily_steps";
    public static final String COL_DS_ID = "id";
    public static final String COL_DS_DATE = "activity_date";
    public static final String COL_DS_STEPS = "steps";
    public static final String COL_DS_SOURCE = "source";
    public static final String COL_DS_CREATED_AT = "created_at";
    public static final String COL_DS_UPDATED_AT = "updated_at";

    public static final String SQL_CREATE_DAILY_STEPS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_DAILY_STEPS + " (" +
                    COL_DS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    // UNIQUE guarantees only one row per date, no matter what code runs.
                    COL_DS_DATE + " TEXT NOT NULL UNIQUE, " +
                    // CHECK rejects negative step totals at the database level.
                    COL_DS_STEPS + " INTEGER NOT NULL DEFAULT 0 CHECK (" + COL_DS_STEPS + " >= 0), " +
                    COL_DS_SOURCE + " TEXT NOT NULL DEFAULT 'manual', " +
                    COL_DS_CREATED_AT + " TEXT NOT NULL, " +
                    COL_DS_UPDATED_AT + " TEXT NOT NULL" +
                    ");";

    // ------------------------------------------------------------------
    // workouts : individual workout records
    // ------------------------------------------------------------------
    public static final String TABLE_WORKOUTS = "workouts";
    public static final String COL_W_ID = "id";
    public static final String COL_W_TYPE = "workout_type";
    public static final String COL_W_DATE = "workout_date";
    public static final String COL_W_DURATION = "duration_minutes";
    public static final String COL_W_NOTES = "notes";
    public static final String COL_W_CREATED_AT = "created_at";
    public static final String COL_W_UPDATED_AT = "updated_at";

    public static final String SQL_CREATE_WORKOUTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_WORKOUTS + " (" +
                    COL_W_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_W_TYPE + " TEXT NOT NULL, " +
                    COL_W_DATE + " TEXT NOT NULL, " +
                    // Duration must always be strictly positive.
                    COL_W_DURATION + " INTEGER NOT NULL CHECK (" + COL_W_DURATION + " > 0), " +
                    COL_W_NOTES + " TEXT, " +
                    COL_W_CREATED_AT + " TEXT NOT NULL, " +
                    COL_W_UPDATED_AT + " TEXT NOT NULL" +
                    ");";

    // ------------------------------------------------------------------
    // fitness_goals : exactly one active goal row (id = 1)
    // ------------------------------------------------------------------
    public static final String TABLE_FITNESS_GOALS = "fitness_goals";
    public static final String COL_G_ID = "id";
    public static final String COL_G_STEP_GOAL = "daily_step_goal";
    public static final String COL_G_MINUTES_GOAL = "daily_workout_minutes_goal";
    public static final String COL_G_UPDATED_AT = "updated_at";

    public static final String SQL_CREATE_FITNESS_GOALS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_FITNESS_GOALS + " (" +
                    // CHECK (id = 1) enforces a single goal record for the MVP.
                    COL_G_ID + " INTEGER PRIMARY KEY CHECK (" + COL_G_ID + " = 1), " +
                    COL_G_STEP_GOAL + " INTEGER NOT NULL CHECK (" + COL_G_STEP_GOAL + " > 0), " +
                    COL_G_MINUTES_GOAL + " INTEGER NOT NULL CHECK (" + COL_G_MINUTES_GOAL + " > 0), " +
                    COL_G_UPDATED_AT + " TEXT NOT NULL" +
                    ");";

    // ------------------------------------------------------------------
    // app_settings : small key/value store (reminder preferences, ...)
    // ------------------------------------------------------------------
    public static final String TABLE_APP_SETTINGS = "app_settings";
    public static final String COL_S_KEY = "setting_key";
    public static final String COL_S_VALUE = "setting_value";

    public static final String SQL_CREATE_APP_SETTINGS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_APP_SETTINGS + " (" +
                    COL_S_KEY + " TEXT PRIMARY KEY, " +
                    COL_S_VALUE + " TEXT" +
                    ");";

    // ------------------------------------------------------------------
    // Indexes to keep date-range summary queries fast.
    // ------------------------------------------------------------------
    public static final String SQL_INDEX_WORKOUT_DATE =
            "CREATE INDEX IF NOT EXISTS idx_workouts_date ON " + TABLE_WORKOUTS +
                    " (" + COL_W_DATE + ");";
    public static final String SQL_INDEX_WORKOUT_TYPE =
            "CREATE INDEX IF NOT EXISTS idx_workouts_type ON " + TABLE_WORKOUTS +
                    " (" + COL_W_TYPE + ");";

    // ------------------------------------------------------------------
    // Settings keys and default values
    // ------------------------------------------------------------------
    public static final String SETTING_REMINDER_ENABLED = "reminder_enabled";
    public static final String SETTING_REMINDER_HOUR = "reminder_hour";
    public static final String SETTING_REMINDER_MINUTE = "reminder_minute";

    public static final boolean DEFAULT_REMINDER_ENABLED = false;
    public static final int DEFAULT_REMINDER_HOUR = 18;
    public static final int DEFAULT_REMINDER_MINUTE = 0;

    // Sensible starting goals for a brand new local profile.
    public static final int DEFAULT_STEP_GOAL = 8000;
    public static final int DEFAULT_WORKOUT_MINUTES_GOAL = 30;
}
