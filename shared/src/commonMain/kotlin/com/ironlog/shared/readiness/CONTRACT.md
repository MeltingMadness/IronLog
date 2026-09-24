# Readiness / Trainingstrend - API-Vertrag

Status: **eingefroren fuer Plattformintegration, Revision 3 (2026-09-11)**
Paket: `com.ironlog.shared.readiness`
Zielplattformen: Android (`:shared`), iOS (`Shared.framework`) - plattformneutral, keine
Abhaengigkeit auf Android-Modelle, Room, DataStore, `java.time` oder `kotlinx.datetime`.

Dieses Dokument ist der Vertrag. Adapter binden dagegen. Aenderungen an Signaturen und
Semantik erfordern eine neue `ReadinessThresholds.revision` und einen Eintrag in `README.md`.

Das parallele Modul `com.ironlog.shared.readinessdata` besitzt die kanonische
Satzintention. Dieser Kern **dupliziert sie nicht**, sondern fuehrt ihre Namen als portable
Codes (siehe 2.3). Ein Aliasadapter ist dadurch nicht noetig.

## 1. Zweck und Abgrenzung

Der Kern beantwortet zwei getrennte Fragen:

1. **Mehrwoechiger Trainingstrend je Uebung.** Hat sich eine Uebung ueber mehrere
   vergleichbare Einheiten wiederholt unerwartet verschlechtert?
2. **Tagesform aus einem optionalen Check-in.** Gibt es heute Hinweise aus Schlaf,
   Energie, Stress oder Muskelkater?

Zusaetzlich liefert er **Fakten je Muskelgruppe** zum heutigen Training
(letzte Belastung, Volumen im Fenster, Muskelkater), ohne eine Erholung in Prozent
oder einen Gesamtwert zu erfinden.

Der Kern **mutiert niemals einen Plan**. Er liefert nur erklaerbare Signale. Das
Setzen, Verwerfen oder Verschieben von Zielen bleibt ein expliziter, bestaetigter
Nutzer- bzw. Adapter-Schritt.

Der Kern behauptet **keine wissenschaftlichen Grenzwerte**. Alle Schwellen sind
Produktheuristiken, zentral in `ReadinessThresholds` hinterlegt, dokumentiert und
versioniert.

## 2. Eingaben (Input)

Alle Zeitstempel sind `Long`-Epoch-Millisekunden. Kalender- und Zeitzonenlogik
(Wochenanfang, DST, "heute") liegt ausschliesslich beim Adapter. Der Kern kennt nur
Instanten und vom Adapter gelieferte Fenstergrenzen.

### 2.1 `ReadinessInput` (Wurzel)

```kotlin
data class ReadinessInput(
    val nowEpochMillis: Long,
    val sessions: List<ExerciseSessionInput> = emptyList(),      // alle Uebungen, alle Einheiten, beliebige Reihenfolge
    val todaySession: TodaySessionInput? = null,                 // heutige Einheit (Muskelgruppen)
    val muscleLoadHistory: List<MuscleLoadEntry> = emptyList(),  // Satzlast je Muskelgruppe/Einheit
    val muscleWindowStartEpochMillis: Long? = null,              // z.B. now - 7 Tage; Satzfenster
    val checkIn: DailyCheckInInput? = null,                      // optionaler Tagesform-Check-in
    val thresholds: ReadinessThresholds = ReadinessThresholds.DEFAULT,
)
```

* `sessions` ist absichtlich flach: eine Zeile = eine Uebung in einer Einheit.
  Die Sortierung uebernimmt der Kern deterministisch
  (`completedAtEpochMillis`, dann `orderingToken`, dann `sessionId`).
* Einheiten in der Zukunft (`completedAtEpochMillis > nowEpochMillis`) werden ignoriert.
* `muscleWindowStartEpochMillis == null` bedeutet: kein Volumenfenster, nur
  "letzte Belastung" und "heute".

### 2.2 `ExerciseSessionInput`

```kotlin
data class ExerciseSessionInput(
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String = "",
    val equipmentType: EquipmentType = EquipmentType.UNKNOWN,
    val completedAtEpochMillis: Long,
    val orderingToken: Long = 0L,
    val slotIndex: Int = 0,
    val slotChangedSincePrevious: Boolean = false,   // explizites Adapter-Signal
    val deloadState: DeloadState = DeloadState.NONE,
    val goal: ProgressionGoal = ProgressionGoal(),
    val sets: List<TrendSetInput> = emptyList(),
)
```

