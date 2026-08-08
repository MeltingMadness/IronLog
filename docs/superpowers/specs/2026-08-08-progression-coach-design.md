# IronLog: Progressions-Coach

Stand: 2026-08-08
Status: Design im Dialog freigegeben, schriftliche Prüfung durch den Nutzer ausstehend

## Ziel

IronLog erweitert planbasierte Workouts um nachvollziehbare Progressionsvorschläge pro Planübung. Die App wertet vorhandene Ziele, abgeschlossene Arbeitssätze und bei Bedarf RPE/RIR aus, zeigt den nächsten Schritt mit Begründung und verändert einen Trainingsplan ausschließlich nach ausdrücklicher Bestätigung.

Die erste Ausbaustufe ist Android-first, vollständig lokal und offline. Sie baut auf den vorhandenen Planfeldern `targetSets`, `targetReps` und `targetWeightKg`, den gespeicherten `WorkoutSet`-Daten und der bereits planbezogen abgegrenzten Gewichtshistorie auf.

## Produktvertrag

- Progression gilt nur für Workouts mit einem konkreten `planId`. Bei Meta-Plänen wird der tatsächlich ausgeführte Unterplan verwendet. Freie Workouts erzeugen keine Planänderung.
- Bestehende und neue Planübungen starten mit `MANUAL`. Ein Schema wird erst aktiv, nachdem der Nutzer es für diese Planübung ausgewählt und gültig konfiguriert hat.
- Beim Start eines planbasierten Workouts kopiert IronLog Zielwerte, Schema, Konfiguration und Regelrevision jeder Planübung als unveränderlichen Session-Snapshot. Aktives Workout und spätere Auswertung lesen diesen Snapshot, damit Planänderungen während des Trainings oder vor einem Retry die damalige Vorgabe nicht umdeuten.
- Die App darf Vorschläge erzeugen, speichern und erklären. Sie darf Zielwerte erst in einer bestätigten Transaktion verändern.
- Ein Coach-Fehler darf ein bereits beendetes Workout weder offen halten noch rückgängig machen. Workout-Abschluss und Vorschlagserzeugung sind getrennte, wiederholbare Schritte.
- Eine Abweichung vom geplanten Arbeitsgewicht, fehlende erforderliche Eingaben oder zu wenige Arbeitssätze führt zu einer erklärten Informationsmeldung, nie zu einer geratenen Progression.
- Warm-up-Sätze zählen nie für eine Progressionsentscheidung. Zusätzliche Arbeitssätze nach den geplanten `targetSets` werden angezeigt, aber nicht als Nachweis für das Erreichen des Ziels verwendet.

Die Trainingswissenschaft begründet progressive Belastungsanpassung und den ergänzenden Einsatz von Autoregulation, aber kein universell bestes Schema. Die geprüften Quellen und daraus abgeleiteten Produktgrenzen stehen in [`docs/research/2026-08-08-progression-schemes.md`](../../research/2026-08-08-progression-schemes.md).

## Begriffe

- **Gezählte Arbeitssätze:** Die ersten `targetSets` Sätze einer Übung, deren `isWarmup` falsch ist.
- **Ziel-Snapshot:** Schema, Konfiguration, Ziel-Sätze, Ziel-Wiederholungen und Zielgewicht, unveränderlich beim Start des Workouts gespeichert.
- **Erfolg:** Alle gezählten Arbeitssätze erfüllen die für das Schema definierte Bedingung bei unverändertem Ziel-Snapshot.
- **Fehlversuch:** Ein vollständig beendetes, vergleichbares Workout verfehlt die Regelbedingung. Unvollständige oder manuell abweichende Daten zählen nicht als Fehlversuch.
- **Vergleichbare Workouts:** Plan, Planübung, Schema, Regelrevision und Ziel-Snapshot stimmen überein.
- **Vorschlag:** Eine gespeicherte, potenziell planändernde Entscheidung mit Ausgangswerten, vorgeschlagenen Werten und maschinenlesbarem Grundcode.
- **Information:** Ein gespeichertes Auswertungsergebnis ohne Planänderung, etwa „Ziel wiederholen“ oder „RPE fehlt“.

## Unterstützte Schemata

### Manuell

