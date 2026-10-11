# Architektur

Thrym hat zwei gleichberechtigte native Clients: **Android** (Kotlin, Jetpack Compose, dieses Gradle-Projekt, `applicationId` `com.thrym.app`) und **iOS** (SwiftUI in `iosApp/`, Bundle-ID `com.thrym.ios`). Beide nutzen den gemeinsamen Kotlin-Multiplatform-Kern `:shared` für Regeln, Backup-Format und Auswertungen. Die Datenhaltung ist plattformspezifisch: Room auf Android, eine JSON-Datei auf iOS.

Android folgt MVVM mit Clean-Architecture-Schichten, aufgeteilt in Gradle-Module. Die Kotlin-Pakete heißen in allen Android-Modulen `com.thrym.app.*`, auch wenn die Module anders aufgeteilt sind. Die Gradle-Namespaces (und damit die `R`-Klassen) folgen dagegen dem Modul: `com.thrym.app` für `app`, `com.thrym.core.<modul>`, `com.thrym.data` und `com.thrym.feature.<modul>`. Der KMP-Kern liegt in `com.thrym.shared.*`.

## Module

```
app/                    Einstieg: MainActivity, Koin-DI (di/AppModule.kt), Navigation, Bottom-Nav
core/
  model/                Domain-Modelle (domain.model) und Repository-Interfaces (domain.repository)
  common/               Logik ohne UI: Formatierung, AppLogger (einzige Android-API: android.util.Log),
                        Progressions-Engine, Deload-Erkennung, Scheibenrechner, Muskelvolumen
  database/             Room: Entities, DAOs, Migrationen, exportierte Schemas (core/database/schemas/)
  designsystem/         Theme, Design-Tokens, gemeinsame Compose-Komponenten, gemeinsame UI-Texte
                        (values/ Englisch, values-de/ Deutsch), Fonts
data/                   Repository-Implementierungen, DataStore-Einstellungen, Backup,
                        Erinnerungen (WorkManager), Incident-Reports
feature/
  dashboard/            Home: Training starten, Plan-Auswahl, Tagesform/Bereitschaft, Deload,
                        Wochenvolumen je Muskelgruppe, Volumentrend
  workout/              Aktives Workout, Übungsauswahl, Abschlussdialog und Zusammenfassung
  plans/                Trainingspläne, Meta-Pläne, Plan-Editor inkl. Progressions-Konfiguration
  progression/          Review-Bildschirm für Progressionsvorschläge
  history/              Verlauf und Workout-Detail
  exercises/            Übungsbibliothek
  statistics/           Statistik pro Übung
  settings/             Einstellungen, Backup, Incident-Report
shared/                 Kotlin Multiplatform (Android + iOS), siehe unten
iosApp/                 Native SwiftUI-App (ThrymIOS), Projekt per generate_project.py, siehe iosApp/README.md
```

**Abhängigkeitsrichtung:** `feature/*` → `core/*`. `data` implementiert die Interfaces aus `core:model`. Nur `app` kennt alle Module und verdrahtet sie über Koin.

**Bottom-Navigation:** Home, Pläne, Verlauf, Übungen. Einstellungen, aktives Workout, Progressions-Review und Detailseiten sind eigene Ziele ohne Tab.

## Datenbank (Room, Version 15)

Die Datenbankklasse ist `ThrymDatabase` (`core/database`), die Datei heißt `thrym.db`. Das exportierte Schema liegt in `core/database/schemas/com.thrym.app.data.local.ThrymDatabase/15.json`. Es gibt durchgehende Migrationen von 1 bis 15. Migration 14→15 ergänzt die Erfolgsbestätigung und stellt aktive Planregeln auf Revision 2 um; historische Snapshots behalten ihre Revision. Tests dazu: `app/src/androidTest/.../ThrymDatabaseMigrationTest.kt`.

