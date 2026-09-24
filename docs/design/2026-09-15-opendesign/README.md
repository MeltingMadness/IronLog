# IronLog · sieben Vorher/Nachher-Boards

Dieses eigenständige Open-Design-Projekt übersetzt die Befunde aus `docs/validation/2026-09-13-competitor-audit/befunde.md` in sieben feste Vergleichsboards. Jedes HTML hat eine 1440 × 1100 px große, nicht scrollende Fläche und kann einzeln als PNG exportiert werden.

## Dateizuordnung

| Datei | Originalfoto | Entwurfsschwerpunkt |
|---|---|---|
| `01-logging-android.html` | `assets/ironlog-android-weight.png` | Kompakte Satzübersicht, 60 kg × 10 übernommen, sichtbares „Satz speichern“, Details auf Abruf |
| `02-logging-ios.html` | `assets/ironlog-ios-second-set-default.jpg` | iOS-eigener Inline-Flow, sichtbare Labels, 60 kg statt 0 kg |
| `03-exercise-picker.html` | `assets/ironlog-android-picker.png` | Suche, Muskelgruppenfilter, Mehrfachauswahl, „3 Übungen hinzufügen“ |
| `04-plan-create.html` | `assets/ironlog-android-plan-bench.png` | Einfache Vorgabe vs. satzweise 20 × 10, 40 × 5, 60 × 8, 55 × 10 |
| `05-finish-early.html` | `assets/ironlog-android-finish.png` | 2 von 9 erledigt, 7 offen, Weitertrainieren oder bewusst beenden |
| `06-completion.html` | `assets/ironlog-android-finished.png` | „Training gespeichert“, 5 min, 2 Sätze, 1.140 kg, vorzeitig beendet |
| `07-plan-changes.html` | `assets/ironlog-android-history-detail.png` | Vorschlag mit offener Einordnung, Standard nur dieses Training, optionale Übernahme |

`index.html` ist die Übersicht und verlinkt alle sieben Boards. Die Originale sind lokal kopiert und werden proportional mit `object-fit: contain` gezeigt. Android-Fotos sind 1080 × 2400 px, das iOS-Foto 368 × 800 px.

## Kurze Designbegründung

Die Boards sind als ruhige Trainings-Cockpits gebaut: dunkles Anthrazit trägt die hohe Informationsdichte, ein warmes Orange markiert jeweils nur die nächste Entscheidung, und Avenir Next plus native Systemschrift trennen Display-Hierarchie von UI-Text. Android nutzt kompakte App-Bar und Bottom-Navigation; iOS nutzt Safe-Area-Abstände, Segmentierung und weichere Sheet-Flächen. Die rechte Seite ist bewusst CSS/SVG aufgebaut, kein Rasterbild.

Die Texte bleiben quellengebunden. Offene Punkte (fehlende Historie, nicht abschließend bestätigte appweite Planübernahme, ungeprüfte iOS-Konkurrenz) sind im jeweiligen Board sichtbar markiert. Es werden keine Rekorde, Leistungswerte oder Produktfunktionen erfunden.
