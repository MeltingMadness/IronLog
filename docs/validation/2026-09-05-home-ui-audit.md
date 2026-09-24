# UI-Audit Startseite – 5. September 2026

## Umfang und Evidenz

Ausgangspunkt: installierte Version 1.1.0 (Code 2), physisches Android-Gerät 24090RA29G, 1220 × 2712 Pixel, Dichte 520 dpi, Systemschrift 100 %. Prüfung der Startseite anhand von vier Screenshots, UI-Hierarchie und Compose-Code. Keine Aussage über vollständige App-Abdeckung oder größere Schriftstufen.

Private Geräteaufnahmen liegen außerhalb des Repositories unter `/Users/maert/Documents/IronLog-Analysen/2026-09-05/ui-audit/`. Eine unabhängige Luna-Max-Codeprüfung ergänzte die Geräteprüfung.

## Befunde und Bearbeitungsstand

| Priorität | Befund | Evidenz | Stand im Kandidaten 1.1.1 |
| --- | --- | --- | --- |
| P1 | Texte der Belastungsanalyse überlagern sich bereits bei normaler Schrift. Material3 Surface hatte zwei direkte Kinder, die übereinander lagen. | 01-home-top.png; DashboardScreen TrainingLoadBentoCard | Gemeinsame Column und volle Kartenbreite implementiert. |
| P2 | Zweispaltige Statuskarten lassen zu wenig Platz für deutsche Texte und Trainingszahlen. | 01-home-top.png | Belastungsanalyse volle Breite; Trainingszahlen in kompakter eigener Zeile. |
| P2 | MEV/MAV-Markierungen stehen links statt an ihrer berechneten Position. | 03-weekly-bottom.png; WeeklyMuscleVolumeCard Marker-Layout | Ausrichtung am rechten Ende des anteilig breiten Containers korrigiert. |
| P2 | Ausgeklappte Muskelgruppenkarte beansprucht viel Scrollfläche. | 02-weekly-volume.png und 03-weekly-bottom.png; Nutzerwunsch | Dashboard standardmäßig eingeklappt; Wochenwahl und Zusammenfassung sichtbar; mindestens 48 dp hohe Schaltfläche, gespeicherter Klappzustand und zugängliche Zustandsbeschreibung. |
| P2 | Alte Muskelgruppen-Chips und neue Wochenkarte zählen sekundäre Muskeln verschieden, ohne den Unterschied zu erklären. Beispielsweise Bizeps 9 versus 6 Sätze. | 03-weekly-bottom.png; DashboardViewModel zählt sekundär 1, MuscleVolumeCalculator 0,5 | Behoben: zusätzliche Heatmap entfernt; die gewichtete Wochenkarte ist die zentrale Muskelübersicht. |
| P2 | Trenddiagramm „8 Wochen“ zeigt Indexzahlen 0–4 statt Kalenderwochen und lässt Wochen ohne Sätze aus. | 04-home-bottom.png; WeeklyVolumeCard und DashboardViewModel | Behoben: acht chronologische, am eingestellten Wochenbeginn ausgerichtete Bins inklusive Nullwochen; Datumsbeschriftung und Einheit kg × Wiederholungen. |

Die feste Höhe der Rekordkarten wurde durch eine Mindesthöhe mit mitwachsendem Inhalt ersetzt. Abschneiden war bei 100 % nicht bestätigt; größere Systemschrift bleibt ein nicht separat geprüfter Grenzfall. Für Navigation und unteren Bildschirmabstand wurde auf den geprüften Aufnahmen kein eigener Fehler bestätigt.

## Abnahme und Grenzen

Erforderlich für die visuelle Abnahme: lesbare Belastungskarte ohne Überlagerung, initial kompakte Wochenkarte, funktionierendes Ein-/Ausklappen und Wochenwechsel, korrekt positionierte Schwellenmarkierungen. Datenberechnung und Trainingsdaten werden durch diese UI-Korrekturen nicht verändert. Der Statistikbildschirm bleibt standardmäßig ausgeklappt.

