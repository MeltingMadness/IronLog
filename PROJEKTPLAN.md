# IronLog — Projektplan & Status

## Übersicht

| Eigenschaft | Wert |
|---|---|
| **App-Name** | IronLog |
| **Pfad** | `C:\Users\maert\IronLog` |
| **Package** | `com.ironlog.app` |
| **Tech Stack** | Kotlin 2.3.10, Jetpack Compose (M3), Room 2.8.4, Koin 4.1.1, Navigation Compose 2.9.7, Vico Charts 2.4.3, DataStore, WorkManager |
| **AGP** | 9.0.0 |
| **Gradle** | 9.1.0 |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 / 36 |
| **JDK** | 17 (Android Studio JBR) |
| **Architektur** | MVVM + Clean Architecture, **Multi-Module** (`core`, `data`, `feature`) |
| **UI-Sprache** | Deutsch |

---

## Was wurde implementiert

### Phase 1: Fundament & Architektur (ABGESCHLOSSEN)
- **Multi-Module Projektstruktur:** Aufteilung in `app`, `core`, `data` und diverse `feature`-Module (skalierbare Trennung der Concerns).
- **Room-Datenbank** mit Tabellen für Übungen, Workouts, Sätze, Rekorde und **Trainingspläne**.
- **Koin DI** für modulübergreifende Dependency Injection.
- **Material 3 Theme** zentralisiert im Modul `core:designsystem`.

### Phase 2: Training-Tracking (ABGESCHLOSSEN)
- **ActiveWorkoutScreen + ViewModel** im Modul `feature:workout`.
- Sätze loggen, mehrere Rest-Timer pro Übung, PR-Erkennung in Echtzeit.

### Phase 3: Verlauf (ABGESCHLOSSEN)
- Chronologische Liste aller Trainings im Modul `feature:history`.
- Detailansicht mit allen Sätzen und Gesamtvolumen.

### Phase 4: Dashboard (ABGESCHLOSSEN)
- Startbildschirm im Modul `feature:dashboard` mit Streak-Berechnung, Wöchentlichem Volumen (Weekly Volume) und Muskel-Heatmap.

### Phase 5: Statistiken & PRs (ABGESCHLOSSEN)
- E1RM-Berechnung und Fortschritts-Diagramme im Modul `feature:statistics`.

### Phase 6: Pläne, Settings & Hintergrund-Dienste (ABGESCHLOSSEN)
- **Trainingspläne:** Erstellen und Verwalten von Plänen (`feature:plans`).
- **Meta-Trainingspläne:** Rotationen über mehrere Unterpläne (`feature:plans`).
- **Einstellungen:** App-Einstellungen über DataStore persistiert (`feature:settings`).
- **Background Tasks:** Training Reminder Worker (`data:reminder`).
- **Daten:** Backup & Export Validierung (`data:backup`), Crash/Fehlerberichte (`data:incident`).
- **Tests:** Unit-Tests plus Android-Smoke-Tests für Navigation, Migrationspfade und zentrale UI-Flows.

---

## Dateistruktur (Multi-Module)

```
C:\Users\maert\IronLog\
├── app/                      # App-Einsprungspunkt (DI, Navigation, MainActivity)
├── core/
│   ├── common/               # Hilfsklassen, Formatierung, Logger
│   ├── database/             # Room DB, Entities (Exercises, Workouts, Plans), DAOs
│   ├── designsystem/         # Material 3 Theme, Tokens, geteilte UI-Komponenten
│   └── model/                # Domain Models (WorkoutSession, Exercise, TrainingPlan, etc.)
├── data/                     # Repository Implementierungen, DataStore, Worker, Backups
├── feature/
│   ├── dashboard/            # Home-Screen
│   ├── exercises/            # Übungsbibliothek
│   ├── history/              # Trainingsverlauf
│   ├── plans/                # Trainingspläne
│   ├── settings/             # Einstellungen
│   ├── statistics/           # Übungsstatistiken & Charts
│   └── workout/              # Aktives Workout Tracking
└── build.gradle.kts          # Root Build File
```

---

## Datenbank-Schema

