# Änderungen

## 1.3.1 (Android versionCode 8) – Progressionsauswahl · 15.09.2026

- Eine Progressionsart antippen übernimmt sie direkt in den Planentwurf und schließt das Menü. Die Übung zeigt die neue Auswahl sofort; anschließend wird der gesamte Plan gespeichert.
- Schrittweite und weitere Progressionsparameter bleiben über „Details anpassen“ erreichbar; deren Übernehmen-Knopf bleibt außerhalb des scrollenden Formulars sichtbar.
- Beim Wechsel von doppelter zu linearer Progression bleiben vorhandene Gewichtsschritte und Entlastungseinstellungen erhalten.

## 1.3.0 (Android versionCode 7) – persönlicher Testrelease · 15.09.2026

- Direktes Trainingslogging mit vorbelegten Werten, Satzbestätigung und aufklappbaren Zusatzangaben.
- Mehrfachauswahl von Übungen und individuelle Satzvorgaben mit manueller Progression.
- Teilabschluss mit offenen Sätzen und Zusammenfassung der tatsächlich absolvierten Sätze.
- Ausdrückliche Planübernahme; offene Vorgaben bleiben erhalten und veraltete Änderungen werden abgelehnt.
- Datenbankschema 14 mit Migration und Satzvorgaben in Snapshots und Backups.

Validierung: neun gezielte Tests und Debug-Build bestanden. Vollständiger Android-Bedienablauf wegen Startabbrüchen der Testumgebung weiterhin offen. Dieser lokale Testrelease ist keine CI- oder Store-Freigabe. Details: [Prüfprotokoll](docs/validation/2026-09-15-approved-design/implementation.md).

## 1.1.3 (Android versionCode 5 / iOS Build 2) – lokaler Paritäts-Korrekturkandidat

- iOS erhält beim Bearbeiten von Sätzen ausgeblendete RPE-Werte; RPE/RIR-Progressionspläne behalten eine passende Intensitätseingabe. Ein anderes Training kann eine aktive Sitzung nicht mehr stillschweigend übernehmen.
- Android schützt vor der Übernahme veralteter Progressionsvorschläge und erhält wiederholte Pläne in Rotationen wie A/B/A.
- Pausentimer berücksichtigen normale Arbeitssätze und Deload-Ziele, werden dauerhaft gespeichert und beim Wiederherstellen gegen veraltete Sitzungen abgesichert. Backoff-Vorschläge verwenden den eingestellten Prozentsatz.
- Statistik und Startseite schließen laufende sowie zukünftige Trainingsdaten aus; lokale Tagesgrenzen werden konsistent verwendet. Der Readiness-Kreis zeigt bei unauffälliger Belastung wieder 100 %.
- Import und Wiederherstellung holen fehlende Progressionsauswertungen nach; ein Fehler dabei wird getrennt vom bereits erfolgreichen Import gemeldet.

Validierung und noch ausstehende Geräte-/TestFlight-Schritte: [Paritätskorrekturen](docs/validation/2026-09-11-parity-fixes.md).

## 1.1.2 (versionCode 4) – lokaler Korrekturkandidat

- Progressionsauswertungen unterscheiden gespeichertes Planziel, trainierte Last und vorgeschlagenes Gewicht. Abweichende Trainingsgewichte können auch beim Wiederholen ausdrücklich in den Plan übernommen werden.
- Alte, unbearbeitete informative Auswertungen mit Nullgewicht als Planbasis werden bei eindeutiger Satzevidenz neu bewertet. Bestätigte und bearbeitete Entscheidungen sowie Trainingsbelege bleiben erhalten.
- Individuelle Gewichtsschritte rechnen relativ zur trainierten Last: etwa 4 → 5,5 → 7 kg bei 1,5 kg Schrittweite. Entlastungen bleiben auf derselben Gewichtsreihe.
- Fehlerserien setzen vergleichbare tatsächlich trainierte Gewichte voraus; wiederholt zu hohe RPE berücksichtigt die konfigurierte Entlastungsregel.
- Doppelte Progression schlägt die Laststeigerung vor, sobald alle gewerteten Sätze die Wiederholungsobergrenze erreichen. Die Auswahl erklärt „Gewicht steigern“ und „Wiederholungen, dann Gewicht“.
- Ohne feste Pausendauer zählt der Pausentimer wieder hoch; nach dem letzten geplanten Arbeitssatz endet er.

## 1.1.1 (versionCode 3) – lokaler UI-Korrekturkandidat

- Die MEV/MAV-Karte auf der Startseite ist zunächst eingeklappt; Wochenwahl und Zusammenfassung bleiben sichtbar. Muskelgruppen lassen sich über eine beschriftete Schaltfläche ein- und ausblenden.
- Die Belastungsanalyse verwendet die volle Breite und ordnet ihre Texte untereinander an, damit sie sich nicht überlagern.
- Wochen- und Monatszahlen erhalten eine kompakte gemeinsame Zeile; MEV/MAV-Markierungen stehen an ihrer berechneten Position.
- Die widersprüchliche zusätzliche Muskelgruppenübersicht entfällt. Der Volumentrend zeigt acht vollständige Wochen mit Datumsbeschriftung und Einheit, einschließlich Wochen ohne Training.
- Rekordkarten können für größere Schrift in der Höhe mitwachsen.
- Der grafische Statuskreis bleibt erhalten: Häkchen bei unauffälliger Belastung, Fragezeichen ohne ausreichende Daten, numerischer Score bei vorhandenen Belastungssignalen.

## 1.1.0 (versionCode 2) – Entwicklungskandidat, nicht veröffentlicht

- Satztypen werden über Sicherung und Wiederherstellung vollständig erhalten; alte Sicherungen bleiben lesbar.
- Readiness-/Deload-Hinweise unterscheiden fehlende Daten von unauffälligen Trainingsdaten. Änderungen am Deload-Modus werden erst nach erfolgreicher Speicherung bestätigt.
- Planfelder unterstützen vollständiges Löschen und Bearbeiten. Speichern und Verlassen der Editoren berücksichtigen laufende Vorgänge und ungesicherte Änderungen.
- Scheibenberechnung berücksichtigt passende Kombinationen auch bei individuell gewählten Scheibengrößen.
- Coach-Vorschläge erhalten mehr Kontext und eine klare Übernahmehandlung. Offene Bearbeitungen werden nicht durch Sammelübernahme verworfen.
- Die Startseite zeigt das wöchentliche Muskelvolumen für alle Muskelgruppen, mit Wochenwahl und Orientierung an den vorhandenen Volumenbereichen. Sätze über einen Wochenwechsel werden nach ihrem Abschlusszeitpunkt zugeordnet.
- Historie und Statistiken erhalten Datums-/Planbezug sowie eine vollständige Darstellung der Satzarten.
- Einstellungen zeigen den letzten erfolgreichen Export und die App-Version; eine lokale Sicherungserinnerung ist optional.

Die genaue Validierung und verbleibende Geräte-/Release-Schritte stehen in `docs/validation/2026-09-05-reliability.md`.