Die Gerätebedienung wurde pausiert, nachdem eine andere App in den Vordergrund wechselte. Eine abschließende Prüfung und Installation von 1.1.1 stehen daher noch aus. Ergebnisse früherer Builds und Tests sind keine Validierung dieser neuen Änderungen.

### Lokale Validierung abgeschlossen

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon :app:assembleRelease`: **BUILD SUCCESSFUL**, inklusive `lintVitalRelease` (58 Sekunden).
- Im ersten Durchlauf fehlte der Import der Compose-Semantik `role`; ergänzt und derselbe Build erfolgreich wiederholt.
- `git diff --check`: bestanden.
- APK: Version **1.1.1**, Code **3**, SHA-256 `2cb0c53f841fc1262d6d47ce530961beaba63899a3b3e2e7bd8beebec92c51fd`.
- APK-Signatur erfolgreich geprüft; Zertifikat SHA-256 `69593f52950e1e081630bbc09b37800bd4a26ce03b84800cf8529196f6292a41` entspricht dem zuvor geprüften Installationszertifikat.
- Keine neue Installation, keine abschließende visuelle Abnahme und keine Instrumentierungstests durchgeführt. Keine neuen breiten Testläufe für diese Layoutänderung.

## Ergänzung nach Auftrag zur vollständigen Behebung

Die beiden offenen bestätigten Befunde sind nun implementiert. Trendtests prüfen acht Einträge, Nullwochen, Datumslabels, Fenstergrenzen und den Sonntag als Wochenbeginn. Ein anfänglicher Fehler in den erwarteten Testvolumina wurde korrigiert (Fixture: 50 kg × 10 Wiederholungen pro Satz). Ein eigener Jahreswechsel-Test wurde nicht ergänzt; die Implementierung verwendet vollständige LocalDate-Wochenanfänge statt KW-Nummern. Die oben dokumentierte erste APK ist durch den folgenden abschließenden Build überholt.

### Abschließende Prüfung der Auditkorrekturen

- 42 DashboardViewModel-Tests, 0 Fehler und 0 Fehlschläge; anschließender Release-Build erfolgreich.
- Installation von 1.1.1 (Code 3) mit `adb install -r`: Success; erste Installation weiterhin 24.03.2026, 20:18:29. Installierte APK entsprach dem Build, keine IronLog-Einträge im geprüften Crash-Puffer.
- Auf dem entsperrten Gerät bestätigt: Belastungsanalyse ohne Überlagerung (06-fixed-home), initial eingeklappte Karte (07-collapsed), Aufklappen und korrekt positionierte MEV/MAV-Markierungen (08-expanded), Wechsel in leere Vorwoche bei erhaltenem Klappzustand (09-previous-expanded), Rückkehr und Einklappen sowie datumsbeschrifteter Trend mit Nullwochen und ohne zusätzliche Heatmap (10-trend).
- Nachträglicher Nutzerwunsch: Statuskreis wieder ergänzt. Er zeigt einen echten Belastungsscore nur bei vorhandenem Score; andernfalls ein Statussymbol statt einer erfundenen Readiness-Prozentzahl. Dieser rein visuelle Nachtrag erhält einen erneuten Release-Build und eine erneute Sichtprüfung; kein erneuter Lauf der unveränderten Berechnungstests erforderlich.

### Finaler Stand mit Statuskreis

Erneuter Release-Build erfolgreich (56 s), signiertes Update erfolgreich installiert. Kaltstart erfolgreich (533 ms). Statuskreis mit Häkchen und unverdeckten Texten auf Gerät bestätigt (`11-final-status-ring.png`). Installierte und lokale APK SHA-256: `e5dad0116d376a601b2e3a7c82e07582cd81c371387df83144a84c90fc7e2681`. Erste Installationszeit weiterhin erhalten; keine IronLog-Crashmarker im geprüften Puffer. Alle bestätigten Befunde dieses Startseiten-Audits sind behoben. Große Systemschrift und andere Seiten wurden nicht umfassend geprüft.