`equipmentType` steuert die Standard-Schrittweite (Langhantel vs. Kurzhantel vs.
Maschine/Koerpergewicht). Der Kern rechnet nicht mit "Langhantel" als Annahme.

### 2.3 `TrendSetInput` und `SetIntention`

```kotlin
data class TrendSetInput(
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,                          // null = fehlend, NICHT "gut" oder "schlecht"
    val setType: SetType = SetType.UNKNOWN,           // WIE der Satz geloggt wurde
    val intention: String = SetIntentionCodes.UNKNOWN, // WARUM er endete (kanonische Namen)
    val completedAtEpochMillis: Long = 0L,
    val orderingToken: Long = 0L,
    val id: Long = 0L,
)

enum class SetType { NORMAL, WARMUP, FAILURE, DROP_SET, UNKNOWN }

object SetIntentionCodes {
    const val PLANNED_FAILURE = "PLANNED_FAILURE"
    const val UNEXPECTED_TARGET_MISS = "UNEXPECTED_TARGET_MISS"
    const val UNKNOWN = "UNKNOWN"
    val ALL: Set<String>
    fun normalize(raw: String?): String   // unbekannt/null -> UNKNOWN, wirft nie
}
```

**Zwei getrennte Achsen.** `setType` beschreibt, *wie* ein Satz geloggt wurde;
`intention` beschreibt, *warum* er so endete. Beide duerfen nie ineinander
umgedeutet werden:

* `SetIntentionCodes` sind exakt die kanonischen Namen von
  `com.ironlog.shared.readinessdata.SetIntention`. Der Adapter uebergibt
  `SetIntention.name`. Es werden **keine** weiteren Intentionen erfunden; ein
  unbekannter String faellt fail-closed auf `UNKNOWN` zurueck.
* Ein historischer `SetType.FAILURE` bedeutet nur "bis zum Muskelversagen
  geloggt". Er traegt **keine** Intention und wird niemals als *geplantes*
  Muskelversagen gelesen. Nur `intention == PLANNED_FAILURE` ist neutral.
* Fehlende Absicht (`UNKNOWN`) ist wie ein Arbeitssatz behandelt, senkt die
  Confidence und erzeugt eine Datenqualitaets-Notiz. `UNEXPECTED_TARGET_MISS`
  wird als Reason `UNEXPECTED_TARGET_MISS_PRESENT` ausgewiesen, nicht erfunden.

### 2.4 `ProgressionGoal`

```kotlin
data class ProgressionGoal(
    val targetWeightKg: Double? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Double? = null,
    val weightIncrementKg: Double? = null,            // kleinste machbare Stufe fuer diese Uebung
    val scheme: ProgressionScheme = ProgressionScheme.UNKNOWN,
)

enum class ProgressionScheme { DOUBLE_PROGRESSION, LINEAR_LOAD, TOTAL_REPS, RPE_TARGET, MANUAL, UNKNOWN }
```

`weightIncrementKg` ist **immer explizit** vom Adapter konfiguriert; der Kern erfindet
**keine** Schrittweite pro Geraet. `null`/ungueltig bedeutet "Schritt unbekannt", dann
wird die Ziel-Reset-Erkennung uebersprungen und `WEIGHT_STEP_UNKNOWN` notiert.
Eine Kurzhantel-Reihe wie 4 / 5.5 / 7 / ... kg wird durch explizites `1.5` abgebildet,
nie durch eine Annahme.

### 2.5 Deload

```kotlin
enum class DeloadState { NONE, PLANNED_DELOAD, POST_DELOAD, UNKNOWN }
```

`PLANNED_DELOAD` und `POST_DELOAD` werden aus der Vergleichsreihe entfernt. Eine
absichtlich leichtere Deload-Einheit darf niemals als Ermuedigung oder
Leistungsabfall gelesen werden.

### 2.6 Tagesform-Check-in

```kotlin
data class DailyCheckInInput(
    val reportedAtEpochMillis: Long,
    val sleepQuality: Int? = null,   // 1..5, hoeher = besser
    val energy: Int? = null,         // 1..5, hoeher = besser
    val stress: Int? = null,         // 1..5, hoeher = schlechter
    val soreness: Int? = null,       // 1..5, hoeher = schlechter
)
```

Alle Felder optional. Es wird **kein** Gesamtscore gebildet.

Hinweis zur Anbindung: `readinessdata.ReadinessCheckIn` fuehrt Muskelkater **pro
Muskelgruppe** (`muscleSoreness`). Der Adapter mappt diese Werte in
`TodaySessionInput.sorenessByMuscle` bzw. `MuscleLoadEntry.soreness`. Das globale
`soreness`-Feld hier ist nur fuer Faelle ohne Muskelgruppen-Aufschluesselung und
wird nie als Ersatz erfunden.

