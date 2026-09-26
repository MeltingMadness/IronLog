# Progressions-Coach

Der Coach schlägt nach einem planbasierten Workout neue Zielwerte pro Planübung vor. **Er ändert einen Plan nie von selbst.** Erst wenn du einen Vorschlag bestätigst, wird der Plan angepasst.

Fachliche Grundlage: [`../research/2026-08-08-progression-schemes.md`](../research/2026-08-08-progression-schemes.md).

## Ablauf

1. **Plan-Editor:** Pro Planübung ein Schema wählen („Progression: Aus“ oder Schemaname). Neue und bestehende Übungen starten mit **Aus** (`MANUAL`).
2. **Workout-Start:** Die App speichert pro Planübung einen unveränderlichen Snapshot aus Zielwerten, Schema und Konfiguration (`workout_plan_targets`). Jeder Satz verweist auf seine Planposition (`planTargetSnapshotId`). Spätere Planänderungen deuten das Training nicht mehr um.
3. **Während des Workouts:** Keine vorläufigen Vorschläge. Angezeigt werden Schema, Ziel und Hinweise:
   - Gewicht: das zuletzt trainierte Gewicht, sonst das Planziel
   - RPE/RIR-Schema: Ziel-RPE als Platzhalter (bei RIR: 10 − Ziel-RPE)
4. **Workout beenden:** Danach erzeugt der Coach die Ergebnisse. Ein Fehler im Coach hält das Workout nicht offen. Das Workout bleibt gespeichert, die Auswertung lässt sich wiederholen und ist idempotent.
5. **Review-Screen** „Progression prüfen“: pro Übung Schema, `alt → neu`, gewertete Sätze und Begründung. Aktionen: **Übernehmen**, **Bearbeiten**, **Verwerfen** sowie **Alle sicheren übernehmen**. Reine Hinweise brauchen keine Entscheidung. Schließen lässt Offenes offen.
6. **Dashboard:** Solange Vorschläge offen sind, erscheint „N Progressionsvorschläge offen · Prüfen“.

## Begriffe

- **Gezählte Arbeitssätze:** die ersten `targetSets` Nicht-Aufwärmsätze einer Planposition. Zusätzliche Sätze werden angezeigt, aber nicht gewertet.
- **Trainiertes Gewicht:** Alle gezählten Sätze müssen dasselbe Gewicht haben, mit einer Toleranz von 0,1 kg. Vorschläge bauen auf diesem **tatsächlich trainierten** Gewicht auf, auch wenn es vom Planziel abweicht. So nähert sich der Plan der Realität an. Gemischte Gewichte in einer Session ergeben den Hinweis `MANUAL_WEIGHT_DEVIATION`, aber keinen Vorschlag.
- **Fehlversuch:** Die Regelbedingung wurde verfehlt. Zählt nur über vergleichbare Workouts mit identischem Snapshot. Erfolg, übernommene Änderung, Planbearbeitung oder geänderte Konfiguration setzen die Zählung zurück.

## Schemata

| Schema | Erfolg, wenn … | Vorschlag bei Erfolg |
|---|---|---|
| **Linear** | alle gezählten Sätze ≥ `targetReps` | Gewicht + Schrittweite |
| **Doppelte Progression** (`minReps ≤ targetReps ≤ maxReps`) | alle gezählten Sätze ≥ `targetReps` | unter `maxReps`: `targetReps + 1`, gleiches Gewicht. An `maxReps`: Gewicht + Schrittweite, `targetReps = minReps` |
| **Gesamtwiederholungen** | Summe der gezählten Wdh. ≥ `targetTotalReps` (Verteilung egal) | Gewicht + Schrittweite |
| **RPE/RIR** | alle gezählten Sätze ≥ `targetReps` **und** höchstes RPE ≤ Ziel-RPE + Toleranz | Gewicht + Schrittweite |

Bei **RPE/RIR** gilt die Prüfreihenfolge:

1. Wiederholungen verfehlt → Fehlversuch. Ein fehlendes RPE kann einen echten Fehlversuch nicht verdecken.
2. RPE fehlt oder ist ungültig (nicht zwischen 1 und 10) → Hinweis `RPE_MISSING` bzw. `RPE_INVALID`. Das ist kein Fehlversuch und setzt die Zählung auch nicht zurück.
3. RPE zu hoch → „Ziel wiederholen“, **zählt als Fehlversuch**. So kann auch hier ein Backoff greifen.

