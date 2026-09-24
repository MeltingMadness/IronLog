# AGENTS.md

Hinweise für KI-Agenten (Claude Code, Cursor u. a.). Überblick über das Projekt: [`README.md`](README.md). Architektur: [`docs/architektur.md`](docs/architektur.md).

IronLog ist eine Android-App (Kotlin, Jetpack Compose, Package `com.ironlog.app`). Das Modul `:shared` ist Kotlin Multiplatform und zielt zusätzlich auf iOS. Gebaut wird mit Gradle 9.1.0, AGP 9.0.0, Kotlin 2.3.10 und **JDK 17**.

## Umgebung

- **Claude Code im Web:** `.claude/hooks/session-start.sh` läuft beim Session-Start und richtet alles ein. Das sind JDK 17, Android SDK unter `~/android-sdk` (Plattformen 35 und 36, build-tools 36.0.0, platform-tools), `local.properties`, ein Maven-Central-Spiegel in `~/.gradle/init.d/` und die Variablen `JAVA_HOME`/`ANDROID_HOME`. Die Netzwerk-Policy der Umgebung muss `dl.google.com` und `maven.google.com` erlauben.
- **Cursor Cloud:** JDK 17, SDK und `local.properties` sind im VM-Snapshot vorinstalliert.
- **Sonst:** vor Gradle-Aufrufen setzen:
  - `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
  - `export ANDROID_HOME=$HOME/android-sdk`

## Bauen, testen, linten

Siehe `.github/workflows/android-ci.yml` und [`docs/quality-gates.md`](docs/quality-gates.md).

- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew test :shared:testAndroidHostTest`: Unit-Tests. `test` allein lässt die KMP-Tests aus.
- `./gradlew lintDebug`: Reports unter `<modul>/build/reports/lint-results-debug.html`
- Für CI-Parität: `./gradlew --no-daemon ...`

## Stolperfallen

- **Kein Emulator in Cloud-VMs.** Dort fehlt `/dev/kvm`. `connectedDebugAndroidTest` läuft dann nur in GitHub Actions (`android-emulator-runner`), lokal reichen Unit-Tests und `assembleDebug`. Auf einem Rechner mit Emulator lassen sich die Instrumentation-Tests auch lokal ausführen.
- **Die CI schaltet Animationen ab** (`disable-animations: true`) und ist langsamer als ein lokaler Emulator. UI-Tests, die lokal grün sind, können dort scheitern. Zum Nachstellen die drei `*_animation_scale`-Einstellungen per `adb shell settings put global … 0` abschalten. UI-Tests sollten vor Klicks auf den aktivierten Zustand warten (`isEnabled()`), Snackbars abwarten und das Ergebnis einer Aktion prüfen, statt blind weiterzuklicken.
- **HTTP 429 von Maven Central** hinter dem Cloud-Proxy: Der Spiegel aus dem Session-Hook fängt das ab. Sonst hilft `--max-workers=2`.
- Manche Testnamen sind deutsch (z. B. `logSet erstellt Satz korrekt`). Das ist gewollt.
- UI-Texte gehören in `core/designsystem/src/main/res/values/strings.xml` oder in die `res/values/*_strings.xml` des Feature-Moduls.
- Logging nur über `AppLogger` (siehe [`docs/logging-guideline.md`](docs/logging-guideline.md)).
- Room-Schemaänderungen brauchen: Entity, Migration, exportiertes Schema in `core/database/schemas/`, Migrationstest und ggf. Backup-Format (`:shared`, `CURRENT_BACKUP_SCHEMA_VERSION`). Die Migrationstests in `IronLogDatabaseMigrationTest` und `BackupLifecycleRoundTripTest` prüfen die aktuelle Version und den Identity-Hash explizit und müssen bei jedem Versionssprung mitgezogen werden.

## Dokumentation pflegen

- `README.md`, `docs/architektur.md`, `docs/design-system.md` und `docs/features/*.md` beschreiben den **aktuellen** Stand. Wer Verhalten ändert, passt die betroffene Datei im selben PR an.
- Pläne für laufende Arbeit dürfen in `docs/plans/` liegen. Nach dem Merge wird das Ergebnis in die Doku übernommen und der Plan gelöscht.
