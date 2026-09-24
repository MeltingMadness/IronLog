# Readiness-Daten: Integration und migrationssicherer Plan

Stand: 11. September 2026. Dieses Dokument beschreibt die **portable Schicht** für
optionalen Tagesform-Check-in und Satzintention sowie den Plan, wie Android und iOS sie
später anbinden. Es ändert bewusst **kein** bestehendes Store-, Backup- oder DB-Schema.

## Zweck und Abgrenzung

Neu: Nutzer können freiwillig ihre Tagesform und die Absicht einzelner Sätze festhalten.

- **Tagesform-Check-in**: lokales Kalenderdatum plus optionale Selbsteinschätzung
  Schlafqualität, Energie und Stress auf Skala 1..5 sowie optional Muskelkater je
  bestehender Muskelgruppe auf Skala 1..5.
- **Satzintention**: warum ein Satz so endete - geplantes Versagen (`PLANNED_FAILURE`),
  unerwartetes Zielverfehlen (`UNEXPECTED_TARGET_MISS`) oder unbekannt (`UNKNOWN`).

Ausdrücklich **nicht** enthalten: Auto-Deload, Planmutationen, erzwungene Angaben,
HealthKit/Cloud, ein zeitloser "heute"-Check-in sowie jede Neuinterpretation von
`SetType.FAILURE`.

## Abgrenzung zu vorhandenen Konzepten

- `com.ironlog.shared.analytics.TrainingReadiness` / `TrainingReadinessScore` ist eine
  **abgeleitete Trainingsbereitschaft** aus Trainingsdaten (Fatigue-Score, Deload-Hinweis).
  Der neue Check-in ist eine **subjektive Nutzerangabe pro Tag** und unabhängig davon. Beide
  können nebeneinander existieren; die Tagesform ersetzt keine abgeleitete Kennzahl.
- Android `com.ironlog.app.domain.model.SetType.FAILURE` beschreibt, **wie** ein Satz geloggt
  wurde (bis zum Versagen ausgeführt). Die neue `SetIntention` beschreibt, **warum** er so
  endete. Beide Achsen sind orthogonal und werden nicht ineinander übersetzt.

## Neue portable Dateien (commonMain)

Alle unter `shared/src/commonMain/kotlin/com/ironlog/shared/readinessdata/`:

| Datei | Inhalt |
|---|---|
| `CONTRACT.md` | Eingefrorene Signaturen + Wire-Format (Grundlage für Plattformintegration) |
| `ReadinessDataModels.kt` | `ReadinessCheckIn`, Skalen-Grenzen, ISO-`LocalDate`-Serializer |
| `SetIntention.kt` | `SetIntention`, `SetIntentionRecord`, `SetIntentionSemantics` |
| `ReadinessData.kt` | Dokument-Wurzel `ReadinessData` + Versionskonstanten |
| `ReadinessDataValidator.kt` | `ReadinessDataValidator`, `ReadinessValidationResult` |
| `ReadinessDataCodec.kt` | `ReadinessDataCodec`, `ReadinessDataValidationException` |
| `ReadinessDataMerger.kt` | `ReadinessDataMerger`, Konflikttypen |

Eigenschaften: nur `kotlinx.serialization` + `kotlinx.datetime`, **kein `java.time`**, keine
Plattform-APIs, keine Kopplung an Room/DataStore/Backup/`SharedStateStore`. Damit ist die
Schicht direkt in `commonMain` testbar und für beide Clients identisch.

## Datenmodell

### Check-in

`ReadinessCheckIn(localDate, sleepQuality?, energy?, stress?, muscleSoreness, recordedAtEpochMillis?, updatedAtEpochMillis?)`

- `localDate` ist ein `kotlinx.datetime.LocalDate`, serialisiert als ISO `yyyy-MM-dd` über
  einen eigenen Serializer. Das Datum wird **immer explizit** gespeichert; es gibt keinen
  Default "heute".
- Alle Einschätzungen sind optional. `null` heißt "nicht beantwortet"; ein fehlender
  Muskelgruppen-Key heißt "nicht gemeldet". Beide Zustände bleiben nach Export/Import erhalten.
- Muskelkater nutzt die bestehenden zehn `com.ironlog.shared.model.MuscleGroup`-Werte
  (`BRUST` ... `WADEN`), sodass Android (`com.ironlog.app.domain.model.MuscleGroup`) und iOS
  denselben Wortschatz teilen.

### Satzintention

`SetIntentionRecord(setId, intention, recordedAtEpochMillis?, note)`, `intention` aus
`SetIntention { PLANNED_FAILURE, UNEXPECTED_TARGET_MISS, UNKNOWN }`.

- Ein Datensatz wird über die durable `setId` verknüpft. Fehlt ein Datensatz, ist die
  Intention `UNKNOWN` (`SetIntentionSemantics.intentionForMissingRecord()`).
- **Altbestand bleibt `UNKNOWN`**: vorhandene Sätze ohne Datensatz werden nie aus
  `SetType.FAILURE` oder fehlenden Wiederholungen abgeleitet. Die historische Intention wird
  als "unbekannt" erhalten, nicht geschätzt.