`MANUAL` ist der sichere Standard. IronLog zeigt weiterhin Ziel, vorherige Einheit, PRs und RPE/RIR, erzeugt für die Planübung aber keine Progressionsauswertung.

### Lineare Progression

Konfiguration: positive Gewichtsschrittweite, Fehlversuchsschwelle und Backoff-Prozent.

Ein Erfolg liegt vor, wenn alle gezählten Arbeitssätze mindestens `targetReps` mit dem geplanten `targetWeightKg` erreichen. Dann wird `targetWeightKg + incrementKg` vorgeschlagen; Sätze und Wiederholungen bleiben gleich. Ein einzelner Fehlversuch erzeugt die Information „Ziel wiederholen“. Erreicht die Folge vergleichbarer Fehlversuche die konfigurierte Schwelle, wird ein Backoff vorgeschlagen.

### Doppelprogression

Konfiguration: `minReps`, `maxReps`, positive Gewichtsschrittweite, Fehlversuchsschwelle und Backoff-Prozent. Es gilt `1 <= minReps <= targetReps <= maxReps`.

Erreichen alle gezählten Arbeitssätze das aktuelle `targetReps` und liegt `targetReps` unter `maxReps`, wird `targetReps + 1` bei unverändertem Gewicht vorgeschlagen. Erreichen alle gezählten Arbeitssätze `maxReps`, wird die Last um die Schrittweite erhöht und `targetReps` auf `minReps` zurückgesetzt. Fehlversuche und Backoff folgen derselben vergleichbaren-Folge-Regel wie bei linearer Progression.

Beim erstmaligen Auswählen werden `minReps = currentTargetReps` und `maxReps = currentTargetReps + 2` vorausgefüllt. Diese Werte sind erst aktiv, nachdem der Nutzer die Konfiguration speichert.

### Gesamtwiederholungen

Konfiguration: positive `targetTotalReps`, positive Gewichtsschrittweite, Fehlversuchsschwelle und Backoff-Prozent.

Die Wiederholungen der gezählten Arbeitssätze werden summiert. Erreicht die Summe `targetTotalReps`, wird die Last um die Schrittweite erhöht; Satzanzahl und Gesamtwiederholungsziel bleiben gleich. Die Verteilung auf die Sätze darf variieren. Beim erstmaligen Auswählen wird `targetSets * targetReps` als sichtbarer, editierbarer Vorschlagswert vorausgefüllt.

### RPE/RIR-autoreguliert

Konfiguration: Ziel-RPE, Toleranz, positive Gewichtsschrittweite, Fehlversuchsschwelle und Backoff-Prozent. RIR-Eingaben werden wie bisher in kanonisches RPE umgerechnet und gespeichert.

Alle gezählten Arbeitssätze müssen mindestens `targetReps` mit dem geplanten Gewicht erreichen und einen RPE-Wert besitzen. Als konservatives Belastungssignal dient der höchste RPE der gezählten Arbeitssätze. Liegt er höchstens bei `targetRpe + tolerance`, wird die Laststeigerung vorgeschlagen. Liegt er darüber, bleibt das Ziel unverändert; weil die vorgeschriebene Arbeit trotzdem erreicht wurde, setzt dieses Ergebnis eine vorherige Fehlversuchsfolge zurück. Verfehlte Wiederholungen zählen wie bei linearer Progression als Fehlversuch. Fehlt ein RPE-Wert, entsteht die Information „RPE/RIR unvollständig“, kein Fehlversuch, kein Zurücksetzen der Folge und keine Laständerung.

`targetRpe` und jeder ausgewertete kanonische RPE-Wert müssen endlich und im Bereich 1,0 bis 10,0 liegen; die Toleranz muss endlich und zwischen 0,0 und 2,0 liegen. Ein historischer Wert außerhalb dieses Bereichs gilt wie ein fehlender Wert als `INSUFFICIENT_DATA`, damit bereits vorhandene fehlerhafte Eingaben keine Empfehlung auslösen.

RPE/RIR ist wegen seiner Messunsicherheit ein ergänzendes Signal. Das Schema darf nie allein aufgrund eines niedrigen RPE erhöhen, wenn Wiederholungs- oder Satzvorgaben verfehlt wurden.

