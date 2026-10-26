package io.github.praneetx.motionfit.services;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import io.github.praneetx.motionfit.logic.TrackingMode;
import io.github.praneetx.motionfit.logic.TrackingModeResolver;
import io.github.praneetx.motionfit.logic.TrackingStartResult;
import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

/**
 * The single owner of automatic daily step tracking.
 *
 * <p>The service registers one {@code TYPE_STEP_COUNTER} listener and applies
 * raw readings through {@link StepsRepository}. It is either a genuine
 * foreground service with a visible persistent notification, or an
 * app-visible-only service that stops as soon as Motionfit leaves the
 * foreground. It never claims background tracking without the notification.</p>
 */
public class ActivityTrackingService extends Service {

    private static final String TAG = "ActivityTrackingService";

    public static final String ACTION_START =
            "io.github.praneetx.motionfit.action.START_TRACKING";
    public static final String ACTION_APP_OPEN =
            "io.github.praneetx.motionfit.action.APP_OPEN_TRACKING";
    public static final String ACTION_APP_BACKGROUND =
            "io.github.praneetx.motionfit.action.APP_BACKGROUND_TRACKING";
    public static final String ACTION_STOP =
            "io.github.praneetx.motionfit.action.STOP_TRACKING";
    public static final String ACTION_DAILY_STEPS_CHANGED =
            "io.github.praneetx.motionfit.action.DAILY_STEPS_CHANGED";
    public static final String ACTION_TRACKING_STATUS_CHANGED =
            "io.github.praneetx.motionfit.action.TRACKING_STATUS_CHANGED";

    public static final String EXTRA_TEST_MODE =
            "io.github.praneetx.motionfit.extra.TEST_MODE";
    public static final String EXTRA_APP_VISIBLE =
            "io.github.praneetx.motionfit.extra.APP_VISIBLE";

    /** Persist at most once per interval; the next reading carries any gap. */
    private static final long PERSIST_INTERVAL_MS = 5_000L;
    private static final long RETRY_INTERVAL_MS = 30_000L;
    private static final long NOTIFICATION_REFRESH_INTERVAL_MS = 60_000L;

    private static final String STATUS_PREFS = "tracking_status";
    private static final String KEY_START_RESULT = "start_result";

    private static volatile ActivityTrackingService activeInstance;
    private static volatile boolean appIsVisible;

    private final LocalBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scheduledPersist = this::persistPendingReading;

    private SettingsRepository settingsRepository;
    private StepsRepository stepsRepository;
    private StepCounterSource stepCounterSource;

    private TrackingStartResult startResult = TrackingStartResult.NOT_ATTEMPTED;
    private boolean testMode;
    private boolean appVisible;
    private boolean sensorRegistered;
    private boolean foregroundStarted;

    private Long latestRaw;
    private Long pendingRaw;
    private String pendingDateIso;
    private String lastAppliedDate;
    private Long lastAppliedCheckpoint;
    private long lastPersistElapsedMs;
    private long lastNotificationRefreshElapsedMs;

    /** Binder used by instrumentation tests and in-process callers. */
    public final class LocalBinder extends Binder {
        public ActivityTrackingService getService() {
            return ActivityTrackingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        settingsRepository = new SettingsRepository(this);
        stepsRepository = new StepsRepository(this);
        stepCounterSource = new StepCounterSource(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null) {
            testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false);
        }
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }

        if (ACTION_APP_BACKGROUND.equals(action)) {
            handleAppBackground();
            return START_NOT_STICKY;
        }

        if (ACTION_APP_OPEN.equals(action)) {
            // Use the live application signal instead of forcing true: a delayed
            // fallback must not start a sensor after the last screen has stopped.
            appVisible = appIsVisible;
            return ensureTracking(false);
        }

        if (intent != null) {
            // The intent records the request-time visibility, but a delayed
            // service start must never override the live application signal.
            appVisible = intent.getBooleanExtra(EXTRA_APP_VISIBLE, false)
                    && appIsVisible;
        }
        return ensureTracking(true);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (currentStatus() != TrackingMode.ACTIVE) {
            stopTracking();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pendingRaw != null) {
            persistPendingReading();
        }
        handler.removeCallbacksAndMessages(null);
        stopSensor();
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        if (activeInstance == this) {
            activeInstance = null;
        }
        sendStatusBroadcast(this);
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Tracking lifecycle
    // ------------------------------------------------------------------

