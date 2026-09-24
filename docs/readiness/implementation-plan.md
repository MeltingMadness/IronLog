# Trainingstrend und Tagesform – Umsetzung

Beauftragt am 11.09.2026. Benutzer wünscht alle acht Verbesserungen sowie vier unabhängige HTML-Designentwürfe. Alle Worker verwenden `opencode-go/deepseek-v4.1-flash`, Reasoning `max`; Orchestrator bleibt im ursprünglichen IronLog-Thread.

## Getrennte Arbeitsbereiche

- UI-Vergleich: Task 01a09218-344d-7c61-9fe7-90d599ae5a79, Worktree 5b85, ausschließlich design/readiness-redesign.
- Portabler Rechenkern: Task 01a09218-9631-71e1-b5ef-f63c946b47f2, Worktree ee0e, neue shared/readiness-Pakete.
- Check-in/Satzintention: Task 01a09218-dc5e-74d2-8999-f236047afbe7, Worktree 6839, neue shared/readinessdata-Pakete.
- Android-Integration: Task 01a0921c-4915-7460-b6c9-0065b37b4d88, Worktree 2053, Android-only.
- iOS/Shared-Persistenz-Integration: Task 01a0921c-aa88-7591-ab1c-e5f9f984b049, Worktree dffb, Swift + Shared Backup/Store/Bridge.
- Android-Dashboard: Task 01a0922c-c7d4-73d2-bd59-9d8e1f11d232, Worktree 6903, feature/dashboard und Dashboardtests. Persistenz/Workout bleibt beim Android-Worker.
- iOS-Dashboard: Task 01a09232-33d1-7222-95f8-cf962b0ba68d, Worktree dbe6.
- Integration, Korrekturen am gemeinsamen Kern, Android-Verlauf und abschließende Abnahme: Orchestrator.

## Abnahmekriterien

1. Gleiche Auswertung auf Android/iOS aus einem gemeinsamen Kern; Kurzhantel-/Maschinen-/Körpergewichtsübungen werden geräteunabhängig auf Vergleichbarkeit geprüft.
2. Eigene Übungshistorie, vergleichbare Satzarten/Ziele, mehr als eine unerwartete Verschlechterung nötig. Gewichtsschritte und Wiederholungs-/RPE-Ziele berücksichtigen.
3. Geplantes Versagen, unerwartetes Zielverfehlen und historische unbekannte Intention unterscheiden. Deload-/Übungsänderungen nicht als pauschale Ermüdung werten.
4. Datenqualität und Ausschlussgründe sichtbar, fehlende Intensitätswerte neutral, keine 100-Prozent-Garantie aus fehlenden Daten.
5. Optionaler datierter Check-in für Schlaf/Energie/Stress/Muskelkater; unabhängig vom Trainingstrend und ohne erfundene physiologische Gesamtformel.
6. Tagesplan-/Muskelgruppenbezug mit vorhandener Wochenvolumenauswertung; keine aus MEV/MAV abgeleiteten Erholungsprozente.
7. Konkrete Gründe und Vorschläge ohne automatische Planänderungen.
8. Neue Daten bleiben nach Neustart sowie Backup/Import erhalten; Altbestand wird ohne erfundene Angaben gelesen. Fehlerwege und veraltete Check-ins werden geprüft.
9. Vier eigenständige UI-Entwürfe einschließlich Liquid Glass in einer lokalen HTML-Datei; keine Designauswahl vorwegnehmen.
10. Gezielte gemeinsame Tests, relevante Plattform-Builds und fokussierte UI-Abnahme. Kein Update auf dem Gerät ohne abgeschlossene Integration und Signaturabgleich.

## Status

Die acht funktionalen Verbesserungen sind im Hauptcheckout für Android und iOS integriert. Version 1.2.0: Android Build 6, iOS Build 3. Noch nicht auf dem Android-Handy installiert; dessen WLAN-Verbindung ist momentan nicht verfügbar.

- Gemeinsame Auswertung aller Gerätekategorien anhand eigener, vergleichbarer Historie und tatsächlicher Ziel-Snapshots.
- Progressionsspezifische Leistungsmetrik; echte Gewichtssteigerungen einschließlich 1,5-kg-Schritten, neue Zielbereiche und geplante Entlastung werden getrennt behandelt.
- Satzart und freiwillige Satzabsicht getrennt. Neue Sätze, Bearbeitung und nachträgliche Angabe im Verlauf angebunden; reine Zahlenänderungen erhalten vorhandene Absicht.
- Robuste Referenz, 28-Tage-Fenster, mehrere vergleichbare Einheiten; keine pauschale Ermüdungswertung bei Stagnation oder geplantem Versagen.
- Grafischer Trainingsindex mit aufklappbarer Evidenz und Ausschlussgründen. Fehlende Daten erzeugen keinen erfundenen 100er-Wert.
- Optionaler datierter Check-in: Schlaf, Energie, Stress und Muskelkater je Muskelgruppe. Fehlende Angaben bleiben leer. Bearbeiten, Abbrechen, Löschen und Tageswechsel berücksichtigt.
- Aktuell geplante Muskelgruppen von übriger Historie getrennt; letzte Belastung und rollierende sieben Tage explizit. Bestehende MEV/MAV-Karte bleibt unabhängig und aufklappbar.
- Neue Daten in der gemeinsamen Backup-Struktur, additive Room-Migration 12→13, atomare Satz-/Absichtsspeicherung und dauerhafter Deload-Kontext. Hinweise ändern keine Pläne automatisch.

Vier separate HTML-Entwürfe geliefert. Benutzer bevorzugt C und D; zusätzlicher Hybrid unter `/Users/maert/Documents/IronLog-Analysen/2026-09-11/redesign/ironlog-c-und-d.html`. Kein C/D-Produktionsredesign übernommen.

Gezielte Prüfungen und verbleibende Grenzen stehen in `validation-2026-09-11.md`. Der gesamte Checkout enthält auch ältere, nicht zu dieser Änderung gehörende Arbeiten; keine pauschale Bereinigung oder Rücksetzung durchgeführt.