### 2.7 Muskelgruppen

```kotlin
data class TodaySessionInput(
    val sessionId: Long,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val muscleGroups: List<String> = emptyList(),
    val sorenessByMuscle: Map<String, Int> = emptyMap(),
)

data class MuscleLoadEntry(
    val muscleGroup: String,
    val sessionId: Long,
    val completedAtEpochMillis: Long,
    val completedSets: Double,
    val soreness: Int? = null,
)
```

Muskelgruppen sind explizite `String`-Codes (Produktvokabular, z.B. `"BRUST"`),
damit derselbe Kern neben dem bestehenden Android-Vokabular funktioniert.

## 3. Ausgaben (Output)

```kotlin
data class ReadinessAssessment(
    val generatedAtEpochMillis: Long,
    val trainingTrend: TrainingTrendAssessment,      // mehrwoechiger Trend, eigener Index
    val dailyForm: DailyFormAssessment?,             // null ohne Check-in
    val muscleGroups: List<MuscleGroupContext>,      // Fakten, keine Erholung in Prozent
    val thresholdsRevision: Int,
)
```

### 3.1 `TrainingTrendAssessment`

```kotlin
data class TrainingTrendAssessment(
    val status: TrainingTrendStatus,
    val trainingIndex: Int?,                // 0..100, null bei geringer Evidenz
    val confidence: EvidenceConfidence,
    val deloadActive: Boolean,
    val deloadSuggested: Boolean,           // nur Vorschlag, nie eine Mutation
    val analyzedExerciseCount: Int,
    val exercisesWithSufficientHistory: Int,
    val repeatedDeclineExerciseCount: Int,
    val exercises: List<ExerciseTrend>,
    val dataQuality: TrendDataQuality,
    val reasons: List<ReadinessReason>,
    val thresholdsRevision: Int,
)

enum class TrainingTrendStatus {
    INSUFFICIENT_DATA,
    NO_NOTABLE_STRAIN,
    SINGLE_EXERCISE_DECLINE,
    MULTIPLE_EXERCISE_DECLINE,
}
```

`trainingIndex` ist ein **klar als heuristisch bezeichneter Trainingsindex**
(100 = keine wiederholte Verschlechterung erkannt, niedriger = mehr). Er ist
`null`, sobald die Evidenz unter `minExercisesForIndex` Uebungen oder
`minIndexEvidenceUnits` vergleichbare Einheiten faellt. Er enthaelt **keine**
Tagesform.

### 3.2 `ExerciseTrend`

```kotlin
data class ExerciseTrend(
    val exerciseId: Long,
    val exerciseName: String,
    val equipmentType: EquipmentType,
    val metricKind: TrendMetricKind,
    val status: ExerciseTrendStatus,
    val comparableUnitCount: Int,
    val latestMetricValue: Double?,
    val baselineMetricValue: Double?,       // eigene Basis, nicht Wochenbestwert
    val changePercent: Double?,
    val repeatedDeclineCount: Int,
    val confidence: EvidenceConfidence,
    val excludedFromFatigue: Boolean,
    val reasons: List<ReadinessReason>,
)

enum class ExerciseTrendStatus { IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA, EXCLUDED }
enum class TrendMetricKind { ESTIMATED_ONE_REP_MAX, GOAL_SCORE, TOTAL_REPS, NONE }
```

**Schemagerechter Vergleich je Einheit** (Revision 3):
* `GOAL_SCORE`: LINEAR_LOAD verwendet die durchschnittlich erreichte Last,
  anteilig reduziert bei fehlenden Mindestwiederholungen. Höhere Zielgewichte
  erzeugen keinen Quotientenabfall.
* `ESTIMATED_ONE_REP_MAX`: übrige gewichtete Schemata verwenden den Mittelwert
  aller Arbeitssätze, nur bei vergleichbaren Wiederholungsbereichen.
* `TOTAL_REPS`: das gleichnamige Schema sowie ungewichtete/Hochrep-Fallbacks
  verwenden die Summe aller Arbeitssatz-Wiederholungen. Geänderte reale Lasten
  beginnen eine neue Reihe.
* Änderungen von Schema, Wiederholungsziel oder RPE-Ziel beginnen eine neue Reihe.
* RPE verändert die Leistungsmetrik nicht. Fehlende RPE bleibt neutral.
* Das aktuelle Analysefenster umfasst standardmäßig 28 Tage.

