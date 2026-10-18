# Testing

This file records **what was actually executed** and what happened. Status values are
`PASS`, `FAIL` or `NOT RUN`.

Environment used: Windows 10, JDK 25 (Android Studio JBR), Gradle 9.6.0, AGP 9.4.0,
Android SDK platform 37.

## Build and packaging

| Test | Command | Expected | Actual | Status |
|---|---|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug` | APK produced | `BUILD SUCCESSFUL`; `app-debug.apk` produced | **PASS** |
| Instrumented test build | `.\gradlew.bat assembleDebugAndroidTest` | test APK compiles | `BUILD SUCCESSFUL` | **PASS** |
| No internet permission | inspect merged manifest | no `INTERNET` | only `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED` | **PASS** |
| Fonts bundled | inspect APK contents | Inter TTFs packaged | 3 files in `res/font` | **PASS** |

## JVM unit tests

Command: `.\gradlew.bat testDebugUnitTest`

Result: **34 tests, 0 failures, 0 errors.**

| Test class | Tests | Status |
|---|---|---|
| `utils.DateUtilsTest` | 8 | **PASS** |
| `utils.ValidationUtilsTest` | 9 | **PASS** |
| `logic.GoalProgressCalculatorTest` (Java → Kotlin) | 10 | **PASS** |
| `logic.ActivitySummaryCalculatorTest` (Java → Kotlin) | 7 | **PASS** |

What they cover:

* date parsing and rejection of impossible dates (`2026-02-30`, non-leap `2025-02-29`);
* Monday–Sunday week boundaries and month boundaries including leap years;
* date-range generation across month boundaries;
* step/minute validation: empty, malformed, negative, zero where disallowed, too large;
* goal percentages: divide-by-zero, values above 100%, clamping, rounding;
* daily aggregation: zero-filling, totals, averages, best day, active days, empty list.

### Defects found and fixed during testing

1. **Impossible dates were accepted.** `isValidIsoDate("2026-02-30")` returned true
   because the default SMART resolver adjusted it to 2026-02-28. Fixed by parsing with
   `uuuu-MM-dd` and `ResolverStyle.STRICT`.
2. **A wrong test expectation** (`roundedPercent(2,3)` is 67, not 66). The code was
   correct; the assertion was fixed.

## Device / emulator checks

| Test | Expected | Actual | Status |
|---|---|---|---|
| APK installs | install succeeds | `Success` | **PASS** |
| App process starts without crashing | no app-level `FATAL EXCEPTION` | process started, no app crash logged | **PASS** |
| Dashboard visible on screen | UI renders | screen captures were solid black; the system logged activity pause/resume timeouts | **NOT RUN** |

**Why the UI was not visually verified:** the development machine had about 8 GB of
RAM while the emulator image recommends 16 GB, and the headless emulator ran on
software rendering. The Android system itself became unresponsive, so no meaningful
screenshot could be produced. Repeat this check on a physical device or a
higher-memory emulator.

## Instrumented SQLite tests

File: `app/src/androidTest/java/io/github/praneetx/motionfit/DatabaseInstrumentedTest.java`

Command:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

| Test | Expected | Status |
|---|---|---|
| `savingSameDateTwice_keepsExactlyOneRecord` | 1 row, latest value | **NOT RUN** |
| `negativeStepTotalIsRejected` | rejected, no row | **NOT RUN** |
| `zeroDurationWorkoutIsRejected` | 0-min rejected, 30-min saved | **NOT RUN** |
| `goalMustBePositiveAndPersists` | 0 rejected, values persisted | **NOT RUN** |
| `weeklySeriesZeroFillsAndSumsCorrectly` | 7 days, correct totals | **NOT RUN** |
| `clearAllData_removesRecordsAndResetsGoal` | records gone, defaults restored | **NOT RUN** |

The instrumented test APK compiles (**PASS**), but the tests have not been executed in
a working device environment. Run the command above on a healthy emulator/device and
record the output here.

## Manual acceptance scenarios

These are **NOT RUN** because the emulator could not render:

1. Log steps, confirm the dashboard total and percentage update.
2. Log the same day twice, confirm only one record exists.
3. Add a workout; confirm it appears in Recent and in the minutes total.
4. Edit and delete a workout (with confirmation).
5. Change goals; confirm progress updates and past records are unchanged.
6. Switch Day / Week / Month and move between periods.
7. Enable a reminder and send a test notification.
8. Clear all data and confirm everything resets.
9. Enable airplane mode and repeat the core flows (offline check).

The logic behind scenarios 1–6 is covered by the 34 passing unit tests.

## Reproducing these results

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebugAndroidTest

# On a working emulator or device:
.\gradlew.bat connectedDebugAndroidTest
```

HTML report: `app\build\reports\tests\testDebugUnitTest\index.html`
