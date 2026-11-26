# Knight Training for Android

A chess training-habit app: plan a week of training, commit to it, time your sessions against
the plan, and look back at how the week went. This is the native Android port of the iOS app,
written in Kotlin with Jetpack Compose and Material 3.

The iOS app (`~/Desktop/oathbreaker`) is the product spec. This app does what iOS does and says
what iOS says, built the way a first-party Material 3 app would build it rather than as a copy of
the iOS screens. [AGENTS.md](AGENTS.md) records every deliberate difference between the two.

## What's in it

- **Weekly plans.** Build a plan from presets, a suggestion, or by hand, commit it to the week,
  and see each goal's progress on Home. A committed week is never renewed automatically.
- **The practice timer.** It runs without a foreground service: elapsed time comes from
  timestamps, so the session survives the process being killed. The ongoing notification has a
  live clock with Pause, Resume and Stop.
- **History.** Week logs, a weekly review with a reflection, and Training History, which charts
  planned against actual time and shows a daily activity grid.
- **Resources.** A library of books, courses, videos, articles and Lichess studies, with
  progress, completions and the time spent on each.
- **Lichess.** Sign in with OAuth (PKCE), rating history, activity detection that offers to log
  games and puzzles, and milestones.
- **Reminders.** Sent only on days you haven't trained, while the week still has work left.
- **Home screen widgets.** The week at three sizes, a daily activity grid, and a stat of your
  choice.
- **Weekly coach.** A tip about last week, written on the device by Gemini Nano after the weekly
  review and shown on Plan This Week. Debug builds only for now.
- **Translations.** English plus pt-PT, pt-BR, es, fr, it, de and nl, mostly reused from the
  iOS catalog.

## Requirements

- Android Studio, whose bundled JDK (JBR) is what the build uses
- The Android SDK, with `compileSdk` 37 installed
- To install the app: a device or emulator on Android 9 (API 28) or later

| | |
|---|---|
| Application id | `eu.numerosinusitados.knighttraining` (Kotlin package: `com.knightindustries.knighttraining`) |
| `minSdk` / `targetSdk` / `compileSdk` | 28 / 36 / 37 |
| Java source/target | 17 |

## Getting started

```sh
# Point Gradle at Android Studio's JDK (macOS path shown).
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# local.properties is gitignored; it needs your SDK path, e.g.
#   sdk.dir=/Users/<you>/Library/Android/sdk

./gradlew :app:assembleDebug          # build a debug APK
./gradlew :app:installDebug           # install it on the connected device or emulator
```

Or open the project in Android Studio and run the `app` configuration.

## Tests

```sh
./gradlew :core:test                 # domain rules and persistence, on the JVM, in seconds
./gradlew :app:testDebugUnitTest     # app-side JVM tests
./gradlew :app:connectedDebugAndroidTest   # UI tests; needs a running emulator or device
```

See [docs/testing.md](docs/testing.md) for the emulator setup, the screenshots the UI tests
write, and how to run a single test.

## Project layout

```
core/    Plain Kotlin/JVM, no Android: domain models, rules, Room persistence, the store API.
app/     The Android app: Compose UI, navigation, platform services, widgets.
tools/   tools/l10n generates the translated strings.xml files from the iOS catalogs.
docs/    Architecture, testing, and the UI/UX gap list against iOS.
```

`core` never depends on Android, so its rules are tested on the JVM in the same way as the iOS
app's Core package. [docs/architecture.md](docs/architecture.md) walks through both modules.

## Documentation

| Document | What it covers |
|---|---|
| [AGENTS.md](AGENTS.md) | The rules: where the port differs from iOS on purpose, persistence conventions, design rules, app structure, and each phase's status. Read it before changing anything. |
| [docs/architecture.md](docs/architecture.md) | How the app is put together: modules, data flow, persistence, navigation, and how each subsystem works. |
| [docs/testing.md](docs/testing.md) | Running the test suites, the emulator, screenshots, and conventions for writing tests. |
| [docs/ux-gaps.md](docs/ux-gaps.md) | The UI/UX comparison with iOS and what was done about each gap. |
| [tools/l10n/README.md](tools/l10n/README.md) | Generating translations, and how drafts work for strings iOS doesn't have. |

## Contributing

- Commit in small, logically grouped commits. Each message is a one-line summary of what changed
  and how it was verified, for example `(core 448/448, full device suite 22/22)`.
- When you port a rule from iOS, port its iOS tests with it. The Core test suite is the
  conformance spec both apps are held to.
- Never edit `values-*/strings.xml` by hand. Add the English string to `values/strings.xml`, then
  regenerate (see `tools/l10n/README.md`).
- If the two apps disagree, raise it rather than quietly picking one.

CI (`.github/workflows/ci.yml`) runs `:core:test` and `:app:assembleDebug` on every push to
`main` and on every pull request. The UI tests aren't in CI: run them locally before you merge.
