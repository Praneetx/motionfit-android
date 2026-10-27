# Privacy policy — Motionfit

> **DRAFT — review before publishing.** This is a plain-language draft written from
> the app's actual behaviour. It is not legal advice, and it does not claim
> compliance with GDPR, CCPA or any other regulation. Have it reviewed before you
> publish the app or attach it to a store listing.

**Last updated:** 2026-10-27

## The short version

Motionfit keeps your fitness data on your device. There is no account, no server and
no internet permission, so the app cannot send your data anywhere. Automatic step
tracking and Running are optional and use an on-device foreground service with a local
notification.

## What the app stores on your device

* Daily step totals, with the date, whether the total came from a manual entry or the
  step sensor, and the latest raw step-counter checkpoint used to avoid double counting.
* Workout records: type, date, duration in whole minutes, optional notes, and — for
  Running workouts — exact duration in seconds and step count.
* An in-progress or paused Running session while a run exists: start/update timestamps,
  timer snapshots, step counts, raw sensor baselines, a boot marker and an interrupted
  flag. The session is deleted when the run is saved or discarded.
* Your daily step goal and daily workout-minutes goal.
* Reminder preferences (whether a reminder is on and at what time), whether automatic
  step counting is enabled, and whether first-run onboarding was completed.

These are stored in a local SQLite database in the app's private storage
(`fitness_tracker.db`).

## What the app does not do

* No accounts, sign-in or registration.
* No analytics, advertising, tracking or profiling.
* No third-party SDKs that collect data.
* No network requests. The app declares **no `INTERNET` permission**, so it cannot
  transmit data even if it wanted to.
* No GPS, location, routes, distance, pace or calorie tracking.
* No access to contacts, photos, files, health records or health platforms.

## Permissions the app may ask for

| Permission | Why | If you decline or block it |
|---|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | show the optional daily reminder, and show the required persistent notification for automatic step tracking / Running | reminders stay off; automatic tracking falls back to app-open-only; a Running session may lose live background updates |
| `ACTIVITY_RECOGNITION` (Android 10+) | read the device's step-counter sensor | automatic counting is unavailable; Running works as a timer-only run; manual entry and workouts still work |
| `RECEIVE_BOOT_COMPLETED` | re-schedule the optional daily reminder after a device restart | reminders may not survive a reboot; step tracking is not started by this receiver |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH` | run the optional automatic-tracking / Running service with a visible notification | Android may reject the service; tracking falls back to app-open-only or Running becomes timer-only while the screen is open |

The app requests the step-counter sensor only through `ACTIVITY_RECOGNITION`. It does
not request location or health-record access. The step-counter sensor is optional and
declared as not required in the manifest.

## Automatic step counting and the notification

Automatic counting is off by default. If you turn it on and grant activity access,
Motionfit reads the device's `TYPE_STEP_COUNTER` and stores a daily total plus the
latest raw sensor checkpoint. A manual step total is treated as a calibration and
becomes the new checkpoint.

When notifications are allowed and Android accepts the foreground service, a
low-importance persistent notification is shown while tracking. Its lock-screen public
version shows only a generic "Motionfit / Tracking is on" message, not your step count.
The notification channel can be blocked in Android settings; doing so changes the
truthful in-app status to app-open-only.

Background tracking is **not guaranteed** on every Android version, device maker or
battery configuration. A force-stop, reboot, battery optimisation, notification block
or platform restriction can stop or delay it. Motionfit does not use a hidden or
location-based workaround.

## Running

Running stores a timer, step count and rolling cadence. It does not use GPS, location,
routes, distance, pace or calories. If activity access or the step sensor is
unavailable, Running continues as a timer-only run and shows `—` for steps/cadence.
While a run is active and Android permits the foreground service, a persistent
notification with Pause/Resume and Stop actions is shown. The Stop action opens a
confirmation before a workout is saved. An interrupted run is recovered from the last
persisted values; downtime after a restart is not added to the timer.

## Backups

The app allows Android's system backup (`android:allowBackup="true"`). If your device
backup is enabled, Android itself may include the app's database in your device
backup. That is operating-system behaviour, not something Motionfit uploads. You can
turn device backup off in your system settings.

## Deleting your data

* Use **Settings → Clear all local data** inside the app. This permanently deletes all
  step records, workouts and any in-progress Running session, and resets goals,
  reminders, automatic-tracking and onboarding settings to their defaults.
* Uninstalling the app, or clearing its storage in Android settings, also removes the
  database.

## Children

The app is a general-purpose fitness log with no accounts and no data collection. It is
not specifically directed at children.

## Changes to this policy

If the app's data behaviour changes, this file will be updated in the same change.

## Contact

Open an issue in this repository.
