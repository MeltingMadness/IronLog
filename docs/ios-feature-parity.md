# IronLog iOS: Funktionsstand und Abnahme

Stand: 9. September 2026. Android bleibt die fachliche Referenz. Diese Liste unterscheidet implementierte Funktionen von tatsächlich geprüften Abläufen; sie ist keine Releasefreigabe.

## Aktueller Stand

Die native SwiftUI-App baut mit Xcode 26.6 und läuft im iPhone-17-Simulator mit iOS 26.5. Dashboard, Training, Verlauf, Pläne und Einstellungen sind echte Screens. Statistik, Progressionsreview, Übungsbibliothek und Editoren sind angebunden. Die früheren Swift-Compiler- und Simulator-Installationsblocker sind behoben.

Der gemeinsame Kotlin-Kern besitzt den kanonischen Trainingsgraphen, Progressionsregeln, Bestleistungen und Auswertungen. iOS speichert den validierten Graphen atomar unter `Application Support/IronLog/training-state.json`. Änderungen werden erst nach erfolgreicher Speicherung veröffentlicht. Einstellungen liegen in NSUserDefaults. Die ältere Repository-Abstraktion ist nicht der direkte UI-Zugriff: Swift verwendet `IosTrainingFeature` und `IosSettingsFeature` als Plattformbrücken zum gemeinsamen Kern.

## Bereits nachgewiesen

| Ablauf | Evidenz | Grenze |
|---|---|---|
| Native App starten und navigieren | Erfolgreicher Xcode-Build, Installation und Start im Simulator; Dashboard, Training, Pläne, Verlauf, Settings und Review geöffnet | Letzter integrierter Build: 21:42 UTC; kein Test auf physischem iPhone |
| Individuelle Gewichtsschritte | Plan mit Kurzhantel-Bankdrücken, 3 × 10 @ 7 kg und linearer Steigerung um 1,5 kg im Simulator erstellt | Weitere Schemen durch gemeinsame Regeltests, nicht alle einzeln durch UI ausgeführt |
| Training und Progression | Drei Sätze geloggt, Training beendet, Vorschlag 8,5 kg explizit angenommen; persistierter Graph enthält Planziel 8,5 kg und unveränderte Quelle 7 kg | Atomare Sammelannahme durch gezielte Lifecycle-Tests geprüft; nicht jede Review-Variante zusätzlich durch die UI ausgeführt |
| Automatische Pause ohne feste Dauer | Count-up nach Arbeitssatz gestartet; nach letztem Plansatz kein neuer Timer | Fester Countdown inklusive Wiederherstellung nach App-Neustart und Stop nach letztem Deload-Satz ebenfalls im Simulator geprüft; physische Haptik offen |
| Verlauf und Bestleistungen | Einheit, drei Sätze, Volumen 210 kg und e1RM 9,3 kg dargestellt; Notiz gespeichert und wieder angezeigt | Abgeschlossenen Satz 10→11 Wiederholungen geändert und anschließend gelöscht: PRs und Volumen korrekt neu berechnet; akzeptierter Vorschlag und Plangewicht bleiben erhalten |
| Native Unit-Konvertierung | Zwei iOS-XCTest-Fälle bestanden: 1,5 kg → METRIC; 2,5 lb → IMPERIAL mit korrektem kanonischem kg-Wert | Keine vollständige UI-Abnahme beider Einheitensysteme |
| Gemeinsame Regeln | Gezielt grüne Progressions-, Lifecycle-, Store-, Platten- und Analytics-Tests; Android-Progressionstests gegen gemeinsamen Kern grün | Android `:app:assembleDebug` nach Shared-Integration erfolgreich; keine vollständige Regression |
| Deload und historische Wochen | Fokussierte gemeinsame Tests für Satzhalbierung, 15-%-Reduktion, Quellschutz, lokale Wochengrenzen und Zukunftsbegrenzung grün | Im Simulator bestätigt: 3→2 Satzvorgabe, Aktivierung/Beenden, vorige/leere Woche und Volumenaktualisierung nach Satzlöschung |
| Backup-Vorbereitung | Export öffnet nativen iOS-Dateidialog; gemeinsamer Store-Roundtrip, beschädigte Datei, atomarer Schreibfehler und veraltete Importvorschau gezielt getestet | Zwei native BackupBridgeTests und ein RecoveryBridgeTest grün: Export/Preview, korruptes JSON ohne Mutation, bestätigter Import und Rückkehr zum vollständigen Ausgangsgraphen. Abschluss im nativen Dateidialog wegen gesperrtem Mac noch nicht bestätigt |
| Superset und parallele Pausen | Zwei Übungen als zusammenhängender Superset sichtbar; zwei unabhängige Countdown-Timer geloggt, nach App-Neustart wiederhergestellt, einer beendet ohne den anderen zu stoppen | Drei native Persistenztests zusätzlich grün (Migration, mehrere Zeilen, Deadline) |
| RPE-Hilfe im Training | RPE 9 erzeugt Next-Set-/Backoff-Hinweis; Übernahme öffnet den Editor mit 9 kg statt 10 kg | Gemeinsame Berechnung und Android-Adapter mit 15 gezielten Tests grün; kein physischer Haptiktest |
| Meta-Plan-Rotation | Zwei Pläne erstellt, Reihenfolge gespeichert, Teilplan explizit übersprungen, nächster Plan gestartet und verworfen; später abgeschlossen und Rotation weitergeschaltet | Ausschließlich Testdaten im Simulator |
| Übungsstatistiken | 1RM-Entwicklung 9,3→11,3 kg (+21,4 %), letzter/vorheriger Trainingsvergleich und fünf aktuelle Arbeitssätze im Simulator geprüft | Keine künstlichen Nullwerte bei fehlenden Vergleichen |
| Bereitschaft und große Schrift | Bei ausreichenden Daten wieder Prozentkreis (100 %); bei fehlenden Daten weiterhin unbekannt. Dashboard bei größter Accessibility-Schrift gesichtet, Ring/Erklärung untereinander | Kein vollständiger VoiceOver-Audit; Standard-Schriftgröße danach wiederhergestellt |

