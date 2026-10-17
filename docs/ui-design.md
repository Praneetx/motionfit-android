# UI design — Wise restyle

This document describes how the app applies the **Wise** design language from
`DESIGN-wise.md`, plus the floating pill navigation. Colours, type and shape values
described there are the source of truth.

## 1. Palette (all values live in `res/values/colors.xml`)

| Token | Value | Used for |
|---|---|---|
| `primary` | `#9FE870` | the only brand accent: primary actions, steps figure, active nav pill |
| `on_primary` | `#0E0F0C` | text/icons on lime |
| `primary_active` | `#CDFFAD` | goal line on the dark chart, ripples |
| `primary_pale` | `#E2F6D5` | positive badge background |
| `ink` | `#0E0F0C` | hero cards, nav pill, headings |
| `ink_deep` | `#163300` | ring track, dark chart baseline |
| `body` | `#454745` | secondary text |
| `mute` | `#868685` | captions, hints, placeholders |
| `canvas` | `#FFFFFF` | cards, inputs, circular icon buttons |
| `canvas_soft` | `#E8EBE6` | the sage screen canvas and status/navigation bars |
| `positive` | `#2EAD4B` | success messages (never lime) |
| `negative` | `#D03238` | destructive actions and errors |
| `negative_bg` | `#320707` | the EXPERIMENTAL badge |
| `accent_cyan` | `#38C8FF` | workout-minutes data only |
| `accent_orange` | `#FFC091` | workout-count data only |

**Brand rule respected:** lime green is reserved for primary actions and the key
progress figure. Success is `positive` green, not lime.

**Elevation = surface contrast.** There are no shadows and no gradients: cards are
plain `LinearLayout`s with rounded drawables (`bg_wise_card*`), so elevation is
exactly 0. `MaterialCardView` is not used anywhere.

## 2. Type scale

Inter is bundled in `res/font` (`inter_regular.ttf` 400, `inter_semibold.ttf` 600,
`inter_black.ttf` 900) so the app stays fully offline — no downloadable fonts.
Wise Sans is proprietary; `DESIGN-wise.md` names Inter at 900 as the display
substitute, which is what we use.

| Role | Style | Size / weight |
|---|---|---|
| Hero number (steps ring, period total) | `Text.Wise.Hero` | 64sp, Inter 900, tabular figures |
| Screen title | `Text.Wise.ScreenTitle` | 32sp, Inter 900 |
| Section title | `Text.Wise.SectionTitle` | 24sp, Inter 600 |
| Body | `Text.Wise.Body` | 16sp, Inter 400 |
| Secondary | `Text.Wise.Secondary` | 14sp, Inter 400 |
| Caption | `Text.Wise.Caption` | 12sp, Inter 400 |
| Button | `Widget.Wise.Button*` | 16sp, Inter 600 |

Heavy (900) is used **only** for hero numbers and screen titles; everything else is
600 or 400. All numeric fields use `fontFeatureSettings="tnum"`.

## 3. Shape and spacing

* Cards, buttons: **24dp** radius (`radius_xl`).
* Inputs: **12dp** radius with a 1dp ink border.
* Badges and the nav highlight: fully round.
* Spacing on a 4dp grid (`spacing_xxs`…`spacing_3xl`); cards use 24dp padding,
  screens a 16dp margin; every touch target is ≥48dp.

## 4. Floating pill navigation

A custom `FloatingNavView` (not `BottomNavigationView`) so the floating, fully
rounded shape and the sliding highlight are exact.

* Ink (`#0E0F0C`) pill, **64dp** tall, fully rounded (32dp), **no shadow**.
* Floats **20dp** from each side and **16dp** above the bottom edge, plus the
  system navigation inset via `WindowInsets` (the app is not edge-to-edge, so the
  inset resolves to 0 and never double-pads).
* Five destinations: Today, Steps, Workouts, Progress, Settings — 24dp icon plus a
  12sp label, labels always visible; inactive items are white at 70%.
* The **active** item sits in a lime pill with an ink icon and label. The highlight
  slides between items (220ms) and is skipped when animations are disabled.
* Every item has a `contentDescription`, is 56dp tall inside the pill, and is
  marked `selected` for accessibility services.
* Every scrolling screen keeps `nav_content_padding` (104dp) at the bottom so
  content is never hidden behind the pill.
* Pushed screens (New/Edit workout, Goals) do not include the nav at all, so there
  is no bar to hide; navigation into them cross-fades instead.

## 5. Component mapping (as requested)

| Spec component | Implementation |
|---|---|
| `card-content` | `bg_wise_card.xml` on white over the sage canvas |
| `card-feature-dark` | `bg_wise_card_dark.xml` — the Today and Progress hero |
| `card-feature-green` | `bg_wise_card_green.xml` (available; used where a goal is met) |
| `card-feature-sage` | `bg_wise_card_sage.xml` (available for secondary blocks) |
| `currency-converter-card` | step-entry dialog fields + workout editor inputs |
| `text-input` | `Widget.Wise.TextInputLayout` (white, 1dp ink, 12dp) |
| `button-primary/secondary/tertiary/icon-circular` | `Widget.Wise.Button.Primary/Secondary/Tertiary` + `bg_icon_circular` |
| `badge-positive` / `badge-negative` | `bg_badge_positive` / `bg_badge_negative` (EXPERIMENTAL badge) |
| `ex-empty-state-card` | empty-state captions inside the white cards |
| `ex-modal-card` | `Theme.FitnessTracker.Dialog` (white, 24dp, no shadow) |
| `ex-toast` | the app keeps its existing chat-style toasts/dialogs |