## Gemeinsame Regelgrenzen

- Für eine Auswertung müssen mindestens `targetSets > 0`, `targetReps > 0` und die jeweils schemaspezifischen Werte gültig sein.
- Alle Gewichte, Schrittweiten, Prozent- und RPE-Werte müssen endlich sein. Zielgewichte dürfen nicht negativ sein, Schrittweiten müssen größer als 0 sein, Wiederholungsgrenzen und Gesamtwiederholungsziele müssen positive Ganzzahlen sein; Summen werden über einen überlaufsicheren Ganzzahltyp berechnet.
- Die gezählten Sätze müssen dasselbe Gewicht wie `targetWeightKg` innerhalb einer technischen Toleranz von 0,01 kg verwenden. Höhere oder niedrigere manuelle Lasten erzeugen `MANUAL_WEIGHT_DEVIATION`, damit der Coach keinen Zielwert aus nicht vergleichbaren Daten ableitet.
- Eine Folge von Fehlversuchen wird nur über vergleichbare Workouts mit identischem Ziel-Snapshot gezählt. Erfolg, angenommene Änderung, manuelle Planbearbeitung oder geänderte Schemakonfiguration setzt die Folge zurück.
- Die Fehlversuchsschwelle ist ganzzahlig und liegt zwischen 1 und 6; der voreingestellte Wert ist 2. `backoffPercent` liegt zwischen 1 und 30; der voreingestellte Wert ist 10.
- Ein Backoff berechnet `currentWeight * (1 - backoffPercent / 100)`. Das Ergebnis wird zuerst in die sichtbare Einheit umgerechnet und auf das nächste Vielfache der dort konfigurierten Schrittweite gerundet; bei exakt gleichem Abstand gewinnt der niedrigere Wert. Erst das gerundete Ergebnis wird zurück in kg konvertiert. Ergibt die Rundung keine Absenkung, wird genau eine sichtbare Schrittweite abgezogen, jedoch nie unter 0.
- Eine Laststeigerung addiert genau die konfigurierte Schrittweite. Es wird kein geschätztes 1RM als versteckte Bezugsgröße verwendet.
- Die Standard-Schrittweite wird in der Konfigurationsoberfläche als 2,5 kg beziehungsweise 5 lb vorausgefüllt. Die Konfiguration speichert ursprünglichen Zahlenwert und Einheit sowie den kanonischen kg-Wert; so bleiben Rundung und Retry auch nach einem späteren Wechsel der Anzeigeeinheit identisch. Die UI zeigt konvertierte Werte in der aktuellen Einheit und nennt bei einer Empfehlung die für die Regel verwendete Schrittweite.
- Fehlversuchsschwelle, Backoff und Schrittweiten-Defaults sind editierbare, konservative Produktvorgaben und keine aus der Literatur abgeleiteten individuellen Trainingsrezepte.
- Eine Regelrevision wird mit jedem Auswertungsergebnis gespeichert. Jede semantische Änderung einer Regel erhöht die Revision; offene Ergebnisse einer älteren Revision werden `STALE` und dürfen nicht angenommen oder still neu interpretiert werden.

## Domain-Architektur

Die typisierten Modelle liegen in `core:model`:

- `ProgressionScheme`
- `ProgressionConfig` und seine schemaspezifischen Varianten
- `ProgressionContext`
- `ProgressionOutcome`
- `ProgressionReasonCode`
- `ProgressionSuggestion` und `ProgressionSuggestionStatus`

Die reine Berechnung liegt in `core:common` hinter `ProgressionEngine.evaluate(context): ProgressionOutcome`. Jede Strategie ist eine kleine, unabhängige Implementierung und wird über Schema plus Regelrevision gewählt. Eine Revision bleibt mindestens so lange ausführbar, wie dazu noch Sessions ohne Auswertung oder offene Vorschläge existieren; fehlt sie wider Erwarten, liefert die Engine `INSUFFICIENT_DATA/RULE_REVISION_UNSUPPORTED` statt mit einer neueren Regel zu rechnen. Die Strategie erhält vollständig geladene Domain-Daten und greift weder auf Room, Repositories, Android APIs noch lokalisierte Strings zu. Grundcodes und numerische Argumente werden erst in der UI in deutsche Texte übersetzt.

