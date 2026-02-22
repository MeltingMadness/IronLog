# IronLog — Projektplan & Status

## Übersicht

| Eigenschaft | Wert |
|---|---|
| **App-Name** | IronLog |
| **Pfad** | `C:\Users\maert\IronLog` |
| **Package** | `com.ironlog.app` |
| **Tech Stack** | Kotlin 2.1, Jetpack Compose (M3), Room 2.6.1, Koin 4.0, Navigation Compose, Vico Charts 2.0.1, DataStore |
| **AGP** | 8.13.2 |
| **Gradle** | 8.13 |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 |
| **JDK** | 21 (Android Studio JBR) |
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
- Sätze loggen, Timer, PR-Erkennung in Echtzeit.

### Phase 3: Verlauf (ABGESCHLOSSEN)
- Chronologische Liste aller Trainings im Modul `feature:history`.
- Detailansicht mit allen Sätzen und Gesamtvolumen.

### Phase 4: Dashboard (ABGESCHLOSSEN)
- Startbildschirm im Modul `feature:dashboard` mit Streak-Berechnung, Wöchentlichem Volumen (Weekly Volume) und Muskel-Heatmap.

### Phase 5: Statistiken & PRs (ABGESCHLOSSEN)
- E1RM-Berechnung und Fortschritts-Diagramme im Modul `feature:statistics`.

### Phase 6: Pläne, Settings & Hintergrund-Dienste (ABGESCHLOSSEN)
- **Trainingspläne:** Erstellen und Verwalten von Plänen (`feature:plans`).
- **Einstellungen:** App-Einstellungen über DataStore persistiert (`feature:settings`, `data:preferences`).
- **Background Tasks:** Training Reminder Worker (`data:reminder`).
- **Daten:** Backup & Export Validierung (`data:backup`), Crash/Fehlerberichte (`data:incident`).
- **Unit Tests:** Umfangreiche Testabdeckung für ViewModels, Repositories, Use-Cases und Validatoren ist implementiert.

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
| H-02 | **Daten-Persistenz testen** | Manueller End-to-End Test für den Lebenszyklus: App starten, Training loggen, App killen, neu starten. Daten müssen sicher gespeichert bleiben. |

### Prioritaet MITTEL — Qualitaet & UX
| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| M-01 | **History Pagination** | Große Trainingsverläufe laden bisher alles auf einmal. Umstellung auf Room Paging 3 in `feature:history`. |
| M-02 | **Ladezustände UI** | Bessere Visualisierung (z.B. CircularProgressIndicator) während asynchrone Datenbank-Queries laufen. |
| M-03 | **Leere Zustände verbessern** | Onboarding-Hinweise im Dashboard und Historie für Erstnutzer hinzufügen. |

### Prioritaet NIEDRIG — Nice-to-Have
| # | Aufgabe | Beschreibung |
|---|---------|-------------|
| N-01 | **String Resources** | Hardcoded Kotlin-Strings (z.B. UI-Texte) systematisch in `strings.xml` extrahieren. |
| N-02 | **Datenbank-Migrationen** | Setup für definierte Migrationspfade bei künftigen Schema-Änderungen. |
| N-03 | **Warmup-Sätze UI** | Das Datenmodell (`isWarmup`) ist vorbereitet, in der UI der `SetInputRow` fehlt aber noch ein Flag dafür. |
| N-04 | **Swipe-to-Delete** | Löschen von Einträgen in der Historie per Wischgeste (aktuell nur Icon-Button). |

---

## Build-Anleitung

### Voraussetzungen (installiert)
- Android Studio 2025.2.3.9 (`C:\Program Files\Android\Android Studio`)
- JDK 21 (Android Studio JBR)
- Android SDK Platform 35, Build Tools 35.0.0, Platform Tools

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
| 2025-02-10 | Migration auf Gradle 8.13 und AGP 8.13.2 |
| **Aktuell** | **Komplette Umstrukturierung zu Multi-Module (`core`, `data`, `feature`)** |
| **Aktuell** | Implementierung von Unit-Tests, Trainingsplänen, DataStore Settings und Background Remindern |
| **Aktuell** | Aktualisierung des Projektplans zur Abbildung der Ist-Architektur |
