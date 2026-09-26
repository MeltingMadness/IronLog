# Liquid Glass (Plan, September 2026)

Grundlage: die Entwürfe auf der Seite „Liquid Glass“ der Design-Vorschläge (Heute, Workout, Pause, Verlauf, Übung, Abschluss, Heute hell, Android ohne Blur). Ziel ist dieselbe Optik auf Android und iOS, ohne das Verhalten der App zu ändern. Neue Funktionen, die in den Entwürfen nur angedeutet sind, gehören nicht dazu (siehe „Nicht Teil dieses Plans“).

Jeder Schritt ist ein eigener PR mit Android und iOS zusammen. Nach dem letzten Merge wird das Ergebnis in `docs/design-system.md` übernommen und diese Datei gelöscht.

## Entscheidungen vorab

1. **Einführung als Darstellungsoption.** In den Einstellungen unter „Darstellung“ gibt es die Wahl „Ember“ (heute) oder „Liquid Glass“. Standard bleibt Ember, bis alle Screens umgestellt sind; in Schritt 7 wird Liquid Glass Standard. So lässt sich jeder Zwischenstand auf dem Handy vergleichen, ohne die App halbfertig umzubauen.
2. **Unschärfe auf Android über die Bibliothek Haze** (`dev.chrisbanes.haze`, Apache 2.0). Compose kann den Hintergrund hinter einer Fläche nicht selbst weichzeichnen. Haze nutzt ab Android 12 (API 31) echte Unschärfe und fällt darunter auf eine getönte Fläche zurück. Das entspricht dem Entwurf „Android ohne Blur“. Alternative ohne Abhängigkeit: immer die getönte Variante, dann ohne echte Unschärfe.
3. **iOS bleibt ab 17.0.** Glas entsteht dort aus den System-Materialien (`.ultraThinMaterial` usw.) plus eigener Glanzkante. Ab iOS 26 kann dieselbe Komponente die native Glas-API nutzen (`if #available`), ohne dass sich die Screens ändern.

## Bausteine (gilt für alle Schritte)

- **Drei Glasstufen** wie im Entwurf: `glass` (Standard), `glassStrong` (Hauptflächen, Navigation, Dock), `glassTint` (in der Akzentfarbe getönt, für Rekorde und Fortschritt). Jede Stufe hat Glanzverlauf oben links, helle Oberkante, dunkle Unterkante, weichen Schatten und eine leichte Grundtönung, damit weißer Text lesbar bleibt.
- **Hintergrund ohne teure Unschärfe:** Die Farbflächen werden als radiale Verläufe gezeichnet, nicht als weichgezeichnete Kreise. Das sieht gleich aus und kostet auf keinem Gerät Leistung. Je Screen-Typ eine feste Komposition (Heute, Training, Pause, Verlauf, Übung, Abschluss); die Hauptfarbe folgt dem gewählten Farbschema.
- **Unschärfe sparsam:** echte Unschärfe nur für wenige, große Flächen (Navigation, Hauptkarte, Workout-Dock, Pause-Linse). Listenzeilen nutzen getöntes Glas ohne Unschärfe, damit das Scrollen im Verlauf flüssig bleibt.
- **Zugänglichkeit:** „Transparenz reduzieren“ (iOS) und die App-Einstellung „Reduzierte Animationen“ schalten auf die getönte Variante ohne Unschärfe. Kontrast für Text mindestens 4,5:1 auf der hellsten Stelle des Hintergrunds, geprüft je Farbschema.
- **Typografie:** gleich breite Ziffern für alle Zahlen (Zeiten, Gewichte, Zähler), Überschriften und Labels nach einem festen Muster.

## 1. Fundament

- Android (`core/designsystem`): `GlassLevel`, `Modifier.liquidGlass(level)`, `LiquidBackground(composition)`, Haze-Anbindung mit Fallback; `IronLogSurfaceCard` wählt je nach Darstellung Ember oder Glas, damit die 21 bestehenden Aufrufer ohne Änderung mitziehen.
- iOS (`Design/`): `LiquidGlassModifier` (`.liquidGlass(_:)`), `LiquidBackground`; `IronLogCard` (27 Aufrufer) schaltet ebenso um.
- Einstellung „Darstellung“: neues Feld in `AppPreferences` und DataStore, im gemeinsamen Kern (`SettingsPreferencesController`, Adapter) und in `IOSSettingsViewModel`. Kein Room- und kein Backup-Format-Wechsel (Einstellungen sind nicht Teil des Backups).
- Tests: Unit-Tests für die Einstellung (Kern und Adapter); Sichtprüfung auf Emulator und Simulator.

