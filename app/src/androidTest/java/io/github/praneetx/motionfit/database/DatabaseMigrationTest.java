package io.github.praneetx.motionfit.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Proves a genuine version-1 database upgrades to version 2 additively.
 *
 * <p>The version-1 DDL is intentionally repeated here instead of using
 * DatabaseContract, so a future edit to the production schema cannot silently
 * rewrite the historical fixture and make this test meaningless.</p>
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    private static final String UPGRADE_DB = "v2_migration_upgrade_test.db";
    private static final String FRESH_DB = "v2_fresh_schema_test.db";

    private static final String V1_DATE = "2031-05-05";
    private static final String V1_TIMESTAMP = "2031-05-05T08:00:00";
    private static final String V1_UPDATED = "2031-05-05T08:30:00";

    private static final String V1_DAILY_STEPS_SQL =
            "CREATE TABLE daily_steps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "activity_date TEXT NOT NULL UNIQUE, " +
                    "steps INTEGER NOT NULL DEFAULT 0 CHECK (steps >= 0), " +
                    "source TEXT NOT NULL DEFAULT 'manual', " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL" +
                    ");";

    private static final String V1_WORKOUTS_SQL =
            "CREATE TABLE workouts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "workout_type TEXT NOT NULL, " +
                    "workout_date TEXT NOT NULL, " +
                    "duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0), " +
                    "notes TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL" +
                    ");";

    private static final String V1_GOALS_SQL =
            "CREATE TABLE fitness_goals (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                    "daily_step_goal INTEGER NOT NULL CHECK (daily_step_goal > 0), " +
                    "daily_workout_minutes_goal INTEGER NOT NULL CHECK (daily_workout_minutes_goal > 0), " +
                    "updated_at TEXT NOT NULL" +
                    ");";

    private static final String V1_SETTINGS_SQL =
            "CREATE TABLE app_settings (" +
                    "setting_key TEXT PRIMARY KEY, " +
                    "setting_value TEXT" +
                    ");";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(UPGRADE_DB);
        context.deleteDatabase(FRESH_DB);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(UPGRADE_DB);
        context.deleteDatabase(FRESH_DB);
    }

    @Test
    public void versionOneDatabase_upgradesToV2AndPreservesRows() {
        createV1Database();

        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context, UPGRADE_DB, 2);
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            assertEquals("migration must set user_version to 2", 2, db.getVersion());

            try (Cursor cursor = db.query(
                    "daily_steps",
                    new String[]{"steps", "source", "created_at", "updated_at", "last_raw_sensor"},
                    "activity_date = ?",
                    new String[]{V1_DATE},
                    null, null, null)) {
                assertTrue("v1 daily_steps row must survive", cursor.moveToFirst());
                assertEquals(4321, cursor.getInt(0));
                assertEquals("manual", cursor.getString(1));
                assertEquals(V1_TIMESTAMP, cursor.getString(2));
                assertEquals(V1_UPDATED, cursor.getString(3));
                assertTrue("new daily_steps column must be NULL for v1 rows", cursor.isNull(4));
            }

            try (Cursor cursor = db.query(
                    "workouts",
                    new String[]{"duration_minutes", "notes", "duration_seconds", "step_count"},
                    "workout_date = ?",
                    new String[]{V1_DATE},
                    null, null, null)) {
                assertTrue("v1 workout row must survive", cursor.moveToFirst());
                assertEquals(30, cursor.getInt(0));
                assertEquals("v1 row", cursor.getString(1));
                assertTrue("duration_seconds must be NULL for v1 rows", cursor.isNull(2));
                assertTrue("step_count must be NULL for v1 rows", cursor.isNull(3));
            }

            try (Cursor cursor = db.query(
                    "fitness_goals",
                    new String[]{"daily_step_goal", "daily_workout_minutes_goal"},
                    "id = 1",
                    null, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(9000, cursor.getInt(0));
                assertEquals(45, cursor.getInt(1));
            }

            assertEquals("true", setting(db, "reminder_enabled"));
            assertEquals("7", setting(db, "reminder_hour"));
            assertEquals("15", setting(db, "reminder_minute"));
            assertEquals("keep-me", setting(db, "legacy_custom"));
            assertEquals("false", setting(db, "auto_steps_enabled"));
            assertEquals("false", setting(db, "onboarding_completed"));

            assertTableColumns(db, "running_sessions", new String[]{
                    "id", "status", "started_at", "started_elapsed_ms",
                    "accumulated_ms", "duration_ms", "last_resumed_elapsed_ms",
                    "step_count", "start_raw_steps", "last_raw_steps",
                    "boot_count", "interrupted", "created_at", "updated_at"
            });
        } finally {
            helper.close();
        }
    }

    @Test
    public void freshInstall_hasSameV2SchemaAndDefaultsAsUpgrade() {
        createV1Database();

        FitnessDatabaseHelper upgradedHelper = new FitnessDatabaseHelper(context, UPGRADE_DB, 2);
        FitnessDatabaseHelper freshHelper = new FitnessDatabaseHelper(context, FRESH_DB, 2);
        SQLiteDatabase upgraded = upgradedHelper.getWritableDatabase();
        SQLiteDatabase fresh = freshHelper.getWritableDatabase();
        try {
            assertEquals(2, upgraded.getVersion());
            assertEquals(2, fresh.getVersion());

            Map<String, String> upgradedSchema = readSchema(upgraded);
            Map<String, String> freshSchema = readSchema(fresh);

            assertRequiredV2Schema(upgradedSchema);
            assertRequiredV2Schema(freshSchema);
            assertEquals("fresh and upgraded databases must expose the same schema",
                    upgradedSchema, freshSchema);

            assertEquals("false", setting(fresh, "auto_steps_enabled"));
            assertEquals("false", setting(fresh, "onboarding_completed"));
            assertEquals("false", setting(fresh, "reminder_enabled"));
            assertEquals("18", setting(fresh, "reminder_hour"));
            assertEquals("0", setting(fresh, "reminder_minute"));
        } finally {
            freshHelper.close();
            upgradedHelper.close();
        }
    }

    @Test
    public void newColumnsRoundTripAndRunningSessionIsSingleton() {
        createV1Database();

        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context, UPGRADE_DB, 2);
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues daily = new ContentValues();
            daily.put("last_raw_sensor", 12345L);
            assertEquals(1, db.update("daily_steps", daily,
                    "activity_date = ?", new String[]{V1_DATE}));
            try (Cursor cursor = db.query("daily_steps", new String[]{"last_raw_sensor"},
                    "activity_date = ?", new String[]{V1_DATE}, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(12345L, cursor.getLong(0));
            }

            ContentValues workout = new ContentValues();
            workout.put("duration_seconds", 1800);
            workout.put("step_count", 321);
            assertEquals(1, db.update("workouts", workout,
                    "workout_date = ?", new String[]{V1_DATE}));
            try (Cursor cursor = db.query("workouts",
                    new String[]{"duration_seconds", "step_count"},
                    "workout_date = ?", new String[]{V1_DATE}, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(1800, cursor.getInt(0));
                assertEquals(321, cursor.getInt(1));
            }

            ContentValues session = new ContentValues();
            session.put("id", 1);
            session.put("status", "active");
            session.put("started_at", V1_TIMESTAMP);
            session.put("started_elapsed_ms", 1000L);
            session.put("created_at", V1_TIMESTAMP);
            session.put("updated_at", V1_UPDATED);
            assertEquals(1L, db.insertOrThrow("running_sessions", null, session));

            try (Cursor cursor = db.query("running_sessions",
                    new String[]{
                            "accumulated_ms", "duration_ms", "last_resumed_elapsed_ms",
                            "step_count", "start_raw_steps", "last_raw_steps",
                            "boot_count", "interrupted"
                    },
                    "id = 1", null, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0L, cursor.getLong(0));
                assertEquals(0L, cursor.getLong(1));
                assertTrue(cursor.isNull(2));
                assertEquals(0, cursor.getInt(3));
                assertTrue(cursor.isNull(4));
                assertTrue(cursor.isNull(5));
                assertEquals(-1, cursor.getInt(6));
                assertEquals(0, cursor.getInt(7));
            }

            try {
                ContentValues duplicate = new ContentValues();
                duplicate.put("id", 1);
                duplicate.put("status", "paused");
                duplicate.put("started_at", V1_TIMESTAMP);
                duplicate.put("started_elapsed_ms", 2000L);
                duplicate.put("created_at", V1_TIMESTAMP);
                duplicate.put("updated_at", V1_UPDATED);
                db.insertOrThrow("running_sessions", null, duplicate);
                fail("a second running_sessions row with id = 1 must be rejected");
            } catch (SQLiteConstraintException expected) {
                // CHECK (id = 1) plus the primary key keep the table a singleton.
            }

            assertEquals(1L, DatabaseUtils.queryNumEntries(db, "running_sessions"));
        } finally {
            helper.close();
        }
    }

    @Test
    public void upgradedDatabase_reopensWithoutLosingData() {
        createV1Database();

        FitnessDatabaseHelper first = new FitnessDatabaseHelper(context, UPGRADE_DB, 2);
        SQLiteDatabase db = first.getWritableDatabase();
        try {
            ContentValues daily = new ContentValues();
            daily.put("last_raw_sensor", 777L);
            assertEquals(1, db.update("daily_steps", daily,
                    "activity_date = ?", new String[]{V1_DATE}));

            ContentValues session = new ContentValues();
            session.put("id", 1);
            session.put("status", "paused");
            session.put("started_at", V1_TIMESTAMP);
            session.put("started_elapsed_ms", 1000L);
            session.put("created_at", V1_TIMESTAMP);
            session.put("updated_at", V1_UPDATED);
            assertEquals(1L, db.insertOrThrow("running_sessions", null, session));
        } finally {
            first.close();
        }

        FitnessDatabaseHelper second = new FitnessDatabaseHelper(context, UPGRADE_DB, 2);
        SQLiteDatabase reopened = second.getWritableDatabase();
        try {
            assertEquals(2, reopened.getVersion());
            assertEquals(4321, queryInt(reopened,
                    "SELECT steps FROM daily_steps WHERE activity_date = ?", V1_DATE));
            assertEquals(777L, queryLong(reopened,
                    "SELECT last_raw_sensor FROM daily_steps WHERE activity_date = ?", V1_DATE));
            assertEquals(1L, DatabaseUtils.queryNumEntries(reopened, "running_sessions"));
        } finally {
            second.close();
        }
    }

    // ------------------------------------------------------------------
    // Version-1 fixture
    // ------------------------------------------------------------------

    private void createV1Database() {
        SQLiteDatabase db = context.openOrCreateDatabase(UPGRADE_DB, Context.MODE_PRIVATE, null);
        try {
            db.beginTransaction();
            try {
                db.execSQL(V1_DAILY_STEPS_SQL);
                db.execSQL(V1_WORKOUTS_SQL);
                db.execSQL(V1_GOALS_SQL);
                db.execSQL(V1_SETTINGS_SQL);
                db.execSQL("CREATE INDEX idx_workouts_date ON workouts (workout_date);");
                db.execSQL("CREATE INDEX idx_workouts_type ON workouts (workout_type);");

                ContentValues daily = new ContentValues();
                daily.put("activity_date", V1_DATE);
                daily.put("steps", 4321);
                daily.put("source", "manual");
                daily.put("created_at", V1_TIMESTAMP);
                daily.put("updated_at", V1_UPDATED);
                db.insertOrThrow("daily_steps", null, daily);

                ContentValues workout = new ContentValues();
                workout.put("workout_type", "Running");
                workout.put("workout_date", V1_DATE);
                workout.put("duration_minutes", 30);
                workout.put("notes", "v1 row");
                workout.put("created_at", V1_TIMESTAMP);
                workout.put("updated_at", V1_UPDATED);
                db.insertOrThrow("workouts", null, workout);

                ContentValues goal = new ContentValues();
                goal.put("id", 1);
                goal.put("daily_step_goal", 9000);
                goal.put("daily_workout_minutes_goal", 45);
                goal.put("updated_at", V1_UPDATED);
                db.insertOrThrow("fitness_goals", null, goal);

                insertSetting(db, "reminder_enabled", "true");
                insertSetting(db, "reminder_hour", "7");
                insertSetting(db, "reminder_minute", "15");
                insertSetting(db, "legacy_custom", "keep-me");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            db.setVersion(1);
        } finally {
            db.close();
        }
    }

    private static void insertSetting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("setting_key", key);
        values.put("setting_value", value);
        db.insertOrThrow("app_settings", null, values);
    }

    // ------------------------------------------------------------------
    // Schema / query helpers
    // ------------------------------------------------------------------

    private static String setting(SQLiteDatabase db, String key) {
        try (Cursor cursor = db.query("app_settings", new String[]{"setting_value"},
                "setting_key = ?", new String[]{key}, null, null, null)) {
            assertTrue("missing setting: " + key, cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private static void assertTableColumns(SQLiteDatabase db, String table, String[] expected) {
        List<String> actual = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                actual.add(cursor.getString(nameIndex));
            }
        }
        for (String column : expected) {
            assertTrue("missing column " + table + "." + column + " in " + actual,
                    actual.contains(column));
        }
    }

    private static void assertRequiredV2Schema(Map<String, String> schema) {
        for (String required : new String[]{
                "daily_steps.last_raw_sensor",
                "workouts.duration_seconds",
                "workouts.step_count",
                "running_sessions.id",
                "running_sessions.status",
                "running_sessions.started_elapsed_ms",
                "running_sessions.accumulated_ms",
                "running_sessions.duration_ms",
                "running_sessions.last_resumed_elapsed_ms",
                "running_sessions.step_count",
                "running_sessions.start_raw_steps",
                "running_sessions.last_raw_steps",
                "running_sessions.boot_count",
                "running_sessions.interrupted"
        }) {
            assertTrue("missing V2 schema element: " + required, schema.containsKey(required));
        }
    }

    private static Map<String, String> readSchema(SQLiteDatabase db) {
        Map<String, String> schema = new TreeMap<>();
        List<String> tables = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                        "AND name NOT IN ('android_metadata', 'sqlite_sequence') " +
                        "ORDER BY name", null)) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }

        for (String table : tables) {
            schema.put(table, "table");
            try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
                int nameIndex = cursor.getColumnIndexOrThrow("name");
                int typeIndex = cursor.getColumnIndexOrThrow("type");
                int notNullIndex = cursor.getColumnIndexOrThrow("notnull");
                int defaultValueIndex = cursor.getColumnIndexOrThrow("dflt_value");
                int primaryKeyIndex = cursor.getColumnIndexOrThrow("pk");
                while (cursor.moveToNext()) {
                    Map<String, String> ignored = new LinkedHashMap<>();
                    ignored.put("type", cursor.getString(typeIndex));
                    ignored.put("notnull", String.valueOf(cursor.getInt(notNullIndex)));
                    ignored.put("default", cursor.isNull(defaultValueIndex)
                            ? "<null>" : cursor.getString(defaultValueIndex));
                    ignored.put("pk", String.valueOf(cursor.getInt(primaryKeyIndex)));
                    schema.put(table + "." + cursor.getString(nameIndex),
                            ignored.toString());
                }
            }
        }
        return schema;
    }

    private static int queryInt(SQLiteDatabase db, String sql, String arg) {
        try (Cursor cursor = db.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static long queryLong(SQLiteDatabase db, String sql, String arg) {
        try (Cursor cursor = db.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }
}
