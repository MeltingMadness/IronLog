## What and why

<!-- What does this pull request change, and why? Link the issue if there is one (e.g. "Closes #123"). -->

## How it was tested

<!-- Commands you ran, devices or simulators you used, and anything you could not test (e.g. "no Mac, iOS checked by CI only"). -->

## Screenshots

<!-- For UI changes: before/after. If you changed text, show both English and German. Use test data only. -->

## Checklist

<!-- Tick what applies. If a point does not apply, write "n/a" after it. -->

**General**

- [ ] The pull request covers one topic and is written in English.
- [ ] It contains no personal training data, backup files, incident reports, signing keys or absolute paths.
- [ ] Current-state docs (`README.md`, `docs/architektur.md`, `docs/design-system.md`, `docs/features/*.md`) are updated in this pull request if behavior changed.
- [ ] Changes that users will notice are listed in the "Unveröffentlicht" section of `CHANGELOG.md`.

**Tests and checks**

- [ ] `./gradlew test :shared:testAndroidHostTest` passes.
- [ ] `./gradlew lintDebug` and `./gradlew assembleDebug` pass.
- [ ] New or changed behavior is covered by tests.
- [ ] UI tests resolve texts through resources or the string catalog (no hard-coded text). Before clicking, they wait until elements are enabled, and they wait for snackbars.
- [ ] iOS: after adding, removing or renaming Swift files or resources, I ran `python3 iosApp/generate_project.py` and committed the regenerated project.
- [ ] iOS: the `xcodebuild test` run passes (or: no Mac available, left to CI).

**Text and translations**

- [ ] No hard-coded UI text. Android strings exist in `values/` (English) **and** `values-de/` (German). iOS strings in `Localizable.xcstrings` have `en` **and** `de`.
- [ ] `python3 .github/scripts/check-translations.py` passes.
- [ ] Enum constants and stored canonical exercise names are unchanged (only their display is translated).

**Data and logging**

- [ ] Logging goes through `AppLogger` only, and logs contain no personal data.
- [ ] Room schema changes include the entity, a migration, the exported schema, an updated migration test and, if needed, the backup format (`CURRENT_BACKUP_SCHEMA_VERSION`).
- [ ] Older backups, including IronLog backups, still import on Android and iOS.

**License**

- [ ] I agree that my contribution is licensed under the Apache License 2.0. Any third-party code or assets are named above together with their licenses.
