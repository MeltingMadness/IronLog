# Polish und Parität (Plan, September 2026)

Grundlage: Durchsicht der Android-App mit realistischen Testdaten (18 Trainings, 6 Wochen) am 26.09.2026 und Codevergleich mit iOS. Die fachlichen Abweichungen aus [`validation/2026-09-11-android-ios-parity.md`](../validation/2026-09-11-android-ios-parity.md) sind laut [`validation/2026-09-11-parity-fixes.md`](../validation/2026-09-11-parity-fixes.md) behoben; offen sind Bedienung und Funktionszugang.

Jeder Punkt ist ein eigener PR. Nach dem letzten Merge wird das Ergebnis in die Doku übernommen und diese Datei gelöscht.

## 1. Aufräumen (kein sichtbarer Unterschied)

- `ActiveWorkoutScreen.kt` in Screen, Supersatz-Gruppen, Übungskarte, aktive Satzkarte, geloggte Zeile und Formatierung aufteilen.
- `ActiveWorkoutViewModel.kt`: Zustandsmodelle, Regelfunktionen, Timer-Codec in eigene Dateien; Pausen-Timer als `WorkoutRestTimerStore`.

## 2. Zahlen und Texte (app-weit)

- Deutsches Zahlenformat überall („82,5 kg“, „12.305 kg“, kein „80.0“), einheitlich „kg × Wdh“.
- Fehler: „Steigerung (%%)“, „1 Trainings“.
- Umlaute in Anzeigenamen („Rücken“, „Gesäß“, „geschätzter“).
- Doppelte Häkchen/Pfeile in Buttons entfernen.
- „Absicht: Nicht angegeben“ nicht unter jedem Satz anzeigen.
- Leere Tagesform: „Eintragen“ statt „Bearbeiten“; Rekord-Leertext passend zum Datenstand.
- Feste Texte im Abschluss-Screen in String-Ressourcen.

## 3. Workout-Screen

- Kompakter Kopf (Zeit, Fortschritt „4/18 Sätze“, Volumen) statt großem Timer-Kreis; Pausen-Timer integriert, kein Springen des Inhalts.
- Nur die aktuelle Übung aufgeklappt; erledigte zeigen Kurzfassung, kommende das Ziel.
- „Vorheriges Training“ mit Verlauf-Symbol statt blauem „+“.
- Löschen weniger prominent, mit Rückgängig.
- „Beenden“ mit ausreichendem Kontrast.

## 4. Start und Abschluss

- „Training jetzt starten“ startet den vorgeschlagenen Plan direkt; „Anderen Plan wählen“ als zweite Aktion.
- Fülltext und doppelten Titel entfernen; Start-Sheet vollständig sichtbar.
- Zusammenfassung: „Fertig“ als Hauptaktion, ein Titel, neue Rekorde hervorheben, „< 1 min“.

## 5. Listen und Übersichten

- Planliste: Meta-Pläne sichtbar, „zuletzt trainiert“, leichtere Karten, besser sichtbarer Plus-Button.
- Trainingsdetails nennen den Plan.
- Volumen-Diagramm: laufende Woche kennzeichnen, Tausenderpunkte.
- Übungsstatistik: einheitliche Kacheln.
- Einstellungen: Farbschema-Auswahl bricht um; Links auf der Startseite bündig.

## 6. Parität: Android zieht nach

- Verlauf: Sätze und Notizen bearbeiten/löschen, Suche.
- Statistik-Übersicht über alle Übungen (Suche, Muskelfilter).
- Entschiedene Progressionsvorschläge einsehbar.

## 7. Parität: iOS zieht nach

- Mehrfachauswahl im Übungs-Picker.
- Muskel-Heatmap.
- Text- und Formatkorrekturen aus Punkt 2, soweit auf iOS zutreffend.