`ProgressionContext` enthält genau:

- Plan-, Planübungs- und Übungsidentität einschließlich Reihenfolge,
- den unveränderlichen Session-Zielsnapshot und die Regelrevision,
- die gezählten Arbeitssätze des Quell-Workouts,
- die vergleichbaren vorherigen Ausgänge, die für die Fehlversuchsfolge nötig sind,
- die bei der Konfiguration gewählte Einheit, den ursprünglichen Schrittwert und die intern normalisierte Schrittweite.

Die Engine liefert einen der folgenden Ausgänge:

- `PROPOSE_CHANGE` mit Ausgangs- und Zielwerten,
- `KEEP_TARGET` mit Begründung,
- `INSUFFICIENT_DATA` mit konkretem Grundcode,
- `NOT_APPLICABLE` für `MANUAL` oder nicht planbasierte Daten.

## Persistenz

### Session-Zielsnapshot

Eine neue Tabelle `workout_plan_targets` speichert beim Start eines planbasierten Workouts pro Planübung einen unveränderlichen Snapshot aus `sessionId`, `planId`, `exerciseId`, `orderIndex`, Zielwerten, Schema, vollständiger Konfiguration, Regelrevision sowie konfigurierter Schritt-Einheit und ursprünglichem Schrittwert. Ein eindeutiger Index über Session und Reihenfolge verhindert doppelte Snapshots; ein zusätzlicher Konsistenzcheck stellt sicher, dass `exerciseId` und Reihenfolge zur damaligen Planübung gehören.

Das Anlegen der Workout-Session und ihrer Snapshots erfolgt in einer Room-Transaktion. Schlägt das Kopieren fehl, gilt das Workout als nicht gestartet. `WorkoutSet` und `workout_sets` erhalten einen nullable Verweis `planTargetSnapshotId`; neue Sätze eines planbasierten Workouts referenzieren damit genau die Planposition, auch wenn dieselbe Übung mehrfach vorkommt. Freie, zusätzlich während des Workouts hinzugefügte und ältere Sätze behalten `null` und sind für diese Progressionsauswertung nicht anwendbar.

Beim Löschen der Session werden ihre Snapshots per Foreign-Key-Cascade entfernt; der Satzverweis nutzt `ON DELETE SET NULL`, damit keine Satzhistorie allein durch einen fehlenden Snapshot verloren gehen kann. Repository und Backup-Validator prüfen zusätzlich, dass Satz und referenzierter Snapshot zur selben Session und Übung gehören. Version-10-Workouts besitzen keinen solchen Snapshot und werden nicht rückwirkend ausgewertet; weil die Migration alle bestehenden Schemata auf `MANUAL` setzt, geht dadurch keine aktive Progressionsentscheidung verloren.

### Planübung

`PlanExercise` enthält eine typisierte `ProgressionConfig`. `PlanExerciseEntity` speichert die Konfiguration flach, weil `TrainingPlanDao.replacePlanAndExercises` Planübungen beim Speichern löscht und neu einfügt. Eine separate 1:1-Tabelle mit `planExerciseId` könnte dadurch ihre Zuordnung verlieren.

Die neuen `plan_exercises`-Spalten erhalten sichere Defaults:

- `progressionScheme = 'MANUAL'`
- nullable schemaspezifische Werte für Wiederholungsbereich, Gesamtwiederholungen, kanonische Schrittweite, ursprünglichen Schrittwert samt Einheit, Ziel-RPE und RPE-Toleranz
- `progressionStallThreshold = 2`
- `progressionBackoffPercent = 10.0`
- `progressionRuleRevision = 1`

Die Planeditor-Validierung verhindert das Speichern einer aktiven, unvollständigen Konfiguration. Repository und Engine validieren dieselben Invarianten erneut, damit fehlerhafte Import- oder Migrationsdaten keine Planänderung auslösen.

### Auswertungs-Queue

Eine neue Tabelle `progression_suggestions` speichert Vorschläge und Informationen. Jeder Datensatz enthält mindestens:

