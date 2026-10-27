# UI design

Motionfit uses a light-first design system derived from Wise's visual language: a lime
brand accent, a sage canvas, near-black ink surfaces, very heavy display type and
generously rounded white cards. V2 keeps that language and adds Running, a dedicated
Steps detail screen and stronger layout resilience.

## Palette

Defined in `res/values/colors.xml`.

| Token | Value | Used for |
|---|---|---|
| `primary` | `#9FE870` | the only brand accent: primary actions, steps figure, active nav pill |
| `on_primary` | `#0E0F0C` | content on lime |
| `primary_active` | `#CDFFAD` | goal line on the dark chart, ripples |
| `primary_pale` | `#E2F6D5` | positive badge background |
| `ink` | `#0E0F0C` | hero cards, nav pill, headings |
| `ink_deep` | `#163300` | ring track, dark chart baseline |
| `body` | `#454745` | secondary text |
| `mute` | `#5F615F` | captions, hints |
| `canvas` | `#FFFFFF` | cards, inputs, circular icon buttons |
| `canvas_soft` | `#E8EBE6` | screen canvas, status and navigation bars |
| `positive` | `#2EAD4B` | success (never the lime brand colour) |
| `negative` | `#D03238` | destructive actions and errors |
| `accent_cyan` | `#38C8FF` | workout-minutes data only |
| `accent_orange` | `#FFC091` | workout-count data only |

Lime is reserved for primary actions and the key progress figure; success uses
`positive` green. Cyan and orange appear **only** inside charts and progress bars,
never on buttons or navigation.

**Elevation is surface contrast.** There are no shadows or gradients: cards are plain
`LinearLayout`s with rounded drawables, so elevation is exactly zero.

## Typography

Inter is bundled in `res/font` (`inter_regular` 400, `inter_semibold` 600,
`inter_black` 900) so the app stays fully offline — no downloadable fonts.

| Role | Style | Size / weight |
|---|---|---|
| Hero number | `Text.Wise.Hero` | 64sp, Inter 900, tabular figures |
| Screen title | `Text.Wise.ScreenTitle` | 32sp, Inter 900 |
| Section title | `Text.Wise.SectionTitle` | 24sp, Inter 600 |
| Body | `Text.Wise.Body` | 16sp, Inter 400 |
| Secondary | `Text.Wise.Secondary` | 14sp, Inter 400 |
| Caption | `Text.Wise.Caption` | 12sp, Inter 400 |
| Button | `Widget.Wise.Button*` | 16sp, Inter 600 |

Weight 900 is used only for hero numbers and screen titles; everything else is 600 or
400. Numeric fields use tabular figures.

## Shape and spacing

* Cards and buttons: 24dp radius.
* Inputs: 12dp radius with a 1dp ink border.
* Badges and the nav highlight: fully round.
* 4dp spacing grid; 24dp card padding, 16dp screen margin, at least 48dp touch targets.
* Scrolling screens reserve enough bottom padding for the floating navigation.

## Floating pill navigation

A custom `FloatingNavView` (not `BottomNavigationView`).

* Ink pill, 64dp tall, fully rounded, no shadow.
* Floats 20dp from each side and 16dp above the bottom edge, plus the system
  navigation inset via `WindowInsets`.
* Five destinations: **Today, Run, Workouts, Progress, Settings** — 24dp icon plus a
  label, labels always visible; inactive items are white at 70%.
* Labels autosize and ellipsize at narrow widths; the active item sits in a lime pill
  with an ink icon and label. The highlight slides between items.
* Every item has a content description, a target of at least 48dp and a selected state.
* Pushed screens (Steps detail, New/Edit workout, Goals) do not include the nav.
* At 360dp with 1.3× font scale the five labels remain visible and unclipped; Today is
  crowded but content scrolls beneath the pill.

## Screens

