# Motionfit V2 — Implementation Plan

**Status:** approved 2026-10-25; completed 2026-10-27

**Spec:** `docs/v2-spec.md` (approved and implemented)

**Baseline:** `v0.1.0` (`eaef9ae`), branch `v2-dev`

**Target:** `2.0.0` (versionCode 2) — implemented locally; not pushed or published

**This plan changes no source code until it is approved.**

The specification is authoritative. This plan only decides the order, the
scope of each commit, the tests written first, and the acceptance checks.

---

## 1. How each task is executed

For **every** task below:

1. **Write the failing test or acceptance check first.**
   - JVM test, instrumented test, or a precise manual checklist when the
     platform behaviour cannot be automated reliably.
   - Run it and record the real failure. A manual row starts as `NOT RUN`; it
     is never called a pass without an actual run.
2. **Make the smallest implementation change** that satisfies the test.
3. **Run the targeted check**, then the relevant full suite.
4. **Build:** `assembleDebug`; `assembleDebugAndroidTest` when tests changed.
5. **Report** with the required format:

   ```
   Test name | Expected | Actual | PASS / FAIL / NOT RUN
   ```

6. **Commit exactly one atomic change.**
7. **Stop and wait for the user to say "continue".**
8. Never commit to `main`, never push until told, and never restore the
   pre-interview stash blindly.

Commands use the existing Java runtime:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

Instrumented tests will run on the available emulator (`emulator-5554`). Those
tests clear app data on the emulator only; no physical device data is touched.
If that emulator contains data that must be kept, I will use a separate AVD
instead. Physical-device sensor/notification/permission rows remain `NOT RUN`
until you run them.

If a task exposes a spec conflict or a platform limitation that blocks its
acceptance, I stop and report it rather than widening the task silently.

---

## 2. Task map

| ID | Phase | Task | Depends on | Commit intent |
|---|---|---|---|---|
| 1 | Data/logic | Pure step, cadence, timer and duration logic | — | `feat: add v2 step and running logic` |
| 2 | Data | Database v2 schema and 1→2 migration | 1 | `feat: migrate database to version 2` |
| 3 | Data | Daily step checkpoint repository | 2 | `feat: add daily step checkpoints` |
| 4 | Data | Workout fields, settings, data reset | 2 | `feat: extend workouts and settings` |
| 5 | Data | Running session persistence and finish transaction | 1, 2, 4 | `feat: persist running sessions` |
| 6 | UI A | Global accessibility foundation | 2 | `a11y: fix global contrast, targets and semantics` |
| 7 | UI A | Today and Steps layout resilience | 6 | `a11y: make today and steps resilient` |
| 8 | UI A | Workouts and Progress resilience | 6 | `a11y: make workouts and progress resilient` |
| 9 | UI A | Settings, Goals and workout editor resilience | 6 | `a11y: fix settings and form accessibility` |
| 10 | UI B | Automatic step service, status and Settings controls | 3, 4, 6, 9 | `feat: add automatic step tracking service` |
| 11 | UI B | First-run onboarding and permission education | 4, 10 | `feat: add first-run onboarding` |
| 12 | Running | Running service controls, binder and notification | 5, 10 | `feat: add running session service` |
| 13 | Running | Running screen | 12 | `feat: add running screen` |
| 14 | Navigation | Run tab, Steps detail, Today/Progress entry points | 3, 13 | `feat: add run tab and steps detail` |
| 15 | UI C | Visual polish and step history | 3, 4, 13, 14 | `polish: add step history and running visuals` |
| 16 | QA | Lint cleanup, regression and full verification | all | `chore: complete v2 verification` |
| 17 | Docs | Version 2.0.0 and documentation | 16 | `docs: publish v2 documentation` |

Phases are deliberate:

- **Data/logic first** so UI and service tasks have tested contracts.
- **A (accessibility/layout) before B (onboarding)** and **B before C (visual
  polish)**, as approved.
- Running service/UI are built to the A standards; visual polish comes last.
- The old one-shot sensor UI is removed in Task 10, when the service exists.
  It is not left beside the new tracking model.

---

## 3. Detailed tasks

### Task 1 — Pure step, cadence, timer and duration logic

**Goal:** Put every non-Android rule in testable pure code before touching the
database or UI.

**Files**

- New: `app/src/main/kotlin/io/github/praneetx/motionfit/logic/StepCountEngine.kt`
- New: `app/src/main/kotlin/io/github/praneetx/motionfit/logic/CadenceTracker.kt`
- New: `app/src/main/kotlin/io/github/praneetx/motionfit/logic/RunningSessionClock.kt`
- New: `app/src/main/kotlin/io/github/praneetx/motionfit/logic/WorkoutDuration.kt`
- New tests under
  `app/src/test/java/io/github/praneetx/motionfit/logic/`:
  - `StepCountEngineTest.java`
  - `CadenceTrackerTest.java`
  - `RunningSessionClockTest.java`
  - `WorkoutDurationTest.java`

**Test-first**

Write the tests before the classes. The initial run is expected to fail to
compile because the classes do not exist; capture that output.

Cover:

- first reading starts at zero and stores a checkpoint;
- normal delta; duplicate reading; decreasing/reboot reading;
- manual calibration and future delta;
- manual-before-sensor; auto-off/no-sensor;
- midnight rollover with and without an in-memory checkpoint;
- never negative; large gap; source transitions;
- stale checkpoint/conflict outcome;
- cadence: insufficient data, steady data, 30-second decay, pause reset,
  duplicate readings;
- clock: start/pause/resume/stop, repeated transitions and recovery snapshot;
- duration: exact seconds, one-second minimum, rounded-up minutes.

**Implementation**

- Immutable state/result classes with no Android imports.
- The step engine consumes the current day state and a raw reading and returns
  the next state plus a persisted delta. It does not write SQLite.
- The clock consumes an injectable `nowMs` value; production passes
  `SystemClock.elapsedRealtime()` from the service, tests pass fixed values.
