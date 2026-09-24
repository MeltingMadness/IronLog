# IronLog 1.1.0 / Build 2 – Prüfbericht

Stand der unten dokumentierten ersten Prüfrunde: 5. September 2026. Lokaler Entwicklungskandidat auf `a0b72ea` plus nicht eingecheckten Änderungen. Der vorherige Arbeitsstand wurde vor Änderungen separat gesichert. Bestehende Theme-/Cockpit-Arbeit wurde weiterverwendet; keine Bereinigung, kein Reset, kein Commit oder Push, keine Installation.

## Umgesetzt

| Punkt aus der Bestandsaufnahme | Ergebnis | Nachweis |
|---|---|---|
| Satztypen gehen beim Backup verloren | Payload-Schema 12 erhält alle vier Typen; alte Sicherungen bleiben lesbar. Unbekannte Typen und widersprüchliche Angaben werden abgewiesen. | Shared-Validator und echte Android-Mapping-Funktionen mit JSON-Roundtrip getestet |
| Readiness ohne ausreichende Daten | Keine erfundenen Prozentwerte. Fehlende Daten, zu wenige Einheiten, keine auffälligen Signale und Belastungssignale werden getrennt dargestellt. Zeitraum/Grundlage sichtbar. | Deload-Tests; Dashboard kompiliert |
| Deload trotz fehlgeschlagener Speicherung sichtbar | Nur erfolgreicher Write ändert den sichtbaren Zustand; laufende Aktionen sind gesperrt. | Fehler- und Doppelklicktests im Dashboard-ViewModel |
| Planfelder nicht normal bearbeitbar | Textentwürfe erlauben Löschen und Zwischenstände, mit Validierung beim Verlassen/Speichern. Leere Pläne werden abgewiesen. | Plan-/Meta-Editor-Tests |
| Mehrfachspeichern und Entwurfsverlust | Speichersperre, Schutz während des Schreibens, Rückfrage beim Verwerfen; eingegebene Gewichtseinheit bleibt konsistent. | Plan-/Meta-Editor-Tests; Oberfläche kompiliert |
| Scheibenrechner übersieht passende Kombination | Exakte Kombination bevorzugt, bei Gleichstand wenige Scheiben, sonst fehlende Last sichtbar. Überlauf-/Rundungsrandfälle abgefangen. | PlateCalculatorTest inkl. 80 kg mit 25-/15-kg-Scheiben |
| Unklare Coach-Handlung | Planvorgabe, vorherige Einheit und Vorschlag beschriftet; Gewichtsübernahme nur auf ausdrücklichen Klick. Review mit Plan/Datum, tatsächlicher Sammelanzahl und Schutz offener Bearbeitungen. | Workout-/Progression-ViewModel-Tests; Komponenten kompiliert |
| Satztypen/Zeiten im Rückblick | Detailansicht unterscheidet Satztypen; Historienfilter wirken in der Room-Abfrage vor Paging; Statistiken mit datierten Werten und Vergleich. | History-/Stats-Tests; Room-Abfrage kompiliert |
| Fehlende externe Sicherungstransparenz | Letzter erfolgreicher Export, optionaler Hinweis in Einstellungen nach sieben Tagen bzw. bei noch fehlendem Export. Kein automatischer Export und keine Systembenachrichtigung. | Preferences-/Settings-Tests mit Erfolg, Fehler, Opt-in und Zeitgrenze |
| Unklare Version und Dokumentation | Version 1.1.0/2, sichtbare Build-Information, aktualisierter Projektplan/Changelog. CI archiviert Test-/Lintberichte aller Module. | APK-Metadaten; Dokument-/Diff-Prüfung |
| Numerische Workout-Eingaben und Abschluss | Ungültige RPE/RIR/Gewichte werden abgewiesen; Fehler lassen die Bearbeitung offen. Abschluss und Rekord-Neuberechnung teilen eine Transaktion. | Workout-ViewModel-/Repository-Tests |

## Ausgeführte Prüfungen

