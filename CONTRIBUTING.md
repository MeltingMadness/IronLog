# Contributing to Thrym

Thank you for your interest in Thrym. Bug reports, fixes, translations and new features are welcome. This guide explains how to build the app, which checks a pull request has to pass, and which rules the project follows.

By taking part, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md). Please report security issues privately as described in [`SECURITY.md`](SECURITY.md), not in a public issue.

## Before you start

- **Small fixes** (typos, obvious bugs, missing translations): open a pull request directly.
- **Larger changes** (new features, new dependencies, data model or backup format changes, design changes): please open an issue first, so we can agree on the approach before you put in the work.
- Thrym is offline by design: no account, no server, no tracking. Changes that need network access or collect data are unlikely to be accepted.

## Language

- **English:** code, identifiers, comments in new code, commit messages, issues and pull requests.
- **German** is welcome in discussions if it's easier for you.
- The internal documents in `docs/` and [`AGENTS.md`](AGENTS.md) are written in German. Some existing comments are German as well, and some test names are German on purpose (e.g. `logSet erstellt Satz korrekt`). You don't need to translate them.

## Prerequisites

**Android** (any OS):

- JDK 17 (CI uses Temurin 17)
- Android SDK with `platforms;android-35`, `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`
- The SDK location, either through `ANDROID_HOME` or a `local.properties` file in the repository root containing `sdk.dir=/path/to/sdk`. The file is gitignored.
- Gradle comes with the wrapper (`./gradlew`, Gradle 9.1.0). Android Studio is optional; if you use it, pick a version that supports AGP 9.0.

**iOS** (macOS only):

- Xcode 26 (CI pins Xcode 26.6)
- Python 3, for the project generator
- JDK 17: the Xcode build phase runs Gradle to build the shared Kotlin framework. `iosApp/scripts/build-shared.sh` uses `JAVA_HOME` if it is set (it must be JDK 17). Otherwise it looks for JDK 17 through `/usr/libexec/java_home -v 17` and in the usual Homebrew locations. It does not install anything.

Toolchain versions: Kotlin 2.3.10, AGP 9.0.0, minSdk 26, targetSdk 35, compileSdk 36, iOS deployment target 17.0, Swift 5.10 language mode.

## Android: build and test

```bash
./gradlew assembleDebug                      # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                       # build and install on a connected device or emulator
./gradlew test :shared:testAndroidHostTest   # all unit tests
./gradlew lintDebug                          # lint → <module>/build/reports/lint-results-debug.html
python3 .github/scripts/check-translations.py   # every text exists in English and German
./gradlew :app:connectedDebugAndroidTest     # instrumentation tests (needs an emulator or device)
```

- `./gradlew test` alone **skips** the tests of the Kotlin Multiplatform module `:shared`. Always run `:shared:testAndroidHostTest` as well.
- For the same behavior as CI, add `--no-daemon`. If Maven Central answers with HTTP 429, `--max-workers=2` helps.
- CI runs the instrumentation tests on an API 35 emulator with **animations disabled**, and it is slower than a local emulator. A UI test that passes locally can still fail there. To reproduce the CI conditions, turn animations off on your emulator:

  ```bash
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  ```

- Cloud VMs usually have no `/dev/kvm` and so cannot run an emulator. There, unit tests, lint and `assembleDebug` are enough, and the instrumentation tests run in CI.

## iOS: build and test

```bash
python3 iosApp/generate_project.py
xcodebuild test -project iosApp/ThrymIOS.xcodeproj -scheme ThrymIOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO
```

You can also open `iosApp/ThrymIOS.xcodeproj` in Xcode after generating it. Use any iPhone simulator you have installed (`xcrun simctl list devices available`). See [`iosApp/README.md`](iosApp/README.md) (German) for more checks, for example how to run a single test class.

**The Xcode project is generated.** `iosApp/generate_project.py` writes `iosApp/ThrymIOS.xcodeproj` and picks up every Swift file in `iosApp/ThrymIOS/` and `iosApp/ThrymIOSTests/` automatically. Never edit `project.pbxproj` by hand. After you add, remove or rename Swift files or resources, or change generator settings, run the generator again and **commit the regenerated project**. CI fails if the committed project differs from the generator output.

**Running on your own iPhone.** Simulator builds need no signing. For a device build:

