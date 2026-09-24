# Architektur

MVVM mit Clean-Architecture-Schichten, aufgeteilt in Gradle-Module. Die Pakete heißen durchgehend `com.ironlog.app.*`, auch wenn die Module anders aufgeteilt sind.

## Module

```
app/                    Einstieg: MainActivity, Koin-DI (di/AppModule.kt), Navigation, Bottom-Nav
core/
  model/                Domain-Modelle (domain.model) und Repository-Interfaces (domain.repository)
  common/               Reine Logik ohne Android: Formatierung, AppLogger, Progressions-Engine
  database/             Room: Entities, DAOs, Migrationen, exportierte Schemas (core/database/schemas/)
  designsystem/         Theme, Design-Tokens, gemeinsame Compose-Komponenten, strings.xml, Fonts
data/                   Repository-Implementierungen, DataStore-Einstellungen, Backup,
                        Erinnerungen (WorkManager), Incident-Reports
feature/
  dashboard/            Home: Training starten, Plan-Auswahl, Wochenvolumen, Muskel-Heatmap
  workout/              Aktives Workout, Übungsauswahl
  plans/                Trainingspläne, Meta-Pläne, Plan-Editor inkl. Progressions-Konfiguration
  progression/          Review-Bildschirm für Progressionsvorschläge
  history/              Verlauf und Workout-Detail
  exercises/            Übungsbibliothek
  statistics/           Statistik pro Übung
  settings/             Einstellungen, Backup, Incident-Report
shared/                 Kotlin Multiplatform (Android + iOS), siehe unten
iosApp/                 SwiftUI-Gerüst, siehe iosApp/README.md
```

**Abhängigkeitsrichtung:** `feature/*` → `core/*`. `data` implementiert die Interfaces aus `core:model`. Nur `app` kennt alle Module und verdrahtet sie über Koin.

**Bottom-Navigation:** Home, Pläne, Verlauf, Übungen. Einstellungen, aktives Workout, Progressions-Review und Detailseiten sind eigene Ziele ohne Tab.

## Datenbank (Room, Version 11)

Das exportierte Schema liegt in `core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/11.json`. Es gibt durchgehende Migrationen von 1 bis 11. Tests dazu: `app/src/androidTest/.../IronLogDatabaseMigrationTest.kt`.

| Tabelle | Zweck | Wichtige Beziehungen |
|---|---|---|
| `exercises` | Übungen (Seed-Daten + eigene), archivierbar | – |
| `workout_sessions` | Ein Training. `endTime = NULL` heißt: läuft noch. `planId`/`metaPlanId` für den Kontext | – |
| `workout_sets` | Ein Satz mit Gewicht, Wdh., Aufwärm-Flag, RPE | → session (CASCADE), → exercise (RESTRICT), → plan-target-snapshot (SET NULL) |
| `personal_records` | Rekorde pro Übung | → exercise (CASCADE) |
| `training_plans` | Trainingspläne | – |
| `plan_exercises` | Übungen im Plan: Zielwerte, Supersatz-Gruppe, Progressions-Konfiguration (flach gespeichert) | → plan (CASCADE), → exercise (CASCADE) |
| `meta_training_plans` | Meta-Pläne | – |
| `meta_plan_items` | Reihenfolge der Teilpläne im Meta-Plan | → meta-plan, → plan (CASCADE) |
| `meta_plan_skips` | „Überspringen“-Ereignisse für die Rotation | → meta-plan, → plan (CASCADE) |
| `workout_plan_targets` | Unveränderlicher Snapshot der Planziele beim Workout-Start | → session, → plan (CASCADE), → exercise (RESTRICT) |
| `progression_suggestions` | Ergebnisse des Progressions-Coachs (offen, angenommen, verworfen, veraltet, Info) | → snapshot, → session, → plan (CASCADE), → exercise (RESTRICT) |

Gewichte werden immer in **kg** gespeichert. Die Umrechnung nach lb passiert nur in der Anzeige.

## Wichtige Invarianten

- **Höchstens ein laufendes Workout.** Der DAO liest immer nur die jüngste offene Session (`endTime IS NULL`). Die Migration 2 → 3 hat Altbestände mit mehreren offenen Sessions bereinigt.
- **Pläne ändern sich nur nach Bestätigung.** Der Progressions-Coach schreibt Vorschläge, nie direkt Zielwerte. Details: [`features/progressions-coach.md`](features/progressions-coach.md).
- **Workouts arbeiten mit einem Snapshot des Plans.** Wird der Plan während oder nach dem Training geändert, bleibt die damalige Vorgabe erhalten.
- **Import ist fail-closed.** Ein ungültiges Backup verändert keine Daten.

## Backup

- Format: JSON, `BackupPayloadV1` mit `schemaVersion = 11`. Modelle und Validator liegen in `:shared` (`com.ironlog.shared.backup`). `data` nutzt sie über Typaliase.
- Ältere Backups ohne neuere Felder (Meta-Plan-Skips, Progression) lassen sich weiter importieren. Fehlende Felder bekommen sichere Standardwerte.
- Vor jedem Import und jeder Wiederherstellung legt die App ein **internes Sicherheitsbackup** an und prüft es per SHA-256 (`FileRecoveryBackupStore`).
- Einstellungen und Erinnerungen sind **nicht** Teil des Backups.

## Einstellungen

`AppPreferences` (in `core:model`) wird per DataStore gespeichert. Standardwerte: metrisch, Wochenstart Montag, dunkles Design, Farbschema Amber, RPE als Intensitätssystem, getrennte Gewichtshistorie zwischen Einzel- und Meta-Plänen.

## Kotlin Multiplatform (`:shared`)

Enthält plattformneutrale Teile: Transportmodelle, Backup-Format und -Validator, Incident-Report-Sanitizer sowie Controller für Dashboard, Workout, Verlauf, Pläne, Statistik und Einstellungen. Android nutzt daraus bisher nur das Backup-Format samt Validator (`data`) und die Einstellungs-Controller (`feature:settings`). Der Incident-Report-Sanitizer existiert doppelt, einmal in `data` und einmal in `:shared`. Die iOS-App bindet das Framework ein, verwendet aber bisher nur die Einstellungen.

Die Tests von `:shared` laufen über `./gradlew :shared:testAndroidHostTest`, **nicht** über `./gradlew test`.