JDK 17: `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

| Aufgabe | Tests | Fehler | Übersprungen |
|---|---:|---:|---:|
| `:core:common:testDebugUnitTest` – PlateCalculator, DeloadDetector | 28 | 0 | 0 |
| `:shared:testAndroidHostTest` – BackupPayloadValidator, SettingsPreferencesController | 40 | 0 | 0 |
| `:data:testDebugUnitTest` – BackupWorkoutSetRoundTrip, BackupRepositoryImpl, AppPreferencesDataStore | 29 | 0 | 0 |
| `:app:testDebugUnitTest` – betroffene ViewModels, WorkoutRepository, BackupValidator, FakeWorkoutRepository | 271 | 0 | 0 |
| `:feature:history:testDebugUnitTest` – WorkoutHistoryScreenState | 4 | 0 | 0 |
| `:feature:workout:testDebugUnitTest` – ActiveWorkoutWeightHint, DeloadTargetAdjustment | 10 | 0 | 0 |
| **Summe** | **382** | **0** | **0** |

Zusätzlich erfolgreich:

- `:app:assembleDebug` – finaler integrierter Lauf mit Exit 0 (`BUILD SUCCESSFUL in 19s`).
- `:app:compileDebugAndroidTestKotlin` – Instrumentationstests inklusive neuer Schnittstellen kompilieren. Das ist keine Ausführung auf einem Gerät.
- `git diff --check`.
- XML-Prüfung der neuen Ressourcen und Syntaxprüfung der zwei geänderten Swift-Dateien.

Die ersten Läufe fanden veraltete Greedy-Testannahmen, fehlende UI-Flow-Abonnements in neuen Tests, drei falsch platzierte Planeditor-Callbacks und eine fehlende Methode in einem Navigation-Testdouble. Diese Punkte wurden korrigiert; die oben genannten Ergebnisse stammen aus den anschließenden erfolgreichen Läufen.

Die app-Testauswahl war auf folgende Klassen beschränkt: `ProgressionReviewViewModelTest`, `DashboardViewModelTest`, `SettingsViewModelTest`, `PlanEditorViewModelTest`, `MetaPlanEditorViewModelTest`, `WorkoutHistoryAndDetailViewModelTest`, `WorkoutHistoryViewModelDeletionTest`, `ExerciseStatsViewModelTest`, `ActiveWorkoutViewModelTest`, `WorkoutRepositoryImplTest`, `BackupPayloadValidatorTest`, `FakeWorkoutRepositoryTest`. Es wurde keine vollständige Testsuite und kein vollständiger Lintlauf ausgeführt.

## Artefakt und Reproduzierbarkeit

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Paket: `com.ironlog.app`; Variante: **debug**; Version **1.1.0**, Code **2**.
- APK-SHA-256: `60da4ccb7cbcd66d11bd44ba2308913fd5a6955c67d942777e93f4fa0f95ac0a`
- Der zugehörige frühere Quellstand ist in `2026-09-05-candidate.sha256` dokumentiert; die anschließende Wochenübersicht verändert diesen Stand. Für einen Abgleich mit dem früheren Stand: `shasum -a 256 -c docs/validation/2026-09-05-candidate.sha256`.
- Die JUnit-Ergebnisse liegen in den jeweiligen `build/test-results/`-Verzeichnissen, die lesbaren Berichte in `build/reports/tests/`.
- Laufprotokolle: `/var/folders/w3/q2fjx2kd6jvgc8m0_ycv4wzm0000gn/T/ironlog-validation-gm6oqq7e/`.

## Noch nicht nachgewiesen

- Nach dieser Prüfrunde wurde das Benutzergerät per WLAN-ADB verbunden und ein Export aus der installierten Version 1.0 gesichert. Der Entwicklungskandidat wurde weiterhin nicht installiert; kein aktueller Instrumentation-/Prozessneustart-/Wiederherstellungslauf. Die ergänzte Wochenübersicht und ihre gesonderte Validierung sind in `2026-09-05-weekly-muscle-volume.md` dokumentiert.
- Kein signierter Release-Build, kein Store-/Sideload-Release, keine CI-Ausführung auf diesem lokalen Kandidaten. Frühere GitHub-Läufe gelten nur für deren ältere Commits.
- iOS: Exportzeitpunkt wird erst nach erfolgreichem SwiftUI-`fileExporter` gesetzt; die geänderten Swift-Dateien wurden syntaktisch geprüft. Ein vollständiger iOS-Build ist ohne Xcode nicht möglich (nur Command Line Tools aktiv). Die iOS-App wurde hier nicht auf Funktionsgleichheit erweitert.
- Vor Auslieferung: die bestehenden Merge-/Release-Gates auf dem finalen Commit, einen Gerätelauf für Training speichern/neu starten sowie Backup/Restore mit allen Satzarten, dann die signierte Aktualisierung mit erhaltener Datenbank prüfen.
