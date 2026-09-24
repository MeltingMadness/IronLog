# Readiness- und Trainingstrend-Kern

Plattformneutraler Kern in `shared/src/commonMain/kotlin/com/ironlog/shared/readiness/`.
Er beantwortet zwei getrennte Fragen und erfindet nichts dazwischen:

1. **Mehrwoechiger Trainingstrend je Uebung** - hat sich eine Uebung ueber mehrere
   *vergleichbare* Einheiten wiederholt unerwartet verschlechtert?
2. **Tagesform aus einem optionalen Check-in** - was hat der Athlet heute gemeldet?

Dazu kommen **Fakten je Muskelgruppe** zum heutigen Training.

Der Vertrag steht in [CONTRACT.md](CONTRACT.md) und ist auf Revision 2 eingefroren.

## Dateien

| Datei | Inhalt |
| --- | --- |
| `CONTRACT.md` | Eingefrorener API-Vertrag (Adapter binden dagegen) |
| `ReadinessModels.kt` | Ein-/Ausgabetypen, Enums, Reason-Codes |
| `ReadinessThresholds.kt` | Alle Produktschwellen, versioniert |
| `TrainingTrendEngine.kt` | Vergleichsreihen, Resets, wiederholte Verschlechterung, Index |
| `ReadinessEngine.kt` | `ReadinessEngine`, `DailyFormEngine`, `MuscleContextEngine` |

## Einstieg

```kotlin
val assessment: ReadinessAssessment = ReadinessEngine.assess(
    ReadinessInput(
        nowEpochMillis = now,
        sessions = exerciseSessions,
        todaySession = today,
        muscleLoadHistory = loadHistory,
        muscleWindowStartEpochMillis = now - 7L * 24 * 60 * 60 * 1000,
        checkIn = checkIn,
    ),
)
```

Alles ist eine pure Funktion: keine Persistenz, keine Uhrzeit, keine
Planmutation, keine Plattformabhaengigkeit. Zeitstempel sind `Long`-Epoch-Millis;
Kalender- und Zeitzonenlogik bleibt im Adapter.

## Warum die Entscheidungen so aussehen

**Aktualität.** Nur abgeschlossene Einheiten der letzten 28 Tage gehen in den aktuellen Trend ein. Ältere oder zukünftige Einheiten erzeugen keinen aktuellen Index.

**Vergleichbarkeit vor Wochenbestwert.** Jede Uebung bekommt ihre *eigene* Reihe.
TOTAL_REPS vergleicht die Summe aller Arbeitssatz-Wiederholungen bei gleicher Last.
LINEAR_LOAD vergleicht die durchschnittlich erreichte Last, reduziert bei
Unterschreiten der vorgeschriebenen Mindestwiederholungen. Die übrigen gewichteten
Schemata verwenden den Mittelwert der E1RM-Schätzungen in vergleichbaren Rep-Bändern.
Neue Wiederholungsziele, Schemen oder RPE-Ziele beginnen eine neue Vergleichsserie.
Das Erhöhen eines Zielgewichts verändert keinen Nenner: gleiche Leistung bleibt
gleiche Leistung. Fehlendes RPE verändert den Leistungswert nicht.
Die Referenz ist der Median der Einheiten außerhalb des jüngsten Fensters;
bei kurzer Historie bleibt die Aussagekraft entsprechend begrenzt.

**Nichts Ungleiches wird verglichen.** Aendert sich die Vergleichsgrundlage -
anderer `metricKind`, anderes Rep-Band, andere Arbeitssatzanzahl - beginnt eine
neue Serie mit explizitem Grund (`METRIC_KIND_CHANGED_RESET`,
`REP_BAND_CHANGED_RESET`, `SET_COUNT_CHANGED_RESET`, Notiz
`COMPARABILITY_BREAK`). Ein Wechsel wird nie als Leistungsabfall gelesen.

**Eine Uebung, eine Einheit.** Mehrere Zeilen derselben Uebung in derselben
Sitzung (z.B. zwei Slots) werden zu genau **einer** Trainingseinheit
zusammengefasst. Sie zaehlen nicht mehrfach als Evidenz.

