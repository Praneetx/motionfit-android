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
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.praneetx.motionfit.logic.CadenceTracker;
import io.github.praneetx.motionfit.logic.RunningClockState;
import io.github.praneetx.motionfit.logic.RunningCommand;
import io.github.praneetx.motionfit.logic.RunningCommandError;
import io.github.praneetx.motionfit.logic.RunningCommandValidation;
import io.github.praneetx.motionfit.logic.RunningSessionClock;
import io.github.praneetx.motionfit.logic.RunningSessionController;
import io.github.praneetx.motionfit.logic.RunningSessionState;
import io.github.praneetx.motionfit.logic.RunningSnapshot;
import io.github.praneetx.motionfit.logic.RunningSnapshotStatus;
import io.github.praneetx.motionfit.logic.RunningStatus;
import io.github.praneetx.motionfit.logic.TrackingMode;
import io.github.praneetx.motionfit.logic.TrackingModeResolver;
import io.github.praneetx.motionfit.logic.TrackingStartResult;
import io.github.praneetx.motionfit.models.AppSettings;
import io.github.praneetx.motionfit.models.RunningSession;
import io.github.praneetx.motionfit.repositories.RunningSessionRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.utils.DateUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

/**
 * The single owner of automatic daily step tracking and Running timing.
 *
 * <p>There is one {@code TYPE_STEP_COUNTER} registration. A raw reading can be
 * applied to the daily checkpoint and to the Running session, but the Running
 * workout only stores the delta from its own start; it is never added to the
 * daily total. The service is either a genuine foreground service with a
 * visible notification or an app-visible-only service that stops as soon as
 * Motionfit leaves the foreground. It never claims background tracking without
 * the notification.</p>
 *
 * <p>A Running session can continue timer-only when the sensor or activity
 * permission is unavailable. The timer and all transitions are persisted in
 * the singleton {@code running_sessions} row so rotation, process death and
 * app-visible falls back do not duplicate a workout.</p>
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
    public static final String ACTION_PAUSE_RUN =
            "io.github.praneetx.motionfit.action.PAUSE_RUN";
    public static final String ACTION_RESUME_RUN =
            "io.github.praneetx.motionfit.action.RESUME_RUN";
    public static final String ACTION_RUN_KEEP_ALIVE =
            "io.github.praneetx.motionfit.action.RUN_KEEP_ALIVE";
    public static final String ACTION_DAILY_STEPS_CHANGED =
            "io.github.praneetx.motionfit.action.DAILY_STEPS_CHANGED";
    public static final String ACTION_TRACKING_STATUS_CHANGED =
            "io.github.praneetx.motionfit.action.TRACKING_STATUS_CHANGED";
    public static final String ACTION_RUNNING_STATUS_CHANGED =
            "io.github.praneetx.motionfit.action.RUNNING_STATUS_CHANGED";

    public static final String EXTRA_TEST_MODE =
            "io.github.praneetx.motionfit.extra.TEST_MODE";
    public static final String EXTRA_APP_VISIBLE =
            "io.github.praneetx.motionfit.extra.APP_VISIBLE";

    /** Persist at most once per interval; the next reading carries any gap. */
    private static final long PERSIST_INTERVAL_MS = 5_000L;
    private static final long RETRY_INTERVAL_MS = 30_000L;
    private static final long DAILY_NOTIFICATION_REFRESH_INTERVAL_MS = 60_000L;
    private static final long RUN_NOTIFICATION_REFRESH_INTERVAL_MS = 10_000L;

    private static final String STATUS_PREFS = "tracking_status";
    private static final String KEY_START_RESULT = "start_result";

    private static volatile ActivityTrackingService activeInstance;
    private static volatile boolean appIsVisible;

    private final LocalBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scheduledPersist = this::persistPendingState;
    private final CopyOnWriteArrayList<RunningListener> runningListeners =
            new CopyOnWriteArrayList<>();
    private final CadenceTracker runningCadence = new CadenceTracker();

    private SettingsRepository settingsRepository;
    private StepsRepository stepsRepository;
    private RunningSessionRepository runningSessionRepository;
    private StepCounterSource stepCounterSource;

    private TrackingStartResult startResult = TrackingStartResult.NOT_ATTEMPTED;
    private boolean testMode;
    private boolean appVisible;
    private boolean sensorRegistered;
    private boolean sensorRegistrationFailed;
    private boolean foregroundStarted;
    private boolean suppressPersistence;

    private Long latestRaw;
    private Long pendingRaw;
    private String pendingDateIso;
    private String lastAppliedDate;
    private Long lastAppliedCheckpoint;
    private long lastPersistElapsedMs;
    private long lastNotificationRefreshElapsedMs;

    private boolean runningSessionLoaded;
    private RunningSession runningSession;
    private int runningStepCount;
    private Long runningStartRaw;
    private Long runningLastRaw;
    private boolean runningEpochLost;
    private boolean runPersistPending;
    private Long testClockMs;
    private long lastFinishedDurationMs = -1L;
    private Integer lastFinishedStepCount;

    /** Binder used by instrumentation tests and in-process callers. */
    public final class LocalBinder extends Binder {
        public ActivityTrackingService getService() {
            return ActivityTrackingService.this;
        }
    }

    /** Callback for the visible Running screen; always delivered on the main thread. */
    public interface RunningListener {
        void onRunningUpdate(RunningSnapshot snapshot);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        settingsRepository = new SettingsRepository(this);
        stepsRepository = new StepsRepository(this);
        runningSessionRepository = new RunningSessionRepository(this);
        stepCounterSource = new StepCounterSource(this);
        loadRunningSession();
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
            // Settings may have disabled daily counting; keep an active run alive.
            appVisible = appIsVisible;
            return syncTracking(true);
        }

        if (ACTION_APP_BACKGROUND.equals(action)) {
            handleAppBackground();
            return START_NOT_STICKY;
        }

        if (ACTION_APP_OPEN.equals(action)) {
            // Use the live application signal instead of forcing true: a delayed
            // fallback must not start a sensor after the last screen has stopped.
            appVisible = appIsVisible;
            return syncTracking(false);
        }

        if (ACTION_PAUSE_RUN.equals(action)) {
            OperationResult result = pauseRun();
            if (!result.isSuccess()) {
                Log.w(TAG, "Notification pause was rejected: " + result.getMessage());
            }
            return foregroundStarted ? START_STICKY : START_NOT_STICKY;
        }

        if (ACTION_RESUME_RUN.equals(action)) {
            OperationResult result = resumeRun();
            if (!result.isSuccess()) {
                Log.w(TAG, "Notification resume was rejected: " + result.getMessage());
            }
            return foregroundStarted ? START_STICKY : START_NOT_STICKY;
        }

        if (ACTION_RUN_KEEP_ALIVE.equals(action)) {
            appVisible = appIsVisible;
            return syncTracking(true);
        }

        if (intent != null) {
            // The intent records the request-time visibility, but a delayed
            // service start must never override the live application signal.
            appVisible = intent.getBooleanExtra(EXTRA_APP_VISIBLE, false)
                    && appIsVisible;
        }
        return syncTracking(true);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!foregroundStarted) {
            stopTracking();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!suppressPersistence) {
            persistPendingState();
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
        notifyRunningChanged();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Service mode: daily tracking and Running share one sensor/service
    // ------------------------------------------------------------------

    private int syncTracking(boolean allowForeground) {
        if (suppressPersistence) {
            return START_NOT_STICKY;
        }
        if (testMode) {
            notifyRunningChanged();
            return START_NOT_STICKY;
        }

        ensureRunningSessionLoaded();
        AppSettings settings = settingsRepository.getSettings();
        boolean autoEnabled = settings.isAutoStepsEnabled();
        boolean sensorPresent = stepCounterSource.isAvailable();
        boolean activityGranted = hasActivityPermission(this);
        boolean notificationsAllowed =
                NotificationHelper.areTrackingNotificationsEnabled(this);
        boolean dailyEligible = autoEnabled && sensorPresent && activityGranted;
        boolean runActive = runningSession != null && !runningSession.isInterrupted();
        // The approved timer-only fallback is explicitly app-visible when
        // activity access is denied. Do not promote it to a background service
        // just because it needs no sensor: the screen must not claim background
        // timing that the product treats as unavailable.
        boolean foregroundWanted = dailyEligible || (runActive && activityGranted);

        if (!dailyEligible && !runActive) {
            if (autoEnabled) {
                setStartResult(TrackingStartResult.NOT_ATTEMPTED);
            }
            stopTracking();
            return START_NOT_STICKY;
        }

        if (allowForeground && notificationsAllowed && foregroundWanted) {
            if (!foregroundStarted) {
                if (startForegroundSafely()) {
                    foregroundStarted = true;
                    if (dailyEligible) {
                        setStartResult(TrackingStartResult.STARTED);
                    }
                } else {
                    foregroundStarted = false;
                    if (dailyEligible) {
                        setStartResult(TrackingStartResult.REJECTED);
                    }
                    return fallBackToAppVisible();
                }
            } else {
                if (dailyEligible) {
                    setStartResult(TrackingStartResult.STARTED);
                }
                refreshNotification(true);
            }
        } else {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                foregroundStarted = false;
            }
            if (dailyEligible) {
                TrackingStartResult stored = readStoredStartResult(this);
                setStartResult(allowForeground || stored == TrackingStartResult.REJECTED
                        ? stored
                        : TrackingStartResult.NOT_ATTEMPTED);
                if (startResult == TrackingStartResult.STARTED) {
                    setStartResult(TrackingStartResult.NOT_ATTEMPTED);
                }
            }
        }

        boolean mayRunNow = foregroundStarted || appVisible;
        if (!mayRunNow) {
            stopTracking();
            return START_NOT_STICKY;
        }

        boolean needSensor = dailyEligible
                || (runActive && sensorPresent && activityGranted);
        if (needSensor) {
            if (!startSensor() && !runActive) {
                setStartResult(TrackingStartResult.REJECTED);
                stopTracking();
                return START_NOT_STICKY;
            }
        } else {
            stopSensor();
        }

        if (foregroundStarted && dailyEligible) {
            setStartResult(TrackingStartResult.STARTED);
        }
        if (foregroundStarted) {
            refreshNotification(true);
        }
        sendStatusBroadcast(this);
        notifyRunningChanged();
        return foregroundStarted ? START_STICKY : START_NOT_STICKY;
    }

    private int fallBackToAppVisible() {
        Log.w(TAG, "Foreground tracking start was rejected; using app-visible mode");
        handler.removeCallbacksAndMessages(null);
        persistPendingState();
        handler.removeCallbacksAndMessages(null);
        foregroundStarted = false;
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
            // The foreground start never succeeded.
        }
        setStartResult(TrackingStartResult.REJECTED);
        sendStatusBroadcast(this);
        notifyRunningChanged();

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
        if (foregroundStarted) {
            sendStatusBroadcast(this);
            notifyRunningChanged();
            return;
        }
        // Persist the row, then stop. A Running session is never discarded here;
        // it is recovered when the Run screen returns.
        stopTracking();
    }

    private void stopTracking() {
        handler.removeCallbacksAndMessages(null);
        if (!suppressPersistence) {
            persistPendingState();
        }
        handler.removeCallbacksAndMessages(null);
        stopSensor();
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
        sendStatusBroadcast(this);
        notifyRunningChanged();
    }

    /**
     * Stops immediately without flushing a pending sensor reading. Used right
     * before destructive data reset so a late service callback cannot recreate a
     * daily row or running session after the reset transaction commits.
     */
    private void discardPendingAndStop() {
        suppressPersistence = true;
        handler.removeCallbacksAndMessages(null);
        pendingRaw = null;
        pendingDateIso = null;
        runPersistPending = false;
        runningSession = null;
        runningSessionLoaded = true;
        runningStartRaw = null;
        runningLastRaw = null;
        runningStepCount = 0;
        runningEpochLost = false;
        runningCadence.reset();
        stopSensor();
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
        sendStatusBroadcast(this);
        notifyRunningChanged();
    }

    private boolean startForegroundSafely() {
        Notification notification = buildCurrentNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        NotificationHelper.TRACKING_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                startForeground(NotificationHelper.TRACKING_NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
            lastNotificationRefreshElapsedMs = nowElapsed();
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Foreground tracking start was rejected");
            foregroundStarted = false;
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (RuntimeException ignored) {
                // The foreground start never succeeded.
            }
            return false;
        }
    }

    private Notification buildCurrentNotification() {
        if (isRunningNotificationActive()) {
            RunningSnapshot snapshot = getRunningSnapshot();
            return NotificationHelper.buildRunningNotification(
                    this,
                    snapshot.getDurationMs(),
                    snapshot.getStepCount(),
                    snapshot.getStatus() != RunningSnapshotStatus.RUNNING);
        }
        return NotificationHelper.buildTrackingNotification(this);
    }

    private boolean isRunningNotificationActive() {
        return runningSession != null && !runningSession.isInterrupted();
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
        sensorRegistrationFailed = !sensorRegistered;
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
        sensorRegistered = false;
        sensorRegistrationFailed = true;
        if (runningSession != null && !runningSession.isInterrupted()) {
            // A Running timer is still useful without steps; keep it alive in
            // the legal foreground/app-visible mode and show timer-only state.
            notifyRunningChanged();
            sendStatusBroadcast(this);
            return;
        }
        stopTracking();
    }

    // ------------------------------------------------------------------
    // Raw readings: one sensor, daily checkpoint and Running delta
    // ------------------------------------------------------------------

    private void handleRawReading(long raw) {
        if (!sensorRegistered) {
            return;
        }
        if (!ensureForegroundStillVisible()) {
            return;
        }
        latestRaw = raw;
        long now = nowElapsed();

        AppSettings settings = settingsRepository.getSettings();
        if (settings.isAutoStepsEnabled() && hasActivityPermission(this)) {
            pendingRaw = raw;
            pendingDateIso = DateUtils.todayIso();
        }

        handleRunningRaw(raw, now);
        schedulePersistence(now);
    }

    private void handleRunningRaw(long raw, long now) {
        ensureRunningSessionLoaded();
        if (runningSession == null || runningSession.isInterrupted()) {
            return;
        }

        if (runningStartRaw == null) {
            OperationResult baseline = runningSessionRepository.initializeStepBaseline(raw);
            if (!baseline.isSuccess()) {
                Log.w(TAG, "Running step baseline could not be saved");
                return;
            }
            runningStartRaw = raw;
            runningLastRaw = raw;
            runningStepCount = 0;
            runningEpochLost = false;
            runningCadence.reset();
            if (runningSession.getStatus() == RunningStatus.ACTIVE) {
                runningCadence.onReading(raw, now);
            }
            notifyRunningChanged();
            return;
        }

        if (runningEpochLost) {
            // Frozen by design: a decrease cannot become negative and the gap is
            // conservatively skipped. The timer continues normally.
            return;
        }
        if (raw < runningStartRaw) {
            runningEpochLost = true;
            runningCadence.reset();
            Log.w(TAG, "Step-counter epoch changed during the run; freezing steps");
            notifyRunningChanged();
            return;
        }

        long delta = raw - runningStartRaw;
        int newSteps = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, delta));
        if (newSteps < runningStepCount) {
            newSteps = runningStepCount;
        }
        runningStepCount = newSteps;
        runningLastRaw = raw;
        runPersistPending = true;

        if (runningSession.getStatus() == RunningStatus.ACTIVE) {
            runningCadence.onReading(raw, now);
        }
        notifyRunningChanged();
        maybeRefreshNotification(now);
    }

    private void schedulePersistence(long now) {
        if (pendingRaw == null && !runPersistPending) {
            return;
        }
        if (lastPersistElapsedMs == 0L
                || now - lastPersistElapsedMs >= PERSIST_INTERVAL_MS) {
            persistPendingState();
        } else {
            handler.removeCallbacks(scheduledPersist);
            handler.postDelayed(scheduledPersist,
                    PERSIST_INTERVAL_MS - (now - lastPersistElapsedMs));
        }
    }

    private synchronized void persistPendingState() {
        handler.removeCallbacks(scheduledPersist);
        if (suppressPersistence) {
            return;
        }
        long now = nowElapsed();
        boolean retry = false;
        boolean dailyChanged = false;

        Long raw = pendingRaw;
        if (raw != null) {
            String dateIso = pendingDateIso == null ? DateUtils.todayIso() : pendingDateIso;
            OperationResult result = applyRepositoryReading(dateIso, raw);
            if (result.isSuccess()) {
                lastAppliedDate = dateIso;
                lastAppliedCheckpoint = raw;
                pendingRaw = null;
                pendingDateIso = null;
                dailyChanged = true;
            } else {
                Log.w(TAG, "Step reading persistence failed; will retry");
                retry = true;
            }
        }

        if (runPersistPending && runningSession != null) {
            OperationResult result = runningSessionRepository.update(
                    now, runningStartRaw == null ? 0 : runningStepCount, runningLastRaw);
            if (result.isSuccess()) {
                runPersistPending = false;
            } else {
                Log.w(TAG, "Running snapshot persistence failed; will retry");
                retry = true;
            }
        }

        lastPersistElapsedMs = now;
        if (dailyChanged) {
            sendStepsChanged(this);
        }
        maybeRefreshNotification(now);
        if (retry) {
            handler.postDelayed(scheduledPersist, RETRY_INTERVAL_MS);
        }
    }

    /**
     * Persists the latest Running step/duration snapshot before a pause or an
     * app-visible stop. Returns false only when the database write failed; the
     * caller must then keep the live state and offer a retry instead of losing
     * the transition.
     */
    private synchronized boolean persistRunProgress(long now) {
        if (runningSession == null || !runPersistPending) {
            return true;
        }
        OperationResult result = runningSessionRepository.update(
                now, runningStartRaw == null ? 0 : runningStepCount, runningLastRaw);
        if (result.isSuccess()) {
            runPersistPending = false;
            return true;
        }
        Log.w(TAG, "Running snapshot persistence failed");
        return false;
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

    // ------------------------------------------------------------------
    // Running commands
    // ------------------------------------------------------------------

    /** Starts the single durable session. Timer-only is always legal. */
    public synchronized OperationResult startRun() {
        ensureRunningSessionLoaded();
        RunningCommandValidation validation = RunningSessionController.validate(
                RunningCommand.START, currentRunningState());
        if (!validation.isAllowed()) {
            return OperationResult.error(runningErrorMessage(validation.getError()));
        }

        long now = nowElapsed();
        Long startRaw = sensorRegistered ? latestRaw : null;
        OperationResult startResult = runningSessionRepository.start(
                DateUtils.nowTimestamp(), now, currentBootCount(this), startRaw);
        if (!startResult.isSuccess()) {
            return startResult;
        }

        loadRunningSession();
        runningCadence.reset();
        lastFinishedDurationMs = -1L;
        lastFinishedStepCount = null;
        appVisible = appVisible || appIsVisible;

        if (!testMode) {
            if (hasActivityPermission(this) && stepCounterSource.isAvailable()) {
                startSensor();
            }
            startRunKeepAlive();
        }
        sendStatusBroadcast(this);
        notifyRunningChanged();
        return OperationResult.ok();
    }

    /** Pauses the monotonic timer, persists steps, and resets cadence. */
    public synchronized OperationResult pauseRun() {
        ensureRunningSessionLoaded();
        RunningCommandValidation validation = RunningSessionController.validate(
                RunningCommand.PAUSE, currentRunningState());
        if (!validation.isAllowed()) {
            return OperationResult.error(runningErrorMessage(validation.getError()));
        }

        long now = nowElapsed();
        if (!persistRunProgress(now)) {
            return OperationResult.error("Could not save the latest run steps. Please try again.");
        }
        OperationResult result = runningSessionRepository.pause(now);
        if (!result.isSuccess()) {
            return result;
        }

        loadRunningSession();
        runningCadence.reset();
        if (!testMode) {
            refreshNotification(true);
        }
        notifyRunningChanged();
        return OperationResult.ok();
    }

    /** Resumes after a pause or an interrupted-recovery screen. */
    public synchronized OperationResult resumeRun() {
        ensureRunningSessionLoaded();
        RunningCommandValidation validation = RunningSessionController.validate(
                RunningCommand.RESUME, currentRunningState());
        if (!validation.isAllowed()) {
            return OperationResult.error(runningErrorMessage(validation.getError()));
        }

        OperationResult result = runningSessionRepository.resume(nowElapsed());
        if (!result.isSuccess()) {
            return result;
        }
        loadRunningSession();
        runningCadence.reset();
        runPersistPending = false;
        if (!testMode) {
            startRunKeepAlive();
            syncTracking(true);
            refreshNotification(true);
        }
        notifyRunningChanged();
        return OperationResult.ok();
    }

    /**
     * Returns the confirmation snapshot without changing state. The screen uses
     * this exact duration and step count before the user confirms Stop.
     */
    public synchronized RunningSnapshot requestFinishSnapshot() {
        return getRunningSnapshot();
    }

    /** Saves exactly one workout and clears the live session. */
    public synchronized OperationResult finishRun() {
        ensureRunningSessionLoaded();
        RunningCommandValidation validation = RunningSessionController.validate(
                RunningCommand.FINISH, currentRunningState());
        if (!validation.isAllowed()) {
            return OperationResult.error(runningErrorMessage(validation.getError()));
        }

        long now = nowElapsed();
        RunningClockState savedClock = RunningSessionClock.finish(
                toClockState(runningSession), now);
        int savedSteps = runningStartRaw == null ? 0 : runningStepCount;
        Integer savedStepSnapshot = runningStartRaw == null ? null : runningStepCount;
        OperationResult result = runningSessionRepository.finish(now, savedSteps);
        if (!result.isSuccess()) {
            return result;
        }

        // The repository commits the same clock snapshot. Keep the exact values
        // for the summary so a dialog left open for a while cannot display a
        // duration that differs from the saved workout.
        lastFinishedDurationMs = savedClock.getDurationMs();
        lastFinishedStepCount = savedStepSnapshot;
        clearRunningState();
        if (!testMode) {
            syncTracking(true);
            refreshNotification(true);
        }
        sendStatusBroadcast(this);
        notifyRunningChanged();
        return OperationResult.ok();
    }

    /** Exact duration saved by the most recent successful finish, or -1. */
    public long getLastFinishedDurationMs() {
        return lastFinishedDurationMs;
    }

    /** Exact steps saved by the most recent finish; null means stored NULL. */
    public Integer getLastFinishedStepCount() {
        return lastFinishedStepCount;
    }

    /** Deletes the session without creating a workout. */
    public synchronized OperationResult discardRun() {
        ensureRunningSessionLoaded();
        RunningCommandValidation validation = RunningSessionController.validate(
                RunningCommand.DISCARD, currentRunningState());
        if (!validation.isAllowed()) {
            return OperationResult.error(runningErrorMessage(validation.getError()));
        }

        OperationResult result = runningSessionRepository.discard();
        if (!result.isSuccess()) {
            return result;
        }

        clearRunningState();
        if (!testMode) {
            syncTracking(true);
            refreshNotification(true);
        }
        sendStatusBroadcast(this);
        notifyRunningChanged();
        return OperationResult.ok();
    }

    private void startRunKeepAlive() {
        Intent keepAlive = new Intent(this, ActivityTrackingService.class);
        keepAlive.setAction(ACTION_RUN_KEEP_ALIVE);
        try {
            startService(keepAlive);
        } catch (RuntimeException e) {
            Log.w(TAG, "Running keep-alive start was rejected");
        }
    }

    private void clearRunningState() {
        runningSession = null;
        runningSessionLoaded = true;
        runningStartRaw = null;
        runningLastRaw = null;
        runningStepCount = 0;
        runningEpochLost = false;
        runPersistPending = false;
        runningCadence.reset();
    }

    private RunningSessionState currentRunningState() {
        return RunningSessionController.stateOf(
                runningSession != null,
                runningSession == null ? null : runningSession.getStatus(),
                runningSession != null && runningSession.isInterrupted());
    }

    private static String runningErrorMessage(RunningCommandError error) {
        switch (error) {
            case NO_SESSION:
                return "No run is in progress.";
            case ALREADY_RUNNING:
                return "A run is already in progress.";
            case ALREADY_ACTIVE:
                return "This run is already active.";
            case ALREADY_PAUSED:
                return "This run is already paused.";
            case SESSION_INTERRUPTED:
                return "This run was interrupted. Resume, finish or discard it.";
            default:
                return "The run could not be updated.";
        }
    }

    // ------------------------------------------------------------------
    // Running snapshots and listeners
    // ------------------------------------------------------------------

    /** Current immutable snapshot for the Running screen. */
    public synchronized RunningSnapshot getRunningSnapshot() {
        ensureRunningSessionLoaded();
        long now = nowElapsed();
        RunningClockState clock = runningSession == null ? null : toClockState(runningSession);
        boolean interrupted = runningSession != null && runningSession.isInterrupted();
        Integer cadence = null;
        if (runningSession != null
                && !interrupted
                && runningSession.getStatus() == RunningStatus.ACTIVE) {
            cadence = runningCadence.cadenceAt(now);
        }

        boolean baseSensor = hasActivityPermission(this)
                && stepCounterSource.isAvailable()
                && !sensorRegistrationFailed;
        Integer steps = runningStartRaw == null ? null : runningStepCount;
        return RunningSessionController.snapshot(
                clock,
                now,
                interrupted,
                steps,
                cadence,
                baseSensor,
                foregroundStarted,
                NotificationHelper.areTrackingNotificationsEnabled(this),
                runningSession == null ? null : runningSession.getStartedAt());
    }

    public void addRunningListener(RunningListener listener) {
        if (listener == null) {
            return;
        }
        runningListeners.add(listener);
        runOnMain(() -> listener.onRunningUpdate(getRunningSnapshot()));
    }

    public void removeRunningListener(RunningListener listener) {
        if (listener != null) {
            runningListeners.remove(listener);
        }
    }

    private void notifyRunningChanged() {
        sendRunningStatusBroadcast(this);
        RunningSnapshot snapshot = getRunningSnapshot();
        runOnMain(() -> {
            for (RunningListener listener : runningListeners) {
                listener.onRunningUpdate(snapshot);
            }
        });
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            handler.post(action);
        }
    }

    private void loadRunningSession() {
        RunningSession session = runningSessionRepository.getSession();
        if (session != null
                && !session.isInterrupted()
                && session.getBootCount() >= 0
                && session.getBootCount() != currentBootCount(this)) {
            runningSessionRepository.markInterruptedAfterReboot();
            session = runningSessionRepository.getSession();
        }
        runningSessionLoaded = true;
        runningSession = session;
        if (session == null) {
            runningStartRaw = null;
            runningLastRaw = null;
            runningStepCount = 0;
        } else {
            runningStartRaw = session.getStartRawSteps();
            runningLastRaw = session.getLastRawSteps();
            runningStepCount = session.getStepCount();
        }
        runningEpochLost = false;
        runPersistPending = false;
        runningCadence.reset();
    }

    private void ensureRunningSessionLoaded() {
        if (!runningSessionLoaded) {
            loadRunningSession();
        }
    }

    private static RunningClockState toClockState(RunningSession session) {
        return new RunningClockState(
                session.getStatus(),
                session.getStartedElapsedMs(),
                session.getAccumulatedMs(),
                session.getLastResumedElapsedMs(),
                session.getDurationMs());
    }

    private long nowElapsed() {
        if (testMode && testClockMs != null) {
            return testClockMs;
        }
        return SystemClock.elapsedRealtime();
    }

    /** Test-only deterministic clock used by the raw-bind Running test. */
    public OperationResult setTestClockForTest(long elapsedMs) {
        if (!testMode) {
            return OperationResult.error("The test clock is only available in test mode.");
        }
        if (elapsedMs < 0L) {
            return OperationResult.error("The test clock cannot be negative.");
        }
        testClockMs = elapsedMs;
        return OperationResult.ok();
    }

    /**
     * Test-only raw sensor route. It exercises the same Running delta logic as a
     * real STEP_COUNTER delivery without pretending to be a hardware sensor.
     */
    public OperationResult applyRunningRawForTest(long raw) {
        if (!testMode) {
            return OperationResult.error("Test readings are only available in test mode.");
        }
        if (raw < 0L) {
            return OperationResult.error("Step count cannot be negative.");
        }
        latestRaw = raw;
        handleRunningRaw(raw, nowElapsed());
        return OperationResult.ok();
    }

    // ------------------------------------------------------------------
    // Notification refresh
    // ------------------------------------------------------------------

    private void maybeRefreshNotification(long now) {
        if (!foregroundStarted) {
            return;
        }
        long interval = isRunningNotificationActive()
                ? RUN_NOTIFICATION_REFRESH_INTERVAL_MS
                : DAILY_NOTIFICATION_REFRESH_INTERVAL_MS;
        if (now - lastNotificationRefreshElapsedMs < interval) {
            return;
        }
        refreshNotification(false);
    }

    private void refreshNotification(boolean force) {
        if (!foregroundStarted) {
            return;
        }
        long now = nowElapsed();
        long interval = isRunningNotificationActive()
                ? RUN_NOTIFICATION_REFRESH_INTERVAL_MS
                : DAILY_NOTIFICATION_REFRESH_INTERVAL_MS;
        if (!force && now - lastNotificationRefreshElapsedMs < interval) {
            return;
        }
        if (!ensureForegroundStillVisible()) {
            return;
        }
        try {
            NotificationManagerCompat.from(this).notify(
                    NotificationHelper.TRACKING_NOTIFICATION_ID,
                    buildCurrentNotification());
            lastNotificationRefreshElapsedMs = now;
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
        notifyRunningChanged();
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
     * Starts daily tracking, or a live Running session, and reports why it
     * cannot start. A rejected foreground promotion can fall back to a plain
     * app-visible service when the app is on screen; a boot attempt never does.
     */
    public static void requestStart(
            Context context, boolean appVisible, boolean allowAppVisibleFallback) {
        Context app = context.getApplicationContext();
        if (appVisible) {
            appIsVisible = true;
        }

        AppSettings settings = new SettingsRepository(app).getSettings();
        RunningSession running =
                new RunningSessionRepository(app).getSession();
        boolean liveRun = running != null && !running.isInterrupted();
        if (!settings.isAutoStepsEnabled() && !liveRun) {
            stop(app);
            sendStatusBroadcast(app);
            return;
        }
        if ((!hasStepCounter(app) || !hasActivityPermission(app)) && !liveRun) {
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

    private static int currentBootCount(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException e) {
            return -1;
        }
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

    private static void sendRunningStatusBroadcast(Context context) {
        Intent changed = new Intent(ACTION_RUNNING_STATUS_CHANGED);
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
