# Architecture

How Knight Training for Android is put together. For the rules (what must stay true, and where
the port differs from iOS on purpose), see [AGENTS.md](../AGENTS.md). This document describes how
things work; AGENTS.md is the authority on what's allowed.

Paths are relative to `app/src/main/kotlin/com/knightindustries/knighttraining/` (app) or
`core/src/main/kotlin/com/knightindustries/knighttraining/core/` (core).

## Two modules

```
┌──────────────────────────── app ────────────────────────────┐
│ Compose screens · ViewModels · navigation · notifications   │
│ widgets · reminders · Lichess sign-in · Gemini Nano adapter │
└──────────────────────────────┬──────────────────────────────┘
                               │ depends on
┌──────────────────────────── core ───────────────────────────┐
│ model/ (pure rules) · store/ (TrainingStore API)            │
│ persistence/ (Room) · lichess/ · coach/ · session/ · net/   │
└─────────────────────────────────────────────────────────────┘
```

**`core`** is plain Kotlin on the JVM, with no Android imports. It's the counterpart of the iOS
`Core` package, and its tests are ports of the iOS tests.

- **`model/`**: domain types and the rules on them. Examples: `WeeklyPlan`, `WeeklyGoal`'s
  crediting (`creditCandidates`), `TimeControl`, the reminder and stretch policies,
  `PlanSuggestionEngine` and `DailyTrainingIntensity`. They're pure functions, easy to test in
  isolation.
- **`persistence/`**: Room entities, DAOs (the SQL), and `Mapping.kt`, which converts between
  entities and domain types.
- **`store/`**: `TrainingStore`, the only API the app calls for data. It's split into files by
  area, the way iOS splits `AppDatabase+*.swift`: weeks, sessions, library, Lichess, milestones,
  reviews, history, and the weekly surface.
- **`session/`**: `PracticeSession`, the timer's state machine.
- **`lichess/`**: the HTTP client, a request gate (one request at a time, a 60 s pause after a
  429), and the sync.
- **`coach/`**: the weekly coach's prompts, context builder and backstops. These don't depend on
  any model; the app supplies one.
- **`net/`**: small HTTP helpers.

There's no translated text in `core`. Where a rule needs words, they come in as arguments, and
every display string lives in the app.

**`app`** is the Android app. It depends on `core`; `core` never depends on it.

## The container

`KnightTrainingApplication` builds one `AppContainer` (in `KnightTrainingApplication.kt`) and
everything hangs off it. There's no dependency-injection framework.

| Member | What it holds |
|---|---|
| `database`, `store` | The Room database file and the `TrainingStore` over it |
| `preferences` | `data/AppPreferences.kt`: DataStore settings such as onboarding, Home layout, reminders, the reminders offer, sounds and haptics |
| `feedback` | `feedback/Feedback.kt`: haptics and sounds |
| `sessions` | `session/SessionRepository.kt`: the timer session in progress, plus its notification |
| `lichessCredentials`, `lichessClient`, `lichessSync`, `lichessAuth` | The Lichess sign-in, the API client and the sync |
| `catalogSync`, `enricher` | The course catalog, and metadata lookup for links that are shared or pasted in |
| `weeklyTipService` | The Gemini Nano coach (debug builds only) |

Screens get their ViewModel from `appViewModel { container -> … }` (`ui/AppViewModel.kt`). Each
ViewModel is scoped to its navigation entry and built by hand from the container.

On start, the application also:

- Seeds the preset rows and syncs the course catalog.
- Watches the weekly surface (described next). It redraws every widget on each change and plays
  the "goal completed" sound when a goal crosses its target.

## Data flow

The one read model that matters is the **weekly surface** (`store/WeeklySurface.kt`): this
week's plan and goals, recent activity, streaks, the daily totals, the resource to continue, and
the Lichess rating. Home, the widgets and the goal-completion sound all read it.

```
 write (log a session, edit a plan, …)
        │  TrainingStore.write { }  — one transaction
        ▼
 Room tables ──invalidationTracker──▶ observeWeeklySurface()
                                         │  re-reads everything in one transaction
                                         ▼
                       Home · widgets · goal-completed sound
```

- **Writes.** Every multi-step write runs in a single transaction (`TrainingStore.write { }`).
  A write that fails leaves nothing behind.
- **Live data.** Live reads use `invalidationTracker.createFlow(...)` and re-read the data in a
  transaction, like iOS's `ValueObservation`. Change events, such as `weeklyPlanCompletedEvents`
  (which triggers the celebration), are `SharedFlow`s that don't replay, like iOS's
  `EventBroadcaster`.
- **One-off reads.** Training History, the review context and the library detail screen read
  once when they load. Screens whose data changes somewhere else with no event (for example the
  week log after a reflection is saved) re-read when they resume.

## Persistence

- **Schema.** Room schema version 1 is the end state of the iOS schema after its migration
  `v24`. Every later change is a new version with a real migration, and the schemas are exported
  to `core/schemas/`.
- **SQLite.** The app bundles its own SQLite (`sqlite-bundled`) on every device and in tests,
  because Android 9's system SQLite lacks upserts and window functions.
