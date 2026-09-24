# Progressionskorrekturen – 09.09.2026

## Verhalten

- Linear, Double, Total Reps und RPE/RIR nutzen bei konsistenten normalen Arbeitssätzen die tatsächlich trainierte Last. Beim Wiederholen mit abweichender Planbasis entsteht ein bestätigungspflichtiger Vorschlag zur Gewichtsübernahme; der historische Quell-Snapshot bleibt unverändert.
- Double erhöht die Last, sobald alle gewerteten Sätze die Obergrenze schaffen. Wiederholt zu hohe RPE nutzt die konfigurierte Entlastungsschwelle.
- Fehlerserien werden bei geänderter oder unbekannter tatsächlicher Last unterbrochen. Vorherige Auswertungen müssen passende Quellen und vollständige, geordnete Satzevidenz besitzen.
- Alte unbearbeitete INFORMATIONAL-Ergebnisse mit Nullgewicht als Planbasis (MANUAL_WEIGHT_DEVIATION und alte REPEAT_TARGET ohne tatsächliche Gewichtsgrundlage) werden bei eindeutiger positiver Satzevidenz in der bestehenden Transaktion neu bewertet. ID, Quelle, Satzbelege und Erstellungszeit bleiben erhalten; berechnetes Ergebnis und gegebenenfalls Status werden aktualisiert. Die ursprüngliche abgeleitete Fehlermeldung wird dabei ersetzt, nicht der Trainingsbeleg. Bereits entschiedene, offene PENDING-, STALE- oder bearbeitete Ergebnisse bleiben unverändert. Es erfolgt keine automatische Planübernahme.
- Review trennt trainierte Last, damaliges Planziel und vorgeschlagenes Gewicht. Unveränderte historische Gewichtsvergleiche werden nicht als echte Mischgewichte ausgegeben.
- Schema-Auswahl erklärt „Gewicht steigern“ (feste Wiederholungen) und „Wiederholungen, dann Gewicht“.

## Kurzhanteln

Vom Nutzer bestätigte Reihe: 4 / 5,5 / 7 / 8,5 / 10 kg, weiter in Schritten von 1,5 kg. Die Berechnung addiert die konfigurierte Schrittweite zur trainierten Last statt auf ein Raster ab null zu runden. Entlastungen verwenden ganze Schritte relativ zur trainierten Last; Beispiel 10 → 8,5 kg bei 1,5 kg Schrittweite. Das ist in allen vier Schemata geprüft.

Die Schrittweite wird pro Übung konfiguriert; bestehende persönliche Pläne auf dem Handy wurden nicht bearbeitet. Für diese Kurzhanteln ist 1,5 kg einzutragen. Die ungefähre Obergrenze von 43 kg ist kein fest hinterlegtes Inventarlimit. Auch eine Mindesthantelgrenze ist nicht Teil der bisherigen Konfiguration; das Modell kennt Schrittweite und die allgemeine Untergrenze 0 kg. NORMAL-Satz-Auswahl und das Ausschließen zusätzlicher/Drop-/Failure-Sätze bleiben unverändert.

## Validierung

- ProgressionEngineTest: 41 Tests erfolgreich.
- ProgressionRepositoryImplTest: 50 Tests erfolgreich, einschließlich echter Engine-Integration für Legacy-Reparatur, Idempotenz, alten Repeat, Entscheidungsschutz und geänderte Trainingslast.
- ProgressionReviewViewModelTest: 26 Tests erfolgreich.
- ProgressionWeightEvidenceTest: 4 Tests erfolgreich; echte 0 kg und fehlende Daten bleiben unterscheidbar, Mischgewichte werden nicht als alter Planvergleich umgedeutet.
- Insgesamt 121 gezielte Tests, keine Fehler oder Fehlschläge.
- Die echten Room-Recovery-Queries wurden zusätzlich mit dem exportierten Schema in SQLite gegen eine Auswahlmatrix geprüft (`python3 docs/validation/check_progression_recovery_query.py`).
- `:app:assembleRelease` einschließlich lintVitalRelease erfolgreich. Der abschließende Lauf nach Kurzhantelkorrektur dauerte 52 Sekunden. `git diff --check` erfolgreich.
- Ein neuer Testfixture-Aufruf musste um seine Zielsumme ergänzt werden; zwei frühere Tests erwarteten ausdrücklich das nun korrigierte Nullraster und wurden auf relative Gewichtsschritte angepasst.
- Keine Geräteprüfung oder Installation in diesem Durchlauf. Kein vollständiger CI-/Instrumentierungslauf. Die zuvor lokal korrigierten Pausentimer sind im Build enthalten; ihre frühere Validierung steht in 2026-09-08-rest-timer.md.

## Artefakt

Version 1.1.2, versionCode 4, signierte APK. SHA-256: `4d939426b2674ebdd4cbd7e65be445035212e14b3c9ec37c2735b54f1acf8ea4`.

Lokales separates Artefakt: `/Users/maert/Documents/IronLog-Analysen/2026-09-09/ironlog-1.1.2-progression.apk`.

## Geräteinstallation anschließend abgeschlossen

Version 1.1.2 wurde per WLAN-ADB als signaturgleiches Update installiert (Success). Installierte APK entspricht SHA-256 oben; erste Installationszeit 24.03.2026 20:18:29 erhalten. Kaltstart erfolgreich in 474 ms. Im geprüften App-Startprotokoll keine Fatal-/SQLite-/Progression-Recovery-Fehlermarker. Das ist keine vollständige inhaltliche Prüfung aller reparierten Auswertungen. Details privat unter `/Users/maert/Documents/IronLog-Analysen/2026-09-09/install/verification.json`. Persönliche Kurzhantel-Schrittweiten wurden nicht verändert.
