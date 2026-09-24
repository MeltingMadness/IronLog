# IronLog · Kombinierter Entwurf

Diese eigenständige HTML/CSS-Sammlung bündelt die bestätigte Nutzerauswahl vom 15.09.2026. Sie ist ein visueller Designvorschlag und ändert weder das produktive Android-/iOS-Projekt noch frühere Entwürfe.

## Nutzerauswahl

1. Trainingslogging Android: Open-Design-Entwurf vom 15.09.
2. Trainingslogging iOS: Open-Design-Entwurf vom 15.09.
3. Übungsauswahl: erster Entwurf vom 14.09.
4. Individuelle Satzvorgaben: erster Entwurf vom 14.09.
5. Training vorzeitig beenden: erster Entwurf vom 14.09.
6. Gespeichertes Ergebnis: Open-Design-Entwurf vom 15.09.
7. Planänderungen: erster Entwurf vom 14.09.

## Designentscheidungen

- Anthrazitflächen und warme Orange-Akzente aus der OpenDesign-Arbeitsannahme; keine Verläufe.
- Einheitliche 390 × 840 px Phone-Bühne, helle Schrift, tabellarische Zahlen und überwiegend großzügige Aktionsflächen.
- Android nutzt kompakte Appbar und Bottom-Navigation; iOS nutzt gruppierte Listen, Segmentierung und Safe-Area-Anmutung.
- Übungsauswahl bewahrt Suchfeld, Filter Alle, „3 ausgewählt“, Auswahl leeren, große Checkbox-Zeilen und die feste CTA „3 Übungen hinzufügen“.
- Satzplanung bewahrt den Tabellenkopf SATZ / TYP / KG / WDH., vier konkrete Zeilen, große Eingabeflächen sowie Progression, Superset und Reihenfolge als bestehende Wege.
- Teilabschluss verwendet bewusst „Weitertrainieren“ als orange Primäraktion und „Trotzdem beenden“ sekundär. Das Ergebnis rechnet 60 × 10 + 60 × 9 = 1.140 kg.
- Planänderungen zeigen Plan und Heute nebeneinander, drei große Radiooptionen und die vorselektierte Standardaktion „Plan unverändert lassen“. Offene Übungen bleiben im Plan.

## Dateien

- `index.html` · Übersicht
- `A-logging.html`, `B-planung.html`, `C-abschluss.html` · Präsentationsboards (1440 × 1100)
- `01-logging-android.html` bis `07-plan-changes.html` · eigenständige Screens
- `style.css` · gemeinsame Tokens und Komponenten

Die Boards und Screens enthalten keine externen Abhängigkeiten oder Hotlinks. Beispiele innerhalb der Phones sind Produktdeutsch; Hinweise zur Einordnung stehen in dieser README oder als knappe Board-Captions.

## Prüfung

Die drei PNG-Boards wurden mit dem offiziellen Bildexport von Open Design 0.22.2 erzeugt und nach kleinen CSS-Korrekturen visuell kontrolliert. Die Einzelansichten sind HTML-Designmuster mit einzelnen Beispielinteraktionen, kein vollständig funktionaler Trainingslogger. Native App-Abläufe wurden in diesem Schritt nicht implementiert oder getestet.
