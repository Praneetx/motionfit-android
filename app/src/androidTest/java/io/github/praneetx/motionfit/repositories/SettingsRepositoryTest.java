package io.github.praneetx.motionfit.repositories;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.praneetx.motionfit.models.AppSettings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented tests for the V2 opt-in settings.
 */
@RunWith(AndroidJUnit4.class)
public class SettingsRepositoryTest {

    private SettingsRepository repository;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new DataResetRepository(context).clearAllData().isSuccess());
        repository = new SettingsRepository(context);
    }

    @Test
    public void newSettingsDefaultToFalse() {
        AppSettings settings = repository.getSettings();

        assertFalse(settings.isAutoStepsEnabled());
        assertFalse(settings.isOnboardingCompleted());
        assertFalse(repository.isAutoStepsEnabled());
        assertFalse(repository.isOnboardingCompleted());
    }

    @Test
    public void autoStepsEnabledRoundTrips() {
        assertTrue(repository.setAutoStepsEnabled(true).isSuccess());

        assertTrue(repository.isAutoStepsEnabled());
        assertTrue(repository.getSettings().isAutoStepsEnabled());

        assertTrue(repository.setAutoStepsEnabled(false).isSuccess());

        assertFalse(repository.isAutoStepsEnabled());
        assertFalse(repository.getSettings().isAutoStepsEnabled());
    }

    @Test
    public void onboardingCompletedRoundTrips() {
        assertTrue(repository.setOnboardingCompleted(true).isSuccess());

        assertTrue(repository.isOnboardingCompleted());
        assertTrue(repository.getSettings().isOnboardingCompleted());

        assertTrue(repository.setOnboardingCompleted(false).isSuccess());

        assertFalse(repository.isOnboardingCompleted());
        assertFalse(repository.getSettings().isOnboardingCompleted());
    }
}