## 2. Navigation und Gerüst

- Schwebende Glas-Pille als Tab-Leiste; der aktive Tab zeigt Symbol und Namen. Android: Abstand zur Gestenleiste über die System-Insets.
- `IronLogScreenScaffold` und der iOS-Root legen den Hintergrund einmal unter alle Screens; Top-Bars werden transparent.

## 3. Startseite

- Hauptkarte mit Plan, den ersten drei Übungen mit Ziel und dem Start-Knopf (weiße Pille mit farbigem Play-Kreis und Dauer).
- Wochenleiste mit Tages-Linsen (trainiert, heute, offen) statt der bisherigen Wochen- und Monatskacheln. Nutzt vorhandene Daten, das Wochenziel entfällt (siehe unten).
- Tagesform, Trainingstrend und Wochenvolumen als Glaskarten; Inhalt und Verhalten bleiben.

## 4. Training und Pause

- Kopf mit Zeit und Übungsleiste (erledigt, aktuell, kommend) aus den vorhandenen Übungsgruppen.
- Aktuelle Übung als Hauptkarte: Satzpunkte, große Zahlen mit +/−-Knöpfen, RPE als Auswahlleiste. Die Eingabelogik aus `ActiveSetCockpitCard` bleibt, nur die Darstellung ändert sich.
- Glas-Dock unten mit Pausen-Mini-Ring und „Satz loggen“.
- Pause als Vollbild-Linse mit sinkender Wellenlinie, „−15 s“/„+30 s“ und „Pause überspringen“. Diese Knöpfe gibt es heute nicht als Vollbild: Der Pausen-Timer (`WorkoutRestTimerStore`) bekommt dafür Aktionen zum Verlängern, Verkürzen und Überspringen.

## 5. Verlauf, Übung, Abschluss

- Verlauf: je Woche eine Glaskarte mit Mini-Balken Mo–So und den Trainings darin; Suche und Filter bleiben. Die Gruppierung nach Woche braucht eine Anpassung am Paging (Trennelemente je Woche).
- Übungsstatistik: 1RM groß, Kurve mit Fläche und Endpunkt, vier gleich große Rekord-Kacheln.
- Abschluss: „Geschafft.“, drei Linsen (Dauer, Volumen, Übungen), Rekord-Karte in getöntem Glas, „Fertig“.

## 6. Übrige Screens und helle Variante

- Pläne, Plan-Editor, Übungsbibliothek, Einstellungen, Trainingsdetails, Sheets und Dialoge auf die Glasstufen umstellen.
- Helle Variante nach dem Entwurf „Heute hell“: Milchglas auf hellem Grund, aktive Elemente dunkel. Folgt der vorhandenen Einstellung Hell/Dunkel/System.

## 7. Umstellen und Aufräumen

- Liquid Glass wird Standard; Ember bleibt als Option oder wird entfernt (Entscheidung nach dem Vergleich auf dem Handy).
- `docs/design-system.md` und README beschreiben das neue System; diese Datei wird gelöscht.

## Nicht Teil dieses Plans

Folgende Punkte kommen in den Entwürfen vor, sind aber neue Funktionen und werden getrennt entschieden:

- Wochenziel („1 / 4 Trainings“).
- Vergleich zum letzten Mal als Etikett („+0,5 kg“), falls dafür neue Berechnungen nötig sind.
- Vibration am Pausenende bei gesperrtem Handy (Pausen-Timer im Hintergrund).
- Trainingskarte als Bild teilen.

## Risiken

- **Leistung:** Unschärfe ist auf älteren Android-Geräten teuer. Gegenmittel: wenige unscharfe Flächen, radiale Verläufe statt weichgezeichneter Kreise, Fallback unter Android 12 und bei reduzierten Animationen.
- **Lesbarkeit über hellen Farbflächen:** Grundtönung und Schleier im Hintergrund; Kontrast je Farbschema prüfen.
- **UI-Tests:** Die Emulator-Tests suchen Texte, nicht Farben. Sie sollten weiter laufen; geänderte Texte (z. B. Knöpfe im Dock) werden im selben PR nachgezogen.
- **Abhängigkeit Haze:** Versionsverträglichkeit mit der Compose-BOM vor Schritt 1 prüfen.
