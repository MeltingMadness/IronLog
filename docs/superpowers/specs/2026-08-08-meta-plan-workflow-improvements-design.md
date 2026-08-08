# IronLog: Meta-Plan-Workflow und Trainingshistorie

Datum: 2026-08-08  
Status: Zur Freigabe  
Plattform: Android

## Ziel

Der Einstieg in ein Training soll Meta-Pläne priorisieren, übersichtlicher werden und die Historie korrekt nach Trainingskontext behandeln. Gleichzeitig wird die nicht mehr gewünschte Serienanzeige vollständig entfernt. Die Änderungen bleiben auf Android beschränkt; iOS und ein allgemeines Redesign sind nicht Teil dieses Vorhabens.

## Funktionsumfang

### Trainingsserie vollständig entfernen

Die Startseite zeigt keine Trainingsserie mehr. Die zugehörige UI, Zustandsfelder, Berechnung, Repository- und DAO-Abfragen, Tests und ausschließlich dafür verwendeten Texte werden entfernt. Die Funktion wird nicht an anderer Stelle wieder eingesetzt.

### Meta-Trainingspläne zuerst anzeigen

Im Dialog „Training starten“ stehen Meta-Trainingspläne oberhalb der normalen Trainingspläne. Innerhalb beider Gruppen bleibt die bestehende Sortierung unverändert.

### Aktuellen Plan eines Meta-Plans überspringen

Für jeden Meta-Plan zeigt „Training starten“ weiterhin den aktuell vorgeschlagenen Teilplan. Direkt an diesem Vorschlag steht die Aktion „Überspringen“.

Ein Überspringen erzeugt ein dauerhaftes Rotationsereignis und wechselt unmittelbar zum nächsten Teilplan. Es erzeugt ausdrücklich kein absolviertes Training und beeinflusst deshalb weder Statistiken noch Trainingsverlauf. Bei einem Meta-Plan mit nur einem Teilplan ist die Aktion deaktiviert.

Die Rotation verwendet pro Teilplan das jeweils jüngste relevante Ereignis: den Startzeitpunkt des letzten abgeschlossenen Trainings oder den Zeitpunkt des letzten Überspringens. Der Teilplan mit dem ältesten Ereignis wird als Nächstes vorgeschlagen; ein Teilplan ohne Ereignis gilt als älter als jeder bereits verwendete Teilplan. Bei Gleichstand bleibt die bestehende Reihenfolge des Meta-Plans maßgeblich.

Wenn der vorgeschlagene Teilplan zwischen Anzeige und Aktion gelöscht wurde oder nicht mehr zum Meta-Plan gehört, wird kein Überspringen gespeichert. Die Liste wird aktualisiert und zeigt den dann gültigen Vorschlag.

### Gewichtshistorie zwischen Kontexten steuern

In den Einstellungen kommt der Schalter „Gewichte zwischen Einzel- und Meta-Plänen teilen“ hinzu. Der Standardwert ist aus.

Bei ausgeschaltetem Schalter werden Gewichtsvorschläge getrennt behandelt:

- Ein normal gestarteter Plan verwendet nur frühere Trainings desselben Plans, die ebenfalls ohne Meta-Plan gestartet wurden.
- Ein innerhalb eines Meta-Plans gestarteter Plan verwendet nur frühere Trainings desselben Plans innerhalb genau dieses Meta-Plans.
- Derselbe Teilplan in zwei verschiedenen Meta-Plänen besitzt damit zwei getrennte Gewichtshistorien.

Bei eingeschaltetem Schalter gilt das bisherige Verhalten: Alle früheren Trainings desselben Plans dürfen unabhängig vom Meta-Plan als Gewichtshistorie dienen.

Der Schalter verändert keine gespeicherten Trainingsdaten. Eine Änderung wirkt bei der nächsten Ermittlung der vorherigen Übungssitzung und ist damit spätestens beim nächsten Trainingsstart sichtbar.

### Erfolgsindikator im aktiven Training

Im aktiven Training zeigt jede geplante Übung einen positiven Indikator im Zielbereich, wenn im letzten relevanten Training der letzte Arbeitssatz sowohl das aktuelle Zielgewicht als auch die aktuelle Zielwiederholungszahl erreicht oder überschritten hat.

Für die Ermittlung gelten diese Regeln:

- Relevant ist dieselbe Historie, die sich aus dem neuen Gewichtshistorien-Schalter ergibt.
- Aufwärmsätze werden ignoriert; ausgewertet wird der letzte Nicht-Aufwärmsatz der Übung.
- Der Indikator erscheint nur, wenn Zielgewicht und Zielwiederholungszahl beide größer als null sind.
- Gewicht und Wiederholungen müssen jeweils mindestens dem aktuellen Ziel entsprechen.
- Freie Trainings und Übungen ohne vollständiges Gewichts- und Wiederholungsziel zeigen keinen Indikator.

Der Indikator ist eine kompakte, semantisch positive Statuszeile oder Plakette nahe der bestehenden Zielanzeige. Er verändert weder die Eingabewerte noch den Trainingsablauf.

## Technische Ausführung

### Persistenz für übersprungene Teilpläne

Room erhält die Tabelle `meta_plan_skips` mit einer eigenen ID sowie `metaPlanId`, `trainingPlanId` und `skippedAt`. Die Fremdschlüssel folgen der bestehenden Löschstrategie der zugehörigen Pläne. Indizes auf den Planbezügen unterstützen die Rotationsabfrage.

Die Datenbankversion steigt von 9 auf 10. Die Migration legt ausschließlich die neue Tabelle und ihre Indizes an; vorhandene Trainings- und Plandaten bleiben unverändert.