- Keep formatting out of these classes except a pure duration-seconds helper.

**Acceptance**

- All new tests pass.
- All 34 existing unit tests still pass.
- No Android imports in the new logic classes.
- `assembleDebug` succeeds.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --tests "io.github.praneetx.motionfit.logic.*" --rerun-tasks
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
```

**Commit:** `feat: add v2 step and running logic`

---

### Task 2 — Database v2 schema and migration

**Goal:** Add the V2 schema and prove a real version-1 database upgrades without
data loss.

**Files**

- `app/src/main/java/io/github/praneetx/motionfit/database/DatabaseContract.java`
- `app/src/main/java/io/github/praneetx/motionfit/database/FitnessDatabaseHelper.java`
- `app/src/androidTest/java/io/github/praneetx/motionfit/database/DatabaseMigrationTest.java` (new)
- `app/src/androidTest/java/io/github/praneetx/motionfit/DatabaseInstrumentedTest.java` (adjust only if the schema change requires it)

**Test-first**

Add a migration test that:

1. creates a **genuine version-1** database file with all four V1 tables;
2. inserts one row into `daily_steps`, `workouts`, `fitness_goals` and
   `app_settings`;
3. opens it with the V2 helper;
4. asserts version 2, all old values preserved, `last_raw_sensor`,
   `duration_seconds`, `step_count` present and NULL for old rows,
   `running_sessions` present, and the new settings defaults;
5. checks a fresh install has the same V2 schema;
6. reopens and verifies no migration/data loss;
7. checks `running_sessions` rejects a second row with `id = 1`.

To make this testable without touching the singleton production database, add
a package-private test constructor on `FitnessDatabaseHelper` that accepts a
database name and target version. Production uses the singleton and
`DATABASE_VERSION`.

Run the test before implementation. Capture the failure (version 1 or missing
columns/table).

**Implementation**

- `DATABASE_VERSION = 2`.
- Add `daily_steps.last_raw_sensor INTEGER`.
- Add `workouts.duration_seconds INTEGER` and `workouts.step_count INTEGER`.
- Add `running_sessions` exactly as specified in `docs/v2-spec.md` §8.4.
- Add settings keys `auto_steps_enabled` and `onboarding_completed`, default
  `false`.
- `onCreate` creates the full V2 schema and seeds defaults.
- `onUpgrade(1, 2)` performs only additive `ALTER TABLE` statements plus
  `CREATE TABLE IF NOT EXISTS`, then ensures default goal/settings rows. It
  must not call `onCreate` as a shortcut.
- `onDowngrade` remains destructive and drops `running_sessions` too.
- Do not add a second baseline column; `last_raw_sensor` is the checkpoint.

**Acceptance**

- Migration test passes on the emulator.
- Existing instrumented database tests pass.
- A fresh install and an upgraded database have the same V2 schema.
- `assembleDebugAndroidTest` succeeds.
- If the emulator test cannot run, the task is blocked and reported; it is not
  marked passed.

**Verification**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: migrate database to version 2`

---

### Task 3 — Daily step checkpoint repository

**Goal:** Make `daily_steps` the durable, atomic home of the step contract.

**Files**

- `app/src/main/java/io/github/praneetx/motionfit/models/DailySteps.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/StepsRepository.java`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/repository/StepsRepositoryTest.java`

**Test-first**

Add repository tests before changing the repository:

- first sensor reading inserts `steps = 0`, a checkpoint and `source = sensor`;
- a later reading adds exactly the delta;
- a duplicate/zero reading changes nothing;
- a decreasing reading preserves the total and moves the checkpoint;
- manual calibration sets the absolute total and current checkpoint;
- a manual entry with auto off clears the checkpoint and sets `manual`;
- a manual entry with no raw value leaves the checkpoint NULL; the next
  reading preserves the total and becomes `sensor_adjusted`;
- a stale expected checkpoint is detected and reloaded, never overwritten;
- one row per date still holds under repeated saves.

Run the tests and capture the failures.

**Implementation**

- Add `Long lastRawSensor` to `DailySteps` and the
  `SOURCE_SENSOR_ADJUSTED` constant.
- Read/write the new column in `StepsRepository`.
- Add explicit operations:
  - `getForDate(date)`;
  - `saveManualTotal(date, total)`;
  - `applySensorReading(date, raw)` or an equivalent atomic operation;
  - `calibrate(date, total, rawNow)`.
- Use the pure `StepCountEngine` for the rule decision, and a transaction for
  the write. A conditional `UPDATE ... WHERE last_raw_sensor = ?` or equivalent
  stale-check is required.
- Preserve the existing public save method for compatibility, but make a manual
  save clear the sensor checkpoint.
- Do not add the service or UI yet.

**Acceptance**

- All new repository tests pass.
- Existing database tests pass.
- The repository never writes a negative total or a decreasing automatic total.
- `assembleDebug` and `assembleDebugAndroidTest` succeed.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: add daily step checkpoints`

---

### Task 4 — Workout fields, settings, data reset

**Goal:** Persist exact Running duration/steps and the two new V2 settings, and
make clear-all safe for the new table.

**Files**

- `app/src/main/java/io/github/praneetx/motionfit/models/Workout.java`
- `app/src/main/java/io/github/praneetx/motionfit/models/AppSettings.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/WorkoutRepository.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/SettingsRepository.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/DataResetRepository.java`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/repository/WorkoutRepositoryTest.java`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/repository/SettingsRepositoryTest.java`
- Extend `DatabaseInstrumentedTest.java` for clear-all.

**Test-first**

Add failing tests for:

- a workout with `durationSeconds` and `stepCount` round-trips both values;
- a manual workout leaves both NULL;
- update preserves the new fields when the model carries them;
- negative values are rejected;
- settings default `auto_steps_enabled = false` and
  `onboarding_completed = false`;
- saving each new setting round-trips;
- clear-all deletes `running_sessions`, `daily_steps` and `workouts`, resets
  goals/reminders and resets the new settings.