| Tabelle | Zweck | Wichtige Beziehungen |
|---|---|---|
| `exercises` | Übungen (Seed-Daten + eigene), archivierbar | – |
| `workout_sessions` | Ein Training. `endTime = NULL` heißt: läuft noch. `planId`/`metaPlanId` für den Kontext, `isDeload` für den Deload-Kontext (`NULL` = unbekannt, bei Altbeständen) | – |
| `workout_sets` | Ein Satz mit Gewicht, Wdh., Satztyp (`NORMAL`, `WARMUP`, `DROP_SET`, `FAILURE`, `BACKOFF`), RPE | → session (CASCADE), → exercise (RESTRICT), → plan-target-snapshot (SET NULL) |
| `personal_records` | Rekorde pro Übung | → exercise (CASCADE) |
| `training_plans` | Trainingspläne | – |
| `plan_exercises` | Übungen im Plan: Zielwerte, optionale individuelle Satzvorgaben (`setTargetsJson`), Supersatz-Gruppe, Progressions-Konfiguration (flach gespeichert) | → plan (CASCADE), → exercise (CASCADE) |
| `meta_training_plans` | Meta-Pläne | – |
| `meta_plan_items` | Reihenfolge der Teilpläne im Meta-Plan | → meta-plan, → plan (CASCADE) |
| `meta_plan_skips` | „Überspringen“-Ereignisse für die Rotation | → meta-plan, → plan (CASCADE) |
| `workout_plan_targets` | Unveränderlicher Snapshot der Planziele und Satzvorgaben beim Workout-Start | → session, → plan (CASCADE), → exercise (RESTRICT) |
| `progression_suggestions` | Ergebnisse des Progressions-Coachs (offen, angenommen, verworfen, veraltet, Info) | → snapshot, → session, → plan (CASCADE), → exercise (RESTRICT) |
| `readiness_data` | Eine Zeile mit einem JSON-Dokument: Tagesform-Check-ins und Satzintentionen (Format in `:shared`, `readinessdata`) | – |

Gewichte werden immer in **kg** gespeichert. Die Umrechnung nach lb passiert nur in der Anzeige.

## Wichtige Invarianten

- **Höchstens ein laufendes Workout.** Der DAO liest immer nur die jüngste offene Session (`endTime IS NULL`). Die Migration 2 → 3 hat Altbestände mit mehreren offenen Sessions bereinigt.
- **Pläne ändern sich nur nach Bestätigung.** Der Progressions-Coach schreibt Vorschläge, nie direkt Zielwerte. Details: [`features/progressions-coach.md`](features/progressions-coach.md).
- **Workouts arbeiten mit einem Snapshot des Plans.** Wird der Plan während oder nach dem Training geändert, bleibt die damalige Vorgabe erhalten.
- **Import ist fail-closed.** Ein ungültiges Backup verändert keine Daten.

## Backup

- Format: JSON, `BackupPayloadV1` mit `formatVersion = 1` und `schemaVersion = 15` (`CURRENT_BACKUP_SCHEMA_VERSION`). Es enthält auch die Readiness-Daten, Satzvorgaben, Erfolgsbestätigung und den Satztyp `BACKOFF`. Modelle und Validator liegen in `:shared` (`com.thrym.shared.backup`). `data` nutzt sie über Typaliase. Beim Import werden nur aktive Plan-Konfigurationen auf die aktuelle Regelrevision umgestellt; historische Quellen bleiben erhalten.
- Android und iOS verwenden dasselbe Format aus `:shared`. Ein Backup ist sprachunabhängig, weil gespeicherte Werte keine übersetzten Texte enthalten (siehe Lokalisierung): Ein Export aus der deutschen Oberfläche importiert in die englische und umgekehrt.
- Ältere Backups ohne neuere Felder (Meta-Plan-Skips, Progression, Readiness, Satzvorgaben) lassen sich weiter importieren. Backups aus einer neueren App-Version werden mit einer klaren Meldung abgelehnt. Fehlende Felder bekommen sichere Standardwerte.
- **Umzug von IronLog:** Thrym ist wegen der neuen IDs eine neue App mit leerer Sandbox und sieht die IronLog-Daten nicht. Der Weg ist „Backup exportieren“ in den Einstellungen von IronLog und „Backup importieren“ in den Einstellungen von Thrym. Der Import ersetzt alle Trainingsdaten in Thrym. IronLog-Backups (`formatVersion` 1, jede `schemaVersion` bis 15) importieren auf beiden Plattformen. Diese Kompatibilität ist eine feste Zusage und braucht Tests, wenn sich das Format ändert.
- Vor jedem Import und jeder Wiederherstellung legt die App ein **internes Sicherheitsbackup** an und prüft es per SHA-256 (`FileRecoveryBackupStore`).
- Einstellungen (Einheiten, Design, Pausen-Timer, Scheiben …) und Erinnerungen sind **nicht** Teil des Backups. Nach einem Umzug müssen sie neu gesetzt werden.

## Einstellungen

