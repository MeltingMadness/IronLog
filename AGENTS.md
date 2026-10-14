# AGENTS.md

Hinweise für KI-Agenten (Claude Code, Cursor u. a.). Überblick über das Projekt: [`README.md`](README.md). Mitarbeit: [`CONTRIBUTING.md`](CONTRIBUTING.md). Architektur: [`docs/architektur.md`](docs/architektur.md).

Thrym (früher IronLog) ist eine Open-Source-App (Apache-2.0) zum Protokollieren von Krafttraining: offline, ohne Konto, alle Daten auf dem Gerät. Es gibt sie nativ auf zwei gleichberechtigten Plattformen:

- **Android:** Kotlin, Jetpack Compose, mehrere Gradle-Module. `applicationId` `com.thrym.app`, minSdk 26, targetSdk 35, compileSdk 37.
- **iOS:** SwiftUI-App in `iosApp/`. Bundle-ID `com.thrym.ios`, iOS 17.0+, Swift 5.10 Language Mode. Sie nutzt das Kotlin-Multiplatform-Modul `:shared` als Framework `Shared`.

Gebaut wird mit Gradle 9.8.1 (Wrapper), AGP 9.4.1, Kotlin 2.4.21 und **JDK 17**, die iOS-App mit Xcode 26.x.

## Sprache im Repository

- `README.md`, `CONTRIBUTING.md` und die übrigen Community-Dateien sind **Englisch**.
- Interne Doku ist **Deutsch**: diese Datei, `docs/`, `iosApp/README.md`, `iosApp/TESTFLIGHT.md` und `CHANGELOG.md`. Sie zitiert UI-Texte in der deutschen Fassung.
- Manche Testnamen sind deutsch (z. B. `logSet erstellt Satz korrekt`). Das ist gewollt.

## Umgebung

- **Claude Code im Web:** `.claude/hooks/session-start.sh` läuft beim Session-Start und richtet alles ein. Das sind JDK 17, Android SDK unter `~/android-sdk` (Plattformen 35 und 37.0, build-tools 36.0.0, platform-tools), `local.properties`, ein Maven-Central-Spiegel in `~/.gradle/init.d/` und die Variablen `JAVA_HOME`/`ANDROID_HOME`. Die Netzwerk-Policy der Umgebung muss `dl.google.com` und `maven.google.com` erlauben.
- **Cursor Cloud:** JDK 17, SDK und `local.properties` sind im VM-Snapshot vorinstalliert.
- **Sonst (Linux):** vor Gradle-Aufrufen setzen:
  - `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
  - `export ANDROID_HOME=$HOME/android-sdk`
- **macOS:** `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Das gilt auch für iOS-Builds, weil eine Build-Phase Gradle aufruft.

## Android: bauen, testen, linten

Siehe `.github/workflows/android-ci.yml` und [`docs/quality-gates.md`](docs/quality-gates.md).

- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew test :shared:testAndroidHostTest`: Unit-Tests. `test` allein lässt die KMP-Tests aus.
- `./gradlew lintDebug`: Reports unter `<modul>/build/reports/lint-results-debug.html`. Lint bricht auch bei fehlenden Übersetzungen ab (`MissingTranslation`).
- `python3 .github/scripts/check-translations.py`: prüft, ob alle Texte in Englisch und Deutsch vorliegen und die Platzhalter übereinstimmen, für Android (`values/` gegen `values-de/`) und iOS (String-Kataloge; dort nur Schlüssel, die schon im Katalog stehen). Mit `--platform android` bzw. `--platform ios` nur eine Plattform; die CI ruft es in beiden Workflows auf.
- `./gradlew :app:connectedDebugAndroidTest`: Instrumentation-Tests auf Emulator oder Gerät. Die CI nutzt API 35 mit abgeschalteten Animationen.
- Für CI-Parität: `./gradlew --no-daemon ...`

## iOS: generieren, bauen, testen

Nur auf macOS mit vollständigem Xcode 26.x. Die CI (`.github/workflows/ios-ci.yml`) läuft auf `macos-26` mit Xcode 26.6. Details: [`iosApp/README.md`](iosApp/README.md).

```sh
python3 iosApp/generate_project.py
xcodebuild test -project iosApp/ThrymIOS.xcodeproj -scheme ThrymIOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO
```

- `iosApp/ThrymIOS.xcodeproj` wird von `iosApp/generate_project.py` erzeugt und ist eingecheckt. Die `project.pbxproj` **nie von Hand ändern**. Nach neuen, umbenannten oder gelöschten Swift-Dateien oder Ressourcen den Generator laufen lassen und das Ergebnis mit committen. Die iOS-CI prüft, dass ein erneuter Generatorlauf nichts ändert.
- Die Build-Phase `Build Shared Framework` (`iosApp/scripts/build-shared.sh`) baut `Shared.framework` mit Gradle. Dafür muss JDK 17 installiert sein; ein gesetztes `JAVA_HOME` muss auf JDK 17 zeigen.
- Simulator-Builds brauchen keine Signierung (`CODE_SIGNING_ALLOWED=NO`). Für ein echtes Gerät `iosApp/Local.xcconfig.example` nach `iosApp/Local.xcconfig` kopieren (git-ignoriert) und dort `DEVELOPMENT_TEAM` und eine eigene `THRYM_BUNDLE_ID` (z. B. `com.example.thrym`) setzen. Die eingecheckte `iosApp/Config/Signing.xcconfig` (Bundle-ID `com.thrym.ios`, leeres Team) nicht für lokale Zwecke ändern.
- Die iOS-CI prüft vor dem Build auch die String-Kataloge und schlägt fehl, wenn kein Test lief. Außerdem lässt sie die Golden-Fixture-Tests von `:shared` unter Kotlin/Native laufen (`./gradlew :shared:iosSimulatorArm64Test --tests 'com.thrym.shared.backup.IronLogBackupFixtureImportTest'`), weil die iOS-App diesen Code nativ ausführt und `:shared:testAndroidHostTest` nur die JVM prüft.
- In Cloud-VMs ohne macOS lässt sich die iOS-App nicht bauen. Dort Swift-Änderungen nur über die iOS-CI prüfen und das im PR sagen.

## Texte und Übersetzungen

Die App gibt es auf Englisch (Standard und Fallback) und Deutsch. Jede Textänderung kommt im **selben PR in beiden Sprachen**.

- **Android:** UI-Texte nie als Literal in Composables oder ViewModels. `res/values/` ist Englisch, `res/values-de/` Deutsch. Gemeinsame Texte liegen in `core/designsystem/src/main/res/values*/`, featurespezifische in `res/values*/*_strings.xml` des Feature-Moduls. Mengen über `plurals` in beiden Sprachen. Sprachneutrale Strings (reine Formatmuster, Symbole) stehen nur in `values/` und tragen `translatable="false"`. Lint und `check-translations.py` brechen ab, wenn eine Sprache fehlt. Ab Android 13 lässt sich die Sprache pro App wählen (`localeConfig` mit `en` und `de`).
- **iOS:** UI-Texte stehen in `iosApp/ThrymIOS/Localizable.xcstrings`, die Namen der eingebauten Übungen in `iosApp/ThrymIOS/Exercises.xcstrings`. Schlüssel ist der deutsche Text (`sourceLanguage` `de`), jeder Eintrag braucht eine `en`- und eine `de`-Lokalisierung; `check-translations.py` prüft das für beide Kataloge. SwiftUI-Literale werden zur Laufzeit im Katalog nachgeschlagen, sonst `String(localized:)`. Mengen über Plural-Varianten im Katalog.
- **iOS-Katalog von Hand pflegen:** Das generierte Projekt extrahiert keine Strings (`SWIFT_EMIT_LOC_STRINGS` ist nicht gesetzt). Jeder neue oder geänderte Text kommt deshalb von Hand mit `de` und `en` in `Localizable.xcstrings`, ein nicht mehr benutzter Schlüssel wird entfernt. `check-translations.py` und die XCTests prüfen nur Schlüssel, die schon im Katalog stehen. Fehlt ein Eintrag, bleibt die CI grün, und die englische Oberfläche zeigt den deutschen Text.
- **Meldungen aus `:shared` auf iOS:** Einige Fehlermeldungen aus `SharedStateStore` und den iOS-Brücken (`IosTrainingFeature`, `IosSettingsFeature` in `shared/src/iosMain`) sind feste deutsche Texte. iOS übersetzt sie mit `ilSharedMessage` (`iosApp/ThrymIOS/SharedBootstrap.swift`), das den deutschen Text als Schlüssel in `Localizable.xcstrings` nachschlägt (Einträge mit `extractionState` `manual`). Wer eine solche Meldung neu einführt oder umformuliert, legt im selben PR den passenden manuellen Eintrag mit `de` und `en` an und entfernt den alten Schlüssel. Sonst zeigt die englische Oberfläche den deutschen Text, und kein Check schlägt an.
- **Enum-Konstanten nie übersetzen oder umbenennen** (`BRUST`, `RUECKEN`, `LANGHANTEL`, `WARMUP`, `PLANNED_FAILURE` …). Sie sind Speicherschlüssel in Room, im iOS-Store und in Backups. Übersetzt wird nur ihre Anzeige.
- **Eingebaute Übungen** sind in Datenbank und Backups unter ihrem deutschen kanonischen Namen gespeichert (Seed Android: `core/database/src/main/java/com/thrym/app/data/seed/ExerciseSeedData.kt`, iOS: `DefaultExerciseCatalog` in `shared/src/commonMain/kotlin/com/thrym/shared/store/`). Beide Plattformen übersetzen sie nur für die Anzeige: Android über `BuiltInExerciseNames` (`core/designsystem/src/main/java/com/thrym/app/presentation/common/BuiltInExerciseNames.kt`) mit `exercise_names.xml` in `core/designsystem/src/main/res/values/` und `values-de/`, iOS über `ilExerciseDisplayName` mit `iosApp/ThrymIOS/Exercises.xcstrings`. Den gespeicherten Namen nie ändern. Eine neue eingebaute Übung gehört in `ExerciseSeedData.kt`, `DefaultExerciseCatalog`, `BuiltInExerciseNames.kt`, beide `exercise_names.xml` und `Exercises.xcstrings`; die erwartete Anzahl (79) in `BuiltInExerciseNamesTest` und `StringCatalogTests` wird mitgezogen. Eigene Übungen der Nutzer werden nicht übersetzt.
- Zahlen und Datumsangaben folgen dem Gerätelocale. Keine fest verdrahteten `Locale.GERMANY`- oder `de_DE`-Formatierer.
- **UI-Tests** (androidTest und XCTest) holen Texte über die Ressourcen (`getString(R.string.…)` bzw. den String-Katalog), nie als hart kodierte deutsche oder englische Literale. Sonst hängen sie von der Gerätesprache ab. Ausnahme sind die Katalog- und Bundle-Tests (`StringCatalogTests`, `LocalizationTests`, `ExerciseDisplayNameTests`): Sie prüfen einige Beispielübersetzungen mit Literalen gegen ein explizit geladenes `en`- bzw. `de`-Bundle.

## Daten und Kompatibilität

- Thrym ist auf beiden Plattformen eine **neue App** mit eigener Sandbox. Nutzer ziehen von IronLog per Backup um: Export in IronLog, Import in Thrym. **Alte IronLog-Backups müssen importierbar bleiben**, also `formatVersion` 1 mit jeder `schemaVersion` bis zur aktuellen (15), auf beiden Plattformen. Das Backup-Format nur additiv ändern, keine Feldnamen oder gespeicherten Schlüssel umbenennen. Einstellungen sind nicht Teil des Backups.
- **Golden-Backups nie anfassen.** `shared/src/commonTest/kotlin/com/thrym/shared/backup/IronLogBackupFixtures.kt` und `data/src/test/resources/backup-fixtures/` enthalten eingefrorene Exporte von IronLog 1.4.0 (Android) und der IronLog-iOS-App (Schema 15), byte-identisch und per SHA-256 festgenagelt. Nie bearbeiten, umformatieren oder neu erzeugen, nie die Hashes ändern. Beide `IronLogBackupFixtureImportTest` (in `:shared` und `data`) müssen nach jeder Änderung am Backup-Format grün bleiben. Schlagen sie fehl, wird der Import-Code korrigiert, nicht die Fixtures.
- Room-Schemaänderungen brauchen: Entity, Migration, exportiertes Schema in `core/database/schemas/com.thrym.app.data.local.ThrymDatabase/`, Migrationstest und ggf. Backup-Format (`:shared`, `CURRENT_BACKUP_SCHEMA_VERSION`). `ThrymDatabaseMigrationTest` prüft die aktuelle Version und den Identity-Hash explizit, `BackupLifecycleRoundTripTest` die aktuelle Schemaversion. Beide müssen bei jedem Versionssprung mitgezogen werden; beide gehören zu den Smoke-Klassen, die `.github/scripts/guard-zero-tests.py` (`EXPECTED_TEST_CLASSES`) in der CI erwartet.
- Store-IDs (`com.thrym.app`, `com.thrym.ios`) und Datenbank-/Dateinamen (`thrym.db`, `Application Support/Thrym/`) nicht ändern. Der sichtbare App-Name steht nur in `branding.properties`.

## Stolperfallen

- **Kein Emulator in Cloud-VMs.** Dort fehlt `/dev/kvm`. `connectedDebugAndroidTest` läuft dann nur in GitHub Actions (`android-emulator-runner`), lokal reichen Unit-Tests und `assembleDebug`. Auf einem Rechner mit Emulator lassen sich die Instrumentation-Tests auch lokal ausführen.
- **Die CI schaltet Animationen ab** (`disable-animations: true`) und ist langsamer als ein lokaler Emulator. UI-Tests, die lokal grün sind, können dort scheitern. Zum Nachstellen die drei `*_animation_scale`-Einstellungen per `adb shell settings put global … 0` abschalten. UI-Tests sollten vor Klicks auf den aktivierten Zustand warten (`isEnabled()`), Snackbars abwarten und das Ergebnis einer Aktion prüfen, statt blind weiterzuklicken.
- **HTTP 429 von Maven Central** hinter dem Cloud-Proxy: Der Spiegel aus dem Session-Hook fängt das ab. Sonst hilft `--max-workers=2`.
- Logging nur über `AppLogger` (siehe [`docs/logging-guideline.md`](docs/logging-guideline.md)).
- Keine persönlichen Dateien, Signierschlüssel oder absoluten Pfade ins Repository. `keystore.properties`, `release-keystore/`, `iosApp/Local.xcconfig` und `local.properties` sind git-ignoriert.

## Dokumentation pflegen

- `README.md`, `docs/architektur.md`, `docs/design-system.md` und `docs/features/*.md` beschreiben den **aktuellen** Stand. Wer Verhalten ändert, passt die betroffene Datei im selben PR an. Für Nutzer sichtbare Änderungen kommen in den Abschnitt „Unveröffentlicht“ von `CHANGELOG.md`.
- Pläne für laufende Arbeit dürfen in `docs/plans/` liegen. Nach dem Merge wird das Ergebnis in die Doku übernommen und der Plan gelöscht.
- `docs/validation/`, `docs/design/` und `docs/research/` sind datierte Protokolle aus der IronLog-Zeit. Sie bleiben, wie sie sind, und werden nicht nachträglich umbenannt.
