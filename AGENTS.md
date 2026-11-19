# AGENTS.md

The Android port of Knight Training, a chess training-habit app. Native Kotlin + Jetpack Compose,
started 2026-11-12.

## The spec is the iOS app

The iOS repo (`~/Desktop/oathbreaker`) is the source of truth for product behaviour. Its
`AGENTS.md` (product principles, data model, key behaviours, localization rules) and `docs/`
apply here unchanged unless this file says otherwise. When porting a rule, port its iOS tests
too: the Core test suite is the conformance spec, so both platforms agree on goal crediting, week
windows, the 48h first-week extension, time-control categories and the reminder policy.

Surface any disagreement between the two apps rather than picking one silently.

## Why native, not shared code

Chosen for stability (2026-11-12). Kotlin Multiplatform would mean rebuilding the iOS app's
foundation; Skip/Swift-on-Android depends on GRDB, which has no official Android support. Don't
re-propose either without a concrete reason.

## Modules

- **`core`**: plain Kotlin/JVM, no Android. Domain models and persistence, the counterpart of the
  iOS `Core` package. Its tests run on the JVM in seconds, no emulator. Keep it Android-free.
  - `model/`: pure domain types and rules (`WeeklyPlan`, `WeeklyGoal.creditCandidates`,
    `TimeControl`, `WeeklyTrainingStats`, the reminder/stretch policies, `PlanEstimator`).
  - `persistence/`: Room entities, DAOs (the SQL), `Mapping.kt` (entity ↔ domain).
  - `store/`: `TrainingStore`, the domain-level API the app calls, split by area like iOS's
    `AppDatabase+*.swift` (`TrainingStoreWeeks.kt`, `TrainingStoreSession.kt`, `WeeklySurface.kt`, …).
- **`app`**: Android app, Compose UI, platform services. Depends on `core`, never the reverse.

### Where the port differs from iOS on purpose

- **No translated text in `core`**: it has no Android resources. Rules that need words take them as
  arguments (`PlanTemplate.untitledName(base, numbered)`, `displayName(fallback)`), and
  `WeeklySurface.planName` is the raw stored name. Display strings (`displayTitle`,
  `targetDescription`, `progressDescription`, date ranges) live in the app.
- **Weeks are a `LocalDate` + a `WeekCalendar`** (zone and first weekday), passed explicitly; iOS
  freezes `weekStart` as an instant against `Calendar.current`. Local wall-clock weeks are the
  accepted difference if the zone changes mid-week.
- **Activity types and resource kinds resolve from `Presets` in memory**, never a join: the rows
  are seeded once and never change. Resources load in one batch per query (iOS does a query per
  goal).
- **An activity's resource is the full row.** iOS's join leaves out `description`/`isAutoImported`
  and lets `lastAccessedAt` default to now.
- **`findOngoingActivityId` takes the most recently started**; iOS's has no `ORDER BY`.
- `WeeklyGoal.withProgress`/`addingProgress` weren't ported: no iOS caller, and they'd drop
  `goalTemplateId`/`isBonus`.
