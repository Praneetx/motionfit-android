package io.github.praneetx.motionfit.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pure contract tests for the effective automatic-tracking state.
 *
 * <p>The resolver never claims ACTIVE unless the foreground service was
 * actually started. A missing sensor or activity permission is UNAVAILABLE;
 * blocked notifications or a rejected foreground start fall back to
 * APP_OPEN_ONLY.</p>
 */
public class TrackingModeResolverTest {

    private static TrackingMode resolve(
            boolean autoEnabled,
            boolean sensorAvailable,
            boolean activityGranted,
            boolean notificationsAllowed,
            TrackingStartResult startResult) {
        return TrackingModeResolver.resolve(
                autoEnabled,
                sensorAvailable,
                activityGranted,
                notificationsAllowed,
                startResult);
    }

    @Test
    public void autoOffIsOffEvenWhenEverythingElseIsAvailable() {
        assertEquals(TrackingMode.OFF,
                resolve(false, true, true, true, TrackingStartResult.STARTED));
    }

    @Test
    public void foregroundServiceStartedWithPermissionsIsActive() {
        assertEquals(TrackingMode.ACTIVE,
                resolve(true, true, true, true, TrackingStartResult.STARTED));
    }

    @Test
    public void blockedNotificationsAreAppOpenOnly() {
        assertEquals(TrackingMode.APP_OPEN_ONLY,
                resolve(true, true, true, false, TrackingStartResult.NOT_ATTEMPTED));
    }

    @Test
    public void rejectedForegroundStartFallsBackToAppOpenOnly() {
        assertEquals(TrackingMode.APP_OPEN_ONLY,
                resolve(true, true, true, true, TrackingStartResult.REJECTED));
    }

    @Test
    public void notYetStartedIsAppOpenOnlyRatherThanAFalseActiveClaim() {
        assertEquals(TrackingMode.APP_OPEN_ONLY,
                resolve(true, true, true, true, TrackingStartResult.NOT_ATTEMPTED));
    }

    @Test
    public void missingStepCounterIsUnavailable() {
        assertEquals(TrackingMode.UNAVAILABLE,
                resolve(true, false, true, true, TrackingStartResult.NOT_ATTEMPTED));
    }

    @Test
    public void deniedActivityRecognitionIsUnavailable() {
        assertEquals(TrackingMode.UNAVAILABLE,
                resolve(true, true, false, true, TrackingStartResult.NOT_ATTEMPTED));
    }

    @Test
    public void unavailableTakesPriorityOverABlockedNotification() {
        assertEquals(TrackingMode.UNAVAILABLE,
                resolve(true, true, false, false, TrackingStartResult.REJECTED));
    }
}