Die Backup-Bestätigung hält die tatsächlich geprüften Bytes und den lokalen Ausgangsgraphen fest. Der Import liest die Datei nicht erneut und prüft den Ausgangsgraphen unter demselben Mutex wie den atomaren Schreibvorgang. Damit ist die bestätigte Vorschau gegen Dateiaustausch und zwischenzeitliche Trainingsänderungen gebunden; ein separater SHA-256-Anzeigewert ist dafür nicht erforderlich.

## Implementiert, noch gezielt abzunehmen

- Verlauf mit Plan-/Datumsfilter; Editieren/Löschen und daraus folgende Bestleistungen sind bereits im Simulator bestätigt.
- Sammelannahme sicherer Progressionsvorschläge, bearbeitete Annahme, Ablehnung und veraltete Vorschläge.
- Eigene Übungen erstellen, ändern und archivieren; Editor unterstützt die zehn kanonischen Muskelgruppen und bis zu drei sekundäre Muskelgruppen. Plan-/Meta-Plan-Reihenfolge, Supersets, Rotation und Überspringen sind bereits im Simulator bestätigt.
- Drop-/Failure-Sätze und weitere Hintergrundabläufe; Warmup, laufendes Training nach Neustart und fester Countdown sind bestätigt.
- Nativen Backup-Dateidialog bis zum Speichern/Importieren abschließen und Reset über die UI. Vorschau, bestätigter Import, Wiederherstellung und Ablehnung beschädigter Daten sind über native Brückentests geprüft.
- Einstellungen nach Neustart; Reminder-Berechtigung und tatsächliche Zustellung; Diagnoseexport.
- Vollständiger VoiceOver-/Kontrast-/Reduced-Motion-Audit und weitere Displaygrößen. Dashboard mit normaler und größter Schrift gezielt geprüft.

## Reproduzierbare Validierung

Zentrale Werkzeuge sind XcodeBuildMCP `build_run_sim` und gezielte `test_sim`-Aufrufe. Das Projekt wird mit `python3 iosApp/generate_project.py` erzeugt; der Xcode-Build integriert das KMP-Framework über `iosApp/scripts/build-shared.sh`.

Gezielte Kotlin-Tests laufen mit JDK 17, zum Beispiel:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --offline --no-daemon :shared:testAndroidHostTest --tests '*DeloadTargetAdjustmentTest' --tests '*SharedTrainingAnalyticsTest'
```

Dieser Aufruf war am 9. September erfolgreich (`/tmp/ironlog-ios-deload-week-test.log`). Frühere gezielte Nachweise liegen in `/tmp/ironlog-ios-framework-import-test.log`, `/tmp/ironlog-ios-focused-retest.log` und `/tmp/ironlog-ios-plates-test.log`. Temporäre Logs sind lokale Prüfevidenz, keine dauerhaften CI-Artefakte.

Die beim Android-/iOS-Abgleich gefundenen Funktionslücken sind implementiert und in den integrierten nativen Build aufgenommen. Die kritischen Trainings-, Progressions- und Datenübernahmeabläufe sind gezielt geprüft; dies ist keine exhaustive UI- oder Release-Abnahme. Signierung, TestFlight/App-Store-Verteilung und ein Test auf einem physischen iPhone sind separat zu bewerten.

Weitere Prüfevidenz: `/tmp/ironlog-ios-history-edit-test.log` (Store/Validator gezielt grün), `/tmp/ironlog-ios-android-compat-build.log` (Android-Debug-APK erfolgreich), XcodeBuildMCP `test_sim_2026-09-09T21-13-07-886Z` (2 BackupBridgeTests, 0 Fehler).

Aktuelle Integrationsevidenz: `/tmp/ironlog-ios-parity-critical-test.log` (40 Tests: Store 17, Analytics 8, NextSetCoach 5, Android-RPE-Adapter 10; 0 Fehler), XcodeBuildMCP `test_sim_2026-09-09T21-40-23-677Z` (4 native Recovery-/Timer-Tests; 0 Fehler), `build_run_sim_2026-09-09T21-42-50-715Z` (integrierter Build/Installation/Start erfolgreich). Das originale Launcherlogo ist als opakes iOS-AppIcon eingebunden; native Bedienelemente übernehmen den gewählten Farbakzent. Der unter iOS wirkungslose Android-Schalter „Dynamische Farben“ wird nicht angeboten.