- **Encodings.** These differ from iOS: UUIDs are stored as TEXT, an `Instant` as epoch
  milliseconds, and a `LocalDate` as ISO text.
- **Weeks.** A week is a `LocalDate` plus a `WeekCalendar`, which knows the zone and the first
  weekday. The calendar is always passed in explicitly, which is how the tests pin the clock and
  zone.
- **Upsert hazards.** These come from bugs already found, and AGENTS.md lists them: never use
  `REPLACE` on rows that other tables reference, and use `@Upsert` only on tables whose sole
  unique key is `id`.

Settings that aren't training data live in DataStore (`AppPreferences`). They include onboarding,
the Home layout, reminders, the sounds and haptics switches, and the reminders-offer flags. So do
a few markers, such as the last week reviewed. "Delete all data" clears the database and leaves
these settings alone.

## Navigation

`navigation/KnightTrainingApp.kt` runs a single Navigation 3 back stack.

- **Onboarding** gates the whole app until it's finished.
- **Tabs.** Home is the root, and the other three tabs sit on top of it, so back always returns
  to Home first. The bottom bar shows only on tab roots.
- **Tab state.** Each tab's root keeps its state when you switch away: entries marked
  `TabRootMetadata` skip forgetting their saveable state (`navigation/TabState.kt`). Re-tapping
  the current tab scrolls it to the top (`ui/TabReselect.kt`).
- **Routes** are `@Serializable`, so the back stack survives the process being killed.
- **App-wide listeners.** The week-complete celebration and the offer to review last week listen
  across the whole app.

## Subsystems

### Practice timer

- **State.** `PracticeSession` (in core) derives elapsed time from timestamps. `SessionRepository`
  writes the session to `practice_session.json` on every change, so the timer survives the
  process being killed without a foreground service.
- **Notification.** The ongoing notification's clock is a system chronometer. Pause and Resume go
  through `SessionActionReceiver`; Stop opens the app on the review.
- **Screen.** `feature/session/` holds the timer screen: the animated mesh background with its
  battery rules (`TimerBackground.kt`), the controls and the study card (`TimerControls.kt`).

### Reminders

`reminders/Reminders.kt` keeps a single inexact alarm (`setAndAllowWhileIdle`). It's re-armed on
boot and when the clock changes. When the alarm fires, the reminder policy in core decides
whether to notify: only on a chosen day, only if nothing was trained that day, and only while
the week still has work left.

Each goal's own reminders (`reminders/ActivityReminders.kt`) hold one inexact alarm apiece for
their next occurrence. They fire on their days whatever the week's progress, read the block's
title when they fire, and are re-armed with the weekly one after a reboot or clock change.

### Lichess

- **Sign-in.** OAuth with PKCE in a Custom Tab, with no client registration. The redirect is
  `com.knightindustries.knighttraining://callback`, handled by `LichessCallbackActivity`.
- **Token.** It's encrypted with a Keystore key in `noBackupFilesDir`, never stored in the
  database.
- **Sync.** It runs on every `onStart`, at most every two minutes, one request at a time.

### Widgets

`widget/` holds three Glance widgets, all redrawn by `WidgetStyle.updateAll` whenever the weekly
surface changes. They run in the app's process, so they read the database directly.

- **`WeekWidget`** is responsive: small, medium and large layouts.
- **`DailyActivityWidget`** draws its grid as a single bitmap.
- **`StatWidget`** shows a stat chosen in `StatWidgetConfigActivity`.

Glance can't draw a determinate ring, so `WidgetStyle.ring` renders one to a bitmap.

### Feedback (haptics and sound)

`feedback/Feedback.kt` maps named moments (`FeedbackEvent`) to haptics and to the two sounds
converted from iOS. Call sites name the moment through `rememberFeedback()` and never pick a
haptic type themselves. Sounds play on the sonification stream, so silent mode mutes them.

### Weekly coach

`coach/` adapts Gemini Nano through ML Kit's Prompt API. It needs Nano 3 or later, and
`ModelGate` checks for that. The prompts, the verifier pass and the backstops are in
`core/coach/`. The coach is on in debug builds only.

### Translations

`values-*/strings.xml` are generated by `tools/l10n/build.py` from the iOS string catalogs, plus
`tools/l10n/drafts.json` for strings iOS doesn't have. See
[tools/l10n/README.md](../tools/l10n/README.md).

## Design system

`designsystem/Palette.kt` holds iOS's light and dark palette (navy, royal blue, cyan, cream),
and `designsystem/Theme.kt` fills a Material 3 scheme from it for each mode; the app follows the
system setting. `designsystem/Backgrounds.kt` has the brand glow (onboarding, the timer, the book
shelf) and the timer's mesh, both in the app icon's colors. Tabs sit on the flat background.
`ui/Motion.kt` has the shared motion pieces: animated progress bars, rolling values, and the
greeting reveal.

Everything else is stock Material 3: the system font, Material motion, bottom sheets, predictive
back, edge to edge. The brand carries over through colour only.