Warmup-Saetze werden immer ausgeschlossen. Fehlende RPE wird neutral behandelt
(sie senkt die Confidence, niemals den Trendwert).

### 3.3 Tagesform

```kotlin
data class DailyFormAssessment(
    val coverage: Int,                      // 0..4 gelieferte Dimensionen
    val confidence: EvidenceConfidence,
    val hasAnyConcern: Boolean,
    val signals: List<DailyFormSignal>,
    val reasons: List<ReadinessReason>,
)

data class DailyFormSignal(
    val dimension: DailyFormDimension,
    val value: Int,
    val state: DailyFormState,
)

enum class DailyFormDimension { SLEEP_QUALITY, ENERGY, STRESS, SORENESS }
enum class DailyFormState { GOOD, NEUTRAL, CONCERN, UNKNOWN }
```

Der `state` beschreibt die Einordnung vollstaendig. Concern- und Coverage-Codes
liegen in `DailyFormAssessment.reasons` (z.B. `SLEEP_BELOW_THRESHOLD`,
`PARTIAL_CHECK_IN`). Es gibt **keinen** summierten Tagesform-Wert.

### 3.4 Muskelgruppen-Kontext

```kotlin
data class MuscleGroupContext(
    val muscleGroup: String,
    val lastTrainedEpochMillis: Long?,
    val setsInWindow: Double,               // Volumen im Adapter-Fenster (z.B. 7 Tage)
    val setsToday: Double,
    val soreness: Int?,
    val flags: List<MuscleGroupFlag>,
)

enum class MuscleGroupFlag {
    TRAINED_TODAY,
    HIGH_SORENESS,
    HIGH_RECENT_VOLUME,
    LOW_RECENT_VOLUME,
    NO_RECENT_LOAD,
}
```

Bewusst **keine** Erholungsprozentangabe und kein "Ready in X Stunden".

### 3.5 Datenqualitaet

```kotlin
data class TrendDataQuality(
    val comparableUnitCount: Int,
    val analyzedExerciseCount: Int,
    val exercisesWithSufficientHistory: Int,
    val insufficientDataExerciseCount: Int,
    val missingRpeSetCount: Int,
    val unknownIntentionSetCount: Int,
    val excludedDeloadUnitCount: Int,
    val notes: List<ReadinessDataQualityNote>,
)

enum class ReadinessDataQualityNote {
    MISSING_RPE_TREATED_NEUTRAL,
    UNKNOWN_SET_INTENTION,
    UNKNOWN_SET_TYPE,
    MIXED_EQUIPMENT_HISTORY,
    EXERCISE_SLOT_CHANGED,
    GOAL_RESET_DETECTED,
    WEIGHT_STEP_UNKNOWN,
    DELOAD_UNITS_EXCLUDED,
    INSUFFICIENT_HISTORY,
    NO_VALID_WORK_SETS,
}
```

## 4. Semantik der Trendentscheidung

1. **Gruppierung.** Zeilen werden nach `exerciseId` gruppiert, innerhalb dessen nach
   `equipmentType` (vergleichbare Linie). Analysiert wird die Linie mit der
   spaetesten Einheit. Gibt es mehrere Linien, notiert der Kern
   `MIXED_EQUIPMENT_HISTORY`.
   **Mehrere Zeilen derselben Uebung in einer Sitzung (z.B. zwei Slots) werden zu
   genau einer Trainingseinheit zusammengefasst** (`MULTIPLE_SLOTS_IN_SESSION`).
   Sie zaehlen nie als mehrere vergleichbare Einheiten.
2. **Deload-Ausschluss.** `PLANNED_DELOAD`/`POST_DELOAD`-Einheiten und Saetze mit
   `setType == WARMUP` werden aus der Reihe entfernt. `DELOAD_UNITS_EXCLUDED`
   dokumentiert das. `UNKNOWN`-setType bleibt Arbeitssatz (mit Notiz).
3. **Segmentierung.** Eine neue Serie beginnt bei einem absichtlichen Bruch:
   `slotChangedSincePrevious`, Wechsel von `equipmentType`, ein zielbedingter
   Lastrueckstieg unter die kleinste Schrittweite (`GOAL_CHANGED_RESET`) oder eine
   Luecke groesser `segmentationGapMillis` (z.B. langer Trainingsausfall).
   Brueche sind neutrale Resets, keine Verschlechterung.
   **Zusaetzlich bricht die Serie, wenn sich die Vergleichsgrundlage aendert:**
   anderer `metricKind` (`METRIC_KIND_CHANGED_RESET`), anderes Rep-Band
   (`REP_BAND_CHANGED_RESET`) oder andere Arbeitssatzanzahl
   (`SET_COUNT_CHANGED_RESET`). Diese Faelle werden nie direkt als
   Leistungsabfall gelesen. Notiz: `COMPARABILITY_BREAK`.