1. **Today** — date caption with chevrons, heavy title, circular “+”; ink hero card with
   the lime steps ring, heavy lime total, percentage and “of N steps”; white card with
   workout time (cyan bar) and workout count; Recent card; links into Steps detail.
2. **Run** — focused full-screen Running mode. The order is timer card → Start/actions
   → recovery card → live Steps/Cadence metrics → status card. The terminal summary or
   error card is scrolled into view immediately. It shows “Timer and steps only. No GPS,
   distance or pace.” A back/close action leaves the run alive; a persistent
   notification owns it when the service is foreground.
3. **Workouts** — heavy title, circular “+”, filter/segment controls, white rows with
   type/date/duration. Running rows show exact `mm:ss` duration and step count (or
   “steps unavailable”); manual rows keep whole-minute metadata. Empty state has one
   Add action.
4. **Progress** — heavy title, pill segmented control (Day/Week/Month), range label with
   circular arrows; ink hero with period steps, average and lime bar chart; white cards
   for minutes and workout count; best day/active days; a seven-day Step history card
   (newest first, real empty state) with `View all` in its header.
5. **Settings** — grouped white cards for Goals, Automatic step counting (status:
   Off/Active/App-open only/Unavailable, permission actions, battery settings), Reminders,
   Local data and a footer with “Everything stays on this device.” plus
   `Version 2.0.0`.
6. **Steps detail** — pushed screen with circular back, selected date, large total,
   “Log steps” action, automatic-counting status card, and step history. It has no
   floating nav; bottom padding keeps the log action clear of the history.
7. **Onboarding** — one skippable first-run screen: welcome/privacy, goals, automatic
   counting explanation and a permission note before any runtime request. Skip keeps
   existing values and records only that onboarding was shown; Finish validates and
   saves the shown goals and the opt-in choice.
8. **New/Edit workout** — circular back, title, labelled fields (Type, Date, Duration,
   Notes), pinned Save pill, red Delete when editing, and read-only exact Running
   duration/steps when editing a recorded run.
9. **Goals** — one white card per goal with a large heavy value, circular minus/plus
   steppers, a progress bar with today's real percentage, the caption that past days
   keep their records, and a pinned Save goals pill.

## Accessibility and resilience

* 48dp minimum touch targets for navigation, steppers, back/close and small icon
  actions; labels are associated with inputs.
* Content descriptions cover steps, dates, Running timer/steps/cadence, chart summaries
  and destructive actions. The steps ring is decorative to TalkBack; the text carries
  the value.
* Running announces state changes (started, paused, resumed, interrupted, saved) and
  uses plurals for cadence. Steps/Cadence show an em dash plus “unavailable” when there
  is no valid value.
* The Steps detail screen uses start/end padding so it remains symmetric in RTL.
* Forms use `adjustResize`; the workout editor and goals screen keep their back action
  reachable.
* Automated checks cover 360dp/1.3× layouts, contrast tokens, nav label autosizing,
  chart accessibility summaries, and 48dp actions. Full TalkBack, reduced-motion, RTL
  and physical-device audits are still **NOT RUN**.

## Motion

* Press feedback: scale 0.97 for 90ms.
* The steps ring fills once on load (about 800ms, decelerate) and the figure counts up.
* The nav highlight slides (about 220ms); screens cross-fade (about 250ms).
* Running's terminal summary/error card is scrolled into view immediately, with no
  smooth-scroll animation.
* **Reduce motion:** the system animator duration scale is checked; when it is 0 the
  count-up, ring fill and slide are skipped and final values appear immediately. The
  Running saving spinner is also hidden at scale 0.

## Notes on scope

* Running deliberately has no distance, pace, route, GPS or calorie UI.
* Workout details are shown by the New/Edit screen rather than a separate screen.
* Goal steppers are view-only UI logic; the save path and validation are unchanged.
* Inter at 900 is the display face; no proprietary font is redistributed.
* Physical-device visual checks and a full TalkBack audit remain outstanding — see
  [testing.md](testing.md).
