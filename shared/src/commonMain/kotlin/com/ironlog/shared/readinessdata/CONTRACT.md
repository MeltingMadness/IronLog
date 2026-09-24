# ReadinessData Contract (stabil, v1)

Paket: `com.ironlog.shared.readinessdata`
Status: **eingefroren für Plattformintegration (Schema 1)**. Rein portabel (commonMain, nur
`kotlinx.serialization` + `kotlinx.datetime`). Kein `java.time`, keine Plattform-APIs, keine
Kopplung an Room/DataStore/Backup/`SharedStateStore`.

## Harte Regeln

- **Lokales Datum explizit**: jeder Check-in trägt `localDate` (ISO `yyyy-MM-dd`). Es gibt
  keinen impliziten "heute"-Check-in.
- **Alles optional**: `null` = nicht beantwortet, fehlender Muskelgruppen-Key = nicht gemeldet.
  Keine erzwungenen Angaben, keine Defaults, die als Antwort missverstanden werden können.
- **Skala fix 1..5**: Werte außerhalb werden nie geclamped/gerundet. Validierung schlägt
  fail-closed fehl und benennt Feld + Wert.
- **Historische Satzintention = `UNKNOWN`**: fehlende Intention-Records lösen immer zu
  `SetIntention.UNKNOWN` auf. `SetType.FAILURE` (Android) wird **nicht** umgedeutet.
- **Kein Auto-Deload, keine Planmutation**: dieses Modul berechnet und ändert nichts.
- **Datenbewahrend**: Export/Import kopiert Werte unverändert; Konflikte werden gemeldet,
  nicht still aufgelöst.

## Typen (Signaturen)

```kotlin
const val READINESS_SCALE_MIN = 1
const val READINESS_SCALE_MAX = 5
const val READINESS_DATA_FORMAT_VERSION = 1
const val CURRENT_READINESS_DATA_SCHEMA_VERSION = 1

object ReadinessLocalDateSerializer : KSerializer<LocalDate>   // ISO-8601 String

@Serializable
data class ReadinessCheckIn(
    val localDate: LocalDate,                       // @Serializable(with = ReadinessLocalDateSerializer)
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val muscleSoreness: Map<MuscleGroup, Int> = emptyMap(),
    val recordedAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
) {
    fun hasAnyAnswer(): Boolean
}

@Serializable
enum class SetIntention { PLANNED_FAILURE, UNEXPECTED_TARGET_MISS, UNKNOWN }

@Serializable
data class SetIntentionRecord(
    val setId: Long,
    val intention: SetIntention,
    val recordedAtEpochMillis: Long? = null,
    val note: String = "",
)

@Serializable
data class ReadinessData(
    val formatVersion: Int = READINESS_DATA_FORMAT_VERSION,
    val schemaVersion: Int = CURRENT_READINESS_DATA_SCHEMA_VERSION,
    val checkIns: List<ReadinessCheckIn> = emptyList(),
    val setIntentions: List<SetIntentionRecord> = emptyList(),
)

enum class ReadinessMergeConflictKind { CHECK_IN, SET_INTENTION }

data class ReadinessMergeConflict(
    val kind: ReadinessMergeConflictKind,
    val key: String,
    val localValue: String,
    val importedValue: String,
    val resolvedValue: String,
)

data class ReadinessMergeResult(
    val data: ReadinessData,
    val conflicts: List<ReadinessMergeConflict>,
)

data class ReadinessValidationResult(val isValid: Boolean, val errors: List<String>)

class ReadinessDataValidationException(val errors: List<String>) : IllegalArgumentException
```

## Objekte (Signaturen)