- Change events are `SharedFlow`s with no replay (iOS's `EventBroadcaster`); live data is
  `invalidationTracker.createFlow` + a re-read in one transaction (iOS's `ValueObservation`).

## Persistence (Room)

- Schema version 1 is the iOS schema's end state after its migration `v24`. Data never moves
  between platforms, so iOS's migrations aren't replayed. Every later change is a new version with
  a real Room migration. Schemas are exported to `core/schemas/`; commit them.
- **Bundled SQLite** (`androidx.sqlite:sqlite-bundled`) on every device and in tests, configured
  once in `AppDatabase.configure()`. Android 9's system SQLite (3.22) lacks upserts and window
  functions, which iOS queries rely on.
- Encodings differ from iOS on purpose: UUIDs are TEXT (iOS stores 16-byte BLOBs), `Instant` is
  epoch milliseconds, `LocalDate` is ISO text. See `Converters.kt`.
- `activity_type`/`resource_kind` have no `title` or `icon` columns: names derive from `code` at
  display time (iOS AGENTS.md §14), and iOS's icons are SF Symbol names.
- Never `INSERT OR REPLACE` (or `OnConflictStrategy.REPLACE`) a row other tables reference: it's a
  delete, and fires their cascades. Use `@Upsert` or an explicit update.
- **`@Upsert` only on tables whose sole unique key is `id`.** Room answers *any* unique conflict
  with an update by id, so on `weekly_plan` (`weekStart`) a new id for a committed week silently
  did nothing, where iOS throws (found porting `ResaveKeepsChildRowsTests`). Tables with a second
  unique key get an explicit `INSERT … ON CONFLICT(id) DO UPDATE` instead (`WeekDao.upsertPlan`,
  `AccountDao.upsert`); `weekly_review`, `shared_course` and `catalog_course` will need the same.
- Every multi-step write is one transaction (`TrainingStore.write { }`); DAO calls inside it join
  it, pinned by `WeeklySurfaceTest.aFailedSessionWriteLeavesNothingBehind`.

## Design

**The closest native Android UX, not a copy of the iOS screens** (maintainer, 2026-11-12). The iOS
app defines *what* each screen does and says; Android decides *how* it looks and behaves, the way
a first-party Material 3 app would: system back and predictive back, edge-to-edge insets, bottom
sheets and dialogs where iOS uses sheets, a navigation bar for tabs, the system font, Material
motion. iOS's pixel-perfect-to-Figma rule doesn't apply here.

Brand carries over through color only: dark-only, accent `#8D59E9`. Tokens in
`app/.../designsystem/Theme.kt` mirror iOS `DesignSystem/Theme.swift` and fill the Material 3
scheme, surface-container ladder included.

## App structure

- `navigation/`: one Navigation 3 back stack (`KnightTrainingApp`). The bottom bar shows on tab
  roots only; non-Home tabs sit on top of Home, so back returns Home first. Onboarding gates the
  whole app until finished, and the week-complete dialog listens app-wide.
- **Tabs keep their state** (iOS keeps every tab alive): `rememberTabKeepingStateDecorator`
  (`navigation/TabState.kt`) skips forgetting saveable state for entries marked `TabRootMetadata`,
  so search, filters and scroll survive a switch. ViewModels are still rebuilt; they only re-read
  the store. Re-tapping the current tab scrolls it to the top (`ScrollToTopOnReselect`). Pinned by
  `TabStateTest`.
- `feature/<area>/`: a screen and its ViewModel per area (`home`, `plan`, `session`, `training`,
  `onboarding`, `celebration`). ViewModels come from `appViewModel { … }`, built from
  `AppContainer` by hand.
- `ui/Labels.kt`: every code → display text/icon mapping. Strings live in `strings.xml`.
- **The timer's background** (`feature/session/TimerBackground.kt`, `designsystem/Backgrounds.kt`):
  iOS's purple glow with its drifting mesh over it, the blob physics ported constant for constant.
  Same battery rules: first 10 running minutes only, frozen while paused, off under battery saver,
  and a notice that brings it back. Android adds one: off when system animations are removed.
  Blur needs API 31; below that the blobs are radial gradients. The frame loop uses
  `withInfiniteAnimationFrameNanos`, or UI tests never go idle.
- **The timer's controls** (`TimerControls.kt`) follow iOS's: a large ringed pause/play between an
  attach-a-resource button (resources can now be attached mid-session, as on iOS) and Stop, all
  icon buttons labelled for TalkBack, and a bottom study card whose bar turns green with a check on
  reaching a duration target. Pinned by `TimerScreenTest`.
- **No foreground service for the timer.** `PracticeSession` (core) derives elapsed time from
  timestamps and `SessionRepository` writes it to a file on every change, so the process can die
  mid-session and resume intact. The ongoing notification's clock is a system chronometer; Pause
  and Resume go through `SessionActionReceiver`, Stop opens the app on the review. Play also
  restricts foreground-service types, and a study timer fits none of them.
- **Default block names aren't stored.** A block without a typed or preset name is saved with
  `name = null` and titled from its activity at display time (`blockTitle`), where iOS stores the
  composed English text. Preset titles are stored, like a typed name.
- Build extended FABs with `LabeledFab`: the `icon`/`text` overload left the label out of the
  semantics tree, so TalkBack read an unlabelled button.
- Text outside a `Scaffold`/`Surface` defaults to black: wrap full-screen content in a `Surface`.
- **Haptics and sounds go through `feedback/Feedback.kt`** (iOS `FeedbackService`, same event
  names and rules: only deliberate taps, sound for accomplishments only, never from a timer tick).
  Call sites use `rememberFeedback()` and name the moment, never a `HapticFeedbackType`. Sounds are
  iOS's CAFs converted to WAV (`res/raw`), played on the sonification stream so silent mode mutes
  them. "Goal completed" is heard from the app-wide weekly-surface observer
  (`GoalCompletionSnapshot`); its "whole week" rule is the celebration's own (bonus goals don't
  count), where iOS counts every goal.
- **Motion** (`ui/Motion.kt`): progress bars animate to new values (`AnimatedLinearProgress`),
  changing numbers roll (`AnimatedValueText`, iOS's `numericText`), and Home's greeting eases in
  once (`RevealedTitle`). Lazy list items use `animateItem()`; cards that change size use
  `animateContentSize()`. Use these rather than a bare `LinearProgressIndicator` or `Text` for live
  values, except on share cards and the widget, which render a still frame.
- **The week-complete celebration** (`feature/celebration/`) is a full-screen dialog: iOS's sound
  with a vibration pattern on the same measured hits (`WeekCompleteSequence`, pinned to the WAV by
  `WeekCompleteSoundTest`), confetti ported count for count and held for the mating move, skipped
  under battery saver or with animations off. iOS also asks for an App Store review here; Play's
  in-app review isn't wired yet.

## Build & test

JDK: Android Studio's bundled JBR. SDK: `~/Library/Android/sdk` (`local.properties`, gitignored).

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:test           # JVM unit tests, fast
./gradlew :app:assembleDebug   # debug APK
# Device tests (CoreLoopTest); needs a running emulator. Keeping the app installed keeps the
# screenshots each step writes to /sdcard/Android/data/<package>/files/screenshots.
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

`compileSdk` is 37 (current AndroidX requires it); `targetSdk` 36, `minSdk` 28. Emulator AVD:
`KT_Pixel_API36`. Headless, start it with `-gpu swiftshader_indirect`, or screenshots come back
black. Its System UI is slow under software rendering and throws "isn't responding" dialogs;
they're the emulator's, not the app's.

UI tests act through the semantics tree (what TalkBack reads), never screen coordinates. The
maintainer confirms how interactions *feel* on a device themselves.

## Phases

0. Skeleton: Gradle, theme tokens, Room schema + seeding. **Done 2026-11-12.** Bugsnag waits
   for an Android project API key.
1. Port Core's domain rules, tests first. No UI. **Done 2026-11-12**: the pure rules, and the
   database operations the core loop needs (committing a week, `logSession`, the weekly surface
   and its observation, editing sessions and plans, streaks, archive, delete-all), with their iOS
   tests. Resources, Lichess, milestones, reviews and the plan suggester port in their own
   phases. The weekly review's queries and `applyCoachTarget` followed with the coach (Phase 6).
2. Core loop UI: onboarding, plan creation + presets, weekly commit, Home (fixed layout),
   practice timer (ongoing notification), log/edit sessions, history. **Built 2026-11-12**, pending
   the maintainer's device check. The weekly review screen and resource picking followed. Not yet:
   "add more" bonus goals from the plan editor, a time
   control when switching to "Something else" mid-session, and crediting an ad-hoc segment when
   several goals fit (it credits only a single fit; iOS asks).
3. Resources: library, detail screens, catalog sync, book lookup, link metadata. **Built
   2026-11-12**: the library tab, a shared kind-aware detail screen, add (link enrichment +
   catalog browser) and edit (cover photo picker), share-to-app, and resource links on plan
   blocks, bonus goals and the session review. Not yet: YouTube playlist import (needs an
   Android-restricted API key from the maintainer), the `shared_course` table and its catalog
   merge, a "see all" shelf per kind.
4. Lichess: OAuth, Elo, activity sync (one request at a time, 60s after a 429), milestones.
   **Built 2026-11-12**:
   - Core: `core/lichess/` (payload models, `LichessClient` + `LichessRequestGate`, `LichessSync`).
   - Store: `TrainingStoreLichess.kt` and `TrainingStoreMilestones.kt`.
   - Sign-in: PKCE in a Custom Tab with no AppAuth dependency (`app/.../lichess/`). The token is
     encrypted with a Keystore key in `noBackupFilesDir` and never written to the database (the
     `account` token columns stay `NULL`).
   - Syncs run on every `MainActivity.onStart`, at most every two minutes.
   - Home has the offer banner and confirm sheet and the milestones card; Profile has the
     connect, ratings and disconnect card.
   - Pinned by `LichessSyncTest`, `LichessBatchTest`, `MilestoneTest` and
     `LichessStreakDetectionTest` (core), plus `LichessProfileTest` and `LichessHomeTest`
     (instrumented).

   Studies sync and Profile's rating chart followed: new studies join the library, matched by
   study id (`LichessSyncTest`), and each Profile rating chip picks the rating the 4-week chart
   draws over time studied (`LichessProfileTest`). "Delete all data" also signs out of Lichess.

   Not yet:
   - A real lichess.org sign-in, which needs the maintainer's account on a device.
5. Reminders, Glance widget, share-to-app intent. **Built 2026-11-12**: behind-only training
   reminders on picked days (one inexact alarm, re-armed on boot/clock change), the share target
   (Phase 3), and a Home Screen widget (`widget/WeekWidget.kt`): plan, goals, minutes and the
   continue line, redrawn by the app on every weekly-surface change (it runs in-process, so it
   reads the database directly, no App Group snapshot). Its layout is pinned by
   `WeekWidgetTest`. Not yet: per-block activity reminders.
6. Suggest a plan, weekly coach (Gemini Nano where available), customizable Home, share cards,
   translations converted from the iOS `.xcstrings`.
   - **Suggest a plan, built 2026-11-12.** `PlanSuggestionEngine`, `RatingScale` and
     `planSuggestionHistory` are ported with their iOS tests, plus the builder's sheet
     (`SuggestPlanTest`). Not yet: the free-text note, which needs an on-device model.
   - **Translations, built 2026-11-12.** pt-PT, es, pt-BR, nl, it, de and fr, generated by
     `tools/l10n/` (see its README). 231 strings reuse the iOS catalog; the other ~160 were drafted
     against the iOS glossaries. All of it awaits native review, as on iOS. `locales_config`
     offers a per-app language in system Settings.
   - Lint's resource checks are clean. The code still trips Compose's `NonObservableLocale` and
     `LocalContext*` checks (pre-existing, so `lintDebug` fails); fix those before making lint a
     gate.
   - **Customizable Home, built 2026-11-12.** The plan card stays pinned; below it the user's tiles
     (`HomeTile`/`HomeLayout`, ids identical to iOS, saved in DataStore so "Delete all data" keeps
     them). Wide tiles take a row, small ones pair up. Edited in `EditHomeSheet` (drag handles,
     add/remove, reset), opened from Home's top bar and Profile. iOS's in-place jiggle mode isn't
     ported: Android has no such convention. Pinned by `HomeLayoutTest` and `EditHomeTest`;
     drag-reordering needs a device check.
   - **Share cards, built 2026-11-12.** "Share my week" (week log's top bar) and "Share my library"
     (Resources' top bar): `WeekShareSummary`/`LibraryShareSummary` in Core (`ShareSummaryTest`),
     the cards in `feature/share/`, rendered through a graphics layer to a PNG in
     `cache/shares/` and handed to the system share sheet via a `FileProvider`. Pinned by
     `ShareCardsTest`.
   - **Weekly coach, built 2026-11-12, debug builds only.** Gemini Nano via ML Kit's Prompt API
     (beta, `com.google.mlkit:genai-prompt`), only on Nano 3 or newer (`ModelGate`; the version
     is read from `getBaseModelName()`, whose format Google doesn't document, so an unreadable
     name keeps the coach off and is logged under `GeminiNano`).
     - `core/coach/` ports iOS's pipeline with the prompts word for word: context builder,
       deterministic backstops (blitz/bullet, coach mentions while the gate is closed, an echoed
       wish, another goal's number), the verifier pass, the coaching gate, puzzle weakness and
       target change.
     - JSON in the prompt replaces Apple's guided generation; ML Kit's structured output is
       still alpha.
     - `app/coach/` holds the Nano adapter (a system prompt where supported, temperature 0.2)
       and `WeeklyTipService` (30 s timeout, silent on any failure).
     - The review screen shows the tip card; accepting saves the focus and applies the target
       (`applyCoachTarget`).
     - Pinned by `WeeklyTipCoachTest`, `CoachSignalsTest`, `CoachStoreTest`, `ModelGateTest`
       and `WeeklyCoachTest`.
     - When the coach stays silent in a debug build, a toast says why ("Coach skipped: …"), and
       the reason is logged under `WeeklyCoach`.
     - Each launch asks AICore for the model when it's downloadable, so the first review
       normally has it (where the coach is on only, to spare everyone else a multi-gigabyte
       download). The status is logged under `GeminiNano`.
     - Tip *quality* is unverified: no emulator runs Nano, so it needs a Pixel 9 or newer, in
       English and each shipped language, before it leaves debug builds.
