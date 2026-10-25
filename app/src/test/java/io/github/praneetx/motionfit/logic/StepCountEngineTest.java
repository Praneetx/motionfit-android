package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Contract tests for the pure daily step rules. These tests run on the JVM and
 * do not touch SQLite or Android.
 */
public class StepCountEngineTest {

    private static final String TODAY = "2026-10-25";
    private static final String YESTERDAY = "2026-10-24";

    private static StepDayState state(String date, int steps, Long raw, StepSource source) {
        return new StepDayState(date, steps, raw, source);
    }

    private static StepUpdate read(StepDayState previous, long raw) {
        return StepCountEngine.applyReading(previous, raw, TODAY);
    }

    @Test
    public void firstReadingStartsAtZeroAndStoresCheckpoint() {
        StepUpdate update = read(null, 1000);

        assertTrue(update.getWriteRequired());
        assertEquals(StepWriteMode.ABSOLUTE, update.getWriteMode());
        assertEquals(0, update.getStepsDelta());
        assertNull(update.getExpectedLastRawSensor());
        assertTrue(update.getDayChanged());
        assertFalse(update.getResetDetected());
        assertEquals(TODAY, update.getState().getDateIso());
        assertEquals(0, update.getState().getSteps());
        assertEquals(Long.valueOf(1000L), update.getState().getLastRawSensor());
        assertEquals(StepSource.SENSOR, update.getState().getSource());
    }

    @Test
    public void laterReadingAddsExactlyTheDeltaAndExposesOldCheckpoint() {
        StepDayState before = state(TODAY, 0, 1000L, StepSource.SENSOR);

        StepUpdate update = read(before, 1010);

        assertTrue(update.getWriteRequired());
        assertEquals(StepWriteMode.DELTA, update.getWriteMode());
        assertEquals(10, update.getStepsDelta());
        assertEquals(Long.valueOf(1000L), update.getExpectedLastRawSensor());
        assertEquals(10, update.getState().getSteps());
        assertEquals(Long.valueOf(1010L), update.getState().getLastRawSensor());
        assertFalse(update.getResetDetected());
    }

    @Test
    public void duplicateReadingWritesNothing() {
        StepDayState before = state(TODAY, 10, 1010L, StepSource.SENSOR);

        StepUpdate update = read(before, 1010);

        assertFalse(update.getWriteRequired());
        assertEquals(StepWriteMode.NONE, update.getWriteMode());
        assertEquals(0, update.getStepsDelta());
        assertEquals(Long.valueOf(1010L), update.getExpectedLastRawSensor());
        assertEquals(10, update.getState().getSteps());
        assertEquals(Long.valueOf(1010L), update.getState().getLastRawSensor());
        assertFalse(update.getDayChanged());
        assertFalse(update.getSourceChanged());
    }

    @Test
    public void decreasingReadingPreservesTotalAndRebases() {
        StepDayState before = state(TODAY, 500, 1000L, StepSource.SENSOR);

        StepUpdate update = read(before, 900);

        assertTrue(update.getWriteRequired());
        assertEquals(StepWriteMode.DELTA, update.getWriteMode());
        assertEquals(0, update.getStepsDelta());
        assertTrue(update.getResetDetected());
        assertEquals(500, update.getState().getSteps());
        assertEquals(Long.valueOf(900L), update.getState().getLastRawSensor());
    }

    @Test
    public void staleCheckpointConflictIsRecomputedFromTheCurrentRow() {
        StepDayState loaded = state(TODAY, 100, 2000L, StepSource.SENSOR);
        StepUpdate pending = read(loaded, 2010);
        assertEquals(Long.valueOf(2000L), pending.getExpectedLastRawSensor());

        // A calibration landed before the pending update. Retrying from the
        // reloaded row adds only the new delta and never overwrites the total.
        StepDayState current = state(TODAY, 5000, 2005L, StepSource.SENSOR_ADJUSTED);
        StepUpdate retry = read(current, 2010);

        assertEquals(5, retry.getStepsDelta());
        assertEquals(5005, retry.getState().getSteps());
        assertEquals(Long.valueOf(2010L), retry.getState().getLastRawSensor());
    }

