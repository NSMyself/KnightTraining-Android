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
- `feature/<area>/`: a screen and its ViewModel per area (`home`, `plan`, `session`, `training`,
  `onboarding`, `celebration`). ViewModels come from `appViewModel { … }`, built from
  `AppContainer` by hand.
- `ui/Labels.kt`: every code → display text/icon mapping. Strings live in `strings.xml`.
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
   phases. The weekly review's queries (and `applyCoachTarget`) aren't ported yet; port them
   with the review screen.
2. Core loop UI: onboarding, plan creation + presets, weekly commit, Home (fixed layout),
   practice timer (ongoing notification), log/edit sessions, history. **Built 2026-11-12**, pending
   the maintainer's device check. Not yet: picking a resource for a session or block (needs Phase
   3's library), the weekly review screen, "add more" bonus goals from the plan editor, a time
   control when switching to "Something else" mid-session, and crediting an ad-hoc segment when
   several goals fit (it credits only a single fit; iOS asks).
3. Resources: library, detail screens, catalog sync, book lookup, link metadata.
4. Lichess: OAuth (AppAuth), Elo, activity sync (one request at a time, 60s after a 429),
   milestones.
5. Reminders, Glance widget, share-to-app intent.
6. Suggest a plan, weekly coach (Gemini Nano where available), customizable Home, share cards,
   translations converted from the iOS `.xcstrings`.
