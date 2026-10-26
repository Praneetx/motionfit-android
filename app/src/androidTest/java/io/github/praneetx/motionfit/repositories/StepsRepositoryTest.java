package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.database.DatabaseContract;
import io.github.praneetx.motionfit.database.FitnessDatabaseHelper;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.utils.OperationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented contract tests for the daily step checkpoint repository.
 *
 * <p>These tests use the production database but clear it first, matching the
 * existing instrumented database tests. The stale-checkpoint test deliberately
 * changes the row behind a loaded snapshot to prove the repository reloads and
 * recomputes instead of overwriting a newer correction.</p>
 */
@RunWith(AndroidJUnit4.class)
public class StepsRepositoryTest {

    private static final String DATE = "2031-06-10";
    private static final String OTHER_DATE = "2031-06-11";

    private StepsRepository repository;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        repository = new StepsRepository(context);
        db = FitnessDatabaseHelper.getInstance(context).getWritableDatabase();
    }

    @Test
    public void firstSensorReadingStartsAtZeroWithCheckpointAndSensorSource() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());

        assertStored(0, 1000L, DailySteps.SOURCE_SENSOR);
    }

    @Test
    public void laterSensorReadingAddsExactlyTheDelta() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());

        assertStored(10, 1010L, DailySteps.SOURCE_SENSOR);
    }

    @Test
    public void duplicateReadingChangesNothing() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());

        // Give updated_at an unmistakable old value so any write would be visible
        // even if the duplicate arrived within the same wall-clock second.
        ContentValues oldTimestamp = new ContentValues();
        oldTimestamp.put(DatabaseContract.COL_DS_UPDATED_AT, "2000-01-01T00:00:00");
        assertEquals(1, db.update(
                DatabaseContract.TABLE_DAILY_STEPS,
                oldTimestamp,
                DatabaseContract.COL_DS_DATE + " = ?",
                new String[]{DATE}));

        DailySteps before = repository.getForDate(DATE);

        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());
        DailySteps after = repository.getForDate(DATE);

        assertNotNull(before);
        assertNotNull(after);
        assertEquals(before.getSteps(), after.getSteps());
        assertEquals(before.getLastRawSensor(), after.getLastRawSensor());
        assertEquals(before.getSource(), after.getSource());
        assertEquals(before.getUpdatedAt(), after.getUpdatedAt());
    }

    @Test
    public void decreasingReadingPreservesTotalAndMovesCheckpoint() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 900L).isSuccess());

        assertStored(10, 900L, DailySteps.SOURCE_SENSOR);
    }

    @Test
    public void calibrationSetsAbsoluteTotalCheckpointAndFutureDelta() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1100L).isSuccess());
        assertStored(100, 1100L, DailySteps.SOURCE_SENSOR);

        assertTrue(repository.calibrate(DATE, 5000, 1100L).isSuccess());
        assertStored(5000, 1100L, DailySteps.SOURCE_SENSOR_ADJUSTED);

        assertTrue(repository.applySensorReading(DATE, 1110L).isSuccess());
        assertStored(5010, 1110L, DailySteps.SOURCE_SENSOR_ADJUSTED);
    }

    @Test
    public void manualTotalWithAutoOffClearsCheckpointAndUsesManualSource() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1100L).isSuccess());
        assertStored(100, 1100L, DailySteps.SOURCE_SENSOR);

        assertTrue(repository.saveManualTotal(DATE, 7000).isSuccess());
        assertStored(7000, null, DailySteps.SOURCE_MANUAL);

        // The next sensor reading preserves the manual total and becomes adjusted.
        assertTrue(repository.applySensorReading(DATE, 2000L).isSuccess());
        assertStored(7000, 2000L, DailySteps.SOURCE_SENSOR_ADJUSTED);
        assertTrue(repository.applySensorReading(DATE, 2010L).isSuccess());
        assertStored(7010, 2010L, DailySteps.SOURCE_SENSOR_ADJUSTED);
    }

    @Test
    public void existingSaveMethodAlsoClearsCheckpoint() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1100L).isSuccess());

        assertTrue(repository.saveDailySteps(DATE, 8000, DailySteps.SOURCE_MANUAL).isSuccess());
        assertStored(8000, null, DailySteps.SOURCE_MANUAL);
    }

    @Test
    public void manualCalibrationWithoutRawPreservesTotalUntilNextReading() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1100L).isSuccess());

        assertTrue(repository.calibrate(DATE, 6000, null).isSuccess());
        assertStored(6000, null, DailySteps.SOURCE_MANUAL);

        assertTrue(repository.applySensorReading(DATE, 5000L).isSuccess());
        assertStored(6000, 5000L, DailySteps.SOURCE_SENSOR_ADJUSTED);
        assertTrue(repository.applySensorReading(DATE, 5010L).isSuccess());
        assertStored(6010, 5010L, DailySteps.SOURCE_SENSOR_ADJUSTED);
    }

    @Test
    public void staleCheckpointIsDetectedReloadedAndNeverOverwritten() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        DailySteps stale = repository.getForDate(DATE);
        assertNotNull(stale);
        assertEquals(Long.valueOf(1000L), stale.getLastRawSensor());

        // Simulate another writer completing a calibration between the snapshot
        // above and the pending sensor update.
        setStoredState(500, 2000L, DailySteps.SOURCE_SENSOR_ADJUSTED);

        assertTrue(repository.applySensorReading(DATE, 2010L, stale).isSuccess());

        // Recomputing from the current row adds only 10, not 1010. An
        // unconditional write would erase the 500-step correction.
        assertStored(510, 2010L, DailySteps.SOURCE_SENSOR_ADJUSTED);
    }

    @Test
    public void repeatedSavesKeepOneRowPerDate() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());
        assertTrue(repository.saveManualTotal(DATE, 2000).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 3000L).isSuccess());
        assertTrue(repository.applySensorReading(OTHER_DATE, 3100L).isSuccess());

        assertEquals(1L, DatabaseUtils.queryNumEntries(
                db,
                DatabaseContract.TABLE_DAILY_STEPS,
                DatabaseContract.COL_DS_DATE + " = ?",
                new String[]{DATE}));
        assertEquals(2, repository.getAll().size());
    }

    @Test
    public void continuousMidnightReadingCarriesTheGapIntoTheNewDay() {
        assertTrue(repository.applySensorReading(DATE, 1000L).isSuccess());
        assertTrue(repository.applySensorReading(DATE, 1010L).isSuccess());

        assertTrue(repository.applyContinuousSensorReading(
                OTHER_DATE, 1100L, DATE, 1010L).isSuccess());

        DailySteps previous = repository.getForDate(DATE);
        DailySteps next = repository.getForDate(OTHER_DATE);
        assertNotNull(previous);
        assertNotNull(next);
        assertEquals(10, previous.getSteps());
        assertEquals(Long.valueOf(1010L), previous.getLastRawSensor());
        assertEquals(90, next.getSteps());
        assertEquals(Long.valueOf(1100L), next.getLastRawSensor());
        assertEquals(DailySteps.SOURCE_SENSOR, next.getSource());
    }

    @Test
    public void continuousMidnightReadingPreservesAnExistingManualNewDayTotal() {
        assertTrue(repository.saveManualTotal(OTHER_DATE, 5000).isSuccess());

        assertTrue(repository.applyContinuousSensorReading(
                OTHER_DATE, 1100L, DATE, 1010L).isSuccess());

        DailySteps next = repository.getForDate(OTHER_DATE);
        assertNotNull(next);
        assertEquals(5000, next.getSteps());
        assertEquals(Long.valueOf(1100L), next.getLastRawSensor());
        assertEquals(DailySteps.SOURCE_SENSOR_ADJUSTED, next.getSource());
    }

    @Test
    public void negativeManualTotalIsRejectedWithoutWriting() {
        OperationResult result = repository.saveManualTotal(DATE, -1);

        assertFalse(result.isSuccess());
        assertNull(repository.getForDate(DATE));
    }

    private void assertStored(int expectedSteps, Long expectedCheckpoint, String expectedSource) {
        DailySteps stored = repository.getForDate(DATE);
        assertNotNull(stored);
        assertEquals(expectedSteps, stored.getSteps());
        assertEquals(expectedCheckpoint, stored.getLastRawSensor());
        assertEquals(expectedSource, stored.getSource());
    }

    private void setStoredState(int steps, Long checkpoint, String source) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.COL_DS_STEPS, steps);
        if (checkpoint == null) {
            values.putNull(DatabaseContract.COL_DS_LAST_RAW_SENSOR);
        } else {
            values.put(DatabaseContract.COL_DS_LAST_RAW_SENSOR, checkpoint);
        }
        values.put(DatabaseContract.COL_DS_SOURCE, source);
        assertEquals(1, db.update(
                DatabaseContract.TABLE_DAILY_STEPS,
                values,
                DatabaseContract.COL_DS_DATE + " = ?",
                new String[]{DATE}));
    }
}
