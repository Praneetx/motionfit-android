package io.github.praneetx.motionfit.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.logic.TrackingMode;
import io.github.praneetx.motionfit.models.DailySteps;
import io.github.praneetx.motionfit.repositories.DataResetRepository;
import io.github.praneetx.motionfit.repositories.SettingsRepository;
import io.github.praneetx.motionfit.repositories.StepsRepository;
import io.github.praneetx.motionfit.utils.OperationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Raw service-binding contract for the automatic tracking service.
 *
 * <p>This deliberately avoids Robolectric or a new dependency. The test binds
 * to a test-visible instance, applies two raw readings through the service's
 * persistence path, reloads the daily row and checks that the status stays a
 * valid enum. Sensor-event injection remains a manual device test.</p>
 */
@RunWith(AndroidJUnit4.class)
public class TrackingServiceBindingTest {

    private static final String DATE = "2031-09-01";

    private Context context;
    private Intent serviceIntent;
    private ActivityTrackingService service;
    private CountDownLatch connected;
    private boolean bound;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        assertTrue(new SettingsRepository(context).setAutoStepsEnabled(true).isSuccess());
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
    public void bindPersistReloadAndReadStatusWithoutCrashing() throws Exception {
        connected = new CountDownLatch(1);
        bound = context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        assertTrue("Service must bind", bound);
        assertTrue("Service connection timed out",
                connected.await(10, TimeUnit.SECONDS));

        assertNotNull("Binder must expose the concrete service", service);
        assertNotNull("Every bound service must report a valid status", service.getStatus());

        OperationResult first = service.applyReadingForTest(DATE, 1000L);
        assertTrue("First reading must persist", first.isSuccess());

        OperationResult second = service.applyReadingForTest(DATE, 1010L);
        assertTrue("Second reading must persist", second.isSuccess());

        DailySteps reloaded = new StepsRepository(context).getForDate(DATE);
        assertNotNull(reloaded);
        assertEquals(10, reloaded.getSteps());
        assertEquals(Long.valueOf(1010L), reloaded.getLastRawSensor());
        assertEquals(DailySteps.SOURCE_SENSOR, reloaded.getSource());

        TrackingMode status = service.getStatus();
        assertNotNull(status);
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
