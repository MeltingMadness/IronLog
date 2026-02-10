# IronLog — Projektplan & Status

## Uebersicht

| Eigenschaft | Wert |
|---|---|
| **App-Name** | IronLog |
| **Pfad** | `C:\Users\maert\IronLog` |
| **Package** | `com.ironlog.app` |
| **Tech Stack** | Kotlin 2.1, Jetpack Compose (M3), Room 2.6.1, Koin 4.0, Navigation Compose, Vico Charts 2.0.1 |
| **AGP** | 8.13.2 |
| **Gradle** | 8.13 |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 |
| **JDK** | 21 (Android Studio JBR) |
| **Architektur** | MVVM + Clean Architecture (data/domain/presentation), Single Module |
| **UI-Sprache** | Deutsch |
| **Build-Status** | assembleDebug ERFOLGREICH (0 Errors, 0 Warnings) |

---

## Was wurde implementiert

### Phase 1: Fundament (ABGESCHLOSSEN)

Alles was das Projekt zum Laufen bringt:

- **Android-Projekt** komplett aufgesetzt: Gradle Wrapper, `build.gradle.kts`, Dependencies, Package-Struktur
- **Room-Datenbank** mit 4 Tabellen:
  - `exercises` — Uebungskatalog mit Muskelgruppen und Kategorien
  - `workout_sessions` — Trainingseinheiten (Start/Ende/Dauer)
  - `workout_sets` — Einzelne Saetze (Gewicht, Wdh, Satznummer)
  - `personal_records` — Persoenliche Rekorde pro Uebung und Typ
- **Seed-Daten:** 76 vorinstallierte deutsche Uebungen (Brust, Ruecken, Beine, Schultern, Bizeps, Trizeps, Gesaess, Core, Unterarme, Waden)
- **4 DAOs** mit Flow-basierter reaktiver Abfrage
- **Foreign Keys** mit CASCADE (Session→Sets) und RESTRICT (Exercise→Sets), Indices
- **Koin DI** komplett eingerichtet (Database, DAOs, Repositories, ViewModels)
- **Material 3 Theme** mit Light/Dark Mode und Dynamic Color (Android 12+)
- **Navigation** mit Bottom Navigation Bar (3 Tabs: Home, Verlauf, Uebungen)

### Phase 2: Training-Tracking (ABGESCHLOSSEN)

Kern-Feature der App:

- **ActiveWorkoutScreen + ViewModel** — Kompletter Workout-Flow
- **Training starten** vom Dashboard aus (erstellt neue Session)
- **Uebung hinzufuegen** ueber ExercisePickerSheet (BottomSheet mit Suche und Muskelgruppen-Filter)
- **Saetze loggen** — Eingabezeile pro Uebung (kg + Wdh + "Loggen"-Button)
- **Saetze loeschen** — Delete-Button pro Satz
- **Laufender Timer** — Zeigt Trainingszeit in Echtzeit (HH:MM:SS)
- **Training beenden** — Bestaetigungsdialog, speichert Endzeit und Dauer
- **PR-Erkennung** — Prueft bei jedem Satz auf Max Gewicht, Max Wdh, E1RM (Epley), Max Volumen
- **PR-Benachrichtigung** — Snackbar-Animation bei neuem persoenlichen Rekord
- **WorkoutRepository** — Start/Finish/AddSet/DeleteSet/GetSets, Session-Verwaltung

### Phase 3: Verlauf (ABGESCHLOSSEN)

- **WorkoutHistoryScreen** — Chronologische Liste aller abgeschlossenen Trainings
- **Karte pro Training:** Datum, Uhrzeit, Dauer, Anzahl Uebungen, Saetze, Gesamtvolumen
- **Loeschen** — Delete-Button mit Bestaetigungsdialog ("unwiderruflich")
- **WorkoutDetailScreen** — Detail-Ansicht eines Trainings
  - Header: Datum, Dauer, Gesamtvolumen, Notizen
  - Gruppiert nach Uebung: Name + alle Saetze (Nr, Gewicht x Wdh)
  - Tippen auf Uebung → Statistik-Screen
- **Leerer Zustand** — Hinweistext wenn noch keine Trainings vorhanden

### Phase 4: Dashboard (ABGESCHLOSSEN)

- **DashboardScreen + ViewModel** — Home-Screen der App
- **"Training starten"** — Grosser Button, erstellt neue Session
- **"Training fortsetzen"** — Wenn aktive Session vorhanden (gruener Button)
- **Schnellstatistik:** 3 Karten nebeneinander
  - Trainings diese Woche
  - Trainings diesen Monat
  - Aktuelle Tages-Serie (Streak)