4. **Einheit pro Session.** Vergleichsmassstab aus Arbeitssaetzen der Session
   (siehe 3.2), schemaabhaengig.
5. **Robuste eigene Basis.** `baselineMetricValue` ist der **Median** der
   Vergleichswerte ausserhalb des letzten Fensters (`declineWindowUnits`). Gibt
   es weniger als drei Kandidaten, wird bewusst die **aelteste** Einheit genutzt,
   nie das Maximum. Ein einzelner Ausreisser wird so nicht zur Basis.
6. **Wiederholte unerwartete Verschlechterung.** Signifikant ist ein Rueckgang ab
   `notableDeclinePercent` gegenueber der Vor-Einheit. "Wiederholt" heisst: in den
   letzten `declineWindowUnits` Deltas sind mindestens `repeatedDeclineCount`
   signifikante Rueckgaenge. Standard: 2 von 2. Eine Einheit, deren Arbeitssaetze
   **ausnahmslos** die Intention `PLANNED_FAILURE` tragen, ist fuer den
   Decline-Test neutral (`PLANNED_FAILURE_UNIT_NEUTRAL`) - ein geplantes
   Muskelversagen ist keine unerwartete Verschlechterung.
7. **Mehrere Uebungen.** Erst ab `multiExerciseDeclineCount` Uebungen mit
   wiederholter Verschlechterung wird `MULTIPLE_EXERCISE_DECLINE` und ein
   Deload-Vorschlag ausgesprochen.
8. **Kein pauschales Ermuedungssignal.** Stagnation (`STAGNATION_NEUTRAL`),
   *geplantes* Muskelversagen (`intention == PLANNED_FAILURE`, Reason
   `PLANNED_FAILURE_NEUTRAL`) und Deload (`DELOAD_CONTEXT_NEUTRAL`) loesen allein
   **keine** Ermuedung aus. Sie werden als Reasons ausgewiesen, damit die
   Entscheidung auditierbar bleibt. Ein blosser `SetType.FAILURE` ist **kein**
   geplantes Versagen und erzeugt bewusst gar kein Neutralitaets-Signal.

## 5. Confidence

`EvidenceConfidence { NONE, LOW, MODERATE, HIGH }`.

Je Uebung:
* `NONE`: weniger als `minComparableUnits` vergleichbare Einheiten oder keine
  verwertbaren Arbeitssaetze.
* sonst Start `MODERATE`/`HIGH` nach Einheitenzahl; ein Schritt Abzug, wenn
  RPE-Abdeckung < `minRpeCoverageForConfidence` oder Unknown-Intention-Anteil >
  `maxUnknownIntentionRatioForConfidence`.

Gesamt:
* `HIGH`: >= 4 Uebungen mit Historie und >= 12 vergleichbare Einheiten.
* `MODERATE`: `trainingIndex` wurde berechnet.
* `LOW`/`NONE`: unter der Index-Evidenz.

**Wichtig:** Fehlende RPE allein macht die Historie nie unbrauchbar. RPE ist ein
Nebensignal; der Vergleichsmassstab kommt aus Gewicht und Wiederholungen. Eine
Uebung ohne RPE bleibt `STABLE`/`DECLINING`/`IMPROVING` und kann weiterhin den
`trainingIndex` tragen - sie verliert hoechstens eine Confidence-Stufe.

## 6. Garantien

* Pure Funktionen, keine Seiteneffekte, keine Persistenz, keine Uhrzeit.
* Determinismus: gleiche Eingabe -> gleiche Ausgabe; stabile Sortierung.
* Keine `java.time`/`kotlinx.datetime`-Typen in `commonMain`.
* Keine Planmutation; `deloadSuggested` ist nur ein Signal mit Begruendung.
* Fehlende oder unbekannte Daten degradieren zu `null`/`UNKNOWN` plus Note, nie zu
  einer stillen positiven Bewertung.
* Fehlende RPE, unbekannte Intention und unbekannter setType veraendern den
  Trendwert nicht; sie senken nur Confidence und erzeugen eine Notiz.

## 7. Versionierung

`ReadinessThresholds.revision` wird in `TrainingTrendAssessment` und
`ReadinessAssessment` gespiegelt. Neue Schwellen oder Semantikaenderungen erhoehen
die Revision und werden in `README.md` dokumentiert. Der Kern kennt keine
"stille" Schwellenaenderung.
