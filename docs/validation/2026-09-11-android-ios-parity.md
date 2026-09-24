# Android–iOS-Paritätsaudit · 11. September 2026

**Urteil: FIX. Die Oberflächen sind nicht gleich aufgebaut; die Geschäftslogik ist noch nicht durchgehend gleich funktional.** Die frühere pauschale Paritätsaussage war zu optimistisch. Ein gemeinsamer Rechenkern ersetzt keine Prüfung der Eingaben, Aufrufer, Speicherregeln und Benutzeraktionen.

## Prüfrahmen und Beleggrenzen

- Quellstand: `main`, HEAD `a0b72ea3d4ec94961b7a544f9ee94a84ca1b2893` plus bereits vorhandene, umfangreiche lokale Änderungen. Der Audit bewertet diesen Arbeitsbaum, nicht nur den Commit.
- Vier unabhängige Luna-Subagents mit Reasoning max prüften Training, Pläne/Progression, Analytik und Einstellungen/Daten. Tragende Befunde wurden im Hauptlauf gegen den Quelltext geprüft.
- Live: Android-Handy, installierte Version **1.1.2 (4)**, Installation vom 9. September; iPhone-17-Simulator mit bestehender iOS-Installation. Startseite, Planliste und Verlauf wurden geöffnet und frisch fotografiert. Anschließend wurden beide Apps auf die Startseite zurückgestellt.
- Es wurde weder neu gebaut noch installiert. Übereinstimmende Versionsfelder sind kein Nachweis, dass die installierten Binärdateien exakt dem aktuellen Arbeitsbaum entsprechen.
- Android enthält reale Trainings, der iOS-Simulator andere Testdaten. Die Screenshots belegen den Aufbau, keinen Zahlenvergleich bei identischer Datenbasis. Farbthemen und Bildschirmgrößen unterscheiden sich ebenfalls.
- Keine Trainingseinträge verändert, keine Vorschläge angenommen, keine Importe durchgeführt. Keine Tests oder vollständigen Testreihen ausgeführt. Die folgenden Logikbeispiele sind aus konkreten Codepfaden abgeleitete Gegenbeispiele, keine heute auf beiden Geräten durchgespielten Mutationen.

**Entscheidendes Kriterium:** Gleiche gültige Daten, Einstellungen und Aktionen müssen dasselbe fachliche Ergebnis liefern; zentrale Funktionen müssen auf beiden Plattformen erreichbar sein. **FAIL** aufgrund der nachfolgenden Gegenbeispiele. Das ist reparierbar und kein Anlass, die native iOS-Architektur zu verwerfen.

## Oberflächenvergleich

| Bereich | Android | iOS | Einordnung |
|---|---|---|---|
| Navigation | 4 Tabs: Home, Pläne, Verlauf, Übungen | 5 Tabs: Start, Training, Verlauf, Pläne, Einstellungen | Andere Informationsarchitektur; live bestätigt |
| Startseite | Einstellungen oben; Belastungskarte; Trainingsstart mit Auswahl | Statistik oben; Readiness-Kreis; direktere Startaktionen | Aktionen und Kartenreihenfolge weichen ab |
| Planliste | Große Karten mit Übungsvorschau und Startknopf | Kompakte Zeilen, Aktionsmenü, Pläne/Meta-Pläne-Umschaltung | Live bestätigt; iOS übernimmt den Android-Aufbau nicht |
| Verlauf | Datums- und Plan-Chips; Satzdetails schreibgeschützt | Suchfeld, Zusammenfassung, Filtermenüs; Satz- und Notizbearbeitung | Mehr Funktionen auf iOS; beide besitzen Planfilter |
| Statistiken | Übungsbezogene Detailansicht | Zusätzlich zentrale Übersicht, Suche, Muskelfilter, Wiederholungsmetrik | Funktionsumfang verschieden |
| Wochen-Muskelvolumen | Wochenvolumen vorhanden | Wochenvolumen vorhanden | Grundregeln gleich; Reihenfolge der Muskelgruppen verschieden |
| Progressionsreview | Global offene Vorschläge | Zusätzlich entschiedene/veraltete Vorschläge | Andere Sichtbarkeit des Verlaufs; keine behauptete Datenlöschung |
| Einstellungen | Restzeit-Auswahl bis 300 Sekunden; Material You | Restzeit-Auswahl bis 600 Sekunden; Deload-Auswahl hier erreichbar | Auswahlumfang unterschiedlich; Material You ist plattformspezifisch |