    @Test
    public void continuousMidnightRolloverUsesGapSincePreviousCheckpoint() {
        StepDayState yesterday = state(YESTERDAY, 500, 1000L, StepSource.SENSOR);

        StepUpdate update = StepCountEngine.applyReading(yesterday, 1100, TODAY, true);

        assertTrue(update.getDayChanged());
        assertEquals(StepWriteMode.ABSOLUTE, update.getWriteMode());
        assertEquals(100, update.getStepsDelta());
        assertEquals(TODAY, update.getState().getDateIso());
        assertEquals(100, update.getState().getSteps());
        assertEquals(Long.valueOf(1100L), update.getState().getLastRawSensor());
        assertEquals(StepSource.SENSOR, update.getState().getSource());
    }

    @Test
    public void midnightWithoutContinuousCheckpointStartsAtZero() {
        StepDayState yesterday = state(YESTERDAY, 500, 1000L, StepSource.SENSOR);

        StepUpdate update = read(yesterday, 1100);

        assertTrue(update.getDayChanged());
        assertEquals(0, update.getStepsDelta());
        assertEquals(0, update.getState().getSteps());
        assertEquals(Long.valueOf(1100L), update.getState().getLastRawSensor());
    }

    @Test
    public void newDayWithoutCheckpointStartsAtZero() {
        StepDayState yesterday = state(YESTERDAY, 500, null, StepSource.MANUAL);

        StepUpdate update = StepCountEngine.applyReading(yesterday, 1100, TODAY, true);

        assertTrue(update.getDayChanged());
        assertEquals(0, update.getState().getSteps());
        assertEquals(Long.valueOf(1100L), update.getState().getLastRawSensor());
    }

    @Test
    public void newDayWithDecreaseStartsAtZero() {
        StepDayState yesterday = state(YESTERDAY, 500, 1000L, StepSource.SENSOR);

        StepUpdate update = StepCountEngine.applyReading(yesterday, 500, TODAY, true);

        assertTrue(update.getDayChanged());
        assertFalse(update.getResetDetected());
        assertEquals(0, update.getState().getSteps());
        assertEquals(Long.valueOf(500L), update.getState().getLastRawSensor());
    }

    @Test
    public void backwardsDateChangeNeverSubtractsSteps() {
        StepDayState tomorrow = state(TODAY, 500, 1000L, StepSource.SENSOR);

        StepUpdate update = StepCountEngine.applyReading(tomorrow, 1200, YESTERDAY, true);

        assertTrue(update.getDayChanged());
        assertEquals(YESTERDAY, update.getState().getDateIso());
        assertEquals(0, update.getState().getSteps());
        assertEquals(Long.valueOf(1200L), update.getState().getLastRawSensor());
    }

    @Test
    public void manualRowGetsCheckpointWithoutLosingTotal() {
        StepDayState before = state(TODAY, 5000, null, StepSource.MANUAL);

        StepUpdate update = read(before, 10000);

        assertTrue(update.getWriteRequired());
        assertEquals(StepWriteMode.DELTA, update.getWriteMode());
        assertEquals(0, update.getStepsDelta());
        assertNull(update.getExpectedLastRawSensor());
        assertTrue(update.getSourceChanged());
        assertEquals(5000, update.getState().getSteps());
        assertEquals(Long.valueOf(10000L), update.getState().getLastRawSensor());
        assertEquals(StepSource.SENSOR_ADJUSTED, update.getState().getSource());
    }

    @Test
    public void calibrationSetsCheckpointAndFutureDeltaContinues() {
        StepDayState before = state(TODAY, 200, 10000L, StepSource.SENSOR);

        StepUpdate calibrated = StepCountEngine.calibrate(before, 5000, 10000L, TODAY);
        assertEquals(StepWriteMode.ABSOLUTE, calibrated.getWriteMode());
        assertEquals(Long.valueOf(10000L), calibrated.getExpectedLastRawSensor());
        assertEquals(5000, calibrated.getState().getSteps());
        assertEquals(Long.valueOf(10000L), calibrated.getState().getLastRawSensor());
        assertEquals(StepSource.SENSOR_ADJUSTED, calibrated.getState().getSource());

        StepUpdate next = read(calibrated.getState(), 10010);
        assertEquals(StepWriteMode.DELTA, next.getWriteMode());
        assertEquals(10, next.getStepsDelta());
        assertEquals(5010, next.getState().getSteps());
        assertEquals(StepSource.SENSOR_ADJUSTED, next.getState().getSource());
    }

