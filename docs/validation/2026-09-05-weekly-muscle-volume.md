# Wochenübersicht der Muskelgruppen – ergänzende Validierung

Stand: 5. September 2026. Ergänzung des lokalen Entwicklungskandidaten 1.1.0 / Code 2. Zum Zeitpunkt dieser Prüfrunde noch nicht auf dem Handy installiert. Das anschließend beauftragte Update und der Datenvergleich sind in `2026-09-05-device-update.md` dokumentiert.

## Umsetzung

- Referenz: `mockups/index.html`, Karte „Wöchentliches Muskelvolumen (MEV / MAV)“ ab Zeile 1460.
- Vollständige Karte auf dem Dashboard, unabhängig von Onboarding/Rekorden: alle zehn Muskelgruppen, auch ohne erfasste Sätze.
- Kalenderwochen mit konkretem Datumsbereich; vorherige/nächste Woche, keine Navigation in zukünftige Wochen. Eingestellter Wochenbeginn Montag/Sonntag wird berücksichtigt. Laufende Woche ausdrücklich gekennzeichnet.
- Bestehende Berechnung: primäre Muskelgruppe 1,0, sekundäre 0,5; NORMAL/DROP_SET/FAILURE zählen, WARMUP nicht. Nur Sätze abgeschlossener Trainings. Die Summe der gewichteten Muskelbeiträge kann höher sein als die Anzahl protokollierter Sätze.
- MEV/MAV/MRV bleiben vorhandene Orientierungsbereiche. Keine garantierte individuelle Optimalität; Nullwerte werden neutral dargestellt.
- Separate Lade-/Fehlerzustände und Wiederholen-Aktion; fehlende Übungszuordnungen werden als unvollständige Daten behandelt. Neuere Wochenwahl kann nicht von älteren Abfragen überschrieben werden; Dashboard-Refresh und Wochenwahl haben getrennte Anfrageschutz-Zähler.
- Gemeinsame Karte nach `core/designsystem` verschoben; Übungsstatistik nutzt dieselbe Darstellung.
- `WorkoutSetDao.getWorkSetsCompletedSince` filtert nach Satzabschluss statt Trainingsbeginn. Dadurch zählen nach dem Wochenwechsel abgeschlossene Sätze auch aus zuvor gestarteten Trainings.

## Prüfung

Finaler gezielter Gradle-Lauf mit JDK 17: erfolgreich in 12 Sekunden.

- `DashboardViewModelTest`: 41 Tests, keine Fehler, keine übersprungenen Tests. Neun neue Fälle prüfen die Wochenübersicht einschließlich Gewichtung/Satzarten/Grenzen, Nullgruppen, Navigation/Refresh, Sonntag, Fehler/Retry, fehlende Übungszuordnung und konkurrierende Ladeanfragen.
- `ExerciseStatsViewModelTest`: 7 Tests, keine Fehler, keine übersprungenen Tests.
- `:app:assembleDebug` erfolgreich; `:app:compileDebugAndroidTestKotlin` erfolgreich. Letzteres kompiliert auch den ergänzten Room-Test für Training über den Wochenwechsel, führt ihn aber nicht aus.
- Die tatsächliche DAO-SQL-Abfrage wurde zusätzlich in SQLite mit Grenzwert, früherem Trainingsbeginn, aktiver Session, Aufwärm-/Drop-/Versagenssatz geprüft: erwartete Datensätze ausgewählt.
- Neue XML-Ressourcen gültig; `git diff --check` sauber.
- Ein erster Lauf hatte einen Fehler im neuen Preferences-Ausfalltest: Android-Logger war in der JVM nicht gemockt. Nach Anpassung des Testdoubles war die gezielte Auswahl vollständig grün.

Keine vollständige Testsuite, kein vollständiger Lintlauf und keine visuelle Geräteprüfung der neuen Karte. Die 382 Tests des vorherigen Berichts sind eine frühere Prüfrunde; sie werden nicht als erneuter vollständiger Nachweis des ergänzten Kandidaten dargestellt.

## Artefakt

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 25.322.787 Bytes.
- SHA-256: `b3109f77dfa64c0e68d0d56be8908a62ea4e6b00ac08036f1a7619848ce82e3d`.
- Aktualisierte Quellprüfsummen: `2026-09-05-weekly-candidate.sha256`.
- Laufprotokoll: `/tmp/ironlog-weekly-volume-validation.log`.

## Handy und Datenanalyse

Das Benutzergerät wurde per WLAN-ADB gekoppelt. Installiert bleibt Version 1.0 / Code 1. Über den regulären Exportdialog wurde eine neue Sicherung erzeugt und lokal außerhalb des Repositories abgelegt. Es wurden keine Trainingsdaten, Planwerte oder gespeicherten Progressionsentscheidungen verändert; kein Update, keine Deinstallation und kein Datenbank-Reset.

Der Export enthält nur das alte Satztypformat `isWarmup`. Für das Wochenvolumen reicht dies, da alle Nicht-Aufwärmsatzarten gleich zählen. Für eine exakte Progressions-Neuberechnung fehlt die Unterscheidung normaler Sätze von Drop-/Versagenssätzen. Die separate private Analyse kennzeichnet diese Grenze; ihre Regelhinweise sind aus Quellcode und Daten abgeleitet, kein ausgeführter Kotlin-Engine-Replay.
