# Abnahmefälle für die neue Auswertung

Diese Fälle beschreiben Produkterwartungen, keine medizinisch validierten Schwellen.

- Kurzhantelbankdrücken wird trotz Kategorie KURZHANTEL ausgewertet. Maschinenhistorie wird nur mit derselben Übung verglichen.
- Schritt 10 kg × 12 auf 11,5 kg × 10 im passenden Progressionsschema löst allein keine Ermüdung aus.
- Konstante Last bei steigender Wiederholungszahl ist keine Stagnationsstrafe.
- Ein einzelner schlechter Trainingstag erzeugt keine pauschale Deload-Empfehlung. Mehrere wiederholte Rückgänge ergeben getrennte, nachvollziehbare Gründe.
- Kein RPE und keine Satzintention im Altbestand werden nicht als perfekte Erholung interpretiert. Ein solider Leistungstrend bleibt ohne RPE auswertbar, mit sichtbarer Einschränkung.
- FAILURE und PLANNED_FAILURE sind verschieden. Eine bestehende FAILURE-Zeile erhält ohne explizite Angabe UNKNOWN.
- Ein neuer oder geänderter Planslot, Übungswechsel und bekannte Deload-Einheiten verändern die Vergleichbarkeit, ohne rückwirkende Annahmen über frühere Einheiten.
- Zukünftige und aktive Sitzungen gehen nicht in den historischen Trend ein.
- Ein Check-in von gestern wird heute nicht als aktuelle Tagesform dargestellt. Fehlende Felder bleiben null. Änderung, Löschen, Prozessneustart und Backup/Import erhalten diese Semantik.
- Schmerzen/Muskelkater, Stress oder wenig Energie werden nicht von einem guten Trainingstrend weggebügelt; beide Aussagen bleiben getrennt.
- Der Bezug zum heutigen Training berücksichtigt tatsächliche Plan-Muskelgruppen. Wochenvolumen bleibt Belastungsinformation, keine Erholungsprozentanzeige.
- Der Vorschlag ändert weder Zielgewicht noch Satzzahl oder Deload-Schalter automatisch.
- Import ersetzt gemäß bestehendem Backup-Vertrag den gesamten Graph. Satzintentionen dürfen nicht anhand gleicher numerischer IDs mit unabhängigen Datenbeständen vermischt werden.
- Android und iOS projizieren dieselben Daten auf dieselben Engine-Eingaben und Ergebnisse. Native UI zeigt nachvollziehbare deutsche Texte statt interner Reason-Codes.
