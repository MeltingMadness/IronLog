# Logging-Richtlinie (Thrym)

## Ziel
- Nutzerdaten sicher loggen, ohne personenbezogene oder sensible Trainingsdaten offenzulegen.
- Ein konsistentes Logging-Verhalten für Debugging und Betrieb sicherstellen.

## Regeln
- Verwende `AppLogger` statt direkter `android.util.Log`-Aufrufe.
- Keine PII in Logs: keine Freitext-Notizen, keine Roh-IDs in `key=value`-Form.
- Log-Meldungen sind technische Texte für Entwickler. Sie stehen nicht in String-Ressourcen und werden nicht übersetzt.
- Fehlermeldungen für Nutzer in ViewModels über zentrales Error-Mapping (`ErrorUiMapper` → String-Ressourcen in Englisch und Deutsch), nicht per Exception-String.
- `debug`-Logs nur in Debug-Builds.
- `error`/`warn` nur mit technischem Kontext, niemals mit kompletten Datensätzen.

## Log-Level
- `d`: kurzlebige Entwicklungsinfos (nur Debug).
- `i`: wichtige Betriebsereignisse ohne sensible Inhalte.
- `w`: behebbare Fehler / degradierte Pfade.
- `e`: nicht erwartete Fehler mit Stacktrace.

## Review-Checkliste
- Enthalten Logs rohe IDs oder Freitext aus Nutzerfeldern?
- Gibt es direkte `Log.*`-Nutzung statt `AppLogger`?
- Ist der Kontext ausreichend, ohne zu viel preiszugeben?
