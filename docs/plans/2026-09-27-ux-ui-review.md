# UX/UI-Review der gesamten App (Plan, September 2026)

Anlass: Nach der Umstellung auf Liquid Glass ist die Schrift schlecht lesbar, vor allem in den Farbschemata außer Amber. Ziel ist ein vollständiger Review von Android und iOS mit einer priorisierten Liste von Befunden und danach Fix-PRs. Schrift und Kontrast kommen zuerst, weil sie jeden Screen betreffen.

## Ausgangslage (schon im Code festgestellt)

1. **Die Android-Schriftskala ist durchgehend kleiner als der Material-Standard.** Fließtext `bodyMedium` 12 sp (Standard 14), `bodySmall` 11 sp (12), `bodyLarge` 14 sp (16), `titleMedium` 14 sp (16), Labels 9 bis 12 sp (11 bis 14). Dazu kommen einzelne feste Größen von 8 bis 11 sp in den Feature-Modulen.
2. **`AthleticLabel`** (11 sp, fett, Großbuchstaben) steht auf fast jeder Glaskarte, meist in der Nebentextfarbe. Bei Glas über einem bunten Hintergrund ist das die kritischste Kombination.
3. **Die Nebentextfarbe `onSurfaceVariant` ist in zwei Schemata farbig statt neutral:** Tide dunkel `#6EE7B7` (Mint), Pulse dunkel `#F472B6` (Pink). Jeder graue Hinweistext wird dort farbig und konkurriert mit Akzent und Hintergrund. Ember hell nutzt Braun `#6B4A2A`.
4. **Die Plattformen sind uneinheitlich:** Android zeichnet alles in Figtree (bis vor Kurzem sogar ohne echte Fettschnitte), iOS nutzt die Systemschrift (SF) mit Dynamic Type. Dieselbe App wirkt dadurch auf beiden Plattformen verschieden.
5. **Kontraste werden nirgends geprüft.** Der Liquid-Glass-Plan forderte 4,5:1 auf der hellsten Stelle des Hintergrunds, einen Test dafür gibt es nicht.

## Maßstäbe

- Kontrast nach WCAG 2.2 AA: Text 4,5:1, großer Text (ab 18 pt bzw. 14 pt fett) und Bedienelemente 3:1, gemessen gegen die **ungünstigste** Stelle des Glases, also Glasfläche über dem hellsten Punkt des farbigen Hintergrunds.
- Mindestgrößen: Fließtext 14 sp/pt, Nebentext 12, nichts unter 11. Zahlen im Training mit gleich breiten Ziffern.
- Schriftgröße des Systems: bis 200 % ohne abgeschnittene Texte oder überlappende Elemente (Android Font Scale, iOS Dynamic Type bis AX3).
- Touch-Ziele mindestens 48 dp bzw. 44 pt. TalkBack und VoiceOver lesen jeden Screen sinnvoll vor.
- Dazu die Heuristiken nach Nielsen je Ablauf (Rückmeldung, Konsistenz, Fehlervermeidung, Wiedererkennen statt Erinnern).

## Vorgehen

### Phase 1: Schrift (zuerst, mit Entscheidung durch dich)

- **Vergleichsfläche** (Design-Canvas) mit denselben drei Screens (Startseite, Training, Verlauf) in drei Schrift-Optionen, jeweils in Amber, Tide und Pulse, dunkel und hell:
  - A. Figtree bleibt, die Skala wird auf Material-Standard angehoben, und Nebentexte werden eine Stufe kräftiger.
  - B. Systemschrift auf beiden Plattformen (Roboto/SF) für Fließtext und Bedienelemente, Figtree nur noch für große Überschriften und Zahlen.
  - C. Eine auf Lesbarkeit ausgelegte Schrift für alles (z. B. Atkinson Hyperlegible oder Inter), auf beiden Plattformen gleich.
- Du wählst eine Option. Danach werden Skala, `AthleticLabel` und `AthleticNumber` auf Android und iOS angepasst, und die festen `fontSize`-Werte in den Screens durch Stile der Skala ersetzt.

### Phase 2: Farbe und Kontrast

- **Automatischer Kontrasttest** (Unit-Test im Designsystem): Für jedes Schema, hell und dunkel, und jede Glasstufe werden `onSurface`, `onSurfaceVariant`, `primary` und `onPrimary` gegen die zusammengesetzte Glasfarbe über dem hellsten Hintergrundpunkt gerechnet. Unterschreitet ein Wert die Maßstäbe, schlägt der Test fehl, damit spätere Farbänderungen nicht wieder unlesbar werden.
- Neutrale Nebentextfarbe in allen Schemata; der Akzent bleibt für Akzente reserviert (Tide, Pulse, Ember hell). Glas-Grundtönung und Hintergrundhelligkeit so weit anpassen, bis der Test grün ist.
- Akzentfarbe als Textfarbe prüfen (z. B. „13 Einheiten · zuletzt …“ in der Übungsliste, Links, Chips) und wo nötig durch eine dunklere bzw. hellere Textvariante ersetzen.

### Phase 3: Screen-für-Screen-Review

- **Inventar:** alle Screens, Sheets und Dialoge beider Plattformen. Das sind Startseite, Training, Satz-Editor, Pause, Abschluss, Verlauf, Trainingsdetails, Übungsstatistik, Statistik-Übersicht, Pläne, Plan-Editor, Meta-Pläne, Übungsbibliothek, Progression, Einstellungen, Backup, Onboarding und Leerzustände.
- **Screenshot-Matrix, automatisch erzeugt** (Emulator und Simulator per Skript):
  - Standard: Liquid Glass dunkel Amber, voll.
  - Stichprobe: alle Schemata hell und dunkel auf vier Leitscreens.
  - Schriftgröße 200 % auf allen Screens.
  - Ember nur kurz zur Kontrolle.
- **Abläufe durchspielen**, mit Blick auf Schritte, Rückmeldung und Fehlerfälle: erstes Öffnen, Plan anlegen, Training starten und loggen, Pause, Training abschließen samt Progression, Verlauf durchsuchen, Satz nachträglich ändern, Backup.
- **Ergebnis:** ein Review-Bericht als Seite mit Screenshots und einer Befundliste. Jeder Befund hat Screen, Plattform, Schwere (kritisch, hoch, mittel, niedrig), Beschreibung und Vorschlag.

### Phase 4: Barrierefreiheit

- TalkBack und VoiceOver auf den Leitabläufen (Training loggen, Pause, Abschluss).
- Touch-Ziele, Fokusreihenfolge, Beschriftungen von Symbolknöpfen.
- Reduzierte Bewegung und „Transparenz reduzieren“ in Liquid Glass.

### Phase 5: Umsetzung

- Befunde gebündelt in PRs, jeweils Android und iOS zusammen: erst Schrift, dann Kontrast, dann „kritisch und hoch“, dann der Rest. Verhalten ändert sich nur, wo ein Befund das verlangt; neue Funktionen werden getrennt entschieden.
- Doku (`docs/design-system.md`) je PR nachziehen; dieser Plan wird nach dem letzten PR gelöscht.

## Was von dir gebraucht wird

- In Phase 1 die Wahl der Schrift-Option (A, B oder C) anhand der Vergleichsfläche.
- In Phase 3 ein kurzer Blick auf die Befundliste: Was soll umgesetzt werden, was bleibt?

## Nicht Teil dieses Reviews

- Neues Logo (erledigt, PR „App-Icon Level Up“), neue Funktionen, Änderungen an Datenmodell oder Backup-Format.