**Implementation**

- Add nullable `Integer`/`Long` fields (or clear sentinel accessors) for exact
  seconds and steps to `Workout`; preserve the old constructor and behaviour.
- Map the new workout columns and add validation.
- Add `autoStepsEnabled` and `onboardingCompleted` to `AppSettings`, plus
  repository getters/setters.
- Add constants/defaults in `DatabaseContract` if not already added in Task 2.
- Extend `DataResetRepository` with `running_sessions` deletion and new setting
  resets, all in one transaction.
- No Activity UI changes yet.

**Acceptance**

- Workout/settings/reset tests pass.
- Existing CRUD, goal and summary tests pass.
- A new manual workout is still stored with NULL seconds/steps.
- `assembleDebugAndroidTest` succeeds.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: extend workouts and settings`

---

### Task 5 — Running session persistence and finish transaction

**Goal:** Persist one in-progress session and save exactly one workout on finish,
including the rollback and duplicate-finish paths.

**Files**

- New: `app/src/main/java/io/github/praneetx/motionfit/models/RunningSession.java`
- New: `app/src/main/java/io/github/praneetx/motionfit/repositories/RunningSessionRepository.java`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/repository/RunningSessionRepositoryTest.java`

**Test-first**

Add failing tests for:

- start inserts the singleton row (`id = 1`) and one active snapshot;
- a second start does not create a second row;
- pause/resume update `status`, `accumulated_ms`, `last_resumed_ms` and the
  duration snapshot;
- an interrupted/reboot state can be stored and read;
- finish inserts one Running workout with date, exact seconds, rounded-up
  minutes, steps/NULL and deletes the session;
- finish twice inserts only one workout;
- a failing workout insert rolls back and leaves the session;
- discard deletes the session without a workout;
- step snapshots are non-negative and survive reload.

**Implementation**

- Model the row exactly as the spec defines, including `duration_ms`,
  `started_at`, `started_elapsed_ms`, `last_resumed_elapsed_ms`, step snapshots,
  `boot_count` and `interrupted`.
- Implement start/get/pause/resume/update/finish/discard operations.
- Finish must re-read the row inside one transaction, reject an absent row,
  insert the workout, delete the session and commit. Any failure rolls back.
- Use the pure clock for calculations; the repository only persists values.
- Do not add the service or screen yet.

**Acceptance**

- All session tests pass.
- Finish produces exactly one workout; duplicate finish produces zero extra.
- Discard produces no workout.
- `assembleDebugAndroidTest` succeeds.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: persist running sessions`

---

### Task 6 — Global accessibility foundation (A1)

**Goal:** Fix the shared causes of inaccessible UI before screen-level work:
contrast, touch targets, text scaling, keyboard behaviour, custom-view
semantics and lint source errors.

**Files**

- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values/themes.xml` and/or a correct `values-v27` override
- `app/src/main/java/io/github/praneetx/motionfit/utils/UiAnimations.java`
- `app/src/main/java/io/github/praneetx/motionfit/views/BarChartView.java`
- `app/src/main/java/io/github/praneetx/motionfit/views/StepsRingView.java`
- `app/src/main/AndroidManifest.xml` (`adjustResize` for input screens)
- Layouts touched by `android:tint` cleanup
- New: `app/src/main/java/io/github/praneetx/motionfit/utils/LoadResult.java`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/UiAccessibilityTest.java`

**Test-first**

Add instrumented assertions or explicit checks that currently fail:

- known 40dp actions/segments do not meet 48dp;
- the caption token is below 4.5:1 on sage/white (manual contrast calculation);
- custom chart/ring views have no useful accessibility summary;
- input screens lack `adjustResize`.

Run the deterministic target-size/accessibility assertions first and capture the
failures. Manual TalkBack/font rows stay `NOT RUN`.

**Implementation**

- Darken `mute` to `#5F615F` (or a tested equivalent) so normal text passes on
  sage and white.
- Change `icon_button_small` and `Widget.Wise.Segment` to 48dp minimums; update
  affected layouts.
- Add label autosize/overflow rules for nav labels; do not yet change the Steps
  destination (that is Task 14).
- Replace `android:tint` with `app:tint` in the 19 lint hits; guard/move the
  API-27 theme attribute; do not add a lint baseline.
- Add `adjustResize` to Goals and Workout editor.
- Give `BarChartView` a textual accessibility summary setter and mark
  `StepsRingView` decorative or give it one combined summary.
- Remove the dead `attachPressFeedback` helper (or implement `performClick`).
- Add `LoadResult<T>` (success/data/error) with a small JVM test; no repository
  behaviour changes in this task.

**Acceptance**

- Target-size assertions pass.
- `UseAppTint` and the API-27 `NewApi` lint errors are gone.
- Custom views are no longer silent to TalkBack.
- Keyboard plan is present and builds.
- Manual 1.3x/TalkBack rows remain `NOT RUN`.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat lintDebug
```

**Commit:** `a11y: fix global contrast, targets and semantics`

---

### Task 7 — Today and Steps layout resilience (A2)

**Goal:** Make the two main step surfaces readable, reachable and honest at
small width and large font, without changing the step engine.

**Files**

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_steps.xml`
- `app/src/main/res/layout/dialog_steps.xml`
- `app/src/main/java/io/github/praneetx/motionfit/activities/MainActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/StepsActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/DashboardRepository.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/StepsRepository.java`
- `app/src/main/res/values/strings.xml`
- New/extended Espresso test for the Today-to-Steps edit path.

**Test-first**

Add an Espresso test that expects a visible `btnEditSteps` on Today and opens
Steps detail. It fails before the button/path exists.

Add a small instrumented check that the selected step value and hero total use
autosize/text-size rules rather than fixed 64sp clipping.

**Implementation**

- Add a visible, 48dp **Edit steps** affordance to the Today hero; keep the
  existing hero/value IDs.
