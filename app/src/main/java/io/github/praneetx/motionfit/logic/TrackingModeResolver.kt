package io.github.praneetx.motionfit.logic

/**
 * Effective automatic-tracking state shown to the user.
 *
 * There is deliberately no vague "on" state: ACTIVE requires a running
 * foreground service with a visible notification.
 */
enum class TrackingMode {
    OFF,
    ACTIVE,
    APP_OPEN_ONLY,
    UNAVAILABLE
}

/** Result of the most recent attempt to promote the tracking service. */
enum class TrackingStartResult {
    NOT_ATTEMPTED,
    STARTED,
    REJECTED
}

/**
 * Pure resolution of the automatic-tracking state.
 *
 * Sensor availability and activity access are prerequisites. Notifications are
 * required for the always-on foreground service; without them the truthful
 * fallback is APP_OPEN_ONLY. A rejected foreground start also falls back to
 * app-visible counting rather than claiming background tracking.
 */
object TrackingModeResolver {

    @JvmStatic
    fun resolve(
        autoStepsEnabled: Boolean,
        sensorAvailable: Boolean,
        activityPermissionGranted: Boolean,
        notificationsAllowed: Boolean,
        startResult: TrackingStartResult
    ): TrackingMode {
        if (!autoStepsEnabled) {
            return TrackingMode.OFF
        }
        if (!sensorAvailable || !activityPermissionGranted) {
            return TrackingMode.UNAVAILABLE
        }
        if (!notificationsAllowed) {
            return TrackingMode.APP_OPEN_ONLY
        }
        return if (startResult == TrackingStartResult.STARTED) {
            TrackingMode.ACTIVE
        } else {
            TrackingMode.APP_OPEN_ONLY
        }
    }
}