- `sourceSessionId`, `sourceTargetSnapshotId`, `planId`, `exerciseId` und den damaligen `orderIndex`,
- den vollständigen Ziel-Snapshot und die Regelrevision,
- Ausgangstyp, Grundcode und numerische Erklärungsargumente,
- vorgeschlagene Zielwerte,
- Status `PENDING`, `ACCEPTED`, `REJECTED`, `STALE` oder `INFORMATIONAL`,
- Kennzeichnung und finale Werte einer manuellen Bearbeitung,
- Erstellungs- und Entscheidungszeitpunkt.

Ein eindeutiger Index über `sourceTargetSnapshotId` und Regelrevision macht die Erzeugung auch bei doppelt vorkommenden Übungen idempotent. Wird das Quell-Workout oder der Plan gelöscht, werden zugehörige Auswertungen per Foreign-Key-Cascade entfernt. Bereits bestätigte Planänderungen bleiben bei einer späteren Workout-Löschung bestehen, weil sie eine bewusste Nutzerentscheidung waren.

## Repositories und Transaktionen

Ein neues `ProgressionRepository` kapselt Beobachtung, Erzeugung und Entscheidungen. Die Konfiguration bleibt Teil des bestehenden `TrainingPlanRepository`.

`GenerateProgressionOutcomesForSession` läuft erst nach erfolgreichem Workout-Abschluss. Der Use Case lädt ausschließlich die Session-Zielsnapshots und die über `planTargetSnapshotId` eindeutig zugeordneten Sätze, wertet jede damals aktiv konfigurierte Planübung separat aus und schreibt die Ergebnisse idempotent. Schlägt er fehl, bleibt das Workout beendet und die UI bietet einen Retry an; eine inzwischen bearbeitete aktuelle Planversion beeinflusst den erneut berechneten Ausgang nicht.

`AcceptProgressionSuggestions` führt für die ausgewählte Menge in einer Room-Transaktion aus:

1. Alle Datensätze sind noch `PENDING`.
2. Der aktuelle Plan enthält dieselbe Übungsidentität an derselben Position.
3. Aktueller Zielwert, Schema, Konfiguration und Regelrevision entsprechen dem gespeicherten Snapshot.
4. Alle vorgeschlagenen oder bearbeiteten Werte erfüllen die Domain-Invarianten.
5. Erst danach werden alle Planwerte aktualisiert und alle Entscheidungen auf `ACCEPTED` gesetzt.

Die Zielaktualisierung adressiert die Planübung über Plan, Übung und Reihenfolge direkt und benutzt nicht den vorhandenen Ganzplan-Speicherpfad, der Planübungen löscht und neu einfügt. Scheitert eine Prüfung, wird kein ausgewählter Vorschlag übernommen. Nicht mehr passende Datensätze werden als `STALE` gekennzeichnet und die UI fordert eine neue Auswertung statt einer Überschreibung an.

## Nutzeroberfläche

### Planeditor

Jede Übungskarte erhält unter den bisherigen Zielwerten eine kompakte Zeile `Progression: Aus` beziehungsweise den Schemanamen. Ein Tap öffnet eine Konfigurations-Sheet mit nur den Feldern des gewählten Schemas, Inline-Validierung, kurzer Regelvorschau und der aktuell sichtbaren Einheit. Speichern aktualisiert zunächst nur den Editorzustand; die bestehende Plan-Speicheraktion persistiert alle Änderungen gemeinsam.

### Aktives Workout

Das aktive Workout bezieht Zielanzeige und Schemaname aus dem Session-Zielsnapshot, behält vorherige Einheit und RPE/RIR-Eingabe und erklärt damit, warum bestimmte Daten benötigt werden. Während des Workouts werden keine vorläufigen Progressionsvorschläge angezeigt; spätere Planbearbeitungen verändern die laufende Zielanzeige nicht.

### Abschluss-Review

Nach erfolgreicher Erzeugung öffnet sich bei mindestens einem aktivierten Schema ein eigener `ProgressionReview`-Screen. Jede Übung zeigt:

- den Schemanamen,
- `alt -> neu` für jede geänderte Zielgröße,
- die gezählten Sätze,
- eine lokalisierte Begründung,
- `Übernehmen`, `Bearbeiten` und `Verwerfen` für verändernde Vorschläge,
- eine reine Information ohne Bestätigungszwang für `KEEP_TARGET` und `INSUFFICIENT_DATA`.

