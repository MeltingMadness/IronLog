# Pausentimer-Korrektur – 08.09.2026

Ursache: logSet legte ohne aktivierten festen Countdown keinen Timer an und startete ihn ausdrücklich auch nach Erreichen der geplanten Satzanzahl neu.

Korrektur: Nach erfolgreichem Speichern eines Arbeitssatzes wird ohne feste Dauer der vorhandene hochzählende Modus (durationSeconds = 0) verwendet. Mit fester Dauer bleibt der Countdown erhalten. Nach Erreichen der Satzanzahl des unveränderlichen Plan-Snapshots wird der Timer dieser konkreten Übungszeile entfernt; zusätzliche Sätze starten ihn nicht erneut. Aufwärmsätze zählen nicht zur Zielanzahl und starten weiterhin keinen Timer. Bei freien Übungen ohne Satzvorgabe ist kein letzter Satz vorab erkennbar; hier endet der Timer durch Schließen oder Trainingsabschluss.

Die Einstellungsbeschriftung lautet nun „Feste Pausendauer verwenden“ und erklärt beide Modi.

Validierung: gezielt ActiveWorkoutViewModelTest mit 88 Tests, 0 Fehlschlägen und 0 Fehlern. Abgedeckt sind hochzählender Modus, feste Dauer, letzter und zusätzlicher geplanter Satz in beiden Modi, Aufwärmsätze, unabhängige Übungstimer und Trainingsabschluss. XML der danach angepassten Einstellungsbeschriftung erfolgreich geparst. Keine Geräteinstallation oder Geräteprüfung in diesem Durchlauf.