    private int ensureTracking(boolean allowForeground) {
        if (testMode) {
            return START_NOT_STICKY;
        }
        AppSettings settings = settingsRepository.getSettings();
        if (!settings.isAutoStepsEnabled()) {
            stopTracking();
            return START_NOT_STICKY;
        }

        if (!stepCounterSource.isAvailable() || !hasActivityPermission(this)) {
            sendStatusBroadcast(this);
            stopTracking();
            return START_NOT_STICKY;
        }

        boolean notificationsAllowed =
                NotificationHelper.areTrackingNotificationsEnabled(this);

        if (allowForeground && notificationsAllowed) {
            if (!foregroundStarted) {
                if (startForegroundSafely()) {
                    foregroundStarted = true;
                    setStartResult(TrackingStartResult.STARTED);
                } else {
                    return fallBackToAppVisible();
                }
            } else {
                setStartResult(TrackingStartResult.STARTED);
            }
        } else {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                foregroundStarted = false;
            }
            TrackingStartResult stored = readStoredStartResult(this);
            setStartResult(allowForeground || stored == TrackingStartResult.REJECTED
                    ? stored
                    : TrackingStartResult.NOT_ATTEMPTED);
            if (startResult == TrackingStartResult.STARTED) {
                setStartResult(TrackingStartResult.NOT_ATTEMPTED);
            }
        }

        boolean mayRunNow = foregroundStarted || appVisible;
        if (!mayRunNow) {
            stopTracking();
            return START_NOT_STICKY;
        }

        if (!startSensor()) {
            sendStatusBroadcast(this);
            stopTracking();
            return START_NOT_STICKY;
        }