- Wire it to `StepsActivity` with the selected date.
- Fix date/caption contrast, overflow and wrapping; autosize the hero/selected
  values.
- Add inline empty/error states for Today; distinguish a load failure from zero
  data using `LoadResult` and offer Retry.
- On Steps: autosize the selected value, improve the manual-entry helper text
  (calibration wording), and use the accessible caption/source labels.
- Keep the current root-nav presence of Steps until Task 14; only the content
  and accessibility change here.
- Preserve existing IDs and public methods.

**Acceptance**

- Today-to-Steps edit path works.
- The hero and selected value do not clip at 360dp/1.3x.
- Empty and error states are distinguishable.
- Manual TalkBack/font rows remain `NOT RUN`.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `a11y: make today and steps resilient`

---

### Task 8 — Workouts and Progress resilience (A3)

**Goal:** Fix row targets, formatting, chart semantics and the false-empty
Progress state.

**Files**

- `app/src/main/res/layout/activity_workout_list.xml`
- `app/src/main/res/layout/item_workout.xml`
- `app/src/main/res/layout/activity_summary.xml`
- `app/src/main/java/io/github/praneetx/motionfit/activities/WorkoutListActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/SummaryActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/adapters/WorkoutAdapter.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/SummaryRepository.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/WorkoutRepository.java`
- `app/src/main/java/io/github/praneetx/motionfit/views/BarChartView.java` (if not complete in Task 6)
- `app/src/main/res/values/strings.xml`
- Espresso/instrumented checks for empty state and 48dp row actions.

**Test-first**

Add checks that currently fail:

- workout row actions are 40dp;
- Progress shows zero cards plus a bottom empty line instead of a real empty
  state;
- a repository read failure is indistinguishable from empty;
- chart has no accessible summary.

**Implementation**

- 48dp row actions with content descriptions; keep row-click details.
- Replace concatenated row metadata with string resources/placeholders.
- Add `LoadResult`-based error/retry for Workouts and Progress; preserve the old
  repository methods for existing tests.
- Make Progress's empty state a real top-level card and hide/soften zero-only
  charts.
- Autosize `totalSteps`, constrain `periodLabel`, keep arrows 48dp, make
  segments 48dp.
- Add a textual chart summary for TalkBack.
- Keep day/week/month selection, arrows, best-day and active-day behaviour.
- Remove the hidden 40dp root back button.

**Acceptance**

- No required text clips at 360dp/1.3x.
- Empty, error and loaded states are distinct.
- Chart has a meaningful summary.
- Manual TalkBack/visual rows remain `NOT RUN`.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `a11y: make workouts and progress resilient`

---

### Task 9 — Settings, Goals and workout editor resilience (A4)

**Goal:** Finish the A-phase accessibility work on settings and form screens.

**Files**

- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/layout/activity_goals.xml`
- `app/src/main/res/layout/activity_workout_edit.xml`
- `app/src/main/java/io/github/praneetx/motionfit/activities/SettingsActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/GoalsActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/WorkoutEditActivity.java`
- `app/src/main/AndroidManifest.xml` (input `adjustResize`)
- `app/src/main/res/values/strings.xml`

**Test-first**

Add/perform the failing checks:

- pinned Save can be covered by the keyboard on Goals/Workout edit;
- goal labels are not associated with their inputs;
- settings reminder row can crowd at large font;
- the root back buttons are hidden 40dp dead controls.

Keep deterministic Espresso checks where practical; keyboard/TalkBack rows are
manual and start `NOT RUN`.

**Implementation**

- Add `adjustResize` and verify Save above the keyboard.
- Add `labelFor`/content descriptions for goal and workout inputs; keep
  steppers 48dp.
- Make the reminder row wrap/stack at large font; use the accessible caption
  token.
- Remove dead root back buttons; keep the legitimate 48dp back on the workout
  editor.
- Replace the hardcoded settings version with the build version or a single
  version resource.
- Do not add the automatic-tracking card yet (Task 10).
- Do not add Running exact-duration display yet (Task 15).

**Acceptance**

- Keyboard does not cover Save.
- Inputs are labelled for TalkBack.
- Settings/goals/editor build and pass existing tests.
- Manual large-font/TalkBack rows remain `NOT RUN`.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

**Commit:** `a11y: fix settings and form accessibility`

---

### Task 10 — Automatic step service, status and Settings controls (B starts)

**Goal:** Replace the foreground-only sensor experiment with the approved
automatic tracking service and make its effective state visible and
controllable.

**Files**

- New: `app/src/main/java/io/github/praneetx/motionfit/logic/TrackingModeResolver.kt`
- New: `app/src/main/java/io/github/praneetx/motionfit/services/ActivityTrackingService.java`
- New: `app/src/main/java/io/github/praneetx/motionfit/services/StepCounterSource.java`
- New: `app/src/main/java/io/github/praneetx/motionfit/MotionfitApplication.java`
- `app/src/main/java/io/github/praneetx/motionfit/services/NotificationHelper.java`
- `app/src/main/java/io/github/praneetx/motionfit/services/BootReceiver.java`
- `app/src/main/java/io/github/praneetx/motionfit/services/StepCounterSensorHelper.java` (remove or leave as a thin deprecated wrapper; no duplicate listener)
- `app/src/main/java/io/github/praneetx/motionfit/activities/StepsActivity.java`
- `app/src/main/res/layout/activity_steps.xml`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/java/io/github/praneetx/motionfit/activities/SettingsActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- New tests: `TrackingModeResolverTest.java`, `TrackingServiceBindingTest.java`

**Test-first**

- Add pure resolver tests for `OFF`, `ACTIVE`, `APP_OPEN_ONLY`, `UNAVAILABLE`
  based on settings, sensor availability, activity permission, notification
  permission and FGS start result.
- Add a raw `bindService`/`ServiceConnection` instrumented test (no new test
  dependency) that starts the service in a test-visible mode, triggers a
  persist/reload path, and asserts no crash and a valid status. Sensor event
  injection remains manual because the emulator may lack a real counter.
- Run the resolver test first and capture the failure; service manual rows are
  `NOT RUN`.

**Implementation**

- One `ActivityTrackingService`:
  - registers one `TYPE_STEP_COUNTER` listener with `SENSOR_DELAY_NORMAL`;
  - applies readings through `StepsRepository` using the checkpoint contract;
  - throttles persistence/broadcast/notification;
  - posts one persistent low-importance notification when foreground is legal;
  - supports app-visible-only fallback when foreground is not legal or when
    daily notifications are blocked;
  - stops app-visible-only work when no Motionfit screen is visible;
  - catches FGS rejection and reports fallback status without crashing.
- `MotionfitApplication` tracks visible activities and tells the service when
  the app enters/leaves the foreground. Register it in the manifest.
- `StepCounterSource` owns registration, availability and epoch/raw delivery;
  remove the old in-memory session-baseline helper from the production path.
- `NotificationHelper` gets a separate low-importance tracking channel and
  private notification builds; no sound/vibration.
- Manifest: add `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH`, declare
  the service as non-exported with `foregroundServiceType="health"`.
- `BootReceiver` restores reminders, marks any in-progress Running session
  interrupted, and makes a best-effort start of automatic tracking when it is
  enabled and legal.
- Settings gets the automatic-tracking card: switch, effective status,
  permission actions, battery information link and truthful fallback wording.
- Steps detail removes the experimental one-shot sensor card and shows the
  effective tracking status. Manual entry behaves as the calibration described
  in the spec, routed through the service when it is active.
- Keep existing manual step entry, history and reminder behaviour.
- Do not add onboarding yet (Task 11).

**Acceptance**

- Resolver tests pass.
- With auto off, no FGS and no sensor writes.
- With auto on + permissions, a persistent notification is shown and daily
  values update.
- With notifications denied, no always-on hidden FGS; app-open-only status is
  visible.
- With sensor/activity permission missing, manual entry works and status is
  clear.
- Boot and FGS rejection do not crash.
- Old one-shot sensor card/button is gone.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

Manual sensor/notification/boot rows are reported individually and stay
`NOT RUN` until executed on a suitable device.

**Commit:** `feat: add automatic step tracking service`

---

### Task 11 — First-run onboarding and permission education (B)

**Goal:** Introduce the approved short, skippable onboarding without blocking
manual use or overwriting data.

**Files**

- New: `app/src/main/java/io/github/praneetx/motionfit/activities/OnboardingActivity.java`
- New: `app/src/main/res/layout/activity_onboarding.xml`
- `app/src/main/java/io/github/praneetx/motionfit/activities/MainActivity.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/drawable/` (only if a new decorative icon is needed)
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/OnboardingTest.java`
- Settings permission-education refinements if Task 10 left gaps.

