# Logging-Richtlinie (IronLog)

## Ziel
- Nutzerdaten sicher loggen, ohne personenbezogene oder sensible Trainingsdaten offenzulegen.
- Ein konsistentes Logging-Verhalten fuer Debugging und Betrieb sicherstellen.

## Regeln
- Verwende `AppLogger` statt direkter `android.util.Log`-Aufrufe.
- Keine PII in Logs: keine Freitext-Notizen, keine Roh-IDs in `key=value`-Form.
- Fehlermeldungen fuer Nutzer in ViewModels ueber zentrales Error-Mapping, nicht per Exception-String.
- `debug`-Logs nur in Debug-Builds.
- `error`/`warn` nur mit technischem Kontext, niemals mit kompletten Datensaetzen.

## Log-Level
- `d`: kurzlebige Entwicklungsinfos (nur Debug).
- `i`: wichtige Betriebsereignisse ohne sensible Inhalte.
- `w`: recoverable Fehler / degradierte Pfade.
- `e`: nicht erwartete Fehler mit Stacktrace.

## Review-Checkliste
- Enthalten Logs rohe IDs oder Freitext aus Nutzerfeldern?
- Gibt es direkte `Log.*`-Nutzung statt `AppLogger`?
- Ist der Kontext ausreichend, aber nicht oversharing?