Quellbelege zur Matrix:
[Screen.kt:54](/Users/maert/Documents/IronLog/app/src/main/java/com/ironlog/app/presentation/navigation/Screen.kt:54) · [RootView.swift:9](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/RootView.swift:9) · [DashboardScreen.kt:101](/Users/maert/Documents/IronLog/feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt:101) · [IOSDashboardScreen.swift:31](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Dashboard/IOSDashboardScreen.swift:31) · [WorkoutDetailScreen.kt:157](/Users/maert/Documents/IronLog/feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutDetailScreen.kt:157) · [IOSHistoryScreen.swift:321](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/History/IOSHistoryScreen.swift:321) · [IOSStatisticsScreen.swift:19](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Statistics/IOSStatisticsScreen.swift:19) · [SettingsScreen.kt:473](/Users/maert/Documents/IronLog/feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsScreen.kt:473) · [SettingsScreen.swift:94](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/SettingsScreen.swift:94)

## Nachgewiesene Unterschiede in den Codepfaden

Priorität P1: Daten oder Trainingsentscheidungen zuerst absichern. P2: weitere fachliche Konsistenz und Bedienung herstellen. Die Priorität bezeichnet die Umsetzungsempfehlung, keinen Nachweis eines bereits eingetretenen Datenverlusts.

### 1. P1 · RPE beim Bearbeiten auf iOS

Bei global ausgeschalteter Intensität setzt der iOS-Satzeditor RPE auf `nil`, auch wenn nur das Gewicht eines bestehenden Satzes geändert wird. Android erhält den vorhandenen RPE bei effektiv ausgeschalteter Intensität. Zusätzlich erzwingt Android für RPE/RIR-Progressionspläne ein RPE-Feld trotz globalem OFF; iOS versteckt es. Dadurch können historische Intensitätswerte verloren gehen oder die Eingaben für die Progression fehlen.

**Belege:** [ActiveWorkoutViewModel.kt:648](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt:648) · [ActiveWorkoutViewModel.kt:757](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt:757) · [IOSWorkoutSetEditor.swift:120](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutSetEditor.swift:120) · [IOSWorkoutSetEditor.swift:173](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutSetEditor.swift:173)

**Kleinste Reparatur/Prüfung:** Gemeinsame Regel für effektive Intensität und das Erhalten verborgener Felder. Regression: RPE 9, global OFF, manuelles Training, nur Gewicht ändern → RPE bleibt 9; RPE/RIR-Plan + OFF → Intensität erfassbar.

### 2. P1 · Start eines anderen Plans bei laufendem Training

Android zeigt bei aktivem Plan A und Start von Plan B einen Fehler. Shared/iOS liefert sofort die ID irgendeiner aktiven Session zurück; iOS wechselt nach erfolgreichem Kommando zum Training. Damit wird beim vermeintlichen Start von B stillschweigend A fortgesetzt.

**Belege:** [TrainingPlanListViewModel.kt:75](/Users/maert/Documents/IronLog/feature/plans/src/main/java/com/ironlog/app/presentation/plans/TrainingPlanListViewModel.kt:75) · [SharedStateStore.kt:554](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/store/SharedStateStore.kt:554) · [IOSTrainingStore.swift:151](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/IOSTrainingStore.swift:151)

**Kleinste Reparatur/Prüfung:** Konfliktprüfung in den gemeinsamen Startvertrag aufnehmen. Regression: A aktiv, B starten → verständlicher Konflikt, keine neue Session und kein stiller Planwechsel.

### 3. P1 · Ältere Progressionsvorschläge