**Test-first**

Add an Espresso test that:

- clears the onboarding setting;
- launches MainActivity;
- expects the onboarding explanation/goals first;
- taps Skip;
- asserts `onboarding_completed = true` and Main is reachable.

Add a second check that an existing V1-style goal value is shown rather than
reset. Permission dialogs are manual and stay `NOT RUN`.

**Implementation**

- `MainActivity` checks `onboarding_completed`; if false, opens
  `OnboardingActivity` and finishes/reroutes cleanly.
- Onboarding uses existing goal controls/validation; it does not reset data.
- Automatic counting copy says counting starts when enabled and manual totals
  can include earlier steps.
- Request `ACTIVITY_RECOGNITION`/`POST_NOTIFICATIONS` only after a user action
  that follows the explanation. Always offer Skip/Not now.
- Finish/Skip sets `onboarding_completed = true`; manual entry/workouts remain
  available.
- Ensure TalkBack, 1.3x font and 360dp work; no dark patterns, no forced
  permission loop.
- Existing users see it once after upgrade; it is skippable.

**Acceptance**

- First-run test passes.
- Skip reaches the app and persists completion.
- Existing data is not overwritten.
- No permission dialog appears before the explanation (manual check).
- Onboarding is accessible at 360dp/1.3x and with TalkBack (manual).

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: add first-run onboarding`

---

### Task 12 — Running service controls, binder and notification

**Goal:** Add the service-owned Running state machine, binder and notification
actions before the screen exists.

**Files**

- New: `app/src/main/java/io/github/praneetx/motionfit/logic/RunningSessionController.kt` (if not fully covered by Task 1)
- `app/src/main/java/io/github/praneetx/motionfit/services/ActivityTrackingService.java`
- `app/src/main/java/io/github/praneetx/motionfit/services/NotificationHelper.java`
- `app/src/main/java/io/github/praneetx/motionfit/repositories/RunningSessionRepository.java`
- `app/src/main/AndroidManifest.xml` (service already declared)
- `app/src/main/res/values/strings.xml`
- New: `RunningSessionControllerTest.java`
- New: `RunningServiceBindingTest.java`

**Test-first**

- Add pure controller tests for start/pause/resume/finish/discard validation,
  repeated calls, interrupted state and duration snapshot.
- Add an instrumented raw-bind service test that starts a session through the
  binder, pauses/resumes, finishes, and asserts exactly one workout and no
  session row.
- Run the controller test first and capture the failure.
- Notification visuals and actual sensor timing remain manual/`NOT RUN`.

**Implementation**

- Extend `ActivityTrackingService` with a local binder and commands:
  start run, pause, resume, update snapshot, request finish, finish, discard.
- Finish uses `RunningSessionRepository.finish` transaction only.
- Broadcast/binder callbacks deliver a `RunningSnapshot` (status, elapsed ms,
  steps, cadence, sensor/status flags).
- Notification content switches between daily counting and Running; Pause and
  Resume are service actions. Stop is omitted here and wired to the screen
  confirmation in Task 13; do not add a dead Stop action.
- On reboot/elapsed invalidation, mark interrupted and expose the recovery
  state; do not auto-resume the timer.
- Running uses the same sensor source as daily counting but keeps its own
  `start_raw_steps` snapshot; never add running steps to the daily total twice.
- If foreground is not legal, use the app-visible fallback; persist on every
  transition.

**Acceptance**

- Controller tests pass.
- Binder service test shows exactly one workout after finish and none after
  discard.
- Pause/resume duration is correct.
- Notification mode changes are manual and reported.
- No running UI exists yet; the screen is Task 13.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: add running session service`