`Alle sicheren übernehmen` wählt ausschließlich `PENDING`-Vorschläge und nutzt die atomare Sammeltransaktion. Schließen lässt Entscheidungen offen.

### Dashboard

Ein kompakter Hinweis erscheint, solange `PENDING`-Vorschläge existieren, und öffnet dieselbe Review. Informations-, angenommene, verworfene und veraltete Ergebnisse erzeugen keinen Dashboard-Hinweis.

## Fehler- und Randfälle

- **App beendet sich nach dem Workout:** Die idempotente Erzeugung kann beim nächsten Start erneut laufen; vorhandene Ergebnisse werden nicht dupliziert.
- **Plan wurde vor der Entscheidung bearbeitet:** Die Annahme schlägt geschlossen fehl, markiert Konflikte als `STALE` und überschreibt nichts.
- **Schema wurde deaktiviert:** Offene Vorschläge aus dem früheren Schema werden bei der nächsten Beobachtung als `STALE` markiert.
- **Plan oder Workout wurde gelöscht:** Zugehörige Auswertungen verschwinden per Cascade. Andere Trainingsdaten bleiben unberührt.
- **RPE/RIR fehlt oder ist ungültig:** Das RPE-Schema liefert `INSUFFICIENT_DATA`; andere Schemata können ohne RPE auswerten.
- **Zu wenige geplante Arbeitssätze:** Es gibt keine Steigerung und keinen Fehlversuch, weil das Workout keine vergleichbare Leistung belegt.
- **Zusatzsätze oder Warm-ups:** Sie bleiben sichtbar, werden aber nicht gezählt.
- **Neue Übung während eines laufenden Workouts:** Sätze ohne Plan-Zielsnapshot bleiben vollständig in der Historie, erzeugen aber keine Progressionsauswertung.
- **Abweichendes Arbeitsgewicht:** Es entsteht `MANUAL_WEIGHT_DEVIATION`, keine automatische Interpretation.
- **Ungültige importierte Konfiguration:** Der Validator meldet den konkreten Pfad; der Import bleibt wie bisher fail-closed.
- **Coach-Fehler nach erfolgreichem Workout-Abschluss:** Die UI zeigt Retry/Später; der Workout-Datensatz bleibt abgeschlossen.

## Migration und Backup

Die Room-Datenbank steigt von Version 10 auf 11. Die Migration ergänzt die Planübungsspalten und `workout_sets.planTargetSnapshotId` und erstellt `workout_plan_targets` sowie `progression_suggestions` samt Foreign Keys und eindeutigen Indizes. Sie verändert keine vorhandenen Zielwerte oder Sätze, setzt jede bestehende Planübung auf `MANUAL` und erzeugt keine rückwirkenden Snapshots oder Vorschläge.

Backupformat V1 wird abwärtskompatibel erweitert:

- Neue optionale Progressionsfelder in `BackupPlanExercise` besitzen `MANUAL`-kompatible Defaults.
- `BackupWorkoutSet.planTargetSnapshotId` ist optional und standardmäßig `null`.
- `workoutPlanTargets` wird als Liste mit Default `emptyList()` ergänzt.
- `progressionSuggestions` wird als Liste mit Default `emptyList()` ergänzt.
- Der Backup-`schemaVersion` steigt auf 11.
- Legacy-JSON ohne Progressionsfelder importiert unverändert und deaktiviert Progression.
- Export, Import, Recovery Snapshot, Hashing/Kanonisierung und Inhaltszählung behandeln die neuen Daten konsistent.

## Teststrategie

Die Implementierung folgt Red-Green-Refactor. Jede Produktionsregel beginnt mit einem beobachtet fehlschlagenden Test.

### Reine Domain-Tests