Bei zwei offenen Vorschlägen derselben Planposition und noch unverändertem Ausgangsziel kann Android den älteren Vorschlag einzeln akzeptieren: Es prüft nur, ob das aktuelle Planziel noch zum Ursprung passt. Shared/iOS prüft zusätzlich auf einen neueren offenen Vorschlag und markiert den alten als STALE. Androids Sammelaktion wählt bereits die neuesten Vorschläge; die Lücke betrifft die Einzelentscheidung.

**Belege:** [ProgressionRepositoryImpl.kt:79](/Users/maert/Documents/IronLog/data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt:79) · [ProgressionRepositoryImpl.kt:133](/Users/maert/Documents/IronLog/data/src/main/java/com/ironlog/app/data/repository/ProgressionRepositoryImpl.kt:133) · [ProgressionLifecycle.kt:193](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/store/ProgressionLifecycle.kt:193) · [ProgressionLifecycle.kt:777](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/store/ProgressionLifecycle.kt:777)

**Kleinste Reparatur/Prüfung:** Dieselbe Neuheitsprüfung in beide Entscheidungspfade bringen. Regression mit zwei Sessions, zwei PENDING-Vorschlägen und identischem Quellziel; alten Vorschlag einzeln annehmen.

### 4. P1 · Meta-Rotation verliert Wiederholungen beim Android-Speichern

iOS erlaubt eine geordnete Rotation A, B, A. Android entfernt identische Plan-IDs beim Laden und beim Speichern mit distinct(). Wird eine solche Rotation nach Datenübernahme auf Android bearbeitet und gespeichert, wird daraus A, B. Das verändert die Trainingsfolge.

**Belege:** [MetaPlanEditorViewModel.kt:85](/Users/maert/Documents/IronLog/feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanEditorViewModel.kt:85) · [MetaPlanEditorViewModel.kt:202](/Users/maert/Documents/IronLog/feature/plans/src/main/java/com/ironlog/app/presentation/plans/MetaPlanEditorViewModel.kt:202) · [IOSMetaPlanEditorScreen.swift:113](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Plans/IOSMetaPlanEditorScreen.swift:113) · [SharedStateStore.kt:418](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/store/SharedStateStore.kt:418)

**Kleinste Reparatur/Prüfung:** Rotation als geordnete Liste von Einträgen auf beiden Plattformen behandeln. Regression: A/B/A laden, Namen ändern, speichern → A/B/A bleibt erhalten.

### 5. P2 · Android-Pausentimer zählt andere Sätze und ignoriert Deload-Ziel

Android entscheidet anhand der unveränderten geplanten Satzzahl und aller Nicht-Warmup-Sätze, ob eine Übung fertig ist. Die angezeigten geplanten Slots und iOS verwenden dagegen NORMAL-Sätze und das reduzierte Deload-Ziel. Beispiel: Plan 4 Sätze, Deload auf 2 → nach dem zweiten normalen Satz stoppt iOS den Timer, Android startet ihn erneut. DROP/FAILURE können auf Android umgekehrt zu früh als erfüllte Slots zählen.

**Belege:** [ActiveWorkoutViewModel.kt:590](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt:590) · [ActiveWorkoutScreen.kt:673](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt:673) · [IOSWorkoutScreen.swift:608](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutScreen.swift:608)

**Kleinste Reparatur/Prüfung:** Eine gemeinsame Funktion für erfüllte Plan-Slots und effektive Deload-Ziele verwenden. Gezielte Fälle: letzter normaler Satz, Warmup, DROP, FAILURE, Deload.

### 6. P2 · Statistik berücksichtigt unterschiedliche Trainings

Die Android-Übungsstatistik lädt Sätze ohne Filter auf abgeschlossene Sessions. iOS nutzt nur abgeschlossene, zeitlich gültige Trainings. Beispiel: abgeschlossen 80 kg × 5, laufendes Training 100 kg × 5 → Android kann bereits 100 kg und e1RM 116,7 anzeigen, iOS weiter 80 kg und e1RM 93,3. Das ist eine abweichende Auswertungsdefinition, kein Beleg für unterschiedliche Epley-Formeln.

