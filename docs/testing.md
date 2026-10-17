# Testing report

This file records **what was actually executed** and what happened. Nothing here is
inferred. The status values are `PASS`, `FAIL` or `NOT RUN`.

Environment: Windows 10 (10.0.19044), JDK 25.0.3 (Android Studio JBR), Gradle 9.6.0,
AGP 9.4.0, Android SDK platform 37, emulator AVD `Medium_Phone` (API 37, headless,
SwiftShader). Host RAM: 7959 MB total (the emulator recommends 16384 MB).

---

## 1. Build and packaging

| Test name | Command | Expected | Actual | Status |
|---|---|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug` | APK produced | `BUILD SUCCESSFUL`; `app-debug.apk` 7.81 MB | **PASS** |
| Instrumented test build | `.\gradlew.bat assembleDebugAndroidTest` | Test APK compiles | `BUILD SUCCESSFUL`; `app-debug-androidTest.apk` 0.57 MB | **PASS** |
| Manifest permission audit | inspect merged manifest | No `INTERNET` permission | Only `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `RECEIVE_BOOT_COMPLETED` (+ AndroidX's internal signature permission) | **PASS** |

## 2. JVM unit tests

Command: `.\gradlew.bat testDebugUnitTest`

Result: **34 tests, 0 failures, 0 errors.**

| Test class | Tests | Status |
|---|---|---|
| `utils.DateUtilsTest` | 8 | **PASS** |
| `utils.ValidationUtilsTest` | 9 | **PASS** |
| `logic.GoalProgressCalculatorTest` (Java → Kotlin) | 10 | **PASS** |
| `logic.ActivitySummaryCalculatorTest` (Java → Kotlin) | 7 | **PASS** |

Details per class:

| Test name | Expected | Actual | Status |
|---|---|---|---|
| `todayIso_hasExpectedFormatAndIsValid` | `YYYY-MM-DD`, parses back | matched, parsed | **PASS** |
| `isValidIsoDate_acceptsRealDates` | accepts real dates incl. 2024-02-29 | accepted | **PASS** |
| `isValidIsoDate_rejectsBadInput` | rejects null/empty/month 13/Feb 30/non-leap Feb 29/wrong order | all rejected | **PASS** |
| `weekBoundaries_areMondayToSunday` | Monday..Sunday, 7 days, contains sample | held for 3 sample dates | **PASS** |
| `monthBoundaries_handleLeapYears` | Feb 2026→28, Feb 2024→29, Jul→31 | correct | **PASS** |
| `isoDatesBetween_includesBothEnds` | 5 dates for 1–5 Jul | 5, correct ends | **PASS** |
| `isoDatesBetween_crossesMonthBoundary` | 4 dates across Jan→Feb | 4, correct order | **PASS** |
| `formatForDisplay_isHumanReadable` | contains day, month, year | contained all | **PASS** |
| `parseNonNegative_acceptsZeroAndPositive` | 0 and 8000 valid | valid | **PASS** |
| `parseNonNegative_trimsSurroundingWhitespace` | `"  42  "` → 42 | 42 | **PASS** |
| `parseNonNegative_rejectsEmpty` | null/""/"   " → EMPTY | EMPTY | **PASS** |
| `parseNonNegative_rejectsNegative` | `-5` → NEGATIVE | NEGATIVE | **PASS** |
| `parseNonNegative_rejectsMalformed` | `abc`, `1.5`, `1e3`, `12 34` → NOT_A_NUMBER | NOT_A_NUMBER | **PASS** |
| `parseNonNegative_rejectsTooLarge` | > max → TOO_LARGE | TOO_LARGE | **PASS** |
| `parsePositive_rejectsZero` | `0` → ZERO_NOT_ALLOWED | ZERO_NOT_ALLOWED | **PASS** |
| `parsePositive_acceptsPositiveValue` | `30` → 30 | 30 | **PASS** |
| `parsePositive_rejectsNegative` | `-1` → NEGATIVE | NEGATIVE | **PASS** |
| `percent_computesFifty` | 5000/10000 → 50.0 | 50.0 | **PASS** |
| `percent_isZeroWhenNothingDone` | 0/8000 → 0 | 0.0 | **PASS** |
| `percent_neverDividesByZero` | goal 0 or −10 → 0 | 0.0 | **PASS** |
| `percent_canExceedHundred` | 12000/8000 → 150 | 150.0 | **PASS** |
| `percent_treatsNegativeActualAsZero` | −500 → 0 | 0.0 | **PASS** |
| `clampedPercent_neverAboveHundred` | 150% → 100, 50% → 50 | correct | **PASS** |
| `roundedPercent_roundsToNearest` | 99.9→100, 66.67→67 | correct | **PASS** |
| `fraction_isClampedBetweenZeroAndOne` | 0.5 / 1.0 / 0.0 | correct | **PASS** |
| `isReached_onlyWhenGoalIsPositiveAndMet` | 8000/8000 true, 7999/8000 false, 5/0 false | correct | **PASS** |
| `formatPercent_hasOneDecimalPlace` | `"50.0%"` | `"50.0%"` | **PASS** |
| `zeroFill_addsMissingDaysAsZero` | 3 dates, 1 recorded → 3 entries | correct | **PASS** |
| `zeroFill_keepsDatesInGivenOrder` | order preserved | preserved | **PASS** |
| `totals_sumEveryDayExactlyOnce` | 3000 / 30 / 3 | correct | **PASS** |
| `averageSteps_roundsToNearest` | 1500 and 1.67→2 | correct | **PASS** |
| `bestStepDay_returnsHighestDay` | 2026-07-02 / 5000 | correct | **PASS** |
| `activeDays_countsDaysWithAnyActivity` | 2 of 4 | 2 | **PASS** |
| `emptyList_isHandledSafely` | 0 / 0 / null / 0 | correct | **PASS** |

### Defects found and fixed during testing

Both were real findings from executed tests, not assumptions:

1. **`isValidIsoDate` accepted impossible dates.** A first run of
   `DateUtilsTest.isValidIsoDate_rejectsBadInput` **FAILED**: `2026-02-30` was
   accepted because `DateTimeFormatter`'s default SMART resolver silently adjusted
   it to `2026-02-28`. **Fix:** switched the ISO formatter to `uuuu-MM-dd` with
   `ResolverStyle.STRICT` in `DateUtils`. Re-run: **PASS**.
2. **One incorrect test expectation.** `roundedPercent(2, 3)` is 66.67 → 67, but the
   test asserted 66. The *code* was correct; the assertion was wrong. **Fix:**
   corrected the expected value to 67. Re-run: **PASS**.

## 3. Device / emulator checks

Command used for install/launch:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.fitnesstracker/.activities.MainActivity
```

| Test name | Expected | Actual | Status |
|---|---|---|---|
| APK installs on emulator | Install succeeds | `Performing Streamed Install` → `Success` | **PASS** |
| App process starts without crashing | Process runs, no app `FATAL EXCEPTION` | App process started (`pid 16860`); logcat showed **no** `FATAL EXCEPTION` from `com.example.fitnesstracker` | **PASS** |
| Dashboard visible on screen | UI drawn | Screen captures were solid black; `mCurrentFocus=null`; ActivityManager logged `Activity pause timeout` / `top resumed state loss timeout` | **NOT RUN** |
| Airplane-mode offline behaviour | Core features still work | Emulator became unusable before this could be exercised | **NOT RUN** |

### Why the dashboard could not be visually verified

The host has ~8 GB RAM while the AVD recommends 16 GB. The headless emulator ran on
SwiftShader (software GPU); the Android system itself logged
`FATAL EXCEPTION: FinalizerWatchdogDaemon` (a **system** process, not this app),
activity pause timeouts, and `mCurrentFocus=null`. Every `screencap` returned an
identical 15,580-byte black PNG, so no meaningful screenshot could be produced.

This is an **environment limitation**, not an app failure: the app compiled,
installed, and started its process without an app-level exception. Visual UI
verification should be repeated on a machine with more RAM or on a physical device.

## 4. Instrumented SQLite tests

File: `app/src/androidTest/java/com/example/fitnesstracker/DatabaseInstrumentedTest.java`

Command:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

| Test name | Expected | Actual | Status |
|---|---|---|---|
| `savingSameDateTwice_keepsExactlyOneRecord` | 1 row, value 2500 | — | **NOT RUN** |
| `negativeStepTotalIsRejected` | rejected, no row | — | **NOT RUN** |
| `zeroDurationWorkoutIsRejected` | 0-min rejected, 30-min saved | — | **NOT RUN** |
| `goalMustBePositiveAndPersists` | 0 rejected, 12345/45 persisted | — | **NOT RUN** |
| `weeklySeriesZeroFillsAndSumsCorrectly` | 7 days, 10000 steps, 50 min, 2 workouts | — | **NOT RUN** |
| `clearAllData_removesRecordsAndResetsGoal` | records gone, defaults restored | — | **NOT RUN** |

The instrumented test APK **compiles successfully** (`assembleDebugAndroidTest`,
**PASS**), but `connectedDebugAndroidTest` could not be executed because the
emulator was too resource-starved to respond reliably. Please run the command above
on a working emulator/device and paste the output into this file.

## 5. Manual acceptance scenarios (emulator blocked)

The scenarios in README section 13 (log steps twice, add/edit/delete workout, change
goals, switch Day/Week/Month, reminder test, clear data, airplane mode) are the
intended manual acceptance tests. Because the emulator rendered a black framebuffer,
these were **NOT RUN** interactively in this environment. The underlying logic they
exercise is covered by the 34 passing unit tests (validation, date boundaries,
goal percentages, zero-filling and totals).

## 6. Reproducing every result

```powershell
cd 'C:\Users\PRANIT DESHMUKH\Desktop\Fitness tracker'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat assembleDebug              # PASS (verified)
.\gradlew.bat testDebugUnitTest          # 34 tests PASS (verified)
.\gradlew.bat assembleDebugAndroidTest   # PASS (verified)

# On a working emulator/device:
.\gradlew.bat connectedDebugAndroidTest  # NOT RUN here
```

HTML unit-test report:
`app\build\reports\tests\testDebugUnitTest\index.html`