- **Letzte Rekorde** — Die 5 neuesten PRs mit Uebungsname und Wert
- **Letztes Training** — Datum, Dauer, Anzahl Uebungen
- **Streak-Berechnung** — Zaehlt aufeinanderfolgende Tage mit Training

### Phase 5: Statistiken & PRs (ABGESCHLOSSEN)

- **ExerciseStatsScreen + ViewModel** — Statistik pro Uebung
- **4 PR-Karten:** Max Gewicht, Max Wdh, Bester geschaetzter 1RM, Max Volumen
- **Fortschritts-Diagramm** mit Vico Charts (Linie ueber Zeit)
- **Metrik-Umschaltung:** 3 FilterChips — Gewicht / Gesch. 1RM / Volumen
- **E1RM-Berechnung** nach Epley-Formel: `gewicht * (1 + wdh / 30)`
- **Zugang:** Von Uebungsbibliothek (Tippen auf Uebung) oder Training-Detail

### Phase 6: Feinschliff (TEILWEISE)

Was schon da ist:
- **Leere Zustaende** — Hinweistext in Verlauf wenn keine Trainings
- **Eingabe-Validierung** — Set-Loggen nur wenn Wdh > 0 und Gewicht >= 0
- **Bestaetigungsdialoge** — Training beenden, Training loeschen
- **App-Icon** — Vektor-basiertes Hantel-Icon (Orange auf Dunkelblau, Adaptive Icon)
- **Edge-to-Edge** — Modernes Android-Design mit Window Insets

---

## Dateistruktur (49 Kotlin-Dateien)

```
C:\Users\maert\IronLog\
├── build.gradle.kts                          # Root build (AGP 8.13.2, Kotlin 2.1.0)
├── settings.gradle.kts                       # Projekt-Settings
├── gradle.properties                         # Gradle-Konfiguration
├── gradlew / gradlew.bat                     # Gradle Wrapper
├── local.properties                          # SDK-Pfad
├── .gitignore
├── PROJEKTPLAN.md                            # <-- DIESES DOKUMENT
│
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties             # Gradle 8.13
│
└── app/
    ├── build.gradle.kts                      # App-Dependencies
    ├── proguard-rules.pro
    │
    └── src/main/
        ├── AndroidManifest.xml
        │
        ├── res/
        │   ├── values/strings.xml, colors.xml, themes.xml
        │   ├── drawable/ic_launcher_foreground.xml, ic_launcher_background.xml
        │   └── mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml
        │
        └── java/com/ironlog/app/
            ├── IronLogApplication.kt         # Koin-Initialisierung
            ├── MainActivity.kt               # Single Activity, Edge-to-Edge, Scaffold
            │
            ├── domain/
            │   ├── model/
            │   │   ├── Exercise.kt           # Uebung (Name, Muskelgruppen, Kategorie)
            │   │   ├── ExerciseCategory.kt   # Enum: Langhantel/Kurzhantel/Maschine/Kabel/Eigengewicht
            │   │   ├── MuscleGroup.kt        # Enum: 10 Muskelgruppen
            │   │   ├── WorkoutSession.kt     # Trainingseinheit
            │   │   ├── WorkoutSet.kt         # Einzelner Satz
            │   │   └── PersonalRecord.kt     # PR + RecordType Enum
            │   └── repository/
            │       ├── ExerciseRepository.kt
            │       ├── WorkoutRepository.kt
            │       └── StatisticsRepository.kt
            │
            ├── data/
            │   ├── local/
            │   │   ├── IronLogDatabase.kt    # Room DB (v1), Seed-Callback
            │   │   ├── dao/
            │   │   │   ├── ExerciseDao.kt
            │   │   │   ├── WorkoutSessionDao.kt
            │   │   │   ├── WorkoutSetDao.kt
            │   │   │   └── PersonalRecordDao.kt
            │   │   ├── entity/
            │   │   │   ├── ExerciseEntity.kt
            │   │   │   ├── WorkoutSessionEntity.kt  # + interne Converters
            │   │   │   ├── WorkoutSetEntity.kt
            │   │   │   └── PersonalRecordEntity.kt
            │   │   ├── relation/
            │   │   │   └── SessionWithSets.kt
            │   │   └── converter/
            │   │       └── Converters.kt     # (registriert, aber Entity-Converters werden genutzt)
            │   ├── repository/
            │   │   ├── ExerciseRepositoryImpl.kt
            │   │   ├── WorkoutRepositoryImpl.kt
            │   │   └── StatisticsRepositoryImpl.kt
            │   └── seed/
            │       └── ExerciseSeedData.kt   # 76 deutsche Uebungen
            │
            ├── presentation/
            │   ├── navigation/
            │   │   ├── Screen.kt             # 6 Routen (sealed class)
            │   │   ├── NavHost.kt            # Navigation-Graph
            │   │   └── BottomNavBar.kt       # Bottom Navigation (3 Tabs)
            │   ├── theme/
            │   │   ├── Color.kt              # M3 Light + Dark Colors
            │   │   ├── Type.kt               # Typography
            │   │   └── Theme.kt              # Dynamic Color Support
            │   ├── common/
            │   │   ├── SetInputRow.kt        # kg + Wdh + Loggen
            │   │   ├── StatCard.kt           # Statistik-Karte
            │   │   └── WorkoutTimer.kt       # Laufender Timer
            │   ├── dashboard/
            │   │   ├── DashboardScreen.kt
            │   │   └── DashboardViewModel.kt
            │   ├── workout/
            │   │   ├── ActiveWorkoutScreen.kt
            │   │   ├── ActiveWorkoutViewModel.kt
            │   │   └── ExercisePickerSheet.kt
            │   ├── exercises/
            │   │   ├── ExerciseLibraryScreen.kt
            │   │   └── ExerciseLibraryViewModel.kt
            │   ├── history/
            │   │   ├── WorkoutHistoryScreen.kt
            │   │   ├── WorkoutHistoryViewModel.kt
            │   │   ├── WorkoutDetailScreen.kt
            │   │   └── WorkoutDetailViewModel.kt
            │   └── statistics/
            │       ├── ExerciseStatsScreen.kt
            │       └── ExerciseStatsViewModel.kt
            │
            └── di/
                └── AppModule.kt              # Koin: DB, DAOs, Repos, ViewModels
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
```

