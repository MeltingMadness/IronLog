# Umsetzung des ausgewählten Designs · 15.09.2026

Die kombinierte Auswahl ist in den nativen Android- und iOS-Quellen umgesetzt. Referenz: [freigegebener Entwurf](../../design/2026-09-15-kombiniert/index.html).

## Verhalten

- Trainingslogging: Gewicht und Wiederholungen direkt in der Übung, vorbelegte Werte und Bestätigung je Satz; erweiterte Angaben separat aufklappbar.
- Planung: mehrere Übungen gemeinsam auswählen; gleichförmige oder individuelle Satzvorgaben mit Aufwärm-, Arbeits- und Backoff-Sätzen.
- Abschluss: offene geplante Sätze anzeigen, Weitertrainieren oder trotzdem speichern; Zusammenfassung aus tatsächlich gespeicherten Sätzen.
- Planänderungen: standardmäßig unverändert lassen; Satzwerte ausdrücklich übernehmen oder Planeditor öffnen. Nicht absolvierte Vorgaben bleiben erhalten. Zwischenzeitlich geänderte Pläne werden gegen den Trainingssnapshot geprüft.
- Individuelle Satzvorgaben verwenden manuelle Progression. Dies wird im Editor und beim Übernehmen angezeigt.
- Persistenz: Datenbankschema 14 mit Migration 13→14; Satzvorgaben in Trainingssnapshots und Backups; ältere Daten ohne Satzliste bleiben lesbar.

## Bestätigte Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Android assembleDebug | Erfolgreich |
| Finaler iOS-Simulator-Build | Erfolgreich |
| Shared PlannedSetsTest | 2 Tests bestanden |
| SharedStateStoreTest.individualTargets* | 2 Tests bestanden |
| iOS IndividualSetTargetsTests | 3 Tests bestanden |
| Android IndividualSetTargetsTest auf Emulator | 2 Tests bestanden |
| Android vollständiger UI-Ablauf | Nicht erfolgreich ausgeführt; Startabbrüche, siehe unten |

Die neun Tests prüfen unter anderem alte/neue Datenformate, ungültige Vorgaben, Satztyp-Zuordnung, individuelle Deload-Vorgaben, Migration, unveränderte offene Satzvorgaben und die Ablehnung veralteter Planänderungen. Es wurde keine vollständige Testsuite ausgeführt.

## Native iOS-Bedienprüfung

Am vorhandenen Testplan Audit A: 60 kg × 10 und 60 kg × 9 geloggt; Teilabschluss mit 2/3 Sätzen und einem offenen Satz; Speicherung und korrekte Zusammenfassung mit 1.140 kg Volumen; Dialog für Planänderungen mit unveränderter Standardauswahl geöffnet. Das tatsächliche Übernehmen über diesen iOS-Dialog wurde nicht manuell abgeschlossen; die Datenoperation ist durch die gezielten Tests geprüft.

Am finalen Build: Einzelne Sätze geöffnet; zwei Übungen ausgewählt und gemeinsam in den Planentwurf übernommen (1→3 Übungen). Danach Entwurf abgebrochen; Ausgangsplan bleibt bei einer Übung. Eine vollständige manuelle Prüfung aller Eingabevarianten wurde nicht durchgeführt.

## Grenzen und Umgebung

Der Android-UI-Test wurde kompiliert, konnte aber nicht bis zum eigentlichen Ablauf ausgeführt werden. Ein Versuch endete mit einem Emulator-Systemabsturz; weitere mit einem ANR während des Prozessstarts ("failed to complete startup"). Auch ein kalter Start mit Softwaregrafik löste dies nicht. Damit ist der Android-Bedienablauf nicht als bestanden belegt; eine appseitige Ursache ist durch diese Beobachtung allein nicht ausgeschlossen.

Beim Android-Instrumentierungslauf installierte der Gradle-Testlauf die App neu; die vorherigen Audit-Testdaten dieser Emulatorinstallation wurden dabei gelöscht. Die iOS-Testdaten blieben erhalten. Es wurde kein physisches Gerät aktualisiert und kein Store-Release erstellt.

Die vier Bilder zum iOS-Logging/Abschluss stammen aus dem funktional geprüften Build vor dem letzten kleinen Farb-/Textfeinschliff. Die drei Planungsbilder stammen aus dem finalen Build. Alle sind echte Simulatoraufnahmen, keine Design-Mockups. Der abschließende Farb-/Textfeinschliff wurde gebaut, sein kompletter Logging-Ablauf aber nicht erneut manuell durchlaufen.

Die temporäre Simulator-Spiegelung und der eigens gestartete Android-Emulator wurden nach der Prüfung beendet. Die Galerie wurde auf vorhandene lokale Bild- und Dokumentverweise geprüft (16 Verweise); ihr Browserlayout wurde nicht zusätzlich visuell getestet.