**Geplantes Muskelversagen ist neutral.** Eine Einheit, deren Arbeitssaetze
ausnahmslos die Intention `PLANNED_FAILURE` tragen, wird beim Decline-Test
uebersprungen (`PLANNED_FAILURE_UNIT_NEUTRAL`): ein bewusst bis zum Versagen
gefahrener Satz ist keine unerwartete Verschlechterung. Ein historischer
`SetType.FAILURE` ohne Intention zaehlt weiterhin normal.

**Alle Geraetearten.** Es gibt keine Langhantel-Annahme. `equipmentType` ist ein
expliziter Eingabewert, und die kleinste Laststufe (`weightIncrementKg`) wird pro
Uebung konfiguriert. Eine Kurzhantel-Reihe wie 4 / 5.5 / 7 / ... kg ist ein
explizites `1.5`; fehlt die Angabe, gilt der Schritt als **unbekannt** und die
Ziel-Reset-Erkennung wird uebersprungen (Notiz `WEIGHT_STEP_UNKNOWN`).

**Fortschrittsziel statt Stagnation.** Zielgewicht, Wiederholungsbereich, Ziel-RPE,
Schrittweite und Schema werden beruecksichtigt. Ein absichtlicher Lastrueckstieg um
mindestens eine volle Stufe beginnt eine neue Serie (`GOAL_CHANGED_RESET`) und ist
keine Verschlechterung.

**Satzart != Absicht.** `setType` (NORMAL/WARMUP/FAILURE/DROP_SET/UNKNOWN) sagt, wie
ein Satz geloggt wurde. `intention` (kanonische Namen aus
`com.ironlog.shared.readinessdata.SetIntention`: `PLANNED_FAILURE`,
`UNEXPECTED_TARGET_MISS`, `UNKNOWN`) sagt, warum er endete. Ein historischer
`SetType.FAILURE` ist **kein** geplantes Muskelversagen; nur eine explizit
gemeldete Absicht zaehlt. Unbekannte Codes werden nie erfunden, sondern fallen auf
`UNKNOWN` zurueck.

**Keine pauschale Ermuedung.** Stagnation (`STAGNATION_NEUTRAL`), geplantes
Muskelversagen (`PLANNED_FAILURE_NEUTRAL`) und eine laufende Deload
(`DELOAD_IN_PROGRESS`, `DELOAD_CONTEXT_NEUTRAL`) loesen allein keinen
Ermuedungshinweis aus. Sie stehen als Reasons in der Ausgabe, damit die
Entscheidung nachvollziehbar bleibt.

**Wiederholt heisst wiederholt.** Ein einzelner Ausreisser genuegt nicht. Signifikant
ist ein Rueckgang ab `notableDeclinePercent`; "wiederholt" verlangt mindestens
`repeatedDeclineCount` der letzten `declineWindowUnits` Deltas (Standard 2 von 2).
Erst ab `multiExerciseDeclineCount` Uebungen spricht der Kern von einer
Mehr-Uebungs-Verschlechterung und macht einen **Deload-Vorschlag** - nie eine
Mutation.

**Datenqualitaet ist explizit.** `TrendDataQuality` zaehlt fehlende RPE, unbekannte
Intentionen, unbekannte Satzarten, ausgeschlossene Deload-Einheiten und
uneinheitliche Geraete und benennt sie in `notes`. Fehlende RPE wird **neutral**
behandelt: sie senkt hoechstens eine Confidence-Stufe und macht die Historie nie
unbrauchbar.

**Tagesform getrennt vom Trend.** Die Tagesform kommt nur aus dem optionalen
Check-in (Schlaf, Energie, Stress, Muskelkater), wird pro Dimension als
`GOOD`/`NEUTRAL`/`CONCERN`/`UNKNOWN` ausgewiesen und **nie** zu einem Gesamtwert
summiert. Der Trainingstrend ist davon unabhaengig. Fehlt der Check-in, ist
`dailyForm` schlicht `null`.

**Muskelgruppen ohne Erholungsprozent.** Der Muskelkontext liefert letzte Belastung,
Saetze im Fenster, heutige Saetze, Muskelkater und Flags. Bewusst **keine**
Erholung in Prozent und kein "Ready in X Stunden" - diese Zahl waere nicht belegt.

