package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** JVM tests for exact Running duration conversion. */
public class WorkoutDurationTest {

    @Test
    public void secondsUsesOneSecondMinimum() {
        assertEquals(1, WorkoutDuration.seconds(0L));
        assertEquals(1, WorkoutDuration.seconds(-100L));
        assertEquals(1, WorkoutDuration.seconds(999L));
        assertEquals(1, WorkoutDuration.seconds(1000L));
    }

    @Test
    public void secondsRoundsToNearestSecond() {
        assertEquals(1, WorkoutDuration.seconds(1499L));
        assertEquals(2, WorkoutDuration.seconds(1500L));
        assertEquals(60, WorkoutDuration.seconds(60400L));
        assertEquals(61, WorkoutDuration.seconds(60500L));
    }

    @Test
    public void minutesRoundUpWithOneMinuteMinimum() {
        assertEquals(1, WorkoutDuration.minutes(0));
        assertEquals(1, WorkoutDuration.minutes(1));
        assertEquals(1, WorkoutDuration.minutes(60));
        assertEquals(2, WorkoutDuration.minutes(61));
        assertEquals(2, WorkoutDuration.minutes(120));
        assertEquals(3, WorkoutDuration.minutes(121));
    }
}
