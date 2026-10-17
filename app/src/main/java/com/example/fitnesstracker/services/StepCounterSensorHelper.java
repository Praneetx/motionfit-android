package com.example.fitnesstracker.services;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * EXPERIMENTAL helper around the device's step-counter sensor.
 *
 * <p>Important limitations (see docs/limitations.md):</p>
 * <ul>
 *   <li>{@code TYPE_STEP_COUNTER} reports the number of steps since the last
 *       device reboot, not a per-day count.</li>
 *   <li>It only delivers values while this listener is registered, i.e. while the
 *       Steps screen is open. No background tracking is implemented.</li>
 *   <li>Many devices do not have the sensor at all; callers must handle that.</li>
 *   <li>On Android 10+ the ACTIVITY_RECOGNITION runtime permission is required.</li>
 * </ul>
 */
public class StepCounterSensorHelper implements SensorEventListener {

    private static final String TAG = "StepCounterSensorHelper";

    /** Callbacks delivered to the screen that owns this helper. */
    public interface Listener {
        void onSessionSteps(int sessionSteps);

        void onSensorUnavailable(String reason);
    }

    private final SensorManager sensorManager;
    private final Sensor stepCounterSensor;

    private Listener listener;
    private boolean registered;
    private int baselineSteps = -1;

    public StepCounterSensorHelper(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        stepCounterSensor = (sensorManager == null)
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    /** True when the device actually has a step-counter sensor. */
    public boolean isAvailable() {
        return stepCounterSensor != null;
    }

    public void start(Listener listener) {
        this.listener = listener;
        if (sensorManager == null || stepCounterSensor == null) {
            if (listener != null) {
                listener.onSensorUnavailable("missing");
            }
            return;
        }
        registered = sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (!registered && listener != null) {
            listener.onSensorUnavailable("register-failed");
        }
        Log.i(TAG, "Sensor listener registered=" + registered);
    }

    public void stop() {
        if (sensorManager != null && registered) {
            sensorManager.unregisterListener(this);
        }
        registered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }
        int totalSinceBoot = (int) event.values[0];
        if (baselineSteps < 0) {
            // First reading becomes the baseline so we report this session's steps.
            baselineSteps = totalSinceBoot;
        }
        int sessionSteps = Math.max(0, totalSinceBoot - baselineSteps);
        if (listener != null) {
            listener.onSessionSteps(sessionSteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for a step counter.
    }
}
