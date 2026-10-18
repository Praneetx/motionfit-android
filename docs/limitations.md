# Known limitations

Motionfit is a working but early-stage app (`0.1.0`). The limitations below are known
and deliberate unless stated otherwise.

## Local-only storage

* All data lives in one SQLite file in app-private storage. It is **not encrypted**.
* There is no cloud database, backend or account system, and no sync between devices.

## Data loss

* Uninstalling the app, or clearing its storage in Android settings, deletes the
  database permanently.
* The in-app "Clear all local data" action deletes every record after confirmation and
  cannot be undone.
* Records survive ordinary app restarts and device reboots.
* Android system backup may copy the app database if the user's device backup is on
  (see [PRIVACY.md](../PRIVACY.md)).

## No internet, by design

* The app declares no `INTERNET` permission and makes no network calls.
* Distance tracking and calorie estimation are not implemented. Cycling is only a
  workout category.

## Automatic step counting (experimental)

* Uses `Sensor.TYPE_STEP_COUNTER`, which reports steps since the last reboot, not a
  per-day figure.
* Readings are only collected **while the Steps screen is open**; there is no
  background service.
* Many devices have no step-counter sensor. The app detects this and disables the
  sensor section while keeping manual entry.
* Android 10+ requires the `ACTIVITY_RECOGNITION` runtime permission. If denied,
  automatic counting is unavailable but manual entry still works.
* To avoid silent double counting the sensor never writes on its own; the user must
  press "Add N steps to this day", which performs one write and then stops the sensor.
* Not verified on a physical device with a real sensor; only the "sensor unavailable"
  fallback has been exercised.

## Reminders

* Local `AlarmManager` alarm plus a `BroadcastReceiver`; no cloud push.
* The alarm is intentionally inexact, so delivery may be slightly late.
* A `BOOT_COMPLETED` receiver restores the schedule after a restart, but a user
  **force-stop** cancels it until the app is opened again.
* Aggressive OEM battery optimisation can suppress alarms.
* Android 13+ requires `POST_NOTIFICATIONS`. If denied, the app warns and the test
  notification button explains the problem instead of silently failing.
* Not verified across reboots, force-stops and every Android version.

## Database migrations

* Schema version is 1. `onUpgrade` has the hook for future migrations and can safely
  re-run additive create statements, but no breaking migration has been written or
  tested.
* `onDowngrade` recreates the schema, which destroys data.

## UI

* Designed for ordinary phone sizes; tablets and landscape are not specifically
  designed.
* Content descriptions and 48dp touch targets are present, but the app has not been
  audited end-to-end with TalkBack.
* "Today" uses the device's local date, so changing the time zone changes which day
  new entries belong to.
* The UI has not yet been visually verified on a device (see
  [testing.md](testing.md)); expect small layout adjustments after the first run.

## Testing gaps

* 34 JVM unit tests pass; the app builds and installs.
* Instrumented SQLite tests compile but have **not been executed**.
* No Espresso UI test suite and no CI pipeline yet.
* No physical-device testing yet, so sensor and notification behaviour on real
  hardware is unverified.

## Before production

* Encrypted storage and a documented export/backup policy.
* Tested schema migrations.
* A release signing config and R8 build.
* A full accessibility and localisation pass.
* A CI pipeline plus an Espresso UI suite.
* Store listing, data-safety form and a reviewed privacy policy.