**Vorbelegungen im Editor:** Schrittweite 2,5 kg bzw. 5 lb. Doppelte Progression: `min = targetReps`, `max = targetReps + 2`. Gesamtwiederholungen: `targetSets × targetReps`. RPE: Ziel 8, Toleranz 0,5.

## Fehlversuche und Backoff

- Ein einzelner Fehlversuch ergibt „Ziel wiederholen“ (`REPEAT_TARGET`).
- Erreicht die Folge die **Fehlversuchsschwelle** (1–6, Standard 2), schlägt der Coach einen **Backoff** vor (`STALL_BACKOFF`). Die Basis ist das trainierte Gewicht × (1 − Backoff %), Backoff 1–30 %, Standard 10 %.

## Rundung

Gerechnet wird in der Einheit, in der die Schrittweite konfiguriert wurde (kg oder lb). Gespeichert wird immer in kg.

- **Steigerung:** trainiertes Gewicht + Schritt, gerundet auf das nächste Vielfache der Schrittweite (bei Gleichstand der niedrigere Wert). Das Ergebnis liegt immer über dem Ausgangswert.
- **Backoff:** ebenfalls auf das Schrittraster gerundet. Ergibt die Rundung keine Absenkung, wird genau ein Schritt abgezogen, aber nie unter 0 (`BACKOFF_FLOOR_REACHED`).

## Übernehmen ist atomar

„Übernehmen“ und „Alle sicheren übernehmen“ laufen in einer Room-Transaktion. Die Transaktion prüft:

- Der Vorschlag ist noch offen.
- Der Plan hat dieselbe Übung an derselben Position.
- Ziel, Schema, Konfiguration und Regelrevision entsprechen dem Snapshot.

Passt etwas nicht, wird nichts übernommen und der Vorschlag wird als **nicht mehr aktuell** (`STALE`) markiert.

Wird ein Satz eines abgeschlossenen Trainings im Verlauf korrigiert oder gelöscht, werden die offenen Vorschläge dieses Trainings ebenfalls `STALE`. Sie beruhen auf den alten Werten. Bereits entschiedene Vorschläge bleiben unverändert. Android und iOS verhalten sich hier gleich.

## Status eines Ergebnisses

`PENDING` (offen) · `INFORMATIONAL` (Hinweis) · `ACCEPTED` · `REJECTED` · `STALE`

Nur `PENDING` zählt für den Dashboard-Hinweis.

## Code

| Teil | Ort |
|---|---|
| Modelle, Grundcodes (`ProgressionReasonCode`) | `core/model/.../domain/model/Progression.kt` |
| Engine und Regeln (Revision 1) | `core/common/.../domain/progression/` und `.../progression/v1/` |
| Persistenz | `ProgressionDao`, Tabellen `workout_plan_targets`, `progression_suggestions` |
| Repository | `data/.../repository/ProgressionRepositoryImpl` |
| UI | `feature/progression` (Review), `feature/plans` (Konfiguration), `feature/workout` (Hinweise) |
| Lifecycle-Test (Emulator) | `app/src/androidTest/.../ProgressionCoachLifecycleTest.kt` |

Jede fachliche Änderung an einer Regel braucht eine neue **Regelrevision**. Offene Vorschläge einer älteren Revision werden dann `STALE`, statt still neu interpretiert zu werden.

## Individuelle Satzvorgaben (manuelle Progression)

Eine Planübung kann statt `targetSets × targetReps @ Gewicht` eine Liste einzelner Satzvorgaben haben (`setTargetsJson`, Modell `PlannedSet` in `:shared`). Diese Vorgaben landen im Snapshot beim Workout-Start.

Nach dem Workout bietet die Zusammenfassung „Planänderungen prüfen“ an. Dort lassen sich die **tatsächlich absolvierten** Satzwerte ausdrücklich als neue Vorgaben übernehmen („Satzwerte übernehmen“) oder der Plan bleibt unverändert. Offene, nicht absolvierte Vorgaben bleiben erhalten. Hat sich der Plan seit dem Workout-Start geändert, wird die Übernahme abgelehnt.

## Plattformen

Die Regeln liegen im gemeinsamen Kern (`:shared`, `progression`). Android ruft sie über `PortableProgressionAdapter` auf, die iOS-App direkt. Der Ablauf mit Review und Bestätigung ist auf beiden Plattformen gleich.

## Nicht enthalten

Prozentwellen, Training-Max-Blöcke, Periodisierung, automatische Planänderungen ohne Bestätigung, KI- oder Cloud-Empfehlungen, medizinische Bewertung.