## Codec und Validierung (fail closed)

`ReadinessDataCodec` nutzt `Json { encodeDefaults = true; explicitNulls = true;
ignoreUnknownKeys = false }` und validiert **in beide Richtungen**:

- `encode` validiert zuerst, damit nie ungültige Daten geschrieben werden.
- `decode` parst und validiert; Fehler werden als `ReadinessDataValidationException` mit
  vollständiger, benannter Fehlerliste geworfen.
- Unbekannte Keys und eine **neuere** `schemaVersion` schlagen fehl, statt still gekürzt zu
  werden. `formatVersion != 1` und `schemaVersion <= 0` sind ebenfalls Fehler.
- Skalenwerte außerhalb 1..5 werden **nicht** geclamped/gerundet. Meldungen nennen Kontext und
  Wert, z. B. `Check-in 2026-09-11 field sleepQuality=7 is outside 1..5` oder
  `Check-in 2026-09-11 field muscleSoreness.WADEN=9 is outside 1..5`.
- Doppelte Check-in-Daten und doppelte/nicht-positive `setId`s werden abgelehnt.
- Fehlende Felder nutzen Defaults: ein Altbestand ohne `setIntention`-Liste dekodiert zu einer
  leeren Liste, also durchgängig `UNKNOWN`.

## Export-/Import-Semantik (vorbereitet)

`ReadinessDataMerger.merge(local, imported, importedWins = false)` ist deterministisch und
datenbewahrend:

- Beide Eingaben werden **vor** dem Merge validiert. Doppelte Schlüssel oder Werte außerhalb
  1..5 werden mit `ReadinessDataValidationException` abgelehnt (Fehler mit
  `local document:`/`imported document:`), damit das Zusammenführen per Schlüssel keine
  Dubletten still verliert.
- Check-ins: Schlüssel `localDate`, Vergleich auf völlige Gleichheit. Einseitig vorhandene Tage
  werden übernommen. Gleicher Tag mit irgendeiner Abweichung (auch nur der Zeitstempel) = Konflikt;
  Standard behält den lokalen Datensatz, `importedWins = true` kehrt das um.
- Satzintentionen: Schlüssel `setId`, Vergleich auf völlige Gleichheit des Records. Ein
  vollständig leerer `UNKNOWN`-Record (keine Note, kein Zeitstempel) verliert gegen einen
  bekannten Wert ohne Konflikt. Jede andere Abweichung - auch gleiche Intention mit
  unterschiedlicher `note`/`recordedAt` - wird als Konflikt gemeldet. Es geht keine Notiz
  unbemerkt verloren. Eine bekannte Intention wird auch bei `importedWins = true` nie durch
  `UNKNOWN` ersetzt; `importedWins` entscheidet nur zwischen zwei informativen Werten.
- Jeder Konflikt landet in `ReadinessMergeResult.conflicts` und wird gemeldet, nie still
  aufgelöst.

Das Dokument ist damit exportierbar (`encode`) und importierbar (`decode` + `merge`), ohne den
Trainingsgraphen oder dessen Backup-Schema zu berühren.

### Warnung: Satzintention ist an die SetID des Graphen gebunden

Satzintentionen referenzieren die rohe `setId` der Trainingsdatenbank. `setId`s sind **nur
innerhalb desselben Graphen** vergleichbar. Der Haupt-Backup-Import in IronLog **ersetzt den
gesamten Trainingsgraphen** (Room-Transaktion, kein freies Merge einzelner Records). Deshalb:

- `ReadinessDataMerger` darf **nicht** blind für den Haupt-Backup-Import oder zum Zusammenführen
  von Satzintentionen über unabhängige Datenbanken/Geräte hinweg verwendet werden - die IDs
  würden nicht dieselben Sätze bezeichnen.
- Zulässig ist der Merger nur für Dokumente **derselben** Lineage (z. B. lokaler Stand vs.
  eigener Export desselben Geräts) sowie für Check-ins, die über `localDate` datumsstabil sind.
- Für einen geräteübergreifenden Transfer muss die Plattform SetIDs neu auflösen (über
  Session/Übung/Satz-Position) oder das Readiness-Dokument getrennt übertragen; das ist eine
  Adapteraufgabe und nicht Teil dieser portablen Schicht.

## Konkrete Integrationsstellen (vom Orchestrator umzusetzen)

Die portable Schicht ist absichtlich nicht verdrahtet. Vorgesehene, getrennte Adapter:

### Android

- **Ablage (Entscheidung)**: eine Room-Single-Row-Tabelle mit dem kanonischen JSON-String
  (`ReadinessDataCodec.encode`) plus separater Import/Export in einer **atomaren Transaktion**.
  Kein Aufsplitten in mehrere Tabellen; der validierte JSON-String ist die Quelle der Wahrheit,
  Room speichert ihn nur. Neue Tabelle in `core/database` (`IronLogDatabase`, aktuell
  `version = 12`), Migration 12 -> 13 additiv mit `CREATE TABLE IF NOT EXISTS`.