---

### Task 13 — Running screen

**Goal:** Implement the full-screen Running UI against the tested service,
including recovery and stop confirmation.

**Files**

- New: `app/src/main/java/io/github/praneetx/motionfit/activities/RunningActivity.java`
- New: `app/src/main/res/layout/activity_running.xml`
- New: `app/src/main/res/drawable/ic_run.xml` (and any small status drawable)
- `app/src/main/java/io/github/praneetx/motionfit/services/ActivityTrackingService.java`
- `app/src/main/java/io/github/praneetx/motionfit/services/NotificationHelper.java`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- New: `app/src/androidTest/java/io/github/praneetx/motionfit/RunningScreenTest.java`

**Test-first**

Add an Espresso test that launches the Ready state and verifies the timer,
Start action, steps/cadence placeholders, no distance/pace text, and the
permission/status card. Run it first and capture the missing-screen failure.

Add a test for the stop-confirmation dialog text/build if it can be driven
without a real sensor; sensor/background/recovery paths remain manual.

**Implementation**

- Implement Ready, Running, Paused, Finishing, Summary, Recovery and Error
  states.
- Bind to the service in `onStart`, unbind in `onStop`; reload from the
  repository on resume/recreation.
- Timer via `elapsedRealtime`; UI tick about once per second while visible.
- Steps/cadence display; “—” until enough data; pause resets cadence.
- Start/Pause/Resume/Stop controls; Stop always confirms with duration/steps.
- Recovery: Resume, Finish and save, Discard; downtime excluded and labelled.
- Notification Tap and Stop open `RunningActivity`; Stop opens the confirmation;
  Pause/Resume are service actions.
- Keep screen on while active and visible; clear on pause/finish.
- No bottom nav; back leaves the session running/fallback and the UI says so.
- 48dp targets, autosize timer, 360dp/1.3x, TalkBack labels, reduced motion.

**Acceptance**

- Ready-screen Espresso test passes.
- Manual timer/pause/resume/background/recovery/notification rows are executed
  where possible; physical-only rows stay `NOT RUN`.
- Stop creates exactly one workout; discard none.
- No distance/pace/calorie/location content exists.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: add running screen`

---

### Task 14 — Run tab, Steps detail and Today/Progress entry points

**Goal:** Complete the approved navigation change and make Steps a detail
screen, not a root destination.

**Files**

- `app/src/main/res/layout/view_floating_nav.xml`
- `app/src/main/java/io/github/praneetx/motionfit/views/FloatingNavView.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/MainActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/SummaryActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/StepsActivity.java`
- `app/src/main/res/layout/activity_steps.xml`
- `app/src/main/res/layout/activity_summary.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- New/extended Espresso navigation test.

**Test-first**

Add an Espresso test that currently fails because Run is absent and Steps is a
tab:

- nav has Today, Workouts, Run, Progress, Settings;
- no Steps nav item;
- tapping Run opens RunningActivity;
- opening Steps from Today shows a visible back action and no floating nav;
- Progress can reach Steps detail.

Run it before implementation and capture the failure.

**Implementation**

- Replace the Steps tab with Run in the nav layout; add a Run icon and
  destination.
- `FloatingNavView`: add `DEST_RUN`, remove Steps routing, keep bind/active
  destination/reorder behavior.
- Give every nav item unique IDs and `app:tint`; fix the duplicate-ID lint
  errors.
- Update root Activities' active destination.
- Remove the nav from Steps detail; use a visible 48dp back; keep `EXTRA_DATE`
  and full history.
- Today's Edit steps path opens the detail.
- Progress gets a step-history entry point (the full card is Task 15).
- Keep 104dp nav padding on roots and safe insets.
- Verify five labels fit at 360dp/1.3x.

**Acceptance**

- Navigation test passes.
- Steps detail has no bottom nav and has a working back path.
- Run opens the Running screen.
- Five labels do not clip.
- Lint duplicate IDs gone.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `feat: add run tab and steps detail`

---

### Task 15 — Visual polish and step history (C)

**Goal:** Add the approved visual/UX polish without reopening the tested data or
service contracts.

**Files**

- `app/src/main/res/layout/activity_summary.xml`
- `app/src/main/res/layout/item_step_history.xml`
- New: `app/src/main/res/layout/item_progress_step_history.xml` if a compact row
  is needed
- `app/src/main/java/io/github/praneetx/motionfit/adapters/StepHistoryAdapter.java`
- `app/src/main/java/io/github/praneetx/motionfit/adapters/WorkoutAdapter.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/SummaryActivity.java`
- `app/src/main/java/io/github/praneetx/motionfit/activities/RunningActivity.java`
- `app/src/main/res/layout/activity_running.xml`
- `app/src/main/res/drawable/` (empty-state/icon polish only; no external assets)
- `app/src/main/res/values/strings.xml`

**Test-first**

Add/extend deterministic adapter/instrumented checks for:

- Step source labels: Manual, Automatic, Adjusted;
- Progress shows the last seven recorded days and “View all”;
- Running workout rows show exact duration/steps when present, while manual
  workouts keep whole-minute metadata;
- empty states do not show fake zero data.

Visual/TalkBack/reduced-motion checks are manual and start `NOT RUN`.

**Implementation**

- Add the Progress “Step history” card with last seven recorded days, readable
  date/steps/source rows and a “View all” action to Steps detail.
- Add source badges/labels in Steps history; never color-only.
- Show exact Running duration and steps in Workout rows/details; preserve the
  existing manual display.
