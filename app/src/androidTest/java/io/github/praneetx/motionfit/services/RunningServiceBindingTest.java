package io.github.praneetx.motionfit.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.logic.RunningSnapshot;
import io.github.praneetx.motionfit.logic.RunningSnapshotStatus;
import io.github.praneetx.motionfit.models.Workout;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.RunningSessionRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.WorkoutRepository;
import io.github.praneetx.motionfit.utils.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Raw-bind contract for the Running state machine.
 *
 * <p>The service exposes a test clock only when bound with the existing
 * test-mode extra, so pause/resume durations are exact without sleeps. Real
 * sensor, foreground notification and reboot behaviour remain manual/NOT RUN.</p>
 */
@RunWith(AndroidJUnit4.class)
public class RunningServiceBindingTest {

    private Context context;
    private Intent serviceIntent;
    private ActivityTrackingService service;
    private CountDownLatch connected;
    private boolean bound;
    private RunningSessionRepository sessionRepository;
    private WorkoutRepository workoutRepository;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        sessionRepository = new RunningSessionRepository(context);
        workoutRepository = new WorkoutRepository(context);
        serviceIntent = new Intent(context, ActivityTrackingService.class);
        serviceIntent.putExtra(ActivityTrackingService.EXTRA_TEST_MODE, true);
    }

    @After
    public void tearDown() {
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
        context.stopService(serviceIntent);
        assertTrue(new SettingsRepository(context).setAutoStepsEnabled(false).isSuccess());
    }

    @Test
    public void startPauseResumeFinishSavesOneWorkoutAndNoSession() throws Exception {
        bindService();

        assertTrue(service.setTestClockForTest(1000L).isSuccess());
        assertTrue(service.startRun().isSuccess());
        assertNotNull(sessionRepository.getSession());

        assertTrue(service.applyRunningRawForTest(500L).isSuccess());
        assertTrue(service.applyRunningRawForTest(550L).isSuccess());

        assertTrue(service.setTestClockForTest(61000L).isSuccess());
        assertTrue(service.pauseRun().isSuccess());
        RunningSnapshot paused = service.getRunningSnapshot();
        assertEquals(RunningSnapshotStatus.PAUSED, paused.getStatus());
        assertEquals(60000L, paused.getDurationMs());
        assertEquals(Integer.valueOf(50), paused.getStepCount());
        assertNull("Paused cadence must not expose a stale value", paused.getCadence());

        assertTrue(service.setTestClockForTest(121000L).isSuccess());
        assertTrue(service.resumeRun().isSuccess());
        assertEquals(60000L, service.getRunningSnapshot().getDurationMs());

        assertTrue(service.setTestClockForTest(181000L).isSuccess());
        assertTrue(service.finishRun().isSuccess());
        assertNull(sessionRepository.getSession());
        assertEquals(RunningSnapshotStatus.READY, service.getRunningSnapshot().getStatus());

        List<Workout> workouts = workoutRepository.getForDate(DateUtils.todayIso());
        assertEquals("Finish must insert exactly one workout", 1, workouts.size());
        Workout workout = workouts.get(0);
        assertEquals("Running", workout.getWorkoutType());
        assertEquals(Integer.valueOf(120), workout.getDurationSeconds());
        assertEquals(2, workout.getDurationMinutes());
        assertEquals(Integer.valueOf(50), workout.getStepCount());

        assertFalse("A repeated finish must not save a second workout",
                service.finishRun().isSuccess());
        assertEquals(1, workoutRepository.getForDate(DateUtils.todayIso()).size());
    }

    @Test
    public void discardDeletesTheSessionWithoutCreatingAWorkout() throws Exception {
        bindService();

        assertTrue(service.setTestClockForTest(1000L).isSuccess());
        assertTrue(service.startRun().isSuccess());
        assertTrue(service.setTestClockForTest(61000L).isSuccess());
        assertTrue(service.discardRun().isSuccess());

        assertNull(sessionRepository.getSession());
        assertTrue(workoutRepository.getForDate(DateUtils.todayIso()).isEmpty());
        assertEquals(RunningSnapshotStatus.READY, service.getRunningSnapshot().getStatus());
    }

    @Test
    public void secondStartIsRejectedWithoutReplacingTheFirstSession() throws Exception {
        bindService();
        assertTrue(service.setTestClockForTest(1000L).isSuccess());
        assertTrue(service.startRun().isSuccess());

        assertFalse(service.startRun().isSuccess());
        assertNotNull(sessionRepository.getSession());
        assertEquals(RunningSnapshotStatus.RUNNING, service.getRunningSnapshot().getStatus());
    }

    @Test
    public void binderListenersReceiveRunningSnapshots() throws Exception {
        bindService();
        AtomicReference<RunningSnapshot> latest = new AtomicReference<>();
        CountDownLatch running = new CountDownLatch(1);
        service.addRunningListener(snapshot -> {
            latest.set(snapshot);
            if (snapshot.getStatus() == RunningSnapshotStatus.RUNNING) {
                running.countDown();
            }
        });

        assertTrue(service.setTestClockForTest(1000L).isSuccess());
        assertTrue(service.startRun().isSuccess());
        assertTrue("A RUNNING snapshot must be delivered",
                running.await(10, TimeUnit.SECONDS));
        assertEquals(RunningSnapshotStatus.RUNNING, latest.get().getStatus());
    }

    @Test
    public void runningNotificationOffersExactlyOnePauseOrResumeAction() {
        android.app.Notification active = NotificationHelper.buildRunningNotification(
                context, 65000L, 123, false);
        android.app.Notification paused = NotificationHelper.buildRunningNotification(
                context, 65000L, null, true);

        assertEquals("Running notification must expose one toggle action",
                1, active.actions.length);
        assertEquals("Paused notification must expose one toggle action",
                1, paused.actions.length);
    }

    private void bindService() throws InterruptedException {
        connected = new CountDownLatch(1);
        bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        assertTrue("Service must bind", bound);
        assertTrue("Service connection timed out",
                connected.await(10, TimeUnit.SECONDS));
        assertNotNull("Binder must expose the concrete service", service);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder instanceof ActivityTrackingService.LocalBinder) {
                service = ((ActivityTrackingService.LocalBinder) binder).getService();
            } else {
                service = null;
            }
            if (connected != null) {
                connected.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };
}