**Belege:** [ExerciseStatsViewModel.kt:99](/Users/maert/Documents/IronLog/feature/statistics/src/main/java/com/ironlog/app/presentation/statistics/ExerciseStatsViewModel.kt:99) · [WorkoutSetDao.kt:35](/Users/maert/Documents/IronLog/core/database/src/main/java/com/ironlog/app/data/local/dao/WorkoutSetDao.kt:35) · [SharedTrainingAnalytics.kt:105](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/analytics/SharedTrainingAnalytics.kt:105) · [SharedTrainingAnalytics.kt:327](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/analytics/SharedTrainingAnalytics.kt:327)

**Kleinste Reparatur/Prüfung:** Festlegen, ob historische Statistik nur abgeschlossene Trainings enthält; Empfehlung: ja, laufende Werte gesondert kennzeichnen. Gleiche Projektion und Fixture auf beiden Plattformen.

### 7. P2 · Backoff-Empfehlung mit verschiedenen Parametern

Android verwendet für das zusätzliche Backoff-Gewicht nach RPE-Überschreitung den Standardabschlag von 10 %. iOS übergibt den konfigurierten backoffPercent an den Coach. Bei 100 kg und 20 % Konfiguration entstehen 90 kg gegenüber 80 kg. Ob der Progressions-Backoff auch für den Folgesatz gelten soll, muss als Produktregel geklärt werden; aus dem Unterschied allein folgt nicht, welche Seite richtig ist.

**Belege:** [ActiveWorkoutViewModel.kt:102](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt:102) · [RpeAutoregulation.kt:57](/Users/maert/Documents/IronLog/core/common/src/main/java/com/ironlog/app/domain/util/RpeAutoregulation.kt:57) · [IOSWorkoutNextSetCoach.swift:42](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutNextSetCoach.swift:42)

**Kleinste Reparatur/Prüfung:** Den beabsichtigten Backoff-Vertrag festlegen und identische Parameter weiterreichen. Regression mit bewusst vom Standard abweichenden 20 %.

### 8. P2 · Readiness und Belastung sind unterschiedlich orientiert

Androids prominente Belastungskarte zeigt fatigueScore; iOS zeigt auf der Bereitschaftskarte 100 − fatigueScore. Derselbe Ermüdungswert 60 erscheint daher als Belastung 60/100 beziehungsweise Bereitschaft 40 %. Bei unauffälliger Belastung waren live auf Android ein Haken und auf iOS 100 % sichtbar. Die Bezeichnungen sind verschieden, daher kein nachgewiesener Rechenfehler; die Plattformen vermitteln aber nicht dieselbe Hauptkennzahl.

**Belege:** [DashboardScreen.kt:648](/Users/maert/Documents/IronLog/feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardScreen.kt:648) · [IOSDashboardScreen.swift:384](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Dashboard/IOSDashboardScreen.swift:384) · [SharedTrainingAnalytics.kt:479](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/analytics/SharedTrainingAnalytics.kt:479)

**Kleinste Reparatur/Prüfung:** Auf beiden Startseiten denselben Readiness-Kreis mit derselben Richtung und Beschriftung zeigen; Belastung separat erklären. Fehlende Datengrundlage weiterhin ausdrücklich darstellen.

### 9. P2 · Zeitfilter und Timer-Wiederherstellung

Androids 30-/90-Tage-Verlauf beginnt um Mitternacht des Grenztags; iOS rechnet vom aktuellen Zeitpunkt zurück. Am 11. September um 15 Uhr wird ein Training vom 12. August um 10 Uhr unter „30 Tage“ daher unterschiedlich gefiltert. Außerdem speichert iOS laufende Pausentimer dauerhaft und stellt sie wieder her; Android hält sie nur im ViewModel. Nach Prozessverlust ist das Verhalten damit verschieden.

**Belege:** [WorkoutHistoryViewModel.kt:50](/Users/maert/Documents/IronLog/feature/history/src/main/java/com/ironlog/app/presentation/history/WorkoutHistoryViewModel.kt:50) · [IOSHistorySupport.swift:54](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/History/IOSHistorySupport.swift:54) · [ActiveWorkoutViewModel.kt:237](/Users/maert/Documents/IronLog/feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutViewModel.kt:237) · [IOSWorkoutRestTimer.swift:52](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutRestTimer.swift:52) · [IOSWorkoutScreen.swift:767](/Users/maert/Documents/IronLog/iosApp/IronLogIOS/Workout/IOSWorkoutScreen.swift:767)