- Refine Running ready/live/paused/summary visuals using the existing Wise
  palette and type scale.
- Improve real empty states on Today, Workouts and Progress with a short
  explanation and one action.
- Add subtle transitions only when reduced motion is off; final values remain
  visible with motion off.
- Do not add new fonts, gradients, shadows, dark mode or third-party assets.

**Acceptance**

- Source/running-metadata tests pass.
- Progress history shows seven recorded days and links to full history.
- Empty states are distinguishable from errors and from zero data.
- Existing manual workout and summary behavior is unchanged.
- Visual/TalkBack/reduced-motion rows are reported honestly.

**Verification**

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

**Commit:** `polish: add step history and running visuals`

---

### Task 16 — Lint cleanup, regression and full verification

**Goal:** Prove the V2 scope is complete and leave the project buildable and
honest about what was not run.

**Files**

- Any remaining source lint hits in touched files
- `app/src/main/res/values/` / layouts / strings only where required
- No unrelated feature work

**Test-first**

This is a verification task, not a new feature. Before making final fixes:

- run the full command set and record actual output;
- list every failure as `FAIL` or `NOT RUN`;
- only fix failures that belong to V2 or were introduced by V2;
- do not chase unrelated unused-resource/overdraw warnings.

**Implementation**

- Fix remaining source lint errors (duplicate IDs, `UseAppTint`, API guard,
  string formatting, RTL symmetry, dead back buttons).
- Verify no `INTERNET`, location, `WAKE_LOCK` or new third-party dependency.
- Verify DB/fresh-install/migration tests, service binding tests, navigation
  tests and unit tests.
- Re-run the manual matrix where the emulator can exercise it. Physical sensor,
  reboot, OEM battery and real notification rows stay `NOT RUN`.
- If a regression is found, stop and create a focused fix task; do not hide it
  in this commit.

**Acceptance**

- `assembleDebug` succeeds.
- All JVM tests pass.
- Instrumented tests compile; those run on the emulator are reported.
- `lintDebug` has no source errors and no baseline was added. The
  environment-only `local.properties` escape warning is not committed; unrelated
  pre-existing unused-resource/overdraw warnings may remain and are reported.
- The manual matrix has a real result for every row that was actually run, and
  `NOT RUN` for the rest.
- No silent failure is presented as a pass.

**Verification**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --rerun-tasks
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

**Commit:** `chore: complete v2 verification`

---

### Task 17 — Version 2.0.0 and documentation

**Goal:** Publish an honest V2 documentation set and release metadata without
pushing.

**Files**

- `app/build.gradle` (`versionCode 2`, `versionName "2.0.0"`)
- `app/src/main/res/values/strings.xml` (version display if not BuildConfig)
- `README.md`
- `PRIVACY.md` (if service/notification wording needs it)
- `docs/database.md`
- `docs/architecture.md`
- `docs/testing.md`
- `docs/limitations.md`
- `docs/ui-design.md`
- `docs/v2-spec.md` (status/verification note if needed)
- `docs/v2-plan.md` (status and final results index)

**Test-first**

No behavior test is added. The documentation task is accepted only if it quotes
the actual final command output and does not claim physical tests that were not
run.

**Implementation**

- Bump version.
- Make Settings show the build version (use `BuildConfig.VERSION_NAME`; enable
  `buildConfig` without a new dependency if the build does not generate it, or
  use one generated version resource).
- Update schema/architecture/testing/limitations/UI docs for V2.
- Update README features/status and the known limitations:
  - sensor accuracy;
  - reboot/day-boundary gaps;
  - FGS/OEM restrictions;
  - notification-denied app-open-only mode;
  - activity-denied timer-only mode;
  - no guaranteed background tracking.
- Include the final build/test/lint results and the manual matrix statuses.
- Keep the approved spec and plan in the docs; mark them approved/completed as
  appropriate.
- Do not push. Do not modify `main`.

**Acceptance**

- Version is `2.0.0` / code 2.
- Docs match the implemented schema and behavior.
- Final commands are quoted with real results.
- Physical-device rows remain `NOT RUN` unless the user has run them.
- Working tree is clean after the final commit.

**Verification**

```powershell
.\gradlew.bat assembleDebug --rerun-tasks
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat lintDebug
git status --short --branch
```

**Commit:** `docs: publish v2 documentation`

---

## 4. Test and reporting strategy

### 4.1 Automated layers

| Layer | What it proves | Where |
|---|---|---|
| JVM unit tests | Step engine, cadence, clock, duration, mode resolver, adapters' pure formatting | `app/src/test` |
| Instrumented SQLite tests | Schema/migration, step checkpoint, workout fields, settings, running transaction, clear-all | `app/src/androidTest` |
| Instrumented UI/service tests | Navigation, onboarding, ready Running screen, raw service binding | `app/src/androidTest` |
| Build/lint | Compilation and source lint errors | Gradle |
| Manual/device | Sensor, permissions, notifications, FGS, reboot, OEM, TalkBack, 1.3x, keyboard | Emulator/physical |

### 4.2 Non-negotiables

- A failed or `NOT RUN` check is never described as passing.
- Physical-device rows stay `NOT RUN` until the user runs them.
- Instrumented tests on `emulator-5554` clear that emulator's app data only.
- No test-only behavior is shipped in the production path.
- No new test dependency is added without asking first.
- Each task ends with the exact report format and a wait for `continue`.

### 4.3 Baseline vs final

The V1 baseline is:

- `assembleDebug`: PASS.
- `testDebugUnitTest`: 34 tests, PASS.
- `assembleDebugAndroidTest`: PASS.
- `connectedDebugAndroidTest`: NOT RUN.
- `lintDebug`: 29 errors / 114 warnings.
- Physical device: NOT RUN.

V2 is not complete until the final table shows every V2 acceptance check as
`PASS`, `FAIL` with a fix, or an explicitly accepted `NOT RUN` (manual/physical).