    @Test
    public void calibrationWithoutRawClearsCheckpoint() {
        StepDayState before = state(TODAY, 200, 10000L, StepSource.SENSOR);

        StepUpdate update = StepCountEngine.calibrate(before, 5000, null, TODAY);

        assertEquals(5000, update.getState().getSteps());
        assertNull(update.getState().getLastRawSensor());
        assertEquals(StepSource.MANUAL, update.getState().getSource());
    }

    @Test
    public void autoOffManualTotalSurvivesFirstSensorReading() {
        StepDayState manual = state(TODAY, 5000, null, StepSource.MANUAL);

        StepUpdate firstSensor = read(manual, 10000);
        assertEquals(5000, firstSensor.getState().getSteps());
        assertEquals(StepSource.SENSOR_ADJUSTED, firstSensor.getState().getSource());

        StepUpdate next = read(firstSensor.getState(), 10010);
        assertEquals(5010, next.getState().getSteps());
    }

    @Test
    public void calibrationWithoutPreviousCreatesManualState() {
        StepUpdate update = StepCountEngine.calibrate(null, 5000, null, TODAY);

        assertTrue(update.getDayChanged());
        assertEquals(TODAY, update.getState().getDateIso());
        assertEquals(5000, update.getState().getSteps());
        assertNull(update.getState().getLastRawSensor());
        assertEquals(StepSource.MANUAL, update.getState().getSource());
    }

    @Test
    public void calibrationNeverStoresANegativeTotal() {
        StepUpdate update = StepCountEngine.calibrate(null, -500, null, TODAY);

        assertEquals(0, update.getState().getSteps());
        assertEquals(StepSource.MANUAL, update.getState().getSource());
    }

    @Test
    public void normalDeltaKeepsAdjustedSource() {
        StepDayState before = state(TODAY, 5000, 10000L, StepSource.SENSOR_ADJUSTED);

        StepUpdate update = read(before, 10010);

        assertEquals(StepSource.SENSOR_ADJUSTED, update.getState().getSource());
        assertFalse(update.getSourceChanged());
    }

    @Test
    public void negativeRawIsClampedToZero() {
        StepUpdate update = read(null, -50);

        assertEquals(Long.valueOf(0L), update.getState().getLastRawSensor());
        assertEquals(0, update.getState().getSteps());
    }

    @Test
    public void largeGapAddsWholeDeltaWithoutOverflow() {
        StepDayState before = state(TODAY, 100, 1000L, StepSource.SENSOR);

        StepUpdate update = read(before, 100000L);

        assertEquals(99000, update.getStepsDelta());
        assertEquals(99100, update.getState().getSteps());
        assertEquals(Long.valueOf(100000L), update.getState().getLastRawSensor());
    }

    @Test
    public void hugeDeltaDoesNotOverflow() {
        StepDayState before = state(TODAY, 0, 0L, StepSource.SENSOR);

        StepUpdate update = read(before, Long.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, update.getState().getSteps());
        assertEquals(Integer.MAX_VALUE, update.getStepsDelta());
        assertEquals(Long.valueOf(Long.MAX_VALUE), update.getState().getLastRawSensor());
    }

    @Test
    public void sourceWireValuesRoundTrip() {
        assertEquals("manual", StepSource.MANUAL.getWireValue());
        assertEquals("sensor", StepSource.SENSOR.getWireValue());
        assertEquals("sensor_adjusted", StepSource.SENSOR_ADJUSTED.getWireValue());
        assertEquals(StepSource.SENSOR_ADJUSTED, StepSource.fromWireValue("sensor_adjusted"));
        assertEquals(StepSource.MANUAL, StepSource.fromWireValue("unexpected"));
        assertEquals(StepSource.MANUAL, StepSource.fromWireValue(null));
    }
}