**Kleinste Reparatur/Prüfung:** Zeitfenster explizit definieren; Timer-Startzeit und Dauer auch unter Android wiederherstellbar speichern. Grenzzeit-Fixture und gezielter Prozess-Neustarttest in Testdatenumgebung.

### 10. P2 · Progressions-Nachberechnung nach Import

iOS erzeugt fehlende Progressionsergebnisse unmittelbar nach Import/Recovery. Androids Importpfad tut dies nicht; der Dashboard-Hook läuft bei ViewModel-Initialisierung, nicht beim bloßen Refresh. Bei einem alten Backup ohne Ergebnisse kann deshalb im bestehenden Android-Lebenszyklus die Nachberechnung fehlen. Eine spätere Neuerstellung kann sie nachholen; bei bereits konsistentem Backup muss kein sichtbarer Unterschied auftreten.

**Belege:** [IosSettingsFeature.kt:730](/Users/maert/Documents/IronLog/shared/src/iosMain/kotlin/com/ironlog/shared/ios/IosSettingsFeature.kt:730) · [BackupRepositoryImpl.kt:157](/Users/maert/Documents/IronLog/data/src/main/java/com/ironlog/app/data/repository/BackupRepositoryImpl.kt:157) · [SettingsViewModel.kt:229](/Users/maert/Documents/IronLog/feature/settings/src/main/java/com/ironlog/app/presentation/settings/SettingsViewModel.kt:229) · [DashboardViewModel.kt:124](/Users/maert/Documents/IronLog/feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt:124) · [DashboardViewModel.kt:556](/Users/maert/Documents/IronLog/feature/dashboard/src/main/java/com/ironlog/app/presentation/dashboard/DashboardViewModel.kt:556)

**Kleinste Reparatur/Prüfung:** Gemeinsamen idempotenten Nachbereitungsablauf nach Import/Recovery verwenden. Fixture: älteres Backup ohne Outcomes im bereits geöffneten Dashboard importieren.

## Was bereits übereinstimmt und erhalten bleiben sollte

- Die Android-Progressionsauswertung delegiert über den PortableProgressionAdapter an den gemeinsamen Kern. Die bestehenden Schemata müssen daher nicht ein zweites Mal neu erfunden werden; die nachgewiesenen Lücken sitzen unter anderem im Entscheidungslebenszyklus und in der Übergabe der Konfiguration.
- Für gültige Trainingsdaten stimmen die geprüften MEV/MAV/MRV-Schwellen sowie die Gewichtung primärer Muskeln mit 1,0 und sekundärer Muskeln mit 0,5 überein. Die unterschiedliche Datenfilterung kann trotzdem andere Resultate erzeugen.
- Die geprüften Deload-Zielanpassungen und zentralen Belastungsformeln stimmen überein. Die Android-Timerentscheidung verwendet diese Zielanpassung noch nicht konsistent.
- Beide Backup-Pfade verwenden Schema 12 und den gemeinsamen Validator. Einstellungen liegen außerhalb des Trainingsbackups. Beide besitzen Schutzmechanismen vor Überschreiben und Recovery. iOS importiert die Vorschau-Bytes; Android liest die Quelle erneut und prüft ihren Hash. Diese unterschiedliche Technik ist für sich kein Integritätsfehler.

[ProgressionEngine.kt:24](/Users/maert/Documents/IronLog/core/common/src/main/java/com/ironlog/app/domain/progression/ProgressionEngine.kt:24) · [SharedTrainingAnalytics.kt:76](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/analytics/SharedTrainingAnalytics.kt:76) · [MuscleVolumeCalculator.kt:76](/Users/maert/Documents/IronLog/core/common/src/main/java/com/ironlog/app/domain/util/MuscleVolumeCalculator.kt:76) · [DeloadTargetAdjustment.kt:15](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/deload/DeloadTargetAdjustment.kt:15) · [BackupPayloadV1.kt:13](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadV1.kt:13) · [BackupPayloadValidator.kt:1](/Users/maert/Documents/IronLog/shared/src/commonMain/kotlin/com/ironlog/shared/backup/BackupPayloadValidator.kt:1)