        sendStatusBroadcast(this);
        return foregroundStarted ? START_STICKY : START_NOT_STICKY;
    }

    private int fallBackToAppVisible() {
        Log.w(TAG, "Foreground tracking start was rejected; using app-visible mode");
        foregroundStarted = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        setStartResult(TrackingStartResult.REJECTED);
        sendStatusBroadcast(this);

        boolean restartWhenVisible = appVisible;
        stopSensor();
        stopSelf();
        if (restartWhenVisible) {
            final Context app = getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!appIsVisible) {
                    return;
                }
                Intent fallback = new Intent(app, ActivityTrackingService.class);
                fallback.setAction(ACTION_APP_OPEN);
                fallback.putExtra(EXTRA_APP_VISIBLE, true);
                try {
                    app.startService(fallback);
                } catch (RuntimeException e) {
                    Log.w(TAG, "App-visible fallback could not start");
                }
            }, 400L);
        }
        return START_NOT_STICKY;
    }

    private void handleAppBackground() {
        appVisible = false;
        if (currentStatus() == TrackingMode.ACTIVE) {
            sendStatusBroadcast(this);
            return;
        }
        stopTracking();
    }

    private void stopTracking() {
        handler.removeCallbacksAndMessages(null);
        if (pendingRaw != null) {
            persistPendingReading();
        }
        handler.removeCallbacksAndMessages(null);
        stopSensor();
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
        sendStatusBroadcast(this);
    }

    /**
     * Stops immediately without flushing a pending sensor reading. Used right
     * before destructive data reset so a late service callback cannot recreate a
     * daily row after the reset transaction commits.
     */
    private void discardPendingAndStop() {
        handler.removeCallbacksAndMessages(null);
        pendingRaw = null;
        pendingDateIso = null;
        stopSensor();
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
        sendStatusBroadcast(this);
    }

    private boolean startForegroundSafely() {
        Notification notification = NotificationHelper.buildTrackingNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NotificationHelper.TRACKING_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(NotificationHelper.TRACKING_NOTIFICATION_ID, notification);
            }
            lastNotificationRefreshElapsedMs = SystemClock.elapsedRealtime();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Foreground tracking start was rejected");
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (RuntimeException ignored) {
                // The foreground start never succeeded.
            }
            return false;
        }
    }

    private boolean startSensor() {
        if (sensorRegistered) {
            return true;
        }
        sensorRegistered = stepCounterSource.start(new StepCounterSource.Listener() {
            @Override
            public void onRawReading(long raw) {
                handleRawReading(raw);
            }

            @Override
            public void onUnavailable() {
                handleSensorUnavailable();
            }
        });
        if (!sensorRegistered) {
            Log.w(TAG, "Step-counter sensor could not be registered");
        }
        return sensorRegistered;
    }

    private void stopSensor() {
        if (stepCounterSource != null) {
            stepCounterSource.stop();
        }
        sensorRegistered = false;
    }

    private void handleSensorUnavailable() {
        Log.w(TAG, "Step-counter sensor became unavailable");
        stopTracking();
    }

    // ------------------------------------------------------------------
    // Reading persistence
    // ------------------------------------------------------------------

    private void handleRawReading(long raw) {
        if (!sensorRegistered) {
            return;
        }
        if (!ensureForegroundStillVisible()) {
            return;
        }
        latestRaw = raw;
        pendingRaw = raw;
        pendingDateIso = DateUtils.todayIso();

        long now = SystemClock.elapsedRealtime();
        if (lastPersistElapsedMs == 0L
                || now - lastPersistElapsedMs >= PERSIST_INTERVAL_MS) {
            persistPendingReading();
        } else {
            handler.removeCallbacks(scheduledPersist);
            handler.postDelayed(scheduledPersist,
                    PERSIST_INTERVAL_MS - (now - lastPersistElapsedMs));
        }
    }

    private void persistPendingReading() {
        handler.removeCallbacks(scheduledPersist);
        Long raw = pendingRaw;
        if (raw == null) {
            return;
        }
        String dateIso = pendingDateIso == null ? DateUtils.todayIso() : pendingDateIso;
        OperationResult result = applyRepositoryReading(dateIso, raw);

        long now = SystemClock.elapsedRealtime();
        if (!result.isSuccess()) {
            Log.w(TAG, "Step reading persistence failed; will retry");
            lastPersistElapsedMs = now;
            handler.postDelayed(scheduledPersist, RETRY_INTERVAL_MS);
            return;
        }

        lastAppliedDate = dateIso;
        lastAppliedCheckpoint = raw;
        pendingRaw = null;
        pendingDateIso = null;
        lastPersistElapsedMs = now;

        maybeRefreshNotification(now);
        sendStepsChanged(this);
    }

    private OperationResult applyRepositoryReading(String dateIso, long raw) {
        if (lastAppliedDate != null
                && lastAppliedCheckpoint != null
                && dateIso.compareTo(lastAppliedDate) > 0) {
            return stepsRepository.applyContinuousSensorReading(
                    dateIso, raw, lastAppliedDate, lastAppliedCheckpoint);
        }
        return stepsRepository.applySensorReading(dateIso, raw);
    }

    /**
     * Applies an explicit user total. When this service has a live raw value the
     * correction becomes the new checkpoint, so the next sensor reading is not
     * undone and is not double-counted.
     */
    public OperationResult calibrate(String dateIso, int total) {
        Long rawNow = null;
        boolean isToday = DateUtils.todayIso().equals(dateIso);
        if (isToday && sensorRegistered && latestRaw != null) {
            rawNow = latestRaw;
            handler.removeCallbacks(scheduledPersist);
            pendingRaw = null;
            pendingDateIso = null;
        }

        OperationResult result = stepsRepository.calibrate(dateIso, total, rawNow);
        if (result.isSuccess()) {
            if (isToday) {
                lastAppliedDate = dateIso;
                lastAppliedCheckpoint = rawNow;
            }
            sendStepsChanged(this);
        }
        return result;
    }

    /**
     * Test-only persistence route used by the raw binder test. Sensor injection
     * itself remains a manual device test.
     */
    public OperationResult applyReadingForTest(String dateIso, long raw) {
        if (!testMode) {
            return OperationResult.error("Test persistence is only available in test mode.");
        }
        OperationResult result = stepsRepository.applySensorReading(dateIso, raw);
        if (result.isSuccess()) {
            lastAppliedDate = dateIso;
            lastAppliedCheckpoint = raw;
            sendStepsChanged(this);
        }
        return result;
    }

    private void maybeRefreshNotification(long now) {
        if (!foregroundStarted
                || now - lastNotificationRefreshElapsedMs < NOTIFICATION_REFRESH_INTERVAL_MS) {
            return;
        }
        lastNotificationRefreshElapsedMs = now;

        if (!ensureForegroundStillVisible()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            NotificationManagerCompat.from(this).notify(
                    NotificationHelper.TRACKING_NOTIFICATION_ID,
                    NotificationHelper.buildTrackingNotification(this));
        } catch (SecurityException e) {
            Log.w(TAG, "Tracking notification could not be refreshed");
        } catch (RuntimeException e) {
            Log.w(TAG, "Tracking notification could not be refreshed");
        }
    }

    /**
     * Keeps the invariant that an always-on service only exists while its
     * notification can actually be shown. Called on every sensor delivery so a
     * revoked permission cannot leave a hidden tracker running indefinitely.
     */
    private boolean ensureForegroundStillVisible() {
        if (!foregroundStarted
                || NotificationHelper.areTrackingNotificationsEnabled(this)) {
            return true;
        }
        Log.w(TAG, "Tracking notification was blocked; dropping foreground mode");
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        setStartResult(TrackingStartResult.NOT_ATTEMPTED);
        sendStatusBroadcast(this);
        if (!appVisible) {
            stopTracking();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    /** Current in-process status, including whether the foreground start worked. */
    public TrackingMode getStatus() {
        TrackingStartResult effective = startResult;
        if (!foregroundStarted && effective == TrackingStartResult.STARTED) {
            effective = TrackingStartResult.NOT_ATTEMPTED;
        }
        return TrackingModeResolver.resolve(
                settingsRepository.getSettings().isAutoStepsEnabled(),
                stepCounterSource.isAvailable(),
                hasActivityPermission(this),
                NotificationHelper.areTrackingNotificationsEnabled(this),
                effective);
    }

    private TrackingMode currentStatus() {
        return getStatus();
    }

    private void setStartResult(TrackingStartResult result) {
        startResult = result;
        storeStartResult(this, result);
    }

    public static TrackingMode getEffectiveStatus(Context context) {
        AppSettings settings = new SettingsRepository(context).getSettings();
        TrackingStartResult startResult = readStoredStartResult(context);
        ActivityTrackingService instance = activeInstance;
        if (instance != null) {
            startResult = instance.startResult;
            if (!instance.foregroundStarted
                    && startResult == TrackingStartResult.STARTED) {
                startResult = TrackingStartResult.NOT_ATTEMPTED;
            }
        } else if (startResult == TrackingStartResult.STARTED) {
            startResult = TrackingStartResult.NOT_ATTEMPTED;
        }

        return TrackingModeResolver.resolve(
                settings.isAutoStepsEnabled(),
                hasStepCounter(context),
                hasActivityPermission(context),
                NotificationHelper.areTrackingNotificationsEnabled(context),
                startResult);
    }

    public static TrackingStartResult getLastStartResult(Context context) {
        ActivityTrackingService instance = activeInstance;
        if (instance != null) {
            return instance.startResult;
        }
        TrackingStartResult stored = readStoredStartResult(context);
        return stored == TrackingStartResult.STARTED
                ? TrackingStartResult.NOT_ATTEMPTED
                : stored;
    }

    // ------------------------------------------------------------------
    // Static app lifecycle helpers
    // ------------------------------------------------------------------

    /** Called when any Motionfit activity becomes visible. */
    public static void onAppForeground(Context context) {
        appIsVisible = true;
        requestStart(context, true, true);
    }

    /** Called when the last Motionfit activity is no longer visible. */
    public static void onAppBackground(Context context) {
        appIsVisible = false;
        ActivityTrackingService instance = activeInstance;
        if (instance == null) {
            sendStatusBroadcast(context);
            return;
        }
        instance.handleAppBackground();
    }

    /** Re-evaluates permissions and starts/upgrades the service when possible. */
    public static void refresh(Context context) {
        requestStart(context, true, true);
    }

    /**
     * Starts tracking or reports why it cannot start. A rejected foreground
     * promotion can fall back to a plain app-visible service when the app is on
     * screen; a boot attempt never does.
     */
    public static void requestStart(
            Context context, boolean appVisible, boolean allowAppVisibleFallback) {
        Context app = context.getApplicationContext();
        if (appVisible) {
            appIsVisible = true;
        }

        AppSettings settings = new SettingsRepository(app).getSettings();
        if (!settings.isAutoStepsEnabled()) {
            stop(app);
            sendStatusBroadcast(app);
            return;
        }
        if (!hasStepCounter(app) || !hasActivityPermission(app)) {
            stop(app);
            sendStatusBroadcast(app);
            return;
        }

        boolean notificationsAllowed =
                NotificationHelper.areTrackingNotificationsEnabled(app);
        Intent intent = new Intent(app, ActivityTrackingService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_APP_VISIBLE, appVisible);

        try {
            if (notificationsAllowed) {
                ContextCompat.startForegroundService(app, intent);
            } else {
                app.startService(intent);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Tracking service could not start");
            if (notificationsAllowed) {
                storeStartResult(app, TrackingStartResult.REJECTED);
                sendStatusBroadcast(app);
                if (allowAppVisibleFallback && appVisible) {
                    startAppVisibleFallback(app);
                }
            }
        }
    }

    /** Best-effort start from the boot receiver; never crashes on OS rejection. */
    public static void startFromBoot(Context context) {
        Context app = context.getApplicationContext();
        appIsVisible = false;
        AppSettings settings = new SettingsRepository(app).getSettings();
        if (!settings.isAutoStepsEnabled()
                || !hasStepCounter(app)
                || !hasActivityPermission(app)
                || !NotificationHelper.areTrackingNotificationsEnabled(app)) {
            return;
        }

        Intent intent = new Intent(app, ActivityTrackingService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_APP_VISIBLE, false);
        try {
            ContextCompat.startForegroundService(app, intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Automatic tracking could not start after boot");
            storeStartResult(app, TrackingStartResult.REJECTED);
            sendStatusBroadcast(app);
        }
    }

    private static void startAppVisibleFallback(Context app) {
        Intent fallback = new Intent(app, ActivityTrackingService.class);
        fallback.setAction(ACTION_APP_OPEN);
        fallback.putExtra(EXTRA_APP_VISIBLE, true);
        try {
            app.startService(fallback);
        } catch (RuntimeException e) {
            Log.w(TAG, "App-visible tracking fallback could not start");
        }
    }

    /**
     * Routes a manual total through the live service when one exists, so its
     * in-memory checkpoint is updated with the same raw value. Without a live
     * service, automatic tracking is not writing, so a checkpoint-less total is
     * the safe calibration.
     */
    public static OperationResult calibrate(Context context, String dateIso, int total) {
        ActivityTrackingService instance = activeInstance;
        if (instance != null) {
            return instance.calibrate(dateIso, total);
        }
        return new StepsRepository(context).calibrate(dateIso, total, null);
    }

    public static void stop(Context context) {
        context.getApplicationContext().stopService(
                new Intent(context.getApplicationContext(), ActivityTrackingService.class));
    }

    /** Stops the tracker without writing a final pending reading before a reset. */
    public static void stopForDataReset(Context context) {
        ActivityTrackingService instance = activeInstance;
        if (instance != null) {
            instance.discardPendingAndStop();
            return;
        }
        stop(context);
    }

    public static boolean hasActivityPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasStepCounter(Context context) {
        return new StepCounterSource(context).isAvailable();
    }

    private static void sendStepsChanged(Context context) {
        Intent changed = new Intent(ACTION_DAILY_STEPS_CHANGED);
        changed.setPackage(context.getPackageName());
        context.sendBroadcast(changed);
    }

    private static void sendStatusBroadcast(Context context) {
        Intent changed = new Intent(ACTION_TRACKING_STATUS_CHANGED);
        changed.setPackage(context.getPackageName());
        context.sendBroadcast(changed);
    }

    private static TrackingStartResult readStoredStartResult(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(
                KEY_START_RESULT, TrackingStartResult.NOT_ATTEMPTED.name());
        try {
            return TrackingStartResult.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return TrackingStartResult.NOT_ATTEMPTED;
        }
    }

    private static void storeStartResult(Context context, TrackingStartResult result) {
        context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_START_RESULT, result.name())
                .apply();
    }
}
