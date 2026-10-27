# Testing

This file records **what was actually executed** and what happened. Status values are
`PASS`, `FAIL` or `NOT RUN`.

Environment used for the final V2 verification (2026-10-27):

* Windows 10, JDK 25 (Android Studio JBR), Gradle 9.6.0, AGP 9.4.0, Android SDK
  platform 37.
* Emulator `emulator-5554`: API 30, 1080×1920 at 480 dpi (360×640 dp), no
  step-counter sensor. Automated tests ran at font scale `1.0`; the manual layout
  checks below ran at `1.3` unless stated otherwise.
* Instrumented tests clear only this emulator's app data.

## Build, packaging and policy checks

| Test | Command / check | Expected | Actual | Status |
|---|---|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug --rerun-tasks` | APK produced | `BUILD SUCCESSFUL in 52s`; APK produced | **PASS** |
| JVM tests | `.\gradlew.bat testDebugUnitTest --rerun-tasks` | all pass | `BUILD SUCCESSFUL in 19s`; 95 tests, 0 failures, 0 errors | **PASS** |
| Instrumented test build | `.\gradlew.bat assembleDebugAndroidTest` | test APK compiles | `BUILD SUCCESSFUL in 8s` | **PASS** |
| Connected tests | `.\gradlew.bat connectedDebugAndroidTest` | all pass | `BUILD SUCCESSFUL in 5m 50s`; 104 tests, 0 failures, 0 errors | **PASS** |
| Lint | `.\gradlew.bat lintDebug` | no source errors, no baseline | `BUILD SUCCESSFUL in 41s`; **0 errors, 105 warnings** | **PASS** |
| Version metadata | inspect debug APK | `versionCode 2`, `versionName 2.0.0` | `versionCode='2' versionName='2.0.0'` | **PASS** |
| Settings version display | emulator UI | `Version 2.0.0` | `Version 2.0.0` after scrolling Settings | **PASS** |
| No internet permission | inspect APK manifest | no `INTERNET` | `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`; no `INTERNET` | **PASS** |
| No location / wake-lock permission | inspect APK manifest | no location or `WAKE_LOCK` | none present | **PASS** |
| No new third-party dependency | diff from V1 baseline | no dependency addition | `app/build.gradle` adds only `buildConfig true` (generated `BuildConfig`) and the V2 version fields; dependency list unchanged | **PASS** |
| Fonts bundled | inspect APK resources | Inter TTFs packaged | bundled in `res/font` | **PASS** |

The 105 lint warnings are non-error warnings (unused resources, overdraw/layout
performance and related checks). No lint baseline was added, and there are no source
errors. The environment-only `local.properties` path is ignored and not committed.

## JVM unit tests

Command: `.\gradlew.bat testDebugUnitTest --rerun-tasks`

Result: **95 tests, 0 failures, 0 errors** (11 test classes).

| Test class | Tests | Status |
|---|---|---|
| `logic.ActivitySummaryCalculatorTest` | 7 | **PASS** |
| `logic.CadenceTrackerTest` | 9 | **PASS** |
| `logic.GoalProgressCalculatorTest` | 10 | **PASS** |
| `logic.RunningSessionClockTest` | 9 | **PASS** |
| `logic.RunningSessionControllerTest` | 9 | **PASS** |
| `logic.StepCountEngineTest` | 21 | **PASS** |
| `logic.TrackingModeResolverTest` | 8 | **PASS** |
| `logic.WorkoutDurationTest` | 3 | **PASS** |
| `utils.DateUtilsTest` | 8 | **PASS** |
| `utils.LoadResultTest` | 2 | **PASS** |
| `utils.ValidationUtilsTest` | 9 | **PASS** |

Coverage includes step checkpoint/delta/calibration/day-rollover rules, sensor resets,
cadence validity and reset, Running clock pause/resume/recovery, duration rounding,
tracking-mode fallback rules, date boundaries, validation, goal percentages and
aggregation.

## Instrumented tests

Command: `.\gradlew.bat connectedDebugAndroidTest`

Result: **104 tests, 0 failures, 0 errors** (20 test classes) on API 30 emulator
`emulator-5554`.

| Test class | Tests | Status |
|---|---|---|
| `DatabaseInstrumentedTest` | 7 | **PASS** |
| `database.DatabaseMigrationTest` | 4 | **PASS** |
| `NavigationTest` | 5 | **PASS** |
| `OnboardingTest` | 2 | **PASS** |
| `ProgressStepHistoryTest` | 2 | **PASS** |
| `RunningScreenTest` | 4 | **PASS** |
| `StepHistoryAdapterTest` | 1 | **PASS** |
| `TodayStepsTest` | 4 | **PASS** |
| `UiAccessibilityTest` | 13 | **PASS** |
| `WorkoutRunningMetadataTest` | 5 | **PASS** |
| `WorkoutsProgressTest` | 8 | **PASS** |
| `repositories.DashboardRepositoryTest` | 4 | **PASS** |
| `repositories.RunningSessionRepositoryTest` | 14 | **PASS** |
| `repositories.SettingsRepositoryTest` | 3 | **PASS** |
| `repositories.StepsRepositoryTest` | 13 | **PASS** |
| `repositories.SummaryRepositoryResultTest` | 2 | **PASS** |
| `repositories.WorkoutRepositoryResultTest` | 2 | **PASS** |
| `repositories.WorkoutRepositoryTest` | 5 | **PASS** |
| `services.RunningServiceBindingTest` | 5 | **PASS** |
| `services.TrackingServiceBindingTest` | 1 | **PASS** |

Key instrumented coverage:

* **Genuine V1 → V2 migration:** `DatabaseMigrationTest` creates a version-1 database,
  upgrades through the real helper, verifies version 2/schema/new NULL columns, checks
  the old rows survive, and reopens the upgraded database.
* **Step checkpoint contract:** first reading, delta, duplicate, decreasing/reset
  reading, manual calibration, stale-checkpoint retry, midnight carry and one row per
  date.
* **Running transaction:** one active singleton, second-start rejection, pause/resume,
  interrupted snapshots, exact finish insert/delete, finish-twice protection,
  rollback/retry, late sensor baseline, reboot-safe recovery and discard.
* **UI/service:** five-tab navigation, Run screen states, Steps detail entry, onboarding,
  Progress history/empty state, exact Running metadata, 360dp/1.3× layout assertions and
  service binding.
* **Data reset:** removes records and any in-progress run, resets goals and the new V2
  settings.

Run note: one earlier full-suite rerun aborted at `ProgressStepHistoryTest` with an
empty failure and an emulator focus/transition timeout. The class passed when rerun
alone, and the final full suite above completed with 104/104 passing. No test was
weakened to hide a product failure.

## Manual emulator matrix

These rows were actually run on the API 30 emulator. They are not a claim about a
physical device.

| Check | Expected | Actual | Status |
|---|---|---|---|
| Onboarding at 360dp / 1.3× | privacy/auto copy and both actions visible | title, privacy copy, “Save goals and continue” and “Skip for now” visible; skip reached Today | **PASS** |
| Today + five-tab nav at 360dp / 1.3× | Today, Run, Workouts, Progress, Settings visible and unclipped | all five labels visible; content scrolls under the floating nav | **PASS** |
| Settings at 360dp / 1.3× | auto/reminder cards stack and scroll | cards stack; controls remain usable; screen scrolls | **PASS** |
| Settings version at 1.0× | `Version 2.0.0` | shown after scrolling | **PASS** |
| Steps detail at 360dp / 1.3× | back action visible, no floating nav | back and title visible; no floating nav; screen scrolls | **PASS** |
| Progress seven-day history layout | history card and empty state usable | manual layout check at 360dp / 1.3×; history/empty data states also covered by automated tests | **PASS** |
| Running ready/live at 360dp / 1.3× | Start, Pause/Stop reachable above metrics | Start visible; Pause and Stop visible above Steps/Cadence; timer ticked | **PASS** |
| Running stop confirmation | no save without confirmation | “Stop and save this run?” dialog with Save/Keep running | **PASS** |
| Running summary auto-scroll | saved card brought into view | summary card auto-scrolled to top; `Duration 00:50 \| steps unavailable` shown on the sensorless emulator | **PASS** |
| Running timer-only fallback | timer works; steps/cadence show `—` | timer ran; Steps/Cadence showed `—`; summary saved with steps unavailable | **PASS** |
| TalkBack end-to-end | all screens usable with TalkBack | not performed | **NOT RUN** |
| Reduced-motion behaviour | animations skipped at system scale 0 | not performed manually | **NOT RUN** |
| RTL layout | mirrored/padded correctly | not performed | **NOT RUN** |
| API 33+ notification permission | grant/deny and channel-blocked states | emulator is API 30; not performed | **NOT RUN** |
| Real tracking/Running notification actions | Pause/Resume/Stop actions on a real notification | not performed | **NOT RUN** |
| FGS background/screen-off/process death | timer/steps persist and recover | not performed manually; in-process repository/service tests pass | **NOT RUN** |
| Reboot and boot restore | reminder restored; run recovery behaves | not performed | **NOT RUN** |
| OEM battery optimisation / force-stop | documented fallback, no false ACTIVE claim | not performed on a physical/OEM device | **NOT RUN** |
| Physical sensor accuracy | real step counts, no double counting | no physical sensor available | **NOT RUN** |
| Real V1→V2 APK upgrade | installed V1 data survives upgrade | genuine V1 database migration test passes; actual installed-APK upgrade not performed | **NOT RUN** |
| Tablet / landscape | usable layout | not performed | **NOT RUN** |
| Keyboard/IME | forms remain usable | not performed manually | **NOT RUN** |

## Reproducing these results

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug --rerun-tasks
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

Reports:

* JVM HTML: `app\build\reports\tests\testDebugUnitTest\index.html`
* Connected XML: `app\build\outputs\androidTest-results\connected\debug\`
* Lint HTML: `app\build\reports\lint-results-debug.html`

Physical-device, real notification, reboot, OEM, TalkBack and installed-APK upgrade
rows remain `NOT RUN` until they are actually executed.