Die Repository-Schnittstelle erhält eine atomare Operation zum Überspringen des aktuell gültigen Teilplans und eine lesbare Quelle für die Rotationsereignisse. Die Aktion übergibt den in der UI erwarteten aktuellen Teilplan. Innerhalb einer Datenbanktransaktion berechnet die Implementierung den aktuellen Vorschlag erneut und schreibt nur dann ein Ereignis, wenn Meta-Plan und Teilplan existieren, zusammengehören, mindestens zwei Teilpläne vorhanden sind und der erwartete Teilplan noch immer der aktuelle Vorschlag ist. Das Ergebnis unterscheidet zwischen erfolgreichem Überspringen und einem inzwischen veralteten Vorschlag.

### Rotation

Die Rotationslogik wird als gemeinsam verwendbare Domänenlogik formuliert, damit Startdialog und Meta-Plan-Verwaltung nicht voneinander abweichen. Als Eingabe dienen die geordnete Teilplanliste, die letzten abgeschlossenen Trainings pro Teilplan und die letzten Überspringereignisse pro Teilplan.

Nach erfolgreichem Überspringen aktualisiert der Startdialog seine Datenquelle und zeigt den nächsten Vorschlag. Ein fehlgeschlagener Validierungsversuch verändert keine Daten und löst ebenfalls eine Aktualisierung aus.

### Einstellung und Historienabfragen

Der neue boolesche Wert wird nach dem bestehenden Muster in `AppPreferencesDataStore`, Preferences-Modell, Repository, `SettingsViewModel` und Einstellungs-UI ergänzt. Fehlt der Wert in einer vorhandenen Installation, wird `false` verwendet.

Die Abfrage der vorherigen Übungssitzung erhält neben der Plan-ID den aktuellen `metaPlanId`-Kontext und den Wert der Einstellung:

| Einstellung | Trainingskontext | Filter |
|---|---|---|
| Aus | Normaler Plan | gleiche `planId`, `metaPlanId IS NULL` |
| Aus | Meta-Plan | gleiche `planId`, gleiche `metaPlanId` |
| An | Beliebig | gleiche `planId`, Meta-Kontext ohne Filter |

`ActiveWorkoutViewModel` verwendet diese Abfrage sowohl für Gewichtsvorschläge als auch für den Erfolgsindikator, damit beide Funktionen immer denselben historischen Bezug haben.

### Backup und Wiederherstellung

Backups enthalten die neuen Überspringereignisse. Export, Import und Validierung werden entsprechend erweitert. Ältere Backups ohne dieses Feld bleiben gültig und werden mit einer leeren Liste von Überspringereignissen importiert. Ungültige Referenzen werden nach den bestehenden Integritätsregeln behandelt und dürfen keine künstlichen Trainings erzeugen.

## Fehler- und Randfälle

- Ein Meta-Plan ohne Teilplan kann weder gestartet noch übersprungen werden; die bestehende leere Darstellung bleibt erhalten.
- Bei genau einem Teilplan ist „Überspringen“ sichtbar, aber deaktiviert, damit der Grund für die fehlende Aktion nachvollziehbar bleibt.
- Mehrfaches schnelles Antippen darf pro bestätigtem Vorschlag höchstens ein wirksames Überspringereignis erzeugen. Während des Schreibvorgangs ist die Aktion deaktiviert; zusätzlich verwirft die transaktionale Prüfung jeden inzwischen veralteten Vorschlag.
- Ein gelöschter oder aus dem Meta-Plan entfernter Vorschlag führt zu keiner Mutation und wird durch eine Aktualisierung ersetzt.
- Eine frühere Übungssitzung ohne Arbeitssatz oder mit unvollständigen Werten erzeugt keinen Erfolgsindikator.
- Zieländerungen werden gegen die aktuellen Planwerte bewertet, nicht gegen die Ziele des früheren Trainings.

## Qualitätssicherung

Die Implementierung folgt gezielten Tests an den geänderten Grenzen:

- Dashboard: Kein Serienzustand und keine Serienberechnung mehr; Meta-Pläne werden im Startdialog vor normalen Plänen dargestellt.
- Rotation: Überspringen wählt den nächsten Teilplan, bleibt nach Repository-Neuladen erhalten, verändert keine Workout-Session und ist bei einem Teilplan wirkungslos.
- Datenbank: Migration 9 auf 10 erhält Bestandsdaten und legt `meta_plan_skips` korrekt an.
- Backup: Neue Backups durchlaufen einen Roundtrip mit Überspringereignissen; ein altes Backup ohne das neue Feld wird weiterhin importiert.
- Einstellungen: Standardwert ist aus, Änderungen werden persistiert und von der Historienabfrage berücksichtigt.
- Historie: Die drei Filterfälle aus der Tabelle liefern jeweils die richtige vorherige Sitzung.
- Indikator: Erreichen und Überschreiten beider Ziele zeigt ihn; ein unterschrittenes Ziel, fehlende Ziele, nur Aufwärmsätze oder ein falscher Historienkontext zeigen ihn nicht.

Nach den fokussierten Tests wird ein Debug-Build erstellt. Die vier bestehenden CI-Gates bleiben die Voraussetzung für den Merge; die Instrumentierungstests laufen in GitHub Actions.

## Abnahmekriterien

Die Änderung ist abnahmefähig, wenn alle fünf Nutzerfunktionen wie beschrieben auf Android funktionieren, Überspringen keine Workout-Session erzeugt, ältere Backups importierbar bleiben, der Standardwert der Gewichtsteilung aus ist und alle gezielten Tests sowie der Debug-Build erfolgreich sind. iOS-Anpassungen, neue Statistikfunktionen und ein allgemeines visuelles Redesign bleiben außerhalb des Scopes.
