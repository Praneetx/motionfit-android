# Known limitations

This is an **educational MVP**, not production software. The limitations below are
deliberate or environmental. Nothing here is claimed to work beyond what was
actually built and tested.

## Local-only storage

* All data lives in one SQLite file (`fitness_tracker.db`) inside the app's private
  storage. It is **not** encrypted (no SQLCipher or similar was added).
* There is no cloud database, no backend server and no account system.
* There is no synchronisation between devices.

## Data loss

* Uninstalling the app, or using Android's *Clear storage* setting, deletes the
  database permanently.
* The in-app **Clear all local data** action also deletes every record after an
  explicit confirmation dialog. This cannot be undone.
* Records survive ordinary app restarts and device reboots, because SQLite writes to
  app-private storage.

## No internet, by design

* The app declares **no INTERNET permission**.
* Distance tracking and calorie estimation are intentionally **not implemented**.
  Cycling exists only as a workout *category*.

## Automatic step counting (EXPERIMENTAL)

* Uses `Sensor.TYPE_STEP_COUNTER`, which reports steps **since the last reboot**,
  not a per-day figure.
* Readings are only collected **while the Daily Steps screen is open**. There is no
  background service and no continuous tracking.
* Some devices have no step-counter sensor; the app detects this and clearly
  disables the sensor section while keeping manual entry working.
* On Android 10+ the `ACTIVITY_RECOGNITION` runtime permission is required. If the
  user denies it, automatic counting is unavailable but manual entry still works.
* To avoid silent double counting, the sensor never writes to the database on its
  own. The user must press *"Add N steps to this day"*, which performs one explicit
  write and then stops the sensor.
* This feature has **not** been verified on a physical phone with a real sensor.
  It is tested only for its "sensor unavailable" fallback path on the emulator.

## Reminders

* Reminders use a local `AlarmManager` alarm plus a `BroadcastReceiver`. No cloud
  push service is involved.
* The alarm is deliberately **inexact** (`setAndAllowWhileIdle`), so Android may
  deliver the notification a little late to save battery.
* Reminders are re-scheduled by a `BOOT_COMPLETED` receiver after a reboot, but a
  user **force-stop** of the app cancels the alarm and it will not fire until the
  app is opened again.
* Aggressive battery optimisation by some phone manufacturers can suppress alarms.
* On Android 13+ the user must grant the `POST_NOTIFICATIONS` permission. If it is
  denied, the app shows a warning and the "Send test notification" button explains
  the problem instead of silently doing nothing.
* The reminder timing behaviour has **not** been verified across reboots,
  force-stops, and every Android version.

## Database migrations

* The schema is version 1. `onUpgrade` contains the hook for future migrations and
  can safely re-run the additive create statements, but genuine breaking migrations
  have not been written or tested yet.
* `onDowngrade` recreates the schema (destroys data). This is a deliberate MVP
  choice and is not user-friendly.

## UI / UX

* The UI is optimised for ordinary phone sizes; large tablets and landscape layouts
  were not specifically designed or tested.
* Accessibility: content descriptions and 48dp touch targets are provided, but the
  app has not been audited with TalkBack end-to-end.
* Time zone: "today" is the device's local date. Changing the device time zone can
  therefore move what counts as "today" for new entries.

## Testing gaps

* Unit tests (JVM) are executed and passing.
* Instrumented tests were written and run on the emulator; see
  `docs/testing.md` for the real results.
* No automated UI (Espresso interaction) test suite was completed; UI checks were
  performed manually on the emulator.
* No physical-device testing was possible in this environment, so sensor and
  notification behaviour on real hardware remains unverified.

## What production readiness would additionally require

* Encrypted local storage and a documented backup/restore policy.
* Robust, tested schema migrations.
* A full accessibility and localisation pass.
* A complete Espresso UI test suite and CI pipeline.
* Verified background step tracking (e.g. a foreground service) if that feature is
  desired.
* Proper handling of exact vs inexact alarms and OEM-specific battery restrictions.