---

## Was noch zu tun ist (Offene Punkte)

### Prioritaet HOCH — Funktionalitaet

| # | Aufgabe | Beschreibung | Aufwand |
|---|---------|-------------|---------|
| H-01 | **Unit Tests** | Keine Tests vorhanden. Mindestens: ViewModels (Dashboard, ActiveWorkout), Repositories, Streak-Berechnung, E1RM-Formel | 4-6h |
| H-02 | **Error Handling** | Kein try-catch in Repositories/ViewModels. DB-Fehler fuehren zu Crashes. Globalen Error-Handler einbauen | 2-3h |
| H-03 | **Daten-Persistenz testen** | Manueller Test: App starten, Training loggen, App killen, neu starten. Daten muessen erhalten bleiben | 1h |
| H-04 | **Enum-Parsing absichern** | `MuscleGroup.valueOf()` in Entity-Convertern crasht bei unbekanntem Wert. Fallback einbauen | 1h |

### Prioritaet MITTEL — Qualitaet & UX

| # | Aufgabe | Beschreibung | Aufwand |
|---|---------|-------------|---------|
| M-01 | **Ladezustaende** | Kein Loading-Indicator bei Datenbankzugriffen. CircularProgressIndicator einbauen | 2h |
| M-02 | **Leere Zustaende verbessern** | Dashboard und Statistik zeigen nichts bei Erstnutzer. Onboarding-Hinweise | 1-2h |
| M-03 | **Datum-Formatierung** | Hardcoded `DateTimeFormatter.ofPattern("dd.MM.yyyy")`. Locale-aware machen | 1h |
| M-04 | **Converter aufraemen** | `RoomConverters` in converter/ wird nicht genutzt. Entweder registrieren und Entity-interne Converters ersetzen, oder loeschen | 1h |
| M-05 | **Epley-Formel zentralisieren** | E1RM-Berechnung dupliziert in ActiveWorkoutViewModel und ExerciseStatsViewModel. In Use Case extrahieren | 1h |
| M-06 | **History Pagination** | Grosse Trainingsverlauefe laden alles auf einmal. Room Paging einbauen | 2-3h |
| M-07 | **Statistik-Chart bei < 2 Datenpunkten** | Zeigt einfach nichts. Hinweistext einblenden | 30min |
| M-08 | **Trainingsname editierbar** | Session-Name wird nie gesetzt. Eingabefeld oder Auto-Name (z.B. "Oberkörper 10.02.") | 1h |

### Prioritaet NIEDRIG — Nice-to-Have