`AppPreferences` (in `core:model`) wird per DataStore gespeichert. Standardwerte: metrisch, Wochenstart Montag, dunkles Design in der Darstellung Liquid Glass, Farbschema Amber, RPE als Intensitätssystem, getrennte Gewichtshistorie zwischen Einzel- und Meta-Plänen, automatischer Pausen-Timer aus (Standarddauer 120 s), Scheibenrechner an (Scheiben 25 bis 1,25 kg, Stange 20 kg), kein Deload-Modus, Backup-Erinnerung aus.

## Kotlin Multiplatform (`:shared`)

Enthält den plattformneutralen Kern (`com.thrym.shared.*`): Transportmodelle, Backup-Format und -Validator, Progressionsregeln, Satzvorgaben (`plans`), Scheibenrechner (`plates`), Deload- und Auswertungslogik (`deload`, `analytics`), Trainingsbereitschaft (`readiness`), Tagesform/Satzintention (`readinessdata`), einen kanonischen Trainingsgraphen samt Store (`store`), Next-Set-Hinweise (`workout`) sowie Controller für Dashboard, Workout, Verlauf, Pläne, Statistik und Einstellungen.

Android nutzt daraus vor allem Backup-Format und Validator, Readiness, Progression (über `PortableProgressionAdapter`), Satzvorgaben, Scheibenrechner und die Einstellungs-Controller. Die Android-Datenhaltung bleibt Room. Die iOS-App bindet `:shared` als statisches Framework `Shared` ein, speichert den Trainingsgraphen aus `store` als JSON-Datei unter `Application Support/Thrym/training-state.json` und greift über `IosTrainingFeature` und `IosSettingsFeature` (`shared/src/iosMain`) auf den Kern zu, siehe [`ios-feature-parity.md`](ios-feature-parity.md). Einstellungen liegen auf iOS in `UserDefaults`.

`:shared` liefert keine fertigen UI-Texte, sondern Werte und Codes (z. B. Reason-Codes der Progression und Bereitschaft). Die Plattformen übersetzen sie in ihre lokalisierten Texte.

Die Tests von `:shared` laufen über `./gradlew :shared:testAndroidHostTest`, **nicht** über `./gradlew test`.

## Lokalisierung

Die App gibt es auf **Englisch** (Standard und Fallback) und **Deutsch**. Sie folgt der Systemsprache; ab Android 13 lässt sich die Sprache auch pro App wählen (`localeConfig` mit `en` und `de`), auf iOS in den Systemeinstellungen der App.

- **Android:** Jedes Modul mit UI-Texten hat `res/values/` (Englisch) und `res/values-de/` (Deutsch) mit denselben Schlüsseln. Gemeinsame Texte und Plurale liegen in `core:designsystem`, featurespezifische im Feature-Modul. Sprachneutrale Strings stehen nur in `values/` mit `translatable="false"`. ViewModels arbeiten mit Ressourcen-IDs oder lösen sie über `UiStrings` auf. Lint (`MissingTranslation`) und `.github/scripts/check-translations.py` sichern ab, dass beide Sprachen vollständig sind und die Platzhalter übereinstimmen.
- **iOS:** Ein String-Katalog `iosApp/ThrymIOS/Localizable.xcstrings`. Schlüssel ist der deutsche Text (`sourceLanguage` `de`), jeder Eintrag hat eine `en`- und eine `de`-Lokalisierung, Mengen als Plural-Varianten. Die `developmentRegion` des Projekts ist `en`, damit Englisch der Fallback für andere Systemsprachen ist. Auch hier prüft `check-translations.py` Vollständigkeit und Platzhalter.
- **Gespeicherte Werte sind sprachneutral.** Enum-Konstanten wie `BRUST`, `RUECKEN`, `LANGHANTEL`, `WARMUP` oder `PLANNED_FAILURE` sind Speicherschlüssel in Room, im iOS-Store und im Backup. Übersetzt wird nur ihre Anzeige.
- **Eingebaute Übungen** werden unter ihrem deutschen kanonischen Namen gespeichert (Android-Seed `ExerciseSeedData.kt` in `core/database`, iOS-Katalog `DefaultExerciseCatalog` in `:shared`). Beide Plattformen übersetzen diese Namen nur für die Anzeige über eine Nachschlagetabelle. Eigene Übungen erscheinen so, wie sie eingegeben wurden.
- **Formatierung:** Zahlen, Datumsangaben und Wochentage folgen dem Gerätelocale („82,5 kg“ auf Deutsch, „82.5 kg“ auf Englisch). Gewichte bleiben in kg gespeichert; lb ist nur Anzeige.