```kotlin
enum class SetIntention {                      // companion object
    PLANNED_FAILURE, UNEXPECTED_TARGET_MISS, UNKNOWN;
    companion object {
        val LEGACY_DEFAULT: SetIntention = UNKNOWN
        fun safeValueOf(name: String?): SetIntention   // wirft nie; unbekannt -> UNKNOWN
    }
}

object SetIntentionSemantics {
    fun intentionForMissingRecord(): SetIntention                                  // -> UNKNOWN
    fun intentionFor(setId: Long, records: List<SetIntentionRecord>): SetIntention // fehlend -> UNKNOWN
}

object ReadinessDataValidator {
    fun validate(
        data: ReadinessData,
        currentSchemaVersion: Int = CURRENT_READINESS_DATA_SCHEMA_VERSION,
    ): ReadinessValidationResult
    fun isValidScale(value: Int): Boolean           // value in 1..5
}

object ReadinessDataCodec {
    fun encode(data: ReadinessData): String         // validiert zuerst, wirft bei ungültig
    fun decode(serialized: String): ReadinessData   // parst + validiert, fail closed
    fun validateOrThrow(data: ReadinessData)
}

object ReadinessDataMerger {
    fun merge(
        local: ReadinessData,
        imported: ReadinessData,
        importedWins: Boolean = false,              // Default: lokaler Datensatz bleibt erhalten
    ): ReadinessMergeResult                          // validiert beide Eingaben zuerst, sonst ReadinessDataValidationException
}
```

## Wire-Format (Beispiel, Schema 1)

```json
{
  "formatVersion": 1,
  "schemaVersion": 1,
  "checkIns": [
    {
      "localDate": "2026-09-11",
      "sleepQuality": 4,
      "energy": 3,
      "muscleSoreness": { "BEINE": 5, "RUECKEN": 1 },
      "recordedAtEpochMillis": 1757500000000
    }
  ],
  "setIntentions": [
    { "setId": 42, "intention": "PLANNED_FAILURE" },
    { "setId": 43, "intention": "UNEXPECTED_TARGET_MISS" }
  ]
}
```

- `Json`: `encodeDefaults = true`, `explicitNulls = true`, `ignoreUnknownKeys = false`.
  Unbekannte Keys und neuere `schemaVersion` schlagen fail-closed fehl.
- Fehlende Felder nutzen Defaults: ein Altbestand ohne `setIntentions` dekodiert zu
  `setIntentions = []`, d. h. jede Satzintention ist `UNKNOWN`.

## Merge-Semantik (Import)

- **Vor dem Merge** werden `local` und `imported` vollständig validiert. Doppelte Schlüssel oder
  Werte außerhalb 1..5 werden mit `ReadinessDataValidationException` abgelehnt (Fehler sind mit
  `local document:`/`imported document:` gekennzeichnet), damit `associateBy` keine Dubletten
  still verliert.
- Check-ins: Schlüssel `localDate`, Vergleich auf **völlige Gleichheit**. Einseitig vorhandene Tage
  werden übernommen; gleicher Tag mit irgendeiner Abweichung (auch nur Zeitstempel) = Konflikt,
  Standard behält lokal (`importedWins = true` kehrt um).
- Satzintentionen: Schlüssel `setId`, Vergleich auf **völlige Gleichheit** des Records.
  Ein vollständig leerer `UNKNOWN`-Record (keine Note, kein Zeitstempel) trägt keine Information
  und verliert gegen einen bekannten Wert (reines Informations-Upgrade, kein Konflikt). Jede
  andere Abweichung - auch gleiche Intention mit unterschiedlicher `note`/`recordedAt` - wird als
  Konflikt gemeldet; Standard behält lokal. Es geht keine Notiz unbemerkt verloren. Eine bekannte
  Intention wird **auch bei `importedWins = true`** nie durch `UNKNOWN` ersetzt (sonst Datenverlust);
  `importedWins` entscheidet nur zwischen zwei informativen Werten.
- Konflikte werden in `ReadinessMergeResult.conflicts` gemeldet, nie still aufgelöst.

### Scope-Warnung (wichtig für Integration)

Satzintentionen sind über die rohe `setId` des Trainingsgraphen verknüpft. Nur Dokumente
**desselben** Graphen (gleiche Geräte-/Backup-Lineage) dürfen gemerged werden. Der Haupt-Backup-Import
**ersetzt den gesamten Trainingsgraphen**, er merged keine einzelnen Records; dieser Merger darf
deshalb nicht verwendet werden, um Satzintentionen über unabhängige Datenbanken hinweg
zusammenzuführen - die IDs würden nicht dieselben Sätze bezeichnen.

## Integration

Siehe `docs/readiness-data-integration.md` für Android-/iOS-Anbindung, versionierte Ablage und
den migrationssicheren Plan. Plattformadapter und Room-/Backup-Schema-Änderungen liegen beim
Orchestrator.