| # | Aufgabe | Beschreibung | Aufwand |
|---|---------|-------------|---------|
| N-01 | **String Resources** | Alle UI-Texte sind hardcoded in Kotlin. In `strings.xml` extrahieren fuer Wartbarkeit | 3-4h |
| N-02 | **Datenbank-Migration** | Kein Migrationspfad definiert. Bei Schema-Aenderung gehen alle Daten verloren | 1h Setup |
| N-03 | **ProGuard/R8** | Release-Build hat `isMinifyEnabled = false`. Fuer Play Store aktivieren | 1h |
| N-04 | **Backup deaktivieren** | `android:allowBackup="true"` — fuer Fitness-Daten unkritisch, aber best practice ist false | 5min |
| N-05 | **Warmup-Saetze markieren** | UI existiert nicht, Datenmodell unterstuetzt es bereits (`isWarmup`) | 2h |
| N-06 | **Notizen pro Training** | Feld existiert im Modell (`notes`), kein UI dafuer | 1h |
| N-07 | **PR-Markierung in Detail-Ansicht** | Plan sieht vor: PR-Badge an Rekord-Saetzen. Noch nicht implementiert | 2h |
| N-08 | **Swipe-to-Delete in Verlauf** | Plan sieht Wisch-Geste vor, aktuell nur Icon-Button | 1-2h |
| N-09 | **Eigene Uebung loeschen aus Bibliothek** | deleteCustomExercise() existiert, kein UI dafuer (z.B. Long Press) | 1h |

### Technische Schulden

| # | Problem | Wo | Auswirkung |
|---|---------|-----|------------|
| T-01 | Seed-Daten via Raw SQL statt DAO | IronLogDatabase.kt:55-65 | Umgeht Typensicherheit |
| T-02 | Converter-Code dupliziert | WorkoutSessionEntity + WorkoutSetEntity haben identische Converters | Wartbarkeit |
| T-03 | Zeitzone immer UTC | Alle Timestamps in UTC gespeichert, Anzeige auch UTC | Falsche Zeitanzeige bei UTC-Offset |
| T-04 | WorkoutRepository zu gross | 18 Methoden in einem Interface | Single Responsibility |
| T-05 | ExercisePickerSheet State-Management | MutableStateFlow in remember{} ist fragil | Race Conditions moeglich |
| T-06 | Timer-LaunchedEffect laeuft endlos | WorkoutTimer.kt while(true) loop | Batterieverbrauch |

---

## Build-Anleitung

### Voraussetzungen (installiert)
- Android Studio 2025.2.3.9 (`C:\Program Files\Android\Android Studio`)
- JDK 21 (Android Studio JBR)
- Android SDK Platform 35, Build Tools 35.0.0, Platform Tools

### Build ausfuehren
```bash
# Environment
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="C:/Users/maert/AppData/Local/Android/Sdk"

# Debug-Build
cd C:\Users\maert\IronLog
./gradlew.bat assembleDebug

# APK-Pfad
app/build/outputs/apk/debug/app-debug.apk
```

### Auf Geraet installieren
```bash
# USB-Debugging am Handy aktivieren, dann:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### In Android Studio oeffnen
1. Android Studio starten
2. "Open" → `C:\Users\maert\IronLog`
3. Gradle Sync abwarten
4. Device Manager → Emulator erstellen oder USB-Geraet verbinden
5. "Run" (gruener Play-Button)

---

## Navigationsstruktur

```
Bottom Navigation Bar
├── [Home] Dashboard
│   ├── "Training starten" → Aktives Training (neuer Session)
│   └── "Training fortsetzen" → Aktives Training (bestehende Session)
│
├── [Verlauf] Trainingsverlauf
│   └── Tippen auf Training → Training-Detail
│       └── Tippen auf Uebung → Uebungs-Statistik
│
└── [Uebungen] Uebungsbibliothek
    ├── Suche + Muskelgruppen-Filter
    ├── FAB → Eigene Uebung erstellen (Dialog)
    └── Tippen auf Uebung → Uebungs-Statistik
```

---

## Aenderungsprotokoll

| Datum | Aenderung |
|-------|-----------|
| 2025-02-09 | Projekt erstellt: Komplette App-Implementierung (Phasen 1-5) |
| 2025-02-09 | Gradle Wrapper JAR heruntergeladen |
| 2025-02-09 | ExercisePicker fix: addExercise() statt Dummy-Set bei Uebungsauswahl |
| 2025-02-09 | Database warning fix: arrayOf<Any>() statt arrayOf() in Seed-Callback |
| 2025-02-10 | Android Studio installiert (winget), SDK eingerichtet |
| 2025-02-10 | Erster erfolgreicher Build (assembleDebug) |
| 2025-02-10 | Gradle aktualisiert: 8.11.1 → 8.13, AGP: 8.7.3 → 8.13.2 (via Android Studio) |
| 2025-02-10 | Projektplan erstellt (dieses Dokument) |