- **Repository**: neues `ReadinessRepository` in `core/model` + Implementierung in `data`
  (analog `AppPreferencesRepositoryImpl`), das `ReadinessDataCodec`/`-Validator` nutzt.
- **Backup**: Erweiterung von `BackupPayloadV1` (shared) um ein optionales
  `readinessData: ReadinessData?`-Feld und passende Validierung. Backup-Schema 12 -> 13. Der
  Haupt-Backup-Import bleibt ein **vollständiger Graph-Ersatz**; siehe Scope-Warnung oben.
- **UI**: optionaler Check-in-Dialog vor/nach dem Training; Intention pro Satz im
  Training-Editor. Kein Zwang, keine Vorbelegung als Antwort.

### iOS

- **Ablage**: eigener versionierter JSON-String über den vorhandenen Musterpfad
  (`IOSSettingsViewModel`/`NSUserDefaults` oder eine eigene Datei unter
  `Application Support/IronLog/`, analog `training-state.json`).
- **Brücke**: neue Methode(n) in `IosTrainingFeature` oder ein eigenes `IosReadinessFeature`
  (`shared/src/iosMain`), die `ReadinessDataCodec`/`-Merger` exponieren.
- **Backup (Entscheidung)**: iOS setzt **BackupPayload-Schema 13 mit `readinessData`** und nutzt
  denselben `ReadinessDataCodec`; Schema-12-Backups importieren weiterhin (Feld fehlt -> leer,
  Intention `UNKNOWN`). Der Merger wird dabei nicht blind auf rohe SetIDs angewendet.
- **UI**: dieselbe optionale Check-in- und Intention-Interaktion wie Android.

## Migrationssicherer Plan

1. **Portable Schicht (dieser Schritt)**: Modelle, Codec, Validierung, Merger - ohne
   Store-/Schema-Berührung. Additiv, jederzeit entfernbar.
2. **Ablage additiv**: neue Tabellen bzw. neuer DataStore-/Datei-Eintrag mit
   `schemaVersion` 1. Kein Backfill: Altbestand ist leer, jede Satzintention ist `UNKNOWN`.
3. **Room-Migration 12 -> 13**: nur `CREATE TABLE IF NOT EXISTS` für die Single-Row-JSON-Tabelle;
   keine Änderung bestehender Tabellen, kein destruktiver Fallback. Schreiben/Lesen des JSON
   läuft in einer Transaktion. Downgrade-Risiko: die neue Tabelle bleibt erhalten, ältere
   App-Versionen ignorieren sie.
4. **Backup-Schema 12 -> 13**: neues Feld `readinessData` ist optional; Import von
   Schema-12-Backups ergibt `readinessData = null`/leer, `UNKNOWN` bleibt `UNKNOWN`. Ein
   Schema-13-Backup darf nicht als Schema-12 deklariert werden (ältere Versionen lehnen
   unbekannte Keys fail-closed ab) - dieselbe Additiv-Regel wie bei den bisherigen
   Schema-Sprüngen.
5. **Adapter**: Android zuerst (fachliche Referenz), dann iOS-Brücke, jeweils mit Roundtrip-
   Test gegen `ReadinessDataCodec`.
6. **Nicht anfassen**: `SharedStateStore`, bestehende `BACKUP_*`-Konstanten, bestehende
   `SetType`-Semantik, Progressions-/Deload-Pfade.

## Tests

`shared/src/commonTest/kotlin/com/ironlog/shared/readinessdata/`:

- `ReadinessDataCodecTest`: Roundtrip, ISO-Datum, Altbestand ohne Intention -> `UNKNOWN`,
  fail-closed bei Skalenwerten außerhalb 1..5, unbekannten Keys, kaputtem JSON, unparsbarem
  Datum, neuerer `schemaVersion` und ungültigem In-Memory-Dokument.
- `ReadinessDataValidatorTest`: Grenzen je Dimension, Muskelkater benennt die Gruppe,
  doppelte Daten/IDs, explizit `UNKNOWN` erlaubt, negative Zeitstempel.
- `ReadinessDataMergerTest`: einseitige Übernahme, Konflikterkennung mit lokaler Vorrangregel,
  `importedWins`, kein Downgrade bekannter Intentionen, Upgrade eines trivialen `UNKNOWN`,
  Konflikt zweier bekannter Werte, Konflikt bei gleicher Intention mit abweichender Note bzw.
  Zeitstempel, Ablehnung doppelter Schlüssel und ungültiger Werte vor dem Merge.

## Grenzen und offene Punkte

- Keine Plattformverdrahtung, kein Room-/Backup-Schema geändert - Integrationsadapter sind
  Aufgabe des Orchestrators.
- Keine UI, kein Auto-Deload, keine Planmutation.
- Keine Validierung gegen reale Nutzerdaten; nur gemeinsame gezielte Tests.
- Der Zeitbezug ("heute") wird ausschließlich von der Plattform geliefert und als `localDate`
  übergeben; die portable Schicht kennt keine Uhr.
