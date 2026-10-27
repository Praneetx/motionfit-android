package io.github.praneetx.motionfit.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.praneetx.motionfit.R;
import io.github.praneetx.motionfit.logic.RunningSnapshot;
import io.github.praneetx.motionfit.logic.RunningSnapshotStatus;
import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.services.NotificationHelper;
import io.github.praneetx.motionfit.utils.FormatUtils;
import io.github.praneetx.motionfit.utils.OperationResult;

/**
 * Focused full-screen Running mode.
 *
 * <p>The activity is deliberately thin: it binds to the single tracking service
 * in {@code onStart}, renders immutable snapshots and sends commands. The clock,
 * sensor baseline, durable session and exactly-once finish transaction all live
 * in the service. This screen adds no distance, pace, calorie or location data.</p>
 *
 * <p>Back/Close leaves the activity without discarding the session. A visible
 * foreground notification or the service's app-visible fallback owns the run
 * while the user is away.</p>
 */
public class RunningActivity extends AppCompatActivity
        implements ActivityTrackingService.RunningListener {

    /** Used by the notification Stop action so it opens the confirmation. */
    public static final String EXTRA_CONFIRM_STOP =
            "io.github.praneetx.motionfit.extra.CONFIRM_STOP";

    private static final int REQUEST_ACTIVITY_RECOGNITION = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final long TICK_INTERVAL_MS = 1_000L;

    private static final String STATE_SUMMARY = "running_summary";
    private static final String STATE_SUMMARY_DURATION = "running_summary_duration";
    private static final String STATE_SUMMARY_STEPS = "running_summary_steps";
    private static final String STATE_SUMMARY_HAS_STEPS = "running_summary_has_steps";

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!uiStarted || service == null || finishing || summaryShown || errorShown) {
                scheduleTick();
                return;
            }
            RunningSnapshot snapshot = service.getRunningSnapshot();
            if (snapshot.getStatus() == RunningSnapshotStatus.RUNNING) {
                render(snapshot, false);
            }
            scheduleTick();
        }
    };

    private ActivityTrackingService service;
    private boolean bound;
    private boolean uiStarted;
    private boolean finishing;
    private boolean summaryShown;
    private boolean errorShown;
    private boolean pendingStopConfirmation;

    private long summaryDurationMs;
    private Integer summarySteps;

    private RunningSnapshot lastSnapshot;
    private RunningSnapshotStatus lastRenderedStatus;

    private TextView timerValue;
    private TextView stepValue;
    private TextView cadenceValue;
    private TextView stateLabel;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView backNote;
    private TextView summaryDetails;
    private TextView errorTitle;
    private TextView errorBody;

    private View statusCard;
    private View recoveryCard;
    private View finishingCard;
    private View summaryCard;
    private View errorCard;
    private View actions;
    private View closeButton;
    private ScrollView scrollView;

    private MaterialButton startButton;
    private MaterialButton pauseResumeButton;
    private MaterialButton stopButton;
    private MaterialButton grantActivityButton;
    private MaterialButton notificationSettingsButton;
    private MaterialButton recoveryResumeButton;
    private MaterialButton recoveryFinishButton;
    private MaterialButton recoveryDiscardButton;
    private MaterialButton summaryDoneButton;
    private MaterialButton retryButton;
    private ProgressBar finishingProgress;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!uiStarted) {
                return;
            }
            if (binder instanceof ActivityTrackingService.LocalBinder) {
                service = ((ActivityTrackingService.LocalBinder) binder).getService();
                service.addRunningListener(RunningActivity.this);
                render(service.getRunningSnapshot(), false);
                if (pendingStopConfirmation) {
                    pendingStopConfirmation = false;
                    maybeShowPendingStop();
                }
            } else {
                service = null;
                enterError(
                        getString(R.string.running_error_title),
                        getString(R.string.running_error_body));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            if (uiStarted) {
                enterError(
                        getString(R.string.running_error_title),
                        getString(R.string.running_error_body));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);
        bindViews();
        wireActions();

        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SUMMARY, false)) {
            summaryShown = true;
            summaryDurationMs = savedInstanceState.getLong(STATE_SUMMARY_DURATION, 0L);
            if (savedInstanceState.getBoolean(STATE_SUMMARY_HAS_STEPS, false)) {
                summarySteps = savedInstanceState.getInt(STATE_SUMMARY_STEPS, 0);
            }
        }

        pendingStopConfirmation = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_CONFIRM_STOP, false);

        if (summaryShown) {
            renderSummary();
        } else {
            renderReady(null, false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_CONFIRM_STOP, false)) {
            if (service == null) {
                pendingStopConfirmation = true;
            } else {
                showStopConfirmation();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        uiStarted = true;
        bindIfNeeded();
        scheduleTick();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from notification settings or a permission dialog must
        // re-evaluate the legal foreground mode without restarting the session.
        if (bound) {
            ActivityTrackingService.refresh(this);
            if (service != null) {
                render(service.getRunningSnapshot(), false);
            }
        }
        applyKeepScreen();
    }

    @Override
    protected void onPause() {
        clearKeepScreen();
        super.onPause();
    }

    @Override
    protected void onStop() {
        uiStarted = false;
        ticker.removeCallbacksAndMessages(null);
        clearKeepScreen();
        if (service != null) {
            service.removeRunningListener(this);
            service = null;
        }
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SUMMARY, summaryShown);
        outState.putLong(STATE_SUMMARY_DURATION, summaryDurationMs);
        outState.putBoolean(STATE_SUMMARY_HAS_STEPS, summarySteps != null);
        if (summarySteps != null) {
            outState.putInt(STATE_SUMMARY_STEPS, summarySteps);
        }
    }

    @Override
    public void onRunningUpdate(RunningSnapshot snapshot) {
        if (!uiStarted) {
            return;
        }
        render(snapshot, true);
    }

    // ------------------------------------------------------------------
    // Binding and rendering
    // ------------------------------------------------------------------

    private void bindIfNeeded() {
        if (bound) {
            return;
        }
        Intent intent = new Intent(this, ActivityTrackingService.class);
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            enterError(
                    getString(R.string.running_error_title),
                    getString(R.string.running_error_body));
        }
    }

    private void bindViews() {
        timerValue = findViewById(R.id.runningTimerValue);
        stepValue = findViewById(R.id.runningStepValue);
        cadenceValue = findViewById(R.id.runningCadenceValue);
        stateLabel = findViewById(R.id.runningStateLabel);
        statusTitle = findViewById(R.id.runningStatusTitle);
        statusBody = findViewById(R.id.runningStatusBody);
        backNote = findViewById(R.id.runningBackNote);
        summaryDetails = findViewById(R.id.runningSummaryDetails);
        errorTitle = findViewById(R.id.runningErrorTitle);
        errorBody = findViewById(R.id.runningErrorBody);

        statusCard = findViewById(R.id.runningStatusCard);
        recoveryCard = findViewById(R.id.runningRecoveryCard);
        finishingCard = findViewById(R.id.runningFinishingCard);
        summaryCard = findViewById(R.id.runningSummaryCard);
        errorCard = findViewById(R.id.runningErrorCard);
        actions = findViewById(R.id.runningActions);
        closeButton = findViewById(R.id.btnCloseRunning);
        scrollView = findViewById(R.id.runningScroll);

        startButton = findViewById(R.id.btnStartRun);
        pauseResumeButton = findViewById(R.id.btnPauseResumeRun);
        stopButton = findViewById(R.id.btnStopRun);
        grantActivityButton = findViewById(R.id.btnGrantActivity);
        notificationSettingsButton = findViewById(R.id.btnOpenNotificationSettings);
        recoveryResumeButton = findViewById(R.id.btnRecoveryResume);
        recoveryFinishButton = findViewById(R.id.btnRecoveryFinish);
        recoveryDiscardButton = findViewById(R.id.btnRecoveryDiscard);
        summaryDoneButton = findViewById(R.id.btnSummaryDone);
        retryButton = findViewById(R.id.btnRetryRunning);
        finishingProgress = findViewById(R.id.runningFinishingProgress);
    }

    private void wireActions() {
        closeButton.setOnClickListener(view -> finish());
        startButton.setOnClickListener(view -> startRun());
        pauseResumeButton.setOnClickListener(view -> togglePauseResume());
        stopButton.setOnClickListener(view -> showStopConfirmation());
        recoveryResumeButton.setOnClickListener(view -> resumeInterruptedRun());
        recoveryFinishButton.setOnClickListener(view -> showStopConfirmation());
        recoveryDiscardButton.setOnClickListener(view -> showDiscardConfirmation());
        summaryDoneButton.setOnClickListener(view -> finish());
        retryButton.setOnClickListener(view -> retry());
        grantActivityButton.setOnClickListener(view -> requestActivityPermission());
        notificationSettingsButton.setOnClickListener(view -> openNotificationSettings());
    }

    private void render(RunningSnapshot snapshot, boolean announceChange) {
        if (finishing) {
            return;
        }
        if (summaryShown) {
            renderSummary();
            return;
        }
        if (errorShown) {
            return;
        }
        if (snapshot == null) {
            enterError(
                    getString(R.string.running_error_title),
                    getString(R.string.running_error_body));
            return;
        }

        lastSnapshot = snapshot;
        RunningSnapshotStatus previous = lastRenderedStatus;
        RunningSnapshotStatus status = snapshot.getStatus();
        lastRenderedStatus = status;

        updateMetrics(snapshot);
        updateStatusCard(snapshot);
        updateVisibility(status);
        updateStateLabel(status);

        if (announceChange && previous != null && previous != status) {
            announceStatus(previous, status);
        }
        applyKeepScreen();
    }

    private void renderReady(RunningSnapshot snapshot, boolean announceChange) {
        RunningSnapshotStatus previous = lastRenderedStatus;
        lastRenderedStatus = RunningSnapshotStatus.READY;
        if (snapshot != null) {
            lastSnapshot = snapshot;
            updateMetrics(snapshot);
        } else {
            setTimer(0L);
            setSteps(null);
            setCadence(null);
        }
        updateStatusCard(snapshot);
        updateVisibility(RunningSnapshotStatus.READY);
        updateStateLabel(RunningSnapshotStatus.READY);
        if (announceChange && previous != null && previous != RunningSnapshotStatus.READY) {
            // Ready is normally reached after discard; the action announces it.
        }
        applyKeepScreen();
    }

    private void updateMetrics(RunningSnapshot snapshot) {
        setTimer(snapshot.getDurationMs());
        setSteps(snapshot.getStepCount());
        setCadence(snapshot.getCadence());
    }

    private void setTimer(long durationMs) {
        String clock = FormatUtils.clockText(durationMs);
        timerValue.setText(clock);
        timerValue.setContentDescription(
                getString(R.string.running_timer_accessibility, clock));
    }

    private void setSteps(Integer steps) {
        if (steps == null) {
            String dash = getString(R.string.running_dash);
            stepValue.setText(dash);
            stepValue.setContentDescription(
                    getString(R.string.running_steps_unavailable_accessibility));
        } else {
            String value = FormatUtils.intText(steps);
            stepValue.setText(value);
            stepValue.setContentDescription(
                    getString(R.string.running_steps_accessibility, value));
        }
    }

    private void setCadence(Integer cadence) {
        if (cadence == null) {
            cadenceValue.setText(R.string.running_dash);
            cadenceValue.setContentDescription(
                    getString(R.string.running_cadence_unavailable_accessibility));
        } else {
            cadenceValue.setText(FormatUtils.intText(cadence));
            cadenceValue.setContentDescription(
                    getResources().getQuantityString(
                            R.plurals.running_cadence_accessibility, cadence, cadence));
        }
    }

    private void updateVisibility(RunningSnapshotStatus status) {
        boolean ready = status == RunningSnapshotStatus.READY;
        boolean running = status == RunningSnapshotStatus.RUNNING;
        boolean paused = status == RunningSnapshotStatus.PAUSED;
        boolean interrupted = status == RunningSnapshotStatus.INTERRUPTED;
        boolean session = running || paused || interrupted;

        startButton.setVisibility(ready ? View.VISIBLE : View.GONE);
        startButton.setEnabled(ready && service != null && !finishing);
        actions.setVisibility(running || paused ? View.VISIBLE : View.GONE);
        pauseResumeButton.setEnabled(service != null && !finishing && (running || paused));
        pauseResumeButton.setText(paused ? R.string.running_resume : R.string.running_pause);
        stopButton.setEnabled(service != null && !finishing && (running || paused));

        recoveryCard.setVisibility(interrupted ? View.VISIBLE : View.GONE);
        recoveryResumeButton.setEnabled(service != null && interrupted && !finishing);
        recoveryFinishButton.setEnabled(service != null && interrupted && !finishing);
        recoveryDiscardButton.setEnabled(service != null && interrupted && !finishing);

        finishingCard.setVisibility(View.GONE);
        summaryCard.setVisibility(View.GONE);
        errorCard.setVisibility(View.GONE);
        statusCard.setVisibility(session || ready ? View.VISIBLE : View.GONE);
        backNote.setVisibility(session ? View.VISIBLE : View.GONE);
    }

    private void updateStateLabel(RunningSnapshotStatus status) {
        switch (status) {
            case RUNNING:
                stateLabel.setText(R.string.running_state_running);
                stateLabel.setBackgroundResource(R.drawable.bg_wise_card_green);
                break;
            case PAUSED:
                stateLabel.setText(R.string.running_state_paused);
                stateLabel.setBackgroundResource(R.drawable.bg_wise_card_sage);
                break;
            case INTERRUPTED:
                stateLabel.setText(R.string.running_state_interrupted);
                stateLabel.setBackgroundResource(R.drawable.bg_wise_card_sage);
                break;
            case READY:
            default:
                stateLabel.setText(R.string.running_state_ready);
                stateLabel.setBackgroundResource(R.drawable.bg_badge_positive);
                break;
        }
    }

    private void announceStatus(
            RunningSnapshotStatus previous, RunningSnapshotStatus current) {
        int message;
        if (current == RunningSnapshotStatus.RUNNING) {
            message = previous == RunningSnapshotStatus.PAUSED
                    ? R.string.running_announce_resumed
                    : R.string.running_announce_started;
        } else if (current == RunningSnapshotStatus.PAUSED) {
            message = R.string.running_announce_paused;
        } else if (current == RunningSnapshotStatus.INTERRUPTED) {
            message = R.string.running_announce_interrupted;
        } else {
            return;
        }
        stateLabel.announceForAccessibility(getString(message));
    }

    private void updateStatusCard(RunningSnapshot snapshot) {
        boolean sensorPresent = ActivityTrackingService.hasStepCounter(this);
        boolean activityGranted = ActivityTrackingService.hasActivityPermission(this);
        boolean notificationsAllowed =
                NotificationHelper.areTrackingNotificationsEnabled(this);
        boolean runState = snapshot != null
                && snapshot.getStatus() != RunningSnapshotStatus.READY;

        String title;
        String body;
        boolean showActivity = false;
        boolean showNotificationSettings = false;

        if (!sensorPresent) {
            title = getString(R.string.running_status_timer_only_title);
            body = getString(R.string.running_status_no_sensor_body);
        } else if (!activityGranted) {
            title = getString(R.string.running_status_timer_only_title);
            body = getString(R.string.running_status_activity_body);
            showActivity = true;
        } else if (!notificationsAllowed) {
            title = getString(R.string.running_status_notifications_title);
            body = getString(R.string.running_status_notifications_body);
            showNotificationSettings = true;
        } else if (runState && snapshot.getForegroundTracking()) {
            title = getString(R.string.running_status_background_on_title);
            body = getString(R.string.running_status_background_on_body);
        } else if (runState) {
            title = getString(R.string.running_status_background_off_title);
            body = getString(R.string.running_status_background_off_body);
        } else {
            title = getString(R.string.running_status_ready_title);
            body = getString(R.string.running_status_ready_body);
        }

        statusTitle.setText(title);
        statusBody.setText(body);
        grantActivityButton.setVisibility(showActivity ? View.VISIBLE : View.GONE);
        notificationSettingsButton.setVisibility(
                showNotificationSettings ? View.VISIBLE : View.GONE);
    }

    private void showFinishing() {
        statusCard.setVisibility(View.GONE);
        recoveryCard.setVisibility(View.GONE);
        summaryCard.setVisibility(View.GONE);
        errorCard.setVisibility(View.GONE);
        startButton.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        backNote.setVisibility(View.GONE);
        finishingCard.setVisibility(View.VISIBLE);
        // A spinner is non-essential motion; leave the textual status when the
        // device has animations disabled.
        finishingProgress.setVisibility(
                animationsEnabled() ? View.VISIBLE : View.GONE);
        stateLabel.setText(R.string.running_state_saving);
        stateLabel.setBackgroundResource(R.drawable.bg_wise_card_sage);
        clearKeepScreen();
    }

    private void renderSummary() {
        if (summaryDurationMs < 0L) {
            summaryDurationMs = 0L;
        }
        setTimer(summaryDurationMs);
        setSteps(summarySteps);
        setCadence(null);
        String stepsText = summarySteps == null
                ? getString(R.string.running_stop_steps_unknown)
                : getString(R.string.running_stop_steps, FormatUtils.intText(summarySteps));
        summaryDetails.setText(getString(
                R.string.running_saved_details,
                FormatUtils.clockText(summaryDurationMs),
                stepsText));
        stateLabel.setText(R.string.running_state_saved);
        stateLabel.setBackgroundResource(R.drawable.bg_badge_positive);

        statusCard.setVisibility(View.GONE);
        recoveryCard.setVisibility(View.GONE);
        finishingCard.setVisibility(View.GONE);
        errorCard.setVisibility(View.GONE);
        startButton.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        backNote.setVisibility(View.GONE);
        summaryCard.setVisibility(View.VISIBLE);
        summaryDoneButton.setEnabled(true);
        revealCard(summaryCard);
        clearKeepScreen();
    }

    private void enterError(String title, String body) {
        errorShown = true;
        errorTitle.setText(title);
        errorBody.setText(body);
        errorCard.setVisibility(View.VISIBLE);
        statusCard.setVisibility(View.GONE);
        recoveryCard.setVisibility(View.GONE);
        finishingCard.setVisibility(View.GONE);
        summaryCard.setVisibility(View.GONE);
        startButton.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        backNote.setVisibility(View.GONE);
        stateLabel.setText(R.string.running_state_error);
        stateLabel.setBackgroundResource(R.drawable.bg_wise_card_sage);
        revealCard(errorCard);
        clearKeepScreen();
    }

    /**
     * Bring a terminal card into view immediately. Smooth scrolling is avoided
     * so reduced-motion users get the result without extra animation.
     */
    private void revealCard(View card) {
        if (scrollView == null || card == null) {
            return;
        }
        card.post(() -> scrollView.scrollTo(0, Math.max(0, card.getTop())));
    }

    private void leaveError() {
        errorShown = false;
        errorCard.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private void startRun() {
        if (service == null) {
            Toast.makeText(this, R.string.running_could_not_start, Toast.LENGTH_SHORT).show();
            return;
        }
        OperationResult result = service.startRun();
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.running_could_not_start, Toast.LENGTH_SHORT).show();
            return;
        }
        leaveError();
        summaryShown = false;
        summarySteps = null;
        summaryDurationMs = 0L;
        render(service.getRunningSnapshot(), true);
    }

    private void togglePauseResume() {
        if (service == null || lastSnapshot == null) {
            return;
        }
        RunningSnapshotStatus status = lastSnapshot.getStatus();
        if (status != RunningSnapshotStatus.RUNNING
                && status != RunningSnapshotStatus.PAUSED) {
            return;
        }
        OperationResult result = status == RunningSnapshotStatus.RUNNING
                ? service.pauseRun()
                : service.resumeRun();
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.running_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        render(service.getRunningSnapshot(), true);
    }

    private void resumeInterruptedRun() {
        if (service == null) {
            Toast.makeText(this, R.string.running_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        OperationResult result = service.resumeRun();
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.running_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingStopConfirmation = false;
        render(service.getRunningSnapshot(), true);
    }

    private void maybeShowPendingStop() {
        if (service == null) {
            return;
        }
        RunningSnapshot snapshot = service.getRunningSnapshot();
        if (snapshot.getStatus() == RunningSnapshotStatus.READY) {
            pendingStopConfirmation = false;
            return;
        }
        showStopConfirmation();
    }

    private void showStopConfirmation() {
        if (service == null || finishing || summaryShown) {
            return;
        }
        RunningSnapshot finalSnapshot = service.requestFinishSnapshot();
        if (finalSnapshot.getStatus() == RunningSnapshotStatus.READY) {
            return;
        }

        String stepsText = finalSnapshot.getStepCount() == null
                ? getString(R.string.running_stop_steps_unknown)
                : getString(R.string.running_stop_steps,
                        FormatUtils.intText(finalSnapshot.getStepCount()));
        String message = getString(
                R.string.running_stop_confirm_message,
                FormatUtils.clockText(finalSnapshot.getDurationMs()),
                stepsText);

        int negative = finalSnapshot.getStatus() == RunningSnapshotStatus.INTERRUPTED
                ? R.string.action_cancel
                : R.string.running_keep_running;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.running_stop_confirm_title)
                .setMessage(message)
                .setNegativeButton(negative, null)
                .setPositiveButton(R.string.running_confirm_button,
                        (dialog, which) -> performFinish(finalSnapshot))
                .show();
    }

    private void performFinish(RunningSnapshot confirmedSnapshot) {
        if (service == null) {
            Toast.makeText(this, R.string.running_could_not_save, Toast.LENGTH_SHORT).show();
            return;
        }
        finishing = true;
        showFinishing();
        OperationResult result = service.finishRun();
        if (result.isSuccess()) {
            long savedDuration = service.getLastFinishedDurationMs();
            summaryDurationMs = savedDuration >= 0L
                    ? savedDuration
                    : confirmedSnapshot.getDurationMs();
            summarySteps = service.getLastFinishedStepCount();
            finishing = false;
            summaryShown = true;
            lastRenderedStatus = null;
            renderSummary();
            stateLabel.announceForAccessibility(getString(R.string.running_announce_saved));
        } else {
            finishing = false;
            String detail = result.getMessage() == null
                    ? ""
                    : "\n\n" + result.getMessage();
            enterError(
                    getString(R.string.running_save_failed_title),
                    getString(R.string.running_save_failed_body) + detail);
        }
    }

    private void showDiscardConfirmation() {
        if (service == null || finishing) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.running_discard_title)
                .setMessage(R.string.running_discard_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.running_discard_confirm,
                        (dialog, which) -> discardRun())
                .show();
    }

    private void discardRun() {
        if (service == null) {
            Toast.makeText(this, R.string.running_could_not_discard, Toast.LENGTH_SHORT).show();
            return;
        }
        OperationResult result = service.discardRun();
        if (!result.isSuccess()) {
            Toast.makeText(this, R.string.running_could_not_discard, Toast.LENGTH_SHORT).show();
            return;
        }
        summaryShown = false;
        summarySteps = null;
        summaryDurationMs = 0L;
        leaveError();
        lastRenderedStatus = null;
        render(service.getRunningSnapshot(), false);
        Toast.makeText(this, R.string.running_announce_discarded, Toast.LENGTH_SHORT).show();
    }

    private void retry() {
        if (service == null) {
            bindIfNeeded();
            return;
        }
        leaveError();
        render(service.getRunningSnapshot(), false);
    }

    private void requestActivityPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED) {
            renderCurrent();
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                REQUEST_ACTIVITY_RECOGNITION);
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        settingsIntent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(settingsIntent);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.running_status_notifications_body,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_ACTIVITY_RECOGNITION
                && requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        ActivityTrackingService.refresh(this);
        renderCurrent();
    }

    private void renderCurrent() {
        if (service == null) {
            return;
        }
        if (summaryShown) {
            renderSummary();
        } else if (errorShown) {
            // The status card can still be refreshed behind an error; Retry is
            // the explicit way back to the service state.
            return;
        } else {
            render(service.getRunningSnapshot(), false);
        }
    }

    // ------------------------------------------------------------------
    // Ticker and window behavior
    // ------------------------------------------------------------------

    private void scheduleTick() {
        ticker.removeCallbacks(tick);
        if (uiStarted) {
            ticker.postDelayed(tick, TICK_INTERVAL_MS);
        }
    }

    private void applyKeepScreen() {
        if (uiStarted
                && lastRenderedStatus == RunningSnapshotStatus.RUNNING
                && !finishing
                && !summaryShown
                && !errorShown) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            clearKeepScreen();
        }
    }

    private void clearKeepScreen() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(
                    getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f) > 0f;
        } catch (RuntimeException e) {
            return true;
        }
    }
}
