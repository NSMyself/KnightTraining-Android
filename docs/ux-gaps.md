# UI/UX gaps: Android vs iOS

Compared 2026-11-19 against `~/Desktop/oathbreaker`. Ranked by how much a user would notice.
Each item follows AGENTS.md's design rule: match what iOS *does and feels like*, built the way a
Material 3 app would build it, not a pixel copy.

Paths: iOS is relative to `oathbreaker/KnightTraining/`, Android to
`app/src/main/kotlin/com/knightindustries/knighttraining/`.

The common thread: **Android has almost no motion, haptics or sound.** The only animation or
haptic APIs in the whole app are two `performHapticFeedback` calls in `feature/home/EditHomeSheet.kt`:
no `animate*AsState`, `AnimatedVisibility`, `AnimatedContent` or `Brush`, and no `res/raw` sounds.
Items 1–5 are mostly about that.

| # | Gap | Impact | Effort |
|---|-----|--------|--------|
| 1 | ~~Timer has no animated mesh gradient~~ Done 2026-11-19 | High | M |
| 2 | ~~No haptics or sounds (and no settings toggles)~~ Done 2026-11-19 | High | S–M |
| 3 | ~~Week-complete celebration is a plain dialog~~ Done 2026-11-19 (review prompt not yet) | High | M |
| 4 | ~~Nothing animates: numbers, progress, state changes~~ Done 2026-11-19 | High | M |
| 5 | ~~Timer controls and study card lack hierarchy~~ Done 2026-11-19 | High | S |
| 6 | ~~Switching tabs throws away each tab's state~~ Done 2026-11-19 | High | S–M |
| 7 | ~~No Training History screen (chart + activity grid)~~ Done 2026-11-19 (from Home's tiles) | High | L |
| 8 | ~~Tapping a goal on Home does nothing~~ Done 2026-11-19 (reminders still to come) | Medium–High | M |
| 9 | ~~Onboarding has no artwork, motion or closing quote~~ Done 2026-11-19 | Medium | M |
| 10 | ~~Flat backgrounds and neutral cards: brand glow missing~~ Done 2026-11-19 | Medium | S |
| 11 | ~~One widget, one size~~ Done 2026-11-19 | Medium | M–L |
| 12 | ~~Profile: no activity grid, simpler rating chart~~ Done 2026-11-19 | Medium | M |
| 13 | ~~Home plan header is missing actions and states~~ Done 2026-11-19 | Medium | S |
| 14 | ~~Library cards, detail and empty/loading states are plain~~ Done 2026-11-19 (no error/Retry state: the library is a local flow) | Medium | M |
| 15 | Weekly review and week log show less | Medium | M |

---

## 1. Timer has no animated mesh gradient

- **iOS** (`Views/StudyingView.swift:107-125`, `Views/AnimatedMeshGradient.swift`):
  - While the timer runs, an `AnimatedMeshGradient` sits over `NewBackgroundView`.
  - **Blobs:** four of them, radius 80–140pt, in #8B5CF6, #EC4899, #6366F1 and #C026D3.
  - **Rendering:** each blob is blurred (55) and blended with `.plusLighter`. They drift at 30fps and fade in over 3s (`:31-58, 183-194`).
  - **Battery rules:** the animation stops after 10 minutes, while paused, and in Low Power Mode. It fades out over 20s when it dims itself and over 0.6s when the user triggered it (`:237, 252-276`).
  - **Dim notice:** "Dimmed to save battery · Tap to show" (`:280-327`).
  - **Clock:** 96pt bold with a soft black shadow.
- **Android** (`feature/session/SessionScreen.kt:114-201`):
  - Flat `colorScheme.background` and a `displayLarge` clock.
  - Pausing only changes the clock color, with no transition.
- **Do:**
  - A `Canvas` that draws 3–4 `Brush.radialGradient` blobs in the same colors.
  - Move them with `rememberInfiniteTransition` and blend them with `BlendMode.Plus`.
  - Keep the battery rules, using `PowerManager.isPowerSaveMode` for Low Power Mode.
  - Fade with `animateFloatAsState`: a 20s tween when it dims itself, about 600ms when the user caused it.
  - Use `Modifier.blur` on API 31+, and wider gradient radii instead on API 28–30.

## 2. No haptics or sounds (and no settings toggles)

- **iOS** (`Services/FeedbackService.swift:66-124`) maps named events to feedback:
  - **Haptics:**
    - session start: medium impact
    - pause / resume: soft impact
    - stop: rigid impact
    - saves and tracked activity: `.success`
    - selections: `.selection`
    - milestones: rigid impact
  - **Sound:** `piece.caf` plays only for accomplishments (goal completed, milestone, book recognized).
  - **Rules:** feedback only follows a deliberate tap, never a timer tick.
  - **Settings:** separate Sounds and Haptics switches (`Screens/Settings/SettingsScreen.swift:218-251`).
- **Android:** none of this exists.
- **Do:**
  - One `Feedback` helper with the same event names, mapped to `HapticFeedbackConstants`:
    - `CONFIRM` for start, stop, save and milestone
    - `CLOCK_TICK` / `CONTEXT_CLICK` for pause and resume
    - `SEGMENT_TICK` for selections
  - Play `piece` through `SoundPool` with `USAGE_ASSISTANCE_SONIFICATION`.
  - Add two `Switch` rows in Profile's settings.
- Do this early: items 3, 4 and 8 build on it.

## 3. Week-complete celebration is a plain dialog

- **iOS** (`Screens/WeeklyPlanCelebrationScreen.swift:32-77`, `UI Elements/Animations/ConfettiView.swift`):
  - A full screen on #151022.
  - **Sound and haptics:** `week_complete.caf` plays in step with a Core Haptics pattern: taps at 0.005, 0.555 and 0.805s, then 1.655 and 1.805s.
  - **Confetti:** starts at 1.3s and is skipped in Low Power Mode.
    - 8 bursts plus a trickle that tapers off, with 3D tumble and shared wind gusts.
    - Palette: accent, #F5C451, #5AD8A6, #FF6F91 and #EBECFE.
  - **Text:** a large 🎉, "Week Complete!", a streak line in accent, and an accent capsule button.
  - The App Store review prompt is requested here, behind gates (`Services/ReviewRequest/*`).
- **Android** (`feature/celebration/WeekCompleteDialog.kt:28-43`):
  - A stock `AlertDialog` with a gold trophy icon and a `TextButton`.
  - No sound, haptics or confetti.
- **Do:**
  - A full-screen dialog or overlay with the trophy springing in.
  - A one-shot `Canvas` confetti in the iOS palette, started at 1.3s.
  - The sound, with a `VibrationEffect.Composition` (`PRIMITIVE_CLICK` / `THUD`) on the same timings.
  - A filled `Button`.
  - Then Play In-App Review behind the same gate logic.

## 4. Nothing animates: numbers, progress, state changes

- **iOS:**
  - `contentTransition(.numericText())` on metric values (`Views/MetricCard.swift:49`, `ResourceDetailSections.swift:59`).
  - The Home greeting reveals glyph by glyph, from blur, with a spring (`Screens/MainScreen.swift:235-240`, `UI Elements/Animations/EmphasisAttribute.swift`).
  - The session's progress bar turns from accent to #6EE7A8 over 0.4s when the target is reached, and a check fades in (`StudyingView.swift:1208-1300`).
  - Chevrons rotate when sections expand.
- **Android:**
  - Values and bars jump.
  - "Target reached" appears instantly (`SessionScreen.kt:232-241`).
- **Do:** a small motion pass across the app:
  - `AnimatedContent` with a slide and fade for counters, minutes and the clock's paused/running state.
  - `animateColorAsState` and `animateFloatAsState` for progress.
  - `AnimatedVisibility` for checks, banners and empty→content changes.
  - `animateContentSize` on expandable cards.
  - Material motion tokens throughout.

## 5. Timer controls and study card lack hierarchy

- **iOS** (`Views/StudyingControlBar.swift:22-73`):
  - Three circular controls.
  - Pause/play is the hero: 73pt with a gradient ring from indigo to purple to pink.
  - Attach-resource and stop sit beside it at 42pt.
  - Block and progress details live in a bottom card made of translucent material (`StudyingView.swift:1208`).
- **Android** (`SessionScreen.kt:187-200`):
  - A tonal Pause pill and a filled Stop button side by side, with an outlined Change button below.
  - The progress bar is the stock `LinearProgressIndicator`.
- **Do:**
  - A large central `FilledIconButton` (or `LargeFloatingActionButton`) for pause/play, swapping its icon with `AnimatedContent`.
  - Smaller `OutlinedIconButton`s on each side for stop and resource.
  - Details in a `surfaceContainer` bottom card.
  - The gradient ring is optional brand flavor.

## 6. Switching tabs throws away each tab's state

- **iOS** keeps every tab alive (`Screens/MainTabView.swift:15-18`). Resources keeps its search, filters and scroll position.
- **Android** (`navigation/KnightTrainingApp.kt:267-270`): `selectTab` pops back to Home and pushes the tab again. The old entry and its saved state are dropped: the query, the kind/status filters and the scroll in `ResourcesScreen.kt:90-94`.
- **Do:**
  - Keep one back stack per tab, or save state per tab, as in Navigation 3's multiple-back-stacks recipe.
  - Re-tapping the current tab scrolls it to the top, as first-party Android apps do.
  - Back from a non-Home tab still returns to Home first.

## 7. No Training History screen (chart + activity grid)

- **iOS** (`Screens/Settings/TrainingHistoryScreen.swift`):
  - A weekly Swift Chart of planned vs actual minutes (`:168, :356`).
  - A 4×9 daily heat grid; press and hold a cell to see its date (`Views/MonthlyActivityGrid.swift:12`).
  - A commitment-streak `MetricCard` and `MiniCard` stats.
  - An empty state, "Nothing tracked yet".
  - Opened from the Home weekly-activity and Last Week tiles, Plan This Week, and the weekly review.
- **Android:** absent. No screen draws daily activity, and those tiles can't be tapped.
- **Do:**
  - A pushed screen with a `Canvas` chart (bars for actual, a dashed line for planned).
  - A heat grid that shows a tooltip on long-press.
  - Stat cards.
  - Make the Weekly Activity and Last Week tiles open it.

## 8. Tapping a goal on Home does nothing

- **iOS:** tapping a goal card opens `ActivityDetailsSheet` (`Views/Today/TodaysTasksCard.swift:134`). It shows:
  - progress
  - a Start button
  - a reminders list for that goal template: add, edit and paused states (`ActivityDetailsSheet.swift:42, 132-179`)
- **Android:** `GoalRow` (`feature/home/HomeScreen.kt:289`) isn't clickable; only its play and add-more icons respond.
- **Do:**
  - Make the row clickable and open a `ModalBottomSheet` with details and Start.
  - Per-goal reminders are still "not yet" in AGENTS.md. The sheet is the place they'll go, with a `TimePicker` dialog and weekday `FilterChip`s.

## 9. Onboarding has no artwork, motion or closing quote

- **iOS** (`Screens/OnboardingScreen.swift`):
  - Each page has its own illustration: the logo, `Onboarding_3_Study`, `Onboarding_2_Treadmill` and `target`.
  - Pages have a title, subtitle and description, with a staged fade-in on page 1 and a "Swipe to continue" hint.
  - The last page expands with a circle reveal into a Lao Tzu `QuoteScreen` (fades in over 0.5s, holds 3s, fades out over 0.7s). Skip crossfades into the same quote.
- **Android** (`feature/onboarding/OnboardingScreen.kt:43-48`):
  - A 72dp Material icon and one body string per page.
  - It ends abruptly in the app.
- **Do:**
  - Port the artwork as vector drawables.
  - Split each page into title, subtitle and body, with `AnimatedVisibility` fade-ins.
  - Add a quote page reached with a fade-through or container transform.
  - Add a page indicator.

## 10. Flat backgrounds and neutral cards: brand glow missing

- **iOS:**
  - **Background** (`UI Elements/Gradients/NewBackgroundView.swift:31-70`): a radial base from #151022 to #141319, with two glows: #8B5CF6 at the top left and #5516BE at the bottom right, at 0.7 opacity. It's used behind the tabs, the bookshelf and the timer.
  - **Cards** (`ViewModifiers/CardContainerModifier.swift`): 24pt radius, an indigo tint (#6B6EF8 at 0.28), a top highlight and a soft shadow.
  - **Progress bars** (`Views/ProgressBar.swift`): 8pt tall, rounded, with an optional gradient from #F3F2F8 to #A282FF.
- **Android:** a flat background, neutral `surfaceContainer` cards, and default indicators.
- **Do:**
  - One `GradientBackground` composable for Home, the tabs and the timer.
  - Shift the card container tokens in `designsystem/Theme.kt` slightly toward indigo.
  - Thicker, rounded progress indicators (the M3 Expressive `LinearWavyProgressIndicator` is optional).
  - Brand through color, as AGENTS.md says. No iOS shadows.

## 11. One widget, one size

- **iOS** (`KnightTrainingWidgets/`):
  - Weekly Progress (small)
  - Weekly Progress Report (medium)
  - Daily Activity, a GitHub-style grid (medium)
  - Training Stat, a configurable small widget: Elo, sessions, time, streak or total hours
- **Android** (`widget/WeekWidget.kt:46`): one widget, with no `SizeMode` and no configuration.
- **Do:**
  - `SizeMode.Responsive` so the week widget has small and medium layouts.
  - A Daily Activity widget, which reuses item 7's grid data.
  - A stat widget with a configuration activity.

## 12. Profile: no activity grid, simpler rating chart

- **iOS:**
  - **Profile** (`Screens/Profile/ProfileScreen.swift:599-617`): a Daily Activity grid.
  - **`EloHistoryChart`:**
    - hours as bars, with the committed hours as a dashed line
    - the latest rating labelled on the axis
    - a staggered reveal animation
    - empty leading weeks trimmed off
  - **`RatingOnlyChart`:** used when there are ratings but no logged time.
- **Android** (`feature/profile/RatingChart.kt`):
  - Bars and a line only.
  - No planned line, labels or animation.
  - With no data, it shows nothing (`:34`).
- **Do:**
  - Reuse item 7's grid.
  - Add the planned line, the rating label and an animated reveal.
  - Add a rating-only variant and an empty state.

## 13. Home plan header is missing actions and states

- **iOS:**
  - **Plan header** (`Views/Today/TodayTasksHeader.swift:49-69`, `Screens/MainScreen.swift:250-276`):
    - the week's tracked time shown large beside the plan name
    - an edit-plan pencil
    - "Well done!" once every goal is done
    - Share my week on Home
  - **After committing a week:** a one-time "Want a nudge?" reminders offer (`Views/EnableRemindersSheet.swift`).
  - **Settings:** a notice when notifications are blocked.
- **Android** (`feature/home/HomeScreen.kt:256-267`):
  - The plan name and "x of y · range" only.
  - Editing and sharing are only reachable from the Training tab.
  - There's no post-commit reminders offer.
  - It never checks `areNotificationsEnabled`, so the reminder switch can silently do nothing.
- **Do:**
  - Put tracked time as `headlineSmall` at the trailing edge.
  - Add an overflow menu on the plan card with Edit plan and Share week.
  - Add a "Well done" state.
  - Show a one-time bottom sheet after commit that leads into the `POST_NOTIFICATIONS` request.
  - Add a warning row that opens `ACTION_APP_NOTIFICATION_SETTINGS` when notifications are blocked.

## 14. Library cards, detail and empty/loading states are plain

- **iOS:**
  - **Card badges** (`Screens/Resources/ResourceCard.swift:24-70`): Reading/Studying, Completed and Auto-imported.
  - **Cover tint:** book and article details are tinted from the cover's color (`BookDetailsScreen.swift:20-36`).
  - **Completion history:** each finish is numbered and shows the time spent on that pass.
  - **Finish wording:** the button matches the kind, e.g. "Mark as Watched".
  - **States** (`ResourcesScreen.swift:436-509`): loading, error with Retry, empty, and no-matches, each with an icon and text.
- **Android:**
  - **Cards:** a progress bar only (`feature/resources/ResourcesScreen.kt:191-200`).
  - **Detail screen:** a plain header.
  - **Finish wording:** always "Mark finished" in an `AlertDialog`.
  - **States:** nothing while loading, and text-only empty states (`:140-144, 213-219`).
- **Do:**
  - `Badge` or `AssistChip` overlays on the cards.
  - androidx Palette to tint the detail header.
  - Wording per kind.
  - A shared empty-state composable (icon, title, body, action) and a loading indicator.

## 15. Weekly review and week log show less

- **iOS:**
  - **Weekly review** (`Screens/WeeklyReviewScreen.swift:248-276`):
    - per-goal results ("You completed X of Y" plus a line for each goal)
    - the full `LastWeekCard`, which taps through to Training History
    - expandable notes
  - **Week log** (`Screens/WeekTrainingLogScreen.swift`):
    - shows the saved reflection (`:38, 111`)
    - swipe-to-delete with confirmation (`:194`)
- **Android:**
  - **Review** (`feature/review/WeeklyReviewScreen.kt:221-243`): a count, a bar and the time. Last week appears as quotes only, and notes are plain text.
  - **Week log** (`feature/training/WeekLogScreen.kt:92-121`): a finished week shows only a "Reflect on week" button, never what was written, and sessions can't be swiped.
- **Do:**
  - Per-goal `ListItem` results in the review.
  - Reuse `LastWeekTile` in the review.
  - Expand notes with `animateContentSize`.
  - A reflection card on the week log.
  - `SwipeToDismissBox` with delete and an undo snackbar.

---

## Next in line

These narrowly missed the list:

- **Session notification:** no accent color (`setColor`/`setColorized`) and no block position or next block. The Live Activity shows both (`KnightTrainingWidgets/StudySessionLiveActivity.swift:217-305`; Android: `session/SessionNotifier.kt:37-65`).
- **Plan This Week:** no Last Week card and no empty state (`Screens/PlanThisWeekScreen.swift:29, 115`).
- **Session editor:** can't link a resource (`LogPastSessionSheet.swift:421`). `ResourcePickerField` already exists to do it.
- **Plan blocks:** no long-press menu or swipe to delete.
- **Create Plan:** no "Training tips" card (`CreateTrainingPlanScreen.swift:209-240`).
- **Start Training:** with a single activity type, iOS skips the picker.
- **Settings:** no Thank you or open-source licenses rows.
- **Chess.com:** account connect (`Screens/Profile/ChessComConnectSheet.swift`). It's a feature rather than polish.

## Deliberately left out

- **"Coach skipped" toast:** debug builds only, by design (AGENTS.md).
- **Home edit mode:** the drag-handle sheet is the Material equivalent of iOS's jiggle mode.
- **Duration entry:** chips plus a text field is acceptable Material in place of iOS's wheel picker.
- **iOS dead code:** `CheckmarkCircle`, `MengToGradient`, `Arc`. Nothing uses them, so there's nothing to port.
- **Book scanner:** debug/TestFlight only on iOS.
- **AGENTS.md "Not yet" items:** playlist import, per-block reminders, bonus goals from the plan editor.