## 6. Screens

1. **Today** — date caption with chevrons, heavy "Today" title, circular "+";
   ink hero card with the lime steps ring (10dp stroke, rounded cap, starts at 12
   o'clock, ink-deep track, heavy lime total inside, "of N steps" beneath); white
   card with workout time (cyan bar, "25 / 30 min") and workout count; white
   Recent card (type, day, duration).
2. **Progress** — heavy title, pill segmented control (active = ink/white), range
   label with circular arrows; ink hero with the period step total (heavy lime),
   the daily average and a lime bar chart with a dashed goal line and today's bar
   at full strength; two white cards side by side (cyan minutes bars, orange count
   bars); best day and active days card.
3. **Settings** — grouped white cards for Goals, Reminders (lime switch + time),
   Data ("Clear all data" in negative red), and the footer line "Everything stays
   on this device." plus one caption about notification permission.
4. **New/Edit workout** — circular back, title, text-input fields (Type, Date,
   Duration, Notes), pinned lime Save pill, red "Delete workout" when editing.
5. **Goals** — one white card per goal with a large heavy value, circular −/+
   steppers, a progress bar with today's real percentage and the caption "Changes
   apply from now on. Past days keep their records."; pinned "Save goals" pill.
6. **Steps / Workouts** — heavy title, circular "+", white cards, 60dp rows.

## 7. Motion (added by me — not from `DESIGN-wise.md`)

* Quick press feedback (scale 0.97, 90ms) via `UiAnimations.attachPressFeedback`.
* The steps ring fills once on load (~800ms, decelerate) and the figure counts up.
* The nav highlight slides between items (~220ms).
* Screens cross-fade (~250ms) using `res/anim/fade_in|fade_out`.
* **Reduce motion:** `UiAnimations.isReduceMotion()` reads the system animator
  duration scale; when it is 0 the count-up, ring fill and highlight slide are
  skipped and final values appear immediately.

## 8. Files

**New:** `values/styles.xml`, `font/inter_regular.ttf`, `font/inter_semibold.ttf`,
`font/inter_black.ttf`, `drawable/bg_wise_card*.xml`, `drawable/bg_wise_input.xml`,
`drawable/bg_nav_pill.xml`, `drawable/bg_nav_highlight.xml`,
`drawable/bg_icon_circular.xml`, `drawable/bg_icon_circle_soft.xml`,
`drawable/bg_badge_*.xml`, `drawable/bg_segment_track.xml`,
`drawable/ic_home.xml`, `drawable/ic_dumbbell.xml`, `drawable/ic_minus.xml`,
`color/segment_bg.xml`, `color/segment_text.xml`, `anim/fade_in.xml`,
`anim/fade_out.xml`, `layout/view_floating_nav.xml`, `views/StepsRingView.java`,
`views/FloatingNavView.java`, `utils/UiAnimations.java`, `docs/ui-design.md`.

**Rewritten (layout only):** `values/colors.xml`, `values/dimens.xml`,
`values/themes.xml`, `values/strings.xml` (additions only), all ten layouts.

**Edited (UI only):** `MainActivity`, `StepsActivity`, `WorkoutListActivity`,
`SummaryActivity`, `WorkoutEditActivity`, `GoalsActivity`, `SettingsActivity`,
`WorkoutAdapter`, `BarChartView`.

**Untouched:** `database/`, `models/`, `repositories/`, `utils/ValidationUtils`,
`utils/DateUtils`, `utils/OperationResult`, `utils/FormatUtils`, and the Kotlin
`logic/` package. No distance or calorie UI exists.

## 9. Deliberate deviations / conflicts

1. **Workout details is not a separate Activity.** Hard rule 1 limits code changes
   to UI plus minimal nav/animation code, so the existing `WorkoutEditActivity`
   serves as details + edit (Type, Date, Notes, added/updated caption, red delete).
   It therefore has no separate "heavy duration feature card".
2. **Goals steppers and live progress** are view-only Activity code (they read
   today's totals from `SummaryRepository` and use `GoalProgressCalculator`); no
   data-layer code changed.
3. **WindowInsets** is applied but resolves to 0 because the app is not
   edge-to-edge; adding it twice would double-pad.
4. **Manrope is not bundled**: `DESIGN-wise.md` explicitly allows Inter at 900 for
   display, so only Inter ships.
5. Web-only parts of the spec (126px hero band, footer band, marketing grids,
   currency-converter layout, pricing tiers, desktop breakpoints) were ignored.
6. **`badge-negative` contrast.** `DESIGN-wise.md` pairs `negative-bg` (`#320707`)
   with `on-primary` text (`#0E0F0C`) — near-black on dark maroon is unreadable.
   Following the file's own accessibility intent, that badge uses white
   (`canvas`) text instead. `badge-positive` is used for the "Goal met" chip on
   the hero card, shown only when the step goal is reached.

## 10. Verification status

* Build and unit tests: see the results reported with the change (steps 8).
* **Visual/device verification: NOT RUN.** The emulator on this machine renders a
  black framebuffer (host RAM limit), so screenshots could not confirm the layout.
  Run the app on a real device or a higher-memory emulator to check spacing,
  the ring animation and the nav highlight.
