# Lokales Geräteupdate vom 5. September 2026

Vom Benutzer ausdrücklich nach Kenntnis der lokalen Prüfung und ausstehenden CI-/Geräteprüfung beauftragt. Installation des lokalen Entwicklungskandidaten; keine Veröffentlichung, kein Commit, Push oder Merge. Frühere grüne CI-Läufe gehören zu anderen Commits.

- Signierter Release-Build mit JDK 17: `:app:assembleRelease`, BUILD SUCCESSFUL in 1m 17s.
- Version 1.0 / Code 1 auf Version 1.1.0 / Code 2 aktualisiert; `adb install -r` meldet Success.
- Release- und installierte Zertifikat-SHA256 stimmen überein: `69593f52950e1e081630bbc09b37800bd4a26ce03b84800cf8529196f6292a41`.
- Installierte APK-SHA256 entspricht dem lokalen Release: `cb62b508dcc981f2d5263ac6767b610debdfeb4dac781ed51606cc7f74194fa5`.
- Ursprüngliches Installationsdatum unverändert: 24.03.2026 20:18:29. Updatezeit: 05.09.2026 22:29:16.
- Kaltstart erfolgreich, Status ok, 422 ms. Keine Crash-Marker seit dem Update; Prozess läuft.
- Wochenkarte auf dem echten Gerät sichtbar und per Screenshot geprüft: drei Trainings, 43,5 gewichtete Muskelbeiträge; Brust/Rücken/Beine je 6, Schultern 7,5. Das ist keine vollständige visuelle QA der gesamten App.
- Export nach Update erfolgreich (Schema 12 / App 1.1.0). Alle IDs und bisherigen exportierten Datenfelder stimmen mit dem Export vor dem Update überein: 84 Übungen, 30 Einheiten, 420 Sätze, 6 Trainingspläne, 22 Planübungen, 92 Rekorde, 1 Metaplan, 6 Metaplaneinträge, 3 Auslassungen, 23 Ziel-Snapshots und 23 Progressionsauswertungen.
- Alle 420 Sätze sind im neuen Export ausdrücklich NORMAL. Die frühere Unsicherheit über den Satztyp ist damit für diesen Datenbestand aufgelöst. Keine Progressionswerte geändert und keine historischen Entscheidungen überschrieben.
- Sicherungen und Vergleichsnachweis liegen privat außerhalb des Git-Repositories unter `/Users/maert/Documents/IronLog-Analysen/2026-09-05/`.

Kein vollständiger Instrumentation-/Backup-Restore-Lauf auf dem Benutzergerät. Kein CI-geprüftes Release dieses Arbeitsstands; die vorhandenen gezielten Tests und Geräteprüfungen bleiben begrenzte Nachweise.
