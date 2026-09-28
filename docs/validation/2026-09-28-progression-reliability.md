# Progression: Regelrevision 2 und zuverlässige Übernahme

Stand: 28.09.2026. Ausgangspunkt: `825b317`. Datenbank- und Backup-Schema: **15**.

## Umfang

Lineare Progression bleibt erhalten und bekommt eine einstellbare Bestätigung durch 1–6 vergleichbare erfolgreiche Einheiten (Standard 1). Doppelte Progression richtet den nächsten Wiederholungsschritt nach den tatsächlich absolvierten Sätzen aus. Revision 2 wertet normale und bis zum Versagen ausgeführte Arbeitssätze, trennt Warmup/Drop/Backoff und behandelt wechselnde Lasten konservativ. Android und iOS erklären Satzwahl, Erfolgs-/Fehlversuchszähler und die Lastbasis.

Dazu kommen die Aktualitätsprüfung vor der Übernahme und der Ausschluss ausdrücklicher Deloads. Die Berechnung liegt im Shared-Kern. Beide Speicherwege erhalten historische Regelrevisionen und stellen nur aktive Plan-Konfigurationen auf Revision 2 um. Individuelle Satzvorgaben bleiben bei manueller Progression; ihre Rollen werden beim Protokollieren und beim Übungsabschluss berücksichtigt.

## Verbindlicher Fallkatalog

Beispielwerte in kg; die Fälle beschreiben Softwareverhalten, keine Trainingsempfehlung.

| Fall | Eingabe / Vorgeschichte | Erwartetes Verhalten |
|---|---|---|
| Linear, Standard | 3 × 8 bei 100, Schritt 2,5, Bestätigung 1 | Vorschlag 102,5; Planänderung erst nach Bestätigung |
| Linear, Bestätigung 2 | Zweimal vergleichbar 3 × 8 bei 100 | Erst „1 von 2“, dann Vorschlag 102,5 |
| Andere Last / unvollständige Arbeit | Erfolg bei 100, danach andere Last oder zu wenige Arbeitssätze | Keine Fortsetzung der alten Erfolgsserie |
| Versagen mit erreichtem Ziel | 8/8/8, letzter Satz `FAILURE` | Drei Arbeitssätze; Erfolgsbedingung erfüllt |
| Versagen mit verfehltem Ziel | 8/8/7, letzter Satz `FAILURE`, Ziel 8 | Ziel wiederholen; regulärer Fehlversuch |
| Zusätzliche Sätze | Warmup, drei Arbeitssätze, Drop/Backoff/Zusatzarbeit | Nur die ersten drei Arbeitssätze werten; ein Zusatzsatz rettet keinen verfehlten Pflichtsatz |
| Gemischte Lasten, Ziel erfüllt | 8/8/8 bei 100/105/102,5; Ziel 100 | Basis 100; Vorschlag 102,5 |
| Gemischte Lasten unter Plan | 100/97,5/95; Plan 100 | Ziel beibehalten, keine künstliche Fehlversuchsserie |
| Gemischte Lasten, Wdh. verfehlt | 8/8/6 bei 100/105/102,5 | Ziel beibehalten, kein vergleichbarer Fehlversuch |
| Doppelte Progression | Bereich 8–12, Ziel 8, tatsächlich 10/10/9 | Nächstes Wiederholungsziel 10 bei gleicher Last |
| Obergrenze erreicht | Bereich 8–10, tatsächlich 10/10/10 | Last + Schritt, Wiederholungsziel zurück auf 8 |
| Neueres Wiederholen / fehlende Auswertung | Älterer Vorschlag, danach neue abgeschlossene Arbeit an derselben Position | Alter Vorschlag veraltet, auch ohne neuen offenen Vorschlag |
| Ausdrücklicher Deload | Entlastungseinheit zwischen regulären Versuchen | Kein Vorschlag; weder erhöhen noch zurücksetzen der Erfolgs-/Fehlversuchsserie |
| Laufend / nur Warmup / andere Position | Noch kein relevantes neues abgeschlossenes Training | Alter regulärer Vorschlag bleibt entscheidbar |
| Alter offener Deload-Vorschlag | Vorherige Version hat ihn gespeichert | Veraltet; Übernahme verhindert |
| Bereits entschiedene Historie | Früher angenommen oder verworfen | Entscheidung und Quelle unverändert |
| Legacy-Kontext unbekannt | `isDeload == null` | Bisherige Behandlung; kein Erraten anhand der Last |
| Migration / Import | Aktiver V1-Plan mit historischen V1-Snapshots | Plan V2, Bestätigung 1; historische Quellen und Entscheidungen V1 |
| Backup / Recovery | V2-Konfiguration mit Bestätigung 2, FAILURE- und BACKOFF-Sätzen | Export, Import und Recovery erhalten Werte, Verweise und Entscheidungen |
| Individuelle Vorgaben | Warmup, normale Slots und Backoff | FAILURE erfüllt normalen Slot, BACKOFF erfüllt Backoff-Slot; ältere NORMAL-Backoffs bleiben zuordenbar |

Die Aktualitätsprüfung berücksichtigt alle Nicht-Aufwärmsätze. Neue, anders ausgeführte Arbeit kann einen alten Vorschlag überholen, ohne bereits eine neue belastbare Steigerung zu begründen. Abschlusszeit und Session-ID bestimmen die Reihenfolge. Übernahme und Abgleich laufen auf Android transaktional, im Shared-Store als atomare Zustandsänderung.

## Prüfnachweise