**Heuristischer Trainingsindex.** `trainingIndex` ist ausdruecklich ein
*heuristischer* Index von 0..100 (100 = keine wiederholte Verschlechterung
erkannt). Er ist **`null`**, sobald die Evidenz unter `minExercisesForIndex`
Uebungen oder `minIndexEvidenceUnits` vergleichbare Einheiten faellt. Er enthaelt
**keine** Tagesform. Er ist kein medizinischer Readiness-Wert.

## Schwellen (Revision 2)

Alle Werte liegen in `ReadinessThresholds` und sind Produktheuristiken, keine
gemessenen oder klinischen Grenzen. Sie sind konfigurierbar.

| Schwelle | Default | Bedeutung |
| --- | --- | --- |
| `minComparableUnits` | 3 | Ab so vielen vergleichbaren Einheiten wird ein Uebungstrend berichtet |
| `declineWindowUnits` | 2 | Betrachtete Deltas fuer "wiederholt" |
| `repeatedDeclineCount` | 2 | Noetige signifikante Rueckgaenge im Fenster |
| `notableDeclinePercent` | 5.0 | Ab diesem Rueckgang gilt ein Delta als signifikant |
| `improveThresholdPercent` | 2.5 | Ab diesem Zuwachs gilt ein Trend als verbessernd |
| `trendBandPercent` | 2.5 | Band um die Basis, in dem ein Trend als stabil gilt |
| `maxRepsForE1rmEstimate` | 12 | Obergrenze der E1RM-Schaetzung |
| `repBandWidth` | 3 | Rep-Band-Breite der E1RM-Vergleichbarkeit |
| `segmentationGapMillis` | 14 Tage | Laengere Luecke startet eine neue Serie |
| `minRpeCoverageForConfidence` | 0.5 | Unter dieser RPE-Abdeckung sinkt die Confidence |
| `minExercisesForIndex` | 2 | Unter so vielen Uebungen bleibt der Index `null` |
| `minIndexEvidenceUnits` | 6 | Unter so vielen Einheiten bleibt der Index `null` |
| `multiExerciseDeclineCount` | 2 | Ab so vielen Uebungen mit wiederholtem Rueckgang Deload-Vorschlag |
| `indexDeclineWeight` | 30 | Abzug je Uebung mit wiederholtem Rueckgang |
| `indexSingleDeclineWeight` | 8 | Abzug je Uebung mit einmaligem Rueckgang |
| `indexMaxPenalty` | 60 | Obergrenze des Index-Abzugs |
| `muscleHighWeeklySets` | 20.0 | Ab so vielen Saetzen im Fenster `HIGH_RECENT_VOLUME` |
| `muscleLowWeeklySets` | 4.0 | Darunter (aber > 0) `LOW_RECENT_VOLUME` |
| `muscleHighSorenessAtLeast` | 4 | Ab diesem Muskelkater `HIGH_SORENESS` |

### Aenderungshistorie

* **Revision 3 (2026-09-11).** Tatsächliche Schema-Verzweigung, absolute
  Leistung statt Zielquotient, Median auch bei zwei Referenzwerten, keine
  numerische RPE-Anpassung und neutrale Zieländerungen.
* **Revision 2 (2026-09-11, Vorentwurf).** Schemegerechter Vergleichsmetrik (`GOAL_SCORE`),
  Serienbruch bei metrik-/rep-band-/satzanzahl-Wechsel, Session-Merge gegen
  Doppelzaehlung, geplante FAILURE-Einheiten neutral im Decline-Test, robuste
  Median-Basis statt Maximalwert, begrenzte Ziel-RPE-Anpassung.
* **Revision 1 (2026-09-11).** Erste eingefrorene Fassung. `setType` und `intention`
  bewusst getrennt; keine Geraete-Schrittweite als Default; fehlende RPE neutral:
  Confidence sinkt hoechstens eine Stufe, der Index bleibt berechenbar.

Neue Schwellen oder Semantikaenderungen erhoehen `revision` und werden hier
dokumentiert. Eine stille Schwellenaenderung gibt es nicht.
