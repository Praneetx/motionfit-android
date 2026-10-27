# Known limitations

Motionfit is a working V2 app (`2.0.0`), but it is not a production release. The
limitations below are known and deliberate unless stated otherwise.

## Local-only storage

* All data lives in one SQLite file in app-private storage. It is **not encrypted**.
* There is no cloud database, backend or account system, and no sync between devices.
* There is no data export or restore feature yet.

## Data loss

* Uninstalling the app, or clearing its storage in Android settings, deletes the
  database permanently.
* The in-app "Clear all local data" action deletes every step, workout and in-progress
  run after confirmation and cannot be undone. It also resets goals, reminders,
  automatic-tracking and onboarding settings.
* Records survive ordinary app restarts and device reboots.
* Android system backup may copy the app database if the user's device backup is on
  (see [PRIVACY.md](../PRIVACY.md)).
* A database downgrade recreates the schema and destroys data. Downgrades are not
  supported.

## No internet, location or fitness extras

* The app declares no `INTERNET` permission and makes no network calls.
* There is no GPS, location, route, distance, pace or calorie tracking. Cycling is only
  a workout category; no distance is recorded or calculated.
* There are no accounts, cloud sync, analytics or advertising SDKs.

## Automatic step counting

* Uses only `Sensor.TYPE_STEP_COUNTER`. It does **not** sum step-detector events or
  accelerometer peaks, and there is no detector fallback.
* The sensor is optional. Many devices and emulators have no step counter; the app
  detects this and keeps manual entry, workouts and Running's timer available.
* Android 10+ requires the `ACTIVITY_RECOGNITION` runtime permission. If denied,
  automatic counting is unavailable and Running is timer-only.
* Counting starts from the moment automatic tracking is enabled and a first raw reading
  is established. Earlier steps are not reconstructed; use a manual total if you want
  to include them.
* The daily total uses a persisted raw checkpoint. A manual total is a calibration; when
  a raw reading is available it becomes the new checkpoint. This prevents silent double
  counting, but sensor accuracy still depends on the device's step counter.
* Across midnight, a continuously running service can carry the sensor gap into the new
  day. If the service was not tracking across midnight, the new day starts at zero and
  the first reading establishes a new checkpoint; the untracked gap is not recovered.
* A reboot resets the sensor's raw counter. The app keeps the existing total and moves
  the checkpoint; it does not add the pre-reboot gap. Automatic tracking is not started
  by the boot receiver, so tracking resumes when the app/service next runs.
* Background tracking requires a visible foreground-service notification. If
  notifications are denied or the channel is blocked, the truthful mode is
  **app-open-only**. If Android rejects the foreground service, the app also falls back
  to app-open-only rather than claiming background tracking.
* A force-stopped app cannot restart itself. Aggressive OEM battery optimisation can
  stop or delay the service even when the app is not force-stopped.
* There is **no guarantee** that background tracking works on every Android version,
  device maker or battery configuration.

## Running

* Running records a timer, step count and rolling cadence only. It has no GPS,
  location, routes, distance, pace or calorie data.
* If activity access is denied or no step counter exists, Running is timer-only:
  steps/cadence show `—`, and the saved workout has no step count.
* The timer uses monotonic `elapsedRealtime()` and persists snapshots, but live
  background timing depends on the foreground service and its notification. If
  notifications are blocked or Android stops the service, live updates may stop when
  the screen is left or the device is locked; the timer is saved when the user returns.
* After process death or reboot, the run is marked interrupted. Recovery offers Resume,
  Finish and save, or Discard. Downtime is never added to the duration.
* Cadence is a rolling 30-second estimate and stays `—` until enough valid readings
  exist. It is not a medical or coaching measurement.
* Finish is transaction-safe and creates exactly one Running workout. If the save
  transaction fails, the session is kept for retry.

## Reminders

* Local `AlarmManager` alarm plus a `BroadcastReceiver`; no cloud push.
* The alarm is intentionally inexact, so delivery may be slightly late.
* A `BOOT_COMPLETED` receiver restores the reminder schedule after a restart, but a user
  **force-stop** cancels it until the app is opened again.
* Aggressive OEM battery optimisation can suppress alarms.
* Android 13+ requires `POST_NOTIFICATIONS`. If denied, reminders stay off and the app
  explains the consequence instead of silently failing.
* Not verified across reboots, force-stops and every Android version.

## Database migrations

* Schema version is 2. The additive 1 → 2 migration preserves existing steps, workouts,
  goals and reminder settings, and is tested from a genuine version-1 database.
* A real installed-APK V1 → V2 upgrade has not been performed.
* `onDowngrade` recreates the schema, which destroys data.

## UI and accessibility

* Designed for ordinary phone sizes in portrait. Tablets, landscape and very large font
  scales are not specifically designed or fully tested.
* The automated suite checks 360dp/1.3× layouts and several accessibility properties.
  The app has not been audited end-to-end with TalkBack, reduced motion or RTL.
* "Today" uses the device's local date, so changing the time zone changes which day
  new entries belong to.
* Physical-device visual and sensor behaviour remains unverified.

## Testing gaps

* 95 JVM unit tests and 104 instrumented tests pass on the API 30 emulator.
* The emulator has no step-counter sensor, so real sensor accuracy, cadence from real
  movement, notification permission flows, FGS background execution, reboot recovery
  and OEM battery behaviour are **NOT RUN**.
* A real V1→V2 APK upgrade, TalkBack, reduced-motion, RTL and physical-device checks are
  **NOT RUN**.
* No CI pipeline is configured.

## Before production

* Physical-device testing of the sensor, notifications, FGS, reboot, process death and
  OEM battery policies.
* A tested real APK upgrade from the V1 release.
* Encrypted storage and a documented export/backup policy.
* A release signing config and R8 build.
* A full TalkBack, reduced-motion, RTL and localisation pass.
* A CI pipeline plus a broader Espresso UI suite.
* Store listing, data-safety form and a reviewed privacy policy.