- Linear: vollständiges Ziel erhöht genau um die Schrittweite; ein unvollständiger Satz erhöht nicht.
- Doppelprogression: Zielwiederholung steigt innerhalb des Bereichs; an der Obergrenze steigen Gewicht und Wiederholungsziel springt auf das Minimum.
- Gesamtwiederholungen: unterschiedliche Satzverteilungen mit gleicher gültiger Summe führen zum gleichen Ergebnis; Zusatzsätze zählen nicht.
- RPE/RIR: gültige vollständige Daten innerhalb der Toleranz erhöhen; fehlender oder zu hoher RPE erhöht nicht.
- Gemeinsame Grenzen: Warm-ups, zu wenige Sätze, manuelle Gewichtsabweichung, ungültige Konfiguration, Rundung und Backoff-Untergrenze.
- Fehlversuchsfolge: nur identische Ziel-Snapshots zählen; Erfolg oder Konfigurationsänderung setzt zurück.

### Persistenz- und ViewModel-Tests

- Migration 10 -> 11 erhält alle bisherigen Daten, setzt `MANUAL` und erstellt gültige Indizes/FKs.
- Legacy-Backup ohne neue Felder sowie vollständiger V11-Roundtrip funktionieren.
- Workout-Start speichert Session und Ziel-Snapshots atomar; Reorder oder Planbearbeitung nach dem Start verändert den Snapshot nicht.
- Planbasierte Satzanlage verknüpft jeden Satz mit der korrekten Snapshot-Position; freie und alte Sätze bleiben mit `null` valide.
- Idempotente Erzeugung erzeugt pro Quellkontext genau einen Datensatz.
- Einzel- und Sammelannahme ändern Plan und Status atomar.
- Stale-Konflikt, Löschen und Retry verändern keine fremden Plan- oder Workoutdaten.
- Planeditor validiert jedes Schema und behält Konfiguration beim Reorder/Speichern.
- Review und Dashboard zeigen ausschließlich die für ihren Status vorgesehenen Aktionen.

### Integrationsnachweis

Ein CI-Instrumentation-Flow deckt `Plan mit Progression -> Workout loggen -> Workout beenden -> Vorschlag prüfen -> bestätigen -> aktualisierten Plan erneut öffnen` ab und muss nachweislich mindestens einen Test ausführen. Lokal werden die kleinsten betroffenen Modul- und Kompilierungsziele ausgeführt; die verbindlichen Remote-PR-Gates bleiben die Merge-Entscheidung.

## Abnahmekriterien

1. Kein bestehender Plan ändert nach Migration oder App-Start seine Ziele oder aktiviert Progression.
2. Alle vier aktiven Schemata erzeugen für ihre dokumentierten Eingaben deterministische Ausgänge.
3. Jede verändernde Empfehlung zeigt Quell-Sätze, unveränderten Session-Zielsnapshot, Zielwerte und Grund.
4. Ohne explizite Bestätigung bleibt der Plan byte-for-byte in seinen Ziel- und Progressionsfeldern unverändert.
5. Eine zwischenzeitliche Planbearbeitung verhindert die Annahme eines veralteten Vorschlags.
6. Sammelannahme ist atomar und kann keine teilweise Planänderung hinterlassen.
7. Workout-Abschluss bleibt auch bei Fehlern der Auswertung dauerhaft gespeichert.
8. Pending-Vorschläge überleben Prozessneustart und Backup/Restore.
9. Legacy-Backups importieren mit deaktivierter Progression.
10. Einheiteneingabe und Rundung liefern in Metric und Imperial verständliche Werte bei kanonischer kg-Speicherung.
11. Ein Retry nach Prozessneustart liefert aus denselben Snapshots denselben Ausgang, auch wenn der aktuelle Plan inzwischen bearbeitet wurde.
12. Der gezielte Domain-, Persistenz- und ViewModel-Testumfang ist grün; der CI-Instrumentation-Flow führt tatsächlich Tests aus.

## Nicht Teil dieser Ausbaustufe

- Prozentwellen, Training-Max-Blöcke, Mesocyclen und periodisierte Kalendersteuerung
- automatische Trainingsplanänderungen ohne Bestätigung
- KI-basierte oder cloudgestützte Empfehlungen
- Wearables, Ernährung, Schlaf- oder Readiness-Scoring
- Schmerz-, Verletzungs- oder medizinische Belastbarkeitsbewertung
- iOS-Implementierung

Die Regel-Schnittstelle und die gespeicherte Regelrevision erlauben spätere Schemata, ohne die bestätigungspflichtige Review oder den bestehenden Workout-Datenpfad neu zu entwerfen.