Neue Verhaltensfälle wurden zunächst gegen den vorherigen Stand geprüft: neun Regressionen für Aktualität/Deload, elf der zwölf V2-Engine-Fälle sowie die neue FAILURE-Slot-Zuordnung schlugen erwartungsgemäß fehl. Die Kontrollfälle für bisheriges Verhalten blieben grün.

Die gezielt ausgewählten Prüfungen sind nach den Änderungen bestanden:

| Ebene | Auswahl / Nachweis |
|---|---|
| Shared-Kern, JVM | 69 Tests: `ProgressionV2Test` (12), `ProgressionReliabilityTest` (11), `ProgressionLifecycleTest` (9), `BackupProgressionFacadeTest` (5), `PlannedSetsTest` (3), `BackupPayloadValidatorTest` (29) |
| Android-Repository | `ProgressionRepositoryImplTest`: 56 Tests einschließlich zweier bestätigter Erfolge, FAILURE-Evidenz, BACKOFF-Ausschluss und tatsächlicher Planübernahme |
| Android-Adapter und Backup | 41 historische Engine-Fälle; zusätzlich Contract-, Backup-Repository- und Workout-Set-Roundtrip-Tests |
| Android-Oberfläche, Logik | Planeditor einschließlich Schwellenwert und ungültiger Eingabe, Review, Gewichtsevidenz und gezielter Pausentimer-Fall für FAILURE |
| Android, echter Emulator API 35 | 10 Migrationstests einschließlich 14→15 mit Schema-Validierung und Erhalt historischer Revisionen; 2 Backup-Lifecycle-Tests mit echter Room-Datenbank, Fremdschlüsselprüfung und Recovery-Datei |
| iOS, nativer Build und Simulator iOS 26.5 | 4 `ProgressionDraftTests`, 2 `BackupBridgeTests`, 4 `IndividualSetTargetsTests`; beide gezielten `xcodebuild test`-Läufe erfolgreich |
| SQL | Tatsächliche Room-Abfragen gegen SQLite mit Schema 15: Deload, Nachholen, ältere Vorschläge, Abschlusszeit-Gleichstand und unabhängige Positionen |
| Diff | `git diff --check` ohne Befund |

Beim Versionssprung wurden ältere Testfixtures ausdrücklich auf Revision 1 festgelegt, damit sie weiterhin das historische Verhalten prüfen. Backup-Erwartungen wurden auf Schema 15 und die erweiterte Satz-Fixture angepasst. Nach diesen Korrekturen wurden jeweils nur die betroffenen Prüfungen wiederholt.

## Nachprüfung im Pull Request

Der erste vollständige CI-Lauf von PR #45 deckte einen bereits vorhandenen datumsabhängigen Test auf: `ExerciseStatsViewModelTest` platzierte sechs als abgeschlossen gedachte Sätze auf den Mittwoch der aktuellen Woche. Bei Ausführung am Montag filtert die App diese korrekt als zukünftig heraus (6 statt der erwarteten 12 Sätze). Die Fixture verwendet nun den Wochenbeginn, der an jedem Wochentag bereits erreicht ist. Die Erwartungswerte und die produktive Statistiklogik bleiben unverändert; beide Wochenvolumen-Fälle wurden gezielt nachgeprüft.

## Reproduzierbare Auswahl

JDK 17 voraussetzen. Die Befehle listen die insgesamt verwendete gezielte Auswahl; bereits erfolgreiche Teilgruppen müssen bei weiteren Änderungen nur bei sachlichem Anlass erneut laufen.

```sh
./gradlew --offline --no-daemon --continue --console=plain \
  :shared:testAndroidHostTest --tests '*Progression*Test' --tests '*PlannedSetsTest' --tests '*BackupPayloadValidatorTest' \
  :core:common:testDebugUnitTest --tests '*ProgressionEngineTest' --tests '*ProgressionContractTest' \
  :data:testDebugUnitTest --tests '*ProgressionRepositoryImplTest' --tests '*BackupRepositoryImplTest' --tests '*BackupWorkoutSetRoundTripTest' \
  :app:testDebugUnitTest --tests '*PlanEditorViewModelTest' --tests '*ProgressionReviewViewModelTest' \
  --tests '*ProgressionWeightEvidenceTest' --tests '*ActiveWorkoutViewModelTest.rest*failure*deload*'

./gradlew --offline --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ironlog.app.data.local.IronLogDatabaseMigrationTest,com.ironlog.app.data.backup.BackupLifecycleRoundTripTest

python3 docs/validation/check_progression_recovery_query.py
```

Native iOS-Auswahl mit einem verfügbaren Test-Simulator:

```sh
xcodebuild -project iosApp/IronLogIOS.xcodeproj -scheme IronLogIOS \
  -configuration Debug -destination 'platform=iOS Simulator,name=IronLog-Progression-V2' \
  -parallel-testing-enabled NO CODE_SIGNING_ALLOWED=NO \
  -only-testing:IronLogIOSTests/ProgressionDraftTests \
  -only-testing:IronLogIOSTests/BackupBridgeTests \
  -only-testing:IronLogIOSTests/IndividualSetTargetsTests test
```

## Grenzen der Prüfung

Android-Debug-APK und nativer iOS-Simulator-Build sind gebaut. Die oben genannten Geräteprüfungen sind Daten-/Logiktests; eine visuelle oder umfassende interaktive UI-Abnahme wurde nicht durchgeführt. Keine vollständige Testsuite, kein Release-Build, keine neue CI, kein Upload und keine Veröffentlichung. Die vorhandenen Compiler-/Gradle-Abkündigungshinweise bleiben bestehen.
