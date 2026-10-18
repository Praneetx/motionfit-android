# Privacy policy — Motionfit

> **DRAFT — review before publishing.** This is a plain-language draft written from
> the app's actual behaviour. It is not legal advice, and it does not claim
> compliance with GDPR, CCPA or any other regulation. Have it reviewed before you
> publish the app or attach it to a store listing.

**Last updated:** _add the date before publishing_

## The short version

Motionfit keeps your fitness data on your device. There is no account, no server and
no internet permission, so the app cannot send your data anywhere.

## What the app stores on your device

* Daily step totals, with the date and whether they were entered manually or read from
  the step sensor.
* Workout records: type, date, duration in minutes, and optional notes.
* Your daily step goal and daily workout-minutes goal.
* Reminder preferences (whether a reminder is on and at what time).

These are stored in a local SQLite database in the app's private storage
(`fitness_tracker.db`).

## What the app does not do

* No accounts, sign-in or registration.
* No analytics, advertising, tracking or profiling.
* No third-party SDKs that collect data.
* No network requests. The app declares **no `INTERNET` permission**, so it cannot
  transmit data even if it wanted to.
* No access to contacts, photos, files, location or health records.

## Permissions the app may ask for

| Permission | Why | If you decline |
|---|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | show the optional daily reminder | reminders stay off; everything else works |
| `ACTIVITY_RECOGNITION` (Android 10+) | read the step-counter sensor on the Steps screen | automatic counting is off; manual entry still works |
| `RECEIVE_BOOT_COMPLETED` | re-schedule a reminder after the device restarts | reminders may not survive a reboot |

The step sensor is read only while the Daily Steps screen is open. No background step
tracking is implemented.

## Backups

The app allows Android's system backup (`android:allowBackup="true"`). If your device
backup is enabled, Android itself may include the app's database in your device
backup. That is operating-system behaviour, not something Motionfit uploads. You can
turn device backup off in your system settings.

## Deleting your data

* Use **Settings → Clear all local data** inside the app. This permanently deletes all
  steps and workouts and resets your goals and reminder settings.
* Uninstalling the app, or clearing its storage in Android settings, also removes the
  database.

## Children

The app is a general-purpose fitness log with no accounts and no data collection. It is
not specifically directed at children.

## Changes to this policy

If the app's data behaviour changes, this file will be updated in the same change.

## Contact

Open an issue in this repository.
