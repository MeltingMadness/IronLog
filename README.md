# IronLog

Android-App zum Protokollieren von Krafttraining. Offline, lokal, deutschsprachige Oberfläche.

- **Package:** `com.ironlog.app`
- **Plattform:** Android (minSdk 26, targetSdk 35, compileSdk 36). Dazu kommt eine native SwiftUI-App für iOS, die den gemeinsamen Kotlin-Kern nutzt, siehe [`iosApp/README.md`](iosApp/README.md) und [`docs/ios-feature-parity.md`](docs/ios-feature-parity.md).
- **Tech:** Kotlin 2.3.10, Jetpack Compose (Material 3), Room 2.8.4, Koin 4.1.1, Navigation Compose, Paging 3, Vico Charts, DataStore, WorkManager, Kotlin Multiplatform (`:shared`)
- **Toolchain:** Gradle 9.1.0, AGP 9.0.0, JDK 17

## Funktionen

| Bereich | Was die App kann |
|---|---|
| **Home** | Training starten (Plan, Meta-Plan oder frei), Tagesform-Check-in und Trainingsbereitschaft, Deload-Hinweis und Deload-Modus (halbe Satzzahl oder −15 % Gewicht), Wochenvolumen je Muskelgruppe (MEV/MAV), Volumentrend über 8 Wochen, Hinweis auf offene Progressionsvorschläge |
| **Aktives Workout** | Direktes Loggen mit vorbelegten Werten (Gewicht, Wdh.), aufklappbare Zusatzangaben (RPE/RIR, Satztyp, Satzintention, Scheibenrechner), Satztypen Normal/Aufwärm/Drop/Versagen, Pausen-Timer, Supersätze, Mehrfachauswahl beim Hinzufügen von Übungen, Live-Erkennung von Rekorden, Teilabschluss mit offenen Sätzen und Zusammenfassung |
| **Pläne** | Trainingspläne mit Zielwerten und optional individuellen Satzvorgaben pro Übung, Meta-Pläne (Rotation über Teilpläne, inkl. „Überspringen“), Progressionsschema pro Planübung |
| **Progressions-Coach** | Nach dem Workout: Vorschläge für neue Zielwerte, die erst nach Bestätigung in den Plan übernommen werden |
| **Verlauf** | Alle Trainings (Paging) mit Suche und Filtern nach Zeitraum und Plan, Detailansicht mit Korrektur und Löschen einzelner Sätze sowie Trainingsnotiz, Löschen per Wischgeste |
| **Übungen** | Übungsbibliothek mit Suche und Muskelfilter, Trainingszahlen je Übung (Einheiten, zuletzt trainiert), eigene Übungen, Archivieren, Statistik pro Übung (Rekorde, geschätztes 1RM, Verlaufsdiagramm) |
| **Einstellungen** | Einheiten (kg/lb), Wochenstart, Design (7 Farbschemata, Hell/Dunkel/System, Dynamic Color), reduzierte Animationen, RPE/RIR/Aus, Pausen-Timer, Scheiben und Stangengewicht, Trainings-Erinnerungen, Backup-Export/-Import mit optionaler Erinnerung, internes Sicherheitsbackup, Incident-Report |

Die Daten bleiben auf dem Gerät. Es gibt keinen Server und keine Cloud-Anbindung.

## Bauen und testen

```bash
./gradlew assembleDebug        # Debug-APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # Unit-Tests
./gradlew :shared:testAndroidHostTest   # KMP-Tests (laufen NICHT über `test`)
./gradlew lintDebug            # Lint
./gradlew installDebug         # Bauen und auf verbundenes Gerät installieren
```

Voraussetzungen: JDK 17 und ein Android SDK (Plattform 35 und 36, build-tools 36). `local.properties` mit `sdk.dir=...` anlegen (git-ignoriert).

- **Lokal unter Windows:** Android Studio mitsamt JBR als JDK. `deploy.ps1` und [`.agents/workflows/deploy.md`](.agents/workflows/deploy.md) beschreiben das Installieren aufs Handy, `release.ps1` den signierten Release-Build (siehe [`docs/release-envelope.md`](docs/release-envelope.md)).
- **Claude Code im Web:** Der SessionStart-Hook [`.claude/hooks/session-start.sh`](.claude/hooks/session-start.sh) richtet JDK, SDK und Gradle automatisch ein. Details in [`AGENTS.md`](AGENTS.md).

Emulator-Tests (`connectedDebugAndroidTest`) laufen in der GitHub-CI. Lokal gehen sie mit einem verbundenen Emulator oder Gerät; die CI schaltet dabei die Animationen ab.

## Dokumentation

| Datei | Inhalt |
|---|---|
| [`docs/architektur.md`](docs/architektur.md) | Module, Schichten, Datenbank-Schema, Backup, Invarianten |
| [`docs/design-system.md`](docs/design-system.md) | Aktuelles Design („Ember“): Farbschemata, Typografie, Komponenten |
| [`docs/features/progressions-coach.md`](docs/features/progressions-coach.md) | Regeln und Ablauf des Progressions-Coachs |
| [`docs/features/meta-plaene.md`](docs/features/meta-plaene.md) | Meta-Plan-Rotation, Überspringen, Gewichtshistorie |
| [`docs/ios-feature-parity.md`](docs/ios-feature-parity.md) | Funktionsstand und Abnahme der iOS-App |
| [`docs/readiness-data-integration.md`](docs/readiness-data-integration.md) | Tagesform-Check-in und Satzintention (Datenmodell, Integration) |
| [`docs/quality-gates.md`](docs/quality-gates.md) | PR-Gates und SLOs |
| [`docs/release-envelope.md`](docs/release-envelope.md) | Release-Kette, Signierung, Distribution |
| [`docs/logging-guideline.md`](docs/logging-guideline.md) | Logging-Regeln |
| [`docs/research/`](docs/research/) | Fachliche Quellen (z. B. Progressionsschemata) |
| [`docs/validation/`](docs/validation/) | Prüfprotokolle einzelner Versionen |
| [`CHANGELOG.md`](CHANGELOG.md) | Änderungen je Version |

### Regel für die Doku

Die Dateien oben beschreiben den **aktuellen Stand**. Pläne für laufende Vorhaben dürfen während der Arbeit in `docs/plans/` liegen. Sobald ein Vorhaben gemergt ist, wird das Ergebnis in die passende Datei oben übernommen und der Plan gelöscht. Alte Pläne bleiben über die Git-Historie auffindbar.