```
exercises                    workout_sessions              workout_sets
┌──────────────────────┐    ┌──────────────────────┐     ┌──────────────────────┐
│ id (PK, auto)        │    │ id (PK, auto)        │     │ id (PK, auto)        │
│ name                 │    │ startTime (Long)     │     │ sessionId (FK→sessions)│ CASCADE
│ primaryMuscleGroup   │    │ endTime (Long?)      │     │ exerciseId (FK→exercises)│ RESTRICT
│ secondaryMuscleGroups│    │ durationSeconds      │     │ setNumber            │
│ category             │    │ name                 │     │ reps                 │
│ isCustom             │    │ notes                │     │ weightKg             │
└──────────────────────┘    └──────────────────────┘     │ isWarmup             │
        │                                  │              │ completedAt (Long)   │
        │ 1:N                              │ 1:N         └──────────────────────┘
        ▼                                  ▼
personal_records             (verknuepft ueber workout_sets)
┌──────────────────────┐
│ id (PK, auto)        │
│ exerciseId (FK)      │ CASCADE
│ type (String/Enum)   │
│ value (Double)       │
│ achievedAt (Long)    │
└──────────────────────┘

training_plans               plan_exercises
┌──────────────────────┐    ┌──────────────────────┐
│ id (PK, auto)        │    │ id (PK, auto)        │
│ name                 │    │ planId (FK)          │ CASCADE
│ createdAt (Long)     │    │ exerciseId (FK)      │ CASCADE
└──────────────────────┘    │ orderIndex           │
                            │ targetSets           │
                            │ targetReps           │
                            │ targetWeightKg       │
                            └──────────────────────┘
```

---

## Was noch zu tun ist (Offene Punkte)

### Prioritaet HOCH — Funktionalitaet
| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| H-01 | **Error Handling UI** | Crash-Reports existieren (`data:incident`), aber detailliertes UI-Feedback bei bestimmten DB-Fehlern fehlt eventuell noch. |
| H-02 | **E2E Persistenz-Check** | Manueller Lebenszyklus-Test: App starten, Training loggen, App killen, neu starten. Daten müssen sicher gespeichert bleiben. |
| H-03 | **Instrumented Regression Gates** | History-Detail-Flow, Shared Transitions und Workout-UX regelmäßig auf Gerät/Emulator gegenprüfen. |

### Prioritaet MITTEL — Qualitaet & UX
| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| M-01 | **History Performance** | Paging 3 ist vorhanden; große Verläufe weiter auf Scroll-Performance und Swipe/Delete-Verhalten beobachten. |
| M-02 | **Ladezustände UI** | Skeletons und States sind vorhanden, aber auf Konsistenz zwischen Features weiter schärfen. |
| M-03 | **Leere Zustände & Onboarding** | Dashboard und Historie sind vorhanden, können textlich und visuell noch feiner abgestimmt werden. |

### Prioritaet NIEDRIG — Nice-to-Have
| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| N-01 | **String Resources** | Restliche Hardcoded Texte konsequent in `strings.xml` ziehen. |
| N-02 | **Datenbank-Migrationen** | Weitere Migrationen und Schema-Tests für kommende Releases ergänzen. |
| N-03 | **Feintuning Workout-UX** | Weitere Politur für Edit-States, Supersets und Rest-Timer-Chips. |
| N-04 | **History Gesten** | Swipe-to-Delete ist aktiv; visuelle Rückmeldung und Undo-Verhalten können noch ausgebaut werden. |

---

## Build-Anleitung

### Voraussetzungen (installiert)
- Android Studio 2025.2.3.9 (`C:\Program Files\Android\Android Studio`)
- JDK 17 (Android Studio JBR)
- Android SDK Platform 36, Target SDK 35, aktuelle Platform Tools

### Build ausführen
```bash
# Environment Variablen setzen (falls Kommandozeile)
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="C:/Users/maert/AppData/Local/Android/Sdk"

# App kompilieren
cd C:\Users\maert\IronLog
./gradlew.bat :app:assembleDebug
```

### In Android Studio oeffnen
1. Android Studio starten
2. "Open" → `C:\Users\maert\IronLog`
3. Gradle Sync abwarten
4. "Run" (grüner Play-Button)

---

## Änderungs- & Architektur-Protokoll

| Datum | Änderung |
|-------|-----------|
| 2025-02-09 | Projekt initial aufgesetzt |
| 2025-02-10 | Migration auf Multi-Module (`core`, `data`, `feature`) |
| 2026-03-20 | Toolchain aktualisiert auf Gradle 9.1.0, AGP 9.0.0, Kotlin 2.3.10 und Compose BOM 2025.12.01 |
| 2026-03-20 | Shared-Transition-Helfer, Workout-State-Reconciliation und Rest-Timer-Handling stabilisiert |
| 2026-03-20 | Projektplan auf den aktuellen Modul-, Test- und Tooling-Stand gebracht |