## Empfehlung für die Umsetzung

1. Zuerst die vier P1-Fälle beheben: RPE erhalten, Konflikt beim Trainingsstart erkennen, ältere Vorschläge sperren, Rotationen verlustfrei erhalten.
2. Danach Timerabschluss, Statistikfilter, Backoff-Parameter, Datumsgrenzen und Import-Nachbereitung über gemeinsame fachliche Funktionen vereinheitlichen. Parameterisierte Fixtures jeweils an beiden Plattform-Adaptern prüfen; nur den gemeinsamen Kern zu testen reicht nicht.
3. Eine gemeinsame Seiten- und Funktionsmatrix als Sollzustand festlegen. Den gewünschten grafischen Readiness-Kreis auf beiden Startseiten verwenden. Planvorschau, direkte Startaktionen, Historienbearbeitung und Statistikzugang konsistent erreichbar machen. Native Bedienelemente können bleiben.
4. Abschließend dieselbe kleine, isolierte Testdatenbasis auf Android und iOS verwenden: Deload, RPE/OFF, 1,5-kg-Hantelreihe, Gewichtsprogression, zwei offene Vorschläge, Rotation A/B/A und laufendes Training. Erst nach gezielten Ergebnisvergleichen ist die Aussage „funktional gleich“ tragfähig.

## Bewertungsraster

Maßstab 9/10: keine wichtige Abweichung im jeweils bezeichneten Prüfbereich und ausreichende passende Belege. Es wird kein Gesamtmittelwert oder vermeintlicher Paritätsprozentsatz berechnet.

| Kriterium | Kritisch? | Erforderlicher Beleg | Ergebnis |
|---|---|---|---|
| Navigation und Funktionszugänge gleichwertig | Nein | UI-Beobachtung + Routen | 5/10: andere Navigation und Funktionsumfänge, Matrix |
| Trainingsaktionen und Werterhalt konsistent | Ja | Codepfade + identische Ablauf-Tests | 4/10: Gegenbeispiele 1, 2, 5; Ablauf-Tests noch offen |
| Progression und Rotationen konsistent | Ja | Entscheidungs- und Speicherpfade | 4/10: Gegenbeispiele 3, 4, 7 |
| Analytik konsistent | Ja | Gleiche Filter/Formeln + gemeinsame Fixtures | 6/10: gemeinsame Grundformeln, Gegenbeispiele 6, 8, 9 |
| Datenübernahme im gesamten Ablauf gleich | Ja | Import-/Recovery-Lauf mit gleicher Datei | UNKNOWN: Codeabweichung 10, kein Importlauf durchgeführt |
| Aktuelle installierte Versionen vollständig geprüft | Ja | Identifizierte Builds und gleiche Datenbasis | UNKNOWN: Live-Aufbau belegt, keine vollständige Funktionsparität |

Niedrigster entscheidender Wert: **4/10** für Trainings- und Progressionskonsistenz. Die offenen Laufzeitnachweise bleiben ausdrücklich UNKNOWN.

Aussagenregister: „UI gleich aufgebaut“ **CONTRADICTED** (Screenshots/Routen); „Geschäftslogik überall gleich“ **CONTRADICTED** (Codegegenbeispiele); „gemeinsamer Kern vorhanden“ **VERIFIED** (Adapter und Shared-Quellen); „alle aktuellen Binärpfade mit identischer Datenbasis getestet“ **UNVERIFIED**.

## Bildbelege

[Drei aktuelle Bildschirmpaare als Vergleich](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/vergleich.html)

- Startseite: [Android](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/android-home.png) · [iOS](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/ios-home.jpg)
- Pläne: [Android](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/android-plans.png) · [iOS](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/ios-plans.jpg)
- Verlauf: [Android](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/android-history.png) · [iOS](/Users/maert/Documents/IronLog-Analysen/2026-09-11/parity/ios-history.jpg)