1. Copy `iosApp/Local.xcconfig.example` to `iosApp/Local.xcconfig`. The copy is gitignored.
2. Set `DEVELOPMENT_TEAM` to your Apple team ID, and `THRYM_BUNDLE_ID` to a bundle ID you control (e.g. `com.example.thrym`).

The tracked `iosApp/Config/Signing.xcconfig` keeps the official bundle ID `com.thrym.ios` and an empty team. Leave it unchanged, and never commit `Local.xcconfig`.

## CI checks

Every pull request has to pass these checks before it can be merged:

**Android** ([`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml))

1. Unit tests: `./gradlew test :shared:testAndroidHostTest`
2. Lint: `./gradlew lintDebug` with no errors. This includes `MissingTranslation`, which fails when a string is missing in one of the languages.
3. Translation completeness: `python3 .github/scripts/check-translations.py` checks that every text exists in English and German with matching placeholders. The Android job runs it with `--platform android`, the iOS job with `--platform ios`; without an option it checks both platforms.
4. Debug build: `./gradlew assembleDebug`
5. Instrumentation smoke tests: `./gradlew :app:connectedDebugAndroidTest` on an API 35 emulator with animations disabled. A guard script (`.github/scripts/guard-zero-tests.py`) fails the job if no test report exists, no test ran, or one of the expected smoke test classes (`EXPECTED_TEST_CLASSES` in the script) did not run. If you rename or remove one of those classes, update the list.

**iOS** (`.github/workflows/ios-ci.yml`, `macos-26` runner with Xcode 26.6)

6. Generator drift check: `python3 iosApp/generate_project.py` must not change the committed `iosApp/ThrymIOS.xcodeproj`.
7. Build and XCTest: the `xcodebuild test` command shown above must pass. It also builds `Shared.framework` with Gradle and JDK 17. As on Android, the job fails if no test ran.

If you can't build the iOS app yourself (no Mac), CI checks it for you. Please mention this in the pull request.

More on the gates in [`docs/quality-gates.md`](docs/quality-gates.md) (German). Signed release builds and their publication are separate, manual steps for the maintainer, see [`docs/release-envelope.md`](docs/release-envelope.md).

## Project rules

### Keep the documentation current

[`README.md`](README.md), [`docs/architektur.md`](docs/architektur.md), [`docs/design-system.md`](docs/design-system.md) and `docs/features/*.md` describe the **current** state. If your change alters behavior, update the affected file **in the same pull request**. Add changes that users will notice to the "Unveröffentlicht" (unreleased) section of [`CHANGELOG.md`](CHANGELOG.md). Plans for ongoing work may live in `docs/plans/` while the work is in progress. After the merge, move the result into the documentation and delete the plan. `docs/validation/`, `docs/design/` and `docs/research/` are historical records and stay as they are.

### UI text and translations

Thrym ships in English (default and fallback) and German. All visible text must come from resources, in both languages.

**Android**

- Never hard-code UI text in Kotlin. Shared strings go in `core/designsystem/src/main/res/values/strings.xml`, feature strings in the feature module's `src/main/res/values/*_strings.xml`.
- Every string exists in **both** `values/` (English) **and** `values-de/` (German), with the same name. Lint's `MissingTranslation` check enforces this.
- Language-neutral strings, such as symbols or pure format patterns, are marked `translatable="false"` and live in `values/` only.
- For counts, use `<plurals>`, not "1 set(s)".

**iOS**

- UI text lives in the string catalog `iosApp/ThrymIOS/Localizable.xcstrings`. The keys are the German texts (source language `de`). Every entry needs a `de` **and** an `en` localization. The project's development region is `en`, so English is the fallback.
- SwiftUI text literals are localized automatically. For strings built in code, use `String(localized:)`. For counts, use plural variants in the catalog.

**Both platforms**

- Every text change comes in **both languages in the same pull request**.
- **Never translate or rename enum constants** such as `BRUST`, `LANGHANTEL`, `WARMUP` or `PLANNED_FAILURE`. They are storage keys in Room, in the iOS store and in backups. Only their display is translated.
- Built-in exercises are stored under their German canonical names in the database and in backups (seed data: `core/database/src/main/java/com/thrym/app/data/seed/ExerciseSeedData.kt`). Both platforms translate them for display through a lookup table. If you add a built-in exercise, add its translation on both platforms. Never change the stored name of an existing one. Exercises that users create are not translated.
- Format numbers, weights and dates with locale-aware APIs. Don't hard-code decimal separators or date patterns.
- To add a language, please open an issue first. It needs all Android resources, the iOS catalog, the exercise names and the language list for Android's per-app language setting.

### Logging

- Log only through `AppLogger` (`core/common/src/main/java/com/thrym/app/domain/util/AppLogger.kt`), never through `android.util.Log` directly.
- Never log personal data: no free-text notes, no raw IDs, no complete records. Debug logs are for debug builds only. Details are in [`docs/logging-guideline.md`](docs/logging-guideline.md) (German).

### Database and backups

People's training history is the most valuable thing the app holds. Changes to how it is stored need extra care.

Any change to the Room schema needs all of the following:

1. the entity change in `core/database`
2. a migration from the previous version
3. the exported schema JSON in `core/database/schemas/com.thrym.app.data.local.ThrymDatabase/`, committed with the change
4. a migration test. `ThrymDatabaseMigrationTest` (in `app/src/androidTest/`) checks the current database version and the identity hash explicitly, and `BackupLifecycleRoundTripTest` checks the current schema version, so update both with every version bump.
5. backup compatibility: if the change affects data that is backed up, update the backup format in `:shared` (`com.thrym.shared.backup`) and `CURRENT_BACKUP_SCHEMA_VERSION`.

**Never break the import of older backups.** Every backup an earlier version exported, including backups from IronLog (format version 1, schema versions up to the current one), must keep importing on Android and iOS. Only change the backup format additively: never rename fields or stored keys. Missing fields get safe defaults. Import is fail-closed: an invalid backup must not change any data. In the same way, the iOS app's stored training state (`Application Support/Thrym/`) must stay readable after an update.

Weights are always stored in kilograms. Pounds are converted for display only.

### UI tests

- Don't match hard-coded text. Resolve expected texts from resources (`getString(R.string.…)` on Android, the string catalog in XCTest), so tests pass in English and in German.
- Before you click, wait until the element is enabled (`isEnabled()`). Wait for snackbars to appear, and assert the result of an action instead of clicking on blindly. CI runs without animations and is slower than your machine.

### App name and IDs

- The visible app name lives only in [`branding.properties`](branding.properties) (`appDisplayName`). After changing it, run `python3 iosApp/generate_project.py`.
- Don't change the official IDs (`com.thrym.app` in `app/build.gradle.kts`, `com.thrym.ios` for iOS) or the names of the database and data files (`thrym.db`, `Application Support/Thrym/`). Changing them would cut existing users off from their data. For local device builds, use `Local.xcconfig` as described above.
- Never commit personal files, signing keys or absolute paths. `keystore.properties`, `release-keystore/`, `local.properties` and `iosApp/Local.xcconfig` are gitignored.

## Pull requests

- Branch off `main` and keep each pull request to one topic.
- Describe what changes and why, and link the issue if there is one. For UI changes, add screenshots. If you changed text, include both languages.
- Fill in the checklist in the pull request template. If a point doesn't apply, say so.
- Never include real training data, backup files or incident reports in issues, pull requests, test fixtures or screenshots. Use made-up test data.

## AI coding agents

[`AGENTS.md`](AGENTS.md) (German) has notes for AI coding agents such as Claude Code or Cursor: how to set up the environment, how to build and test, and known pitfalls. AI-assisted contributions are welcome. You are responsible for every line you submit, so review and test it like your own code.

## Licensing of contributions

Thrym is licensed under the [Apache License 2.0](LICENSE). Under section 5 of that license, any contribution you intentionally submit for inclusion is licensed under the same terms (inbound = outbound). There is **no CLA**. Only submit work you have the right to license this way. If you add third-party code, fonts or other assets, they must be under a compatible license. Name them in the pull request, so that `NOTICE` and `docs/licenses/` can be updated.

## Forks

You are welcome to fork Thrym under the Apache License. The name "Thrym", the logo and the app icon are not licensed for use by forks (see [README → License](README.md#license)). A published fork needs:

- its own name (`appDisplayName` in `branding.properties`)
- its own app icon
- its own Android `applicationId` (`app/build.gradle.kts`)
- its own iOS bundle ID (`THRYM_BUNDLE_ID` in `iosApp/Config/Signing.xcconfig`)
