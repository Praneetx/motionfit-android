package io.github.praneetx.motionfit.services;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * The single owner of the {@link Sensor#TYPE_STEP_COUNTER} registration.
 *
 * <p>The source reports raw cumulative values since boot. It deliberately keeps
 * no per-session baseline: the daily checkpoint lives in the database so it
 * survives process death and every consumer sees the same epoch.</p>
 */
public class StepCounterSource implements SensorEventListener {

    private static final String TAG = "StepCounterSource";

    /** Callbacks are delivered on the sensor manager's main-thread looper. */
    public interface Listener {
        void onRawReading(long raw);

        void onUnavailable();
    }

    private final SensorManager sensorManager;
    private final Sensor stepCounter;

    private Listener listener;
    private boolean registered;

    public StepCounterSource(Context context) {
        SensorManager manager =
                (SensorManager) context.getApplicationContext()
                        .getSystemService(Context.SENSOR_SERVICE);
        sensorManager = manager;
        stepCounter = manager == null
                ? null
                : manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    /** True when the device advertises a hardware step counter. */
    public boolean isAvailable() {
        return stepCounter != null;
    }

    public boolean isRunning() {
        return registered;
    }

    /**
     * Registers the only step-counter listener. Returns false when the sensor or
     * registration is unavailable; callers must not fall back to another sensor.
     */
    public boolean start(Listener newListener) {
        if (registered) {
            listener = newListener;
            return true;
        }
        listener = newListener;
        if (sensorManager == null || stepCounter == null) {
            notifyUnavailable();
            return false;
        }
        registered = sensorManager.registerListener(
                this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        if (!registered) {
            Log.w(TAG, "Step-counter registration failed");
            notifyUnavailable();
        }
        return registered;
    }

    public void stop() {
        if (sensorManager != null && registered) {
            sensorManager.unregisterListener(this);
        }
        registered = false;
        listener = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!registered || event == null || event.sensor == null
                || event.sensor.getType() != Sensor.TYPE_STEP_COUNTER
                || event.values.length == 0) {
            return;
        }
        long raw = Math.max(0L, Math.round((double) event.values[0]));
        if (listener != null) {
            listener.onRawReading(raw);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // A step counter has no useful accuracy adjustment.
    }

    private void notifyUnavailable() {
        if (listener != null) {
            listener.onUnavailable();
        }
    }
}
