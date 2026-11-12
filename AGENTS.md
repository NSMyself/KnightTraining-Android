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
- **`app`**: Android app, Compose UI, platform services. Depends on `core`, never the reverse.

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

## Design

Dark-only, accent `#8D59E9`. Tokens in `app/.../designsystem/Theme.kt` mirror iOS
`DesignSystem/Theme.swift`. Material 3 is plumbing only; the scheme is filled from the tokens.
Typography is still the system default: SF Rounded can't ship on Android, and the replacement
font is undecided.

## Build & test

JDK: Android Studio's bundled JBR. SDK: `~/Library/Android/sdk` (`local.properties`, gitignored).

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:test           # JVM unit tests, fast
./gradlew :app:assembleDebug   # debug APK
```

`compileSdk` is 37 (current AndroidX requires it); `targetSdk` 36, `minSdk` 28. Emulator AVD:
`KT_Pixel_API36`.

## Phases

0. Skeleton: Gradle, theme tokens, Room schema + seeding. **Done 2026-11-12.** Bugsnag waits
   for an Android project API key.
1. Port Core's domain rules, tests first. No UI.
2. Core loop UI: onboarding, plan creation + presets, weekly commit, Home (fixed layout),
   practice timer (foreground service + ongoing notification), log/edit sessions, history. First
   tester build.
3. Resources: library, detail screens, catalog sync, book lookup, link metadata.
4. Lichess: OAuth (AppAuth), Elo, activity sync (one request at a time, 60s after a 429),
   milestones.
5. Reminders, Glance widget, share-to-app intent.
6. Suggest a plan, weekly coach (Gemini Nano where available), customizable Home, share cards,
   translations converted from the iOS `.xcstrings`.