---

## 5. Risks, holds and rollback

| Risk / hold point | Action |
|---|---|
| Migration test fails or old rows change | Stop Task 2. Do not proceed. Fix migration; never ship a destructive fallback. |
| New repository test finds a double count | Stop. Correct the contract before the service/UI uses it. |
| FGS cannot start on emulator/API 34 | Report the exact error; do not claim tracking. Continue only with the explicit app-visible fallback, and keep physical rows NOT RUN. |
| Notification permission behaviour differs by API | Test API 33+/34+ separately; document per-version behaviour. |
| Sensor unavailable on emulator | Use timer/UI/manual tests; sensor rows stay NOT RUN. Do not fake steps. |
| UI automated tests are flaky | Keep deterministic structural tests; move visual/TalkBack rows to manual; do not add sleeps that hide races. |
| Running service/UI split leaves an unreachable screen temporarily | Acceptable between Tasks 12 and 13; no fake nav entry, no dead notification Stop action. |
| Scope creep | Reject new dependencies/features; the spec's non-goals are binding. |
| Bad task | Revert only that task's commit (or fix forward with a new commit); do not reset user work or the stash. |

No push occurs during any task.

---

## 6. Definition of done

V2 is done only when:

- All AC-STEP, AC-RUN, AC-UI, AC-DB, AC-OFFLINE and AC-REG acceptance criteria
  have a real result.
- A version-1 database upgrades with data preserved and is tested.
- The five-tab navigation, Steps detail, Running service/screen, onboarding,
  automatic tracking fallback, step history and accessibility work are present.
- No location/GPS/distance/pace/calorie fields or permissions exist.
- No internet permission, analytics, cloud or new third-party dependency exists.
- `assembleDebug`, JVM tests, instrumented tests and lint are reported with real
  output.
- Physical-device rows are explicitly `NOT RUN` until the user runs them.
- README/docs/schema/version match the code.
- The working tree is clean on `v2-dev`; `main` is untouched; nothing is pushed.

---

## 7. Approval checklist

Please approve or amend:

1. The 17-task order and dependencies in section 2.
2. A → B → C UI order: global accessibility, then onboarding, then visual
   polish; Running UI meets the A standards.
3. Splitting Running into service (Task 12) and screen (Task 13), with the
   screen unreachable until navigation in Task 14.
4. Running instrumented tests on `emulator-5554`, which clears that emulator's
   app data only (no physical data).
5. Manual and physical rows remain `NOT RUN`; no automated claim replaces them.
6. Test-first applies to logic/DB/service/UI where automation is possible, with
   explicit manual checks for TalkBack, font scale, permissions, sensors and
   OEM behavior.
7. No new dependencies; no new test dependency without asking.
8. The approved spec/plan stay uncommitted until Task 17 unless you prefer an
   early docs commit.
9. Version `2.0.0` / versionCode 2 and the final docs update.
10. One atomic local commit per task, status report, then wait for `continue`;
    no push.

This checklist was approved. All 17 tasks are complete on `v2-dev`. The Task 17
commit is local only: `main` is untouched and nothing has been pushed or published.

---

## 8. Final results index (2026-10-27)

**Release metadata:** `versionCode 2`, `versionName "2.0.0"`; Settings displays
`Version 2.0.0`.

| Check | Command / method | Actual final result | Status |
|---|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug --rerun-tasks` | `BUILD SUCCESSFUL in 52s`; APK produced | **PASS** |
| JVM tests | `.\gradlew.bat testDebugUnitTest --rerun-tasks` | `BUILD SUCCESSFUL in 19s`; **95 tests, 0 failures, 0 errors** | **PASS** |
| Instrumented test build | `.\gradlew.bat assembleDebugAndroidTest` | `BUILD SUCCESSFUL in 8s` | **PASS** |
| Connected tests | `.\gradlew.bat connectedDebugAndroidTest` | `BUILD SUCCESSFUL in 5m 50s`; **104 tests, 0 failures, 0 errors** on API 30 emulator | **PASS** |
| Lint | `.\gradlew.bat lintDebug` | `BUILD SUCCESSFUL in 41s`; **0 errors, 105 warnings**; no baseline | **PASS** |
| Version / APK metadata | inspect debug APK | `versionCode='2' versionName='2.0.0'` | **PASS** |
| Offline permissions | inspect merged manifest/APK | no `INTERNET`, location or `WAKE_LOCK`; only notifications, activity recognition, boot-completed, foreground-service and health foreground-service permissions | **PASS** |
| No new third-party dependency | diff from `v0.1.0` baseline | dependency list unchanged; only `buildConfig true` and version fields added | **PASS** |
| Genuine V1 database migration | `DatabaseMigrationTest` | version 1 → 2, old rows preserved, new columns NULL, reopen passes | **PASS** |
| Manual 360dp/1.3× layout | emulator inspection | onboarding, five-tab Today, Settings, Steps detail, Progress history, Running ready/live/stop/summary all passed | **PASS** |
| Running timer-only | sensorless API 30 emulator | timer worked; steps/cadence showed `—`; summary saved steps unavailable | **PASS** |
| Physical sensor accuracy | physical device | not performed | **NOT RUN** |
| Real V1→V2 APK upgrade | installed APK upgrade | not performed; database migration test only | **NOT RUN** |
| TalkBack / reduced motion / RTL | device audit | not performed | **NOT RUN** |
| API 33+ notification permission and actions | device audit | not performed; emulator is API 30 | **NOT RUN** |
| FGS background/screen-off/process death | device audit | not performed manually | **NOT RUN** |
| Reboot, OEM battery and force-stop | device audit | not performed | **NOT RUN** |
| Tablet / landscape / keyboard | device audit | not performed | **NOT RUN** |

The detailed commands, test-class counts, manual matrix and report paths are in
[testing.md](testing.md). No automated result is used to claim a physical-device,
TalkBack, notification, process-death, reboot or OEM check that was not actually run.
