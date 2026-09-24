# Android 1.3.1 · Progressionsauswahl

## Ursache und Reproduktion

Der bisherige Radio-Klick rief nur `selectProgressionScheme` auf. Dieser änderte den separaten Menüentwurf. Erst `saveProgressionEditor` übertrug die Konfiguration in den Planentwurf; Schließen oder Wegklicken verwarf den Menüentwurf. Der zusätzliche Übernehmen-Knopf stand unter dem langen Parameterformular.

Gezielte Reproduktion: bestehender Plan mit doppelter Progression, 1,5 kg Schrittweite und eigener Entlastungseinstellung → Menü öffnen → linear wählen. Der Test auf direktes Schließen schlug vor der Korrektur fehl. Der Kontrolltest mit ausdrücklichem Übernehmen und Plan speichern bestand.

## Korrektur

- Die kompakte Auswahl ruft `chooseProgressionScheme` auf: Auswahl, validierte Übernahme in den Planentwurf, Menü schließen.
- Im Planeditor steht die neue Progressionsart unmittelbar an der Übung. Der gesamte Plan wird weiterhin über den Speichern-Knopf gesichert; das Menü nennt diesen letzten Schritt.
- „Details anpassen“ öffnet das bestehende Parameterformular. Sein Übernehmen-Knopf liegt außerhalb des scrollenden Bereichs.
- Bestehende Schrittweite (einschließlich ihrer ursprünglichen Einheit) und Entlastungswerte bleiben beim Wechsel erhalten.
- Ungültige Parameter werden weiterhin validiert; Feldfehler öffnen die Detailansicht.

## Validierung

`./gradlew :app:testDebugUnitTest --tests '*PlanEditorViewModelTest' --no-daemon`: 28 Tests, 0 Fehler. Darunter die Regression mit Menüschluss, verändertem Planentwurf, Speichern und erneutem Laden sowie bestehende Tests für Einheiten, Validierung, Abbrechen und Speicherzustände.

Der Test-Dummy des Plan-Repositories wurde an die zuvor erweiterten DAO-Schnittstellen angepasst, damit die Unit-Tests wieder kompilieren. Unbenutzte Workout-Abfragen schlagen dort ausdrücklich fehl.

Signierter Release-Build 1.3.1 (versionCode 8) erfolgreich. `apksigner verify` erfolgreich; Zertifikat identisch zur archivierten 1.3.0. Eine manuelle Bedienprüfung dieser Korrektur auf einem verbundenen Android-Gerät wurde nicht durchgeführt. Die APK wird nicht automatisch auf dem Handy installiert.
