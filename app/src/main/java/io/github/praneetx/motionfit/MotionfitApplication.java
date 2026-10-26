package io.github.praneetx.motionfit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import io.github.praneetx.motionfit.services.ActivityTrackingService;
import io.github.praneetx.motionfit.services.NotificationHelper;

/**
 * Application-level owner of the app foreground/background signal.
 *
 * <p>The automatic tracking service uses this signal to run its app-visible-only
 * fallback only while a Motionfit screen is present. An active foreground
 * service is left running when the app leaves the screen.</p>
 */
public class MotionfitApplication extends Application {

    private int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureTrackingChannel(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                // No tracking decision needed for creation alone.
            }

            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                if (startedActivities == 1) {
                    ActivityTrackingService.onAppForeground(
                            MotionfitApplication.this);
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                // Handled by started/stopped so multi-window screens stay safe.
            }

            @Override
            public void onActivityPaused(Activity activity) {
                // Handled by started/stopped.
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) {
                    ActivityTrackingService.onAppBackground(
                            MotionfitApplication.this);
                }
            }

            @Override
            public void onActivitySaveInstanceState(
                    Activity activity, Bundle outState) {
                // Not needed.
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                // Not needed.
            }
        });
    }
}
