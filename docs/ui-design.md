# UI design

Motionfit uses a light-first design system derived from Wise's visual language: a lime
brand accent, a sage canvas, near-black ink surfaces, very heavy display type and
generously rounded white cards.

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
| `mute` | `#868685` | captions, hints |
| `canvas` | `#FFFFFF` | cards, inputs, circular icon buttons |
| `canvas_soft` | `#E8EBE6` | screen canvas, status and navigation bars |
| `positive` | `#2EAD4B` | success (never the lime brand colour) |
| `negative` | `#D03238` | destructive actions and errors |
| `negative_bg` | `#320707` | the EXPERIMENTAL badge background |
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

## Floating pill navigation

A custom `FloatingNavView` (not `BottomNavigationView`).

* Ink pill, 64dp tall, fully rounded, no shadow.
* Floats 20dp from each side and 16dp above the bottom edge, plus the system
  navigation inset via `WindowInsets`. The app is not edge-to-edge, so the inset
  resolves to 0 and never double-pads.
* Five destinations: Today, Steps, Workouts, Progress, Settings — 24dp icon plus a
  12sp label, labels always visible; inactive items are white at 70%.
* The active item sits in a lime pill with an ink icon and label. The highlight slides
  between items.
* Every item has a content description, a target of at least 48dp and a selected state.
* Scrolling screens reserve 104dp bottom padding so content is never hidden.
* Pushed screens (New/Edit workout, Goals) simply do not include the nav.

## Components

| Component | Implementation |
|---|---|
| White card | `bg_wise_card.xml` on the sage canvas |
| Dark hero card | `bg_wise_card_dark.xml` (Today and Progress) |
| Sage / pale-green cards | `bg_wise_card_sage.xml`, `bg_wise_card_green.xml` |
| Text input | `Widget.Wise.TextInputLayout` (white, 1dp ink, 12dp) |
| Buttons | `Widget.Wise.Button.Primary` / `.Secondary` / `.Tertiary` / `.Destructive` |
| Circular icon button | `bg_icon_circular.xml`, `bg_icon_circle_soft.xml` |
| Badges | `bg_badge_positive.xml`, `bg_badge_negative.xml` |
| Dialogs | `Theme.Motionfit.Dialog` (white, 24dp, no shadow) |

## Screens

1. **Today** — date caption with chevrons, heavy title, circular "+"; ink hero card with
   the lime steps ring (10dp rounded stroke from 12 o'clock, ink-deep track, heavy lime
   total inside, "of N steps" beneath) and a positive "Goal met" badge; white card with
   workout time (cyan bar, "25 / 30 min") and workout count; Recent card.
2. **Progress** — heavy title, pill segmented control (active = ink/white), range label
   with circular arrows; ink hero with the period step total, daily average and lime bar
   chart (dashed goal line, today's bar full strength, zero days as small stubs); two
   white cards with cyan (minutes) and orange (count) charts; best day and active days.
3. **Settings** — grouped white cards for Goals, Reminders (lime switch + time) and Data
   ("Clear all data" in negative red), plus a footer line "Everything stays on this
   device." and a caption about notification permission.
4. **New/Edit workout** — circular back, title, text-input fields (Type, Date, Duration,
   Notes), pinned lime Save pill, red "Delete workout" when editing, and an
   "Added/Updated" caption.
5. **Goals** — one white card per goal with a large heavy value, circular minus/plus
   steppers, a progress bar with today's real percentage, the caption that past days
   keep their records, and a pinned "Save goals" pill.
6. **Steps / Workouts** — heavy title, circular "+", white cards, 60dp rows.

## Motion

* Press feedback: scale 0.97 for 90ms.
* The steps ring fills once on load (about 800ms, decelerate) and the figure counts up.
* The nav highlight slides (about 220ms); screens cross-fade (about 250ms).
* **Reduce motion:** the system animator duration scale is checked; when it is 0 the
  count-up, ring fill and slide are skipped and final values appear immediately.

## Notes on scope

* Workout details are shown by the New/Edit screen rather than a separate screen, so
  there is no separate heavy-duration feature card.
* Goal steppers are view-only UI logic; the save path and validation are unchanged.
* Inter at 900 is the display face; no proprietary font is redistributed.
* The UI has not yet been visually verified on a device — see
  [testing.md](testing.md).
