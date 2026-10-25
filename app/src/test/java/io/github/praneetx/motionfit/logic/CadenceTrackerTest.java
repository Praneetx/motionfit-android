package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** JVM tests for the rolling 30-second cadence estimate. */
public class CadenceTrackerTest {

    @Test
    public void firstReadingHasNoCadence() {
        CadenceTracker tracker = new CadenceTracker();
        assertNull(tracker.onReading(100, 0));
    }

    @Test
    public void oneDeltaIsNotEnoughData() {
        CadenceTracker tracker = new CadenceTracker();
        assertNull(tracker.onReading(100, 0));
        assertNull(tracker.onReading(101, 3000));
    }

    @Test
    public void twoDeltasAfterFiveSecondsProduceCadence() {
        CadenceTracker tracker = new CadenceTracker();
        assertNull(tracker.onReading(100, 0));
        assertNull(tracker.onReading(101, 3000));
        assertEquals(Integer.valueOf(20), tracker.onReading(102, 6000));
    }

    @Test
    public void duplicateReadingDoesNotResetData() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 3000);
        assertEquals(Integer.valueOf(20), tracker.onReading(102, 6000));

        assertEquals(Integer.valueOf(17), tracker.onReading(102, 7000));
    }

    @Test
    public void cadenceDecaysToZeroAfterThirtySecondsWithoutSteps() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 3000);
        tracker.onReading(102, 6000);

        assertEquals(Integer.valueOf(0), tracker.cadenceAt(37000));
    }

    @Test
    public void resetClearsCadenceAndRequiresNewWindow() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 3000);
        tracker.onReading(102, 6000);

        tracker.reset();

        assertNull(tracker.cadenceAt(7000));
        assertNull(tracker.onReading(200, 8000));
    }

    @Test
    public void windowMustBeAtLeastFiveSeconds() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 1000);
        assertNull(tracker.onReading(102, 4000));
        assertEquals(Integer.valueOf(30), tracker.onReading(103, 6000));
    }

    @Test
    public void decreasingRawValueResetsTheEpoch() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 3000);
        tracker.onReading(102, 6000);

        assertNull(tracker.onReading(10, 7000));
        assertNull(tracker.cadenceAt(8000));
    }

    @Test
    public void oldSamplesArePrunedFromTheWindow() {
        CadenceTracker tracker = new CadenceTracker();
        tracker.onReading(100, 0);
        tracker.onReading(101, 1000);
        assertEquals(Integer.valueOf(20), tracker.onReading(102, 6000));
        assertEquals(Integer.valueOf(2), tracker.cadenceAt(35000));
    }
}
