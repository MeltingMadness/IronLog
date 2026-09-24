# Trainingslogger-Vergleich: Befunde und Konsequenzen für IronLog

13. September 2026 · [Visueller Vergleich](./vergleich.html) · [Testumfang und Quellen](./README.md) · [Android-Protokoll](./evidence/android-protokoll.md)

**Die größte belegte Lücke liegt im täglichen Bedienablauf.** IronLog speichert Sätze und Korrekturen im geprüften Android-Durchlauf korrekt. Gegenüber Hevy und Strong benötigt der Standardfall aber deutlich mehr Bildschirmfläche; im ersten Training ohne Plangewicht fehlt die Übernahme des gerade geloggten Gewichts. Planerstellung und Abschluss sind weitere klare Ansatzpunkte. Eine objektive Geschwindigkeitsrangliste wurde nicht gemessen.

Vier Android-Konkurrenten wurden mit eigenen Plänen und echten Logs geprüft. Fitbod wurde installiert und bis zur Abo-Seite bedient; sein Logging bleibt ungeprüft. IronLog wurde auf beiden Plattformen getestet. Native iOS-Konkurrenten sind mangels geeigneter Builds/Gerätezugang **nicht** getestet. Details und Grenzen stehen im [Prüfumfang](./README.md).

## 1. Was die einzelnen Apps praktisch zeigen

| App | Plan erstellen | Training loggen | Reibung / Grenze | Was IronLog daraus gewinnen kann |
|---|---|---|---|---|
| **Hevy** | Drei Übungen gesammelt auswählen. Pro Übung einzelne Satzzeilen; Add Set übernimmt Werte. | Kompakte Zeilen mit PREVIOUS/KG/REPS/Haken. Zweite Session zeigt echte Vorwerte. Korrektur eines bestätigten Satzes aktualisiert Volumen sofort. | Konto und mehrere Einrichtungsschritte. Sichtbarkeit im Test bei beiden Workouts zunächst Everyone; jeweils manuell Private. Resttimer war OFF. | Vorwerte direkt am Eingabeort, wiederholte Sätze ohne Neueingabe, übersichtliche Planstruktur. Datenschutzvorgaben bewusst anders wählen. |
| **Strong** | Mehrfachauswahl, ähnlicher Editor für Vorlage und Training; Abschlussfelder in Vorlage gesperrt. | Ein Haken bestätigt Satz und startet Pause. Vorwerte im zweiten Training. Korrektur verlangt erneutes Abhaken. | Bei Teilabschluss kann man auch unbestätigte gültige Sätze komplettieren; das darf keine versehentliche Erfassung auslösen. Unterschiedliche Übungseinheiten erfordern Aufmerksamkeit. | Explizite Behandlung offener Sätze und überprüfbare Vorlagenänderungen. Gleiche Grundbedienung beim Planen und Loggen. |
| **JEFIT** | Eigener mehrtägiger Plan, Checkbox-Mehrfachwahl, Equipment-/Muskel-/Bewegungsfilter. Individuelle Satzvorgaben. | Log Set übernimmt geänderte Werte in den nächsten Satz. Pause mit ±15s/Skip. Ergebnis und Planübernahmeoptionen. | Trotz „eigener Plan“ langes Onboarding mit Körperprofil. Swap zeigt Elite-Abzeichen. Community-Veröffentlichung zunächst aktiviert, im Test deaktiviert. | Gute Suche und Folgewertübernahme. Für erfahrene Nutzer einen kurzen Einstieg behalten. |
| **FitNotes** | Routine/Tag mit individuellen Vorgabezeilen; alternativ vorige Sätze kopieren oder nichts vorgeben. | Zentrale Eingabe und Save wiederholen Werte; Zeile antippen wechselt auf Update/Delete. Korrektur und Kaltstart erfolgreich. | Log All erzeugt im Standardmodus bereits Logs. Nicht dasselbe Modell wie ein aktives Training mit abschließendem Finish. Kategoriegebundene Suche kann vermeintlich keine Treffer liefern. | Flexible Vorgaben und sehr kurze wiederholte Eingabe. Geplant und absolviert in IronLog weiterhin eindeutig unterscheiden. |
| **Fitbod** | Geführte Ziele, Gym/Equipment, vorhandene Kraftwerte; erzeugte Push/Pull/Legs-Vorschau. | **Nicht getestet.** Vollbild-Abo-Seite nach Profilanlage. | Kein sichtbares Überspringen; Zurück/Neustart helfen nicht. Sieben Tage Probezeit, anschließend 109,99 €/Jahr im konkreten Angebot. | Geräteprofile und individuelle Ausgangswerte als Planungshilfe untersuchen. Keine Laufzeitbewertung des Loggers ableitbar. |

### Konkrete Kontrollrechnungen

- **IronLog Android:** 60×10 + korrigierte 60×9 = **1.140 kg**. Nach vollständigem Neustart im Verlauf und in Satzdetails bestätigt.
- **Hevy:** Erste Session 2×60×10 = **1.200 kg**. Zweite Session von 62,5×10 auf 62,5×9 korrigiert = **562,5 kg**, weiterhin ein Satz.
- **Strong:** Zweite Session nach Korrektur genau **ein Satz 60×9**, Ergebnis 1.190 lb, entsprechend gerundet 540 kg. Erste Vorlage blieb trotz Teilabschluss vollständig erhalten.
- **JEFIT:** Zwei bestätigte Sätze 60×10 = **1.200 kg**, im Abschluss gespeichert. Keine kontrollierte zweite Session/Korrekturprüfung.
- **FitNotes:** Wiederholter Satz durch Save angehängt; derselbe Eintrag anschließend auf **62,5×9** aktualisiert, ohne weiteren Eintrag. Kaltstart bewahrt ihn.
- **IronLog iOS:** Ein Satz 60×10 = **600 kg** im Verlauf; der nächste Satzeditor begann erneut bei 0 kg.

Das sind funktionale Stichproben mit absichtlich kurzen Teiltrainings, keine simulierte sportliche Leistungsbewertung. Die Protokolle dokumentieren abweichende Plangewichte, Satzanzahlen und Einheiten; eine direkte Tapzahl- oder Zeitrangliste wäre daraus irreführend.

## 2. Wo IronLog noch nicht auf Augenhöhe ist

### P1 · Wiederholte Arbeitssätze ohne erneute Gewichtseingabe

**Beide Plattformen praktisch belegt:** Neuer Plan ohne festes Gewicht, 3×10 Bankdrücken. Ersten Satz mit 60 kg erfassen. Auf Android ist Satz 2 wieder leer und Loggen deaktiviert; auf iOS öffnet der nächste Satzeditor erneut mit 0 kg. Das bereits geleistete Gewicht wird in diesem Fall nicht zur nächsten Eingabe angeboten.

**Wichtige Eingrenzung:** Android kann bereits positive Planziele oder Werte einer vorherigen Session als Platzhalter verwenden und beim Loggen übernehmen. Der Befund betrifft den ersten Durchlauf ohne diese Vorgaben, nicht grundsätzlich jede Eingabe. Quellcode: [Vorwertherkunft und eingeklappte Historie](../../../feature/workout/src/main/java/com/ironlog/app/presentation/workout/ActiveWorkoutScreen.kt), insbesondere `previousWeightHint`, `targetWeightHint`, `showPreviousSession`.

**Vorschlag:** Bei gleichartigen Arbeitssätzen den gerade verwendeten Wert anbieten. Herkunft erkennbar: „Plan“, „Letzte Einheit“ oder „Gerade geloggt“. Bewusste abweichende Satzvorgaben und Progressionsziele haben Vorrang; keine stille Veränderung des Plans.

**Abnahme:** Im beschriebenen 0-kg-Plan kann Satz 2 als 60×10 direkt bestätigt werden. Ein ausdrücklich geplanter Backoff-Satz behält sein anderes Gewicht. Editieren und Zurücksetzen bleiben möglich. Der Nutzer sieht vor Bestätigung die tatsächlichen Werte.

Belege: [Android Satz 2](./evidence/ironlog-android-second-default.png), [iOS Satz 2](./evidence/ironlog-ios-second-set-default.jpg), [Hevy Vorwerte](./evidence/hevy-previous-values.png).

### P1 · Den Loggen-Knopf und Satzübersicht sichtbar halten

**Android UI:** Ein aktiver Satz enthält große Eingaben, RPE-Chips, Hantelbeladung und drei volle Absicht-Zeilen. Nach Eingabe von 60 kg liegt der Loggen-Knopf unterhalb der sichtbaren Fläche des Pixel-7-Profils. Scrollen zum Bestätigen ist erforderlich. Der jeweils aktive Satzbereich erscheint außerdem bei mehreren Übungen, was die Übersicht verlängert.

**iOS UI:** Jeder neue Satz benötigt „Satz hinzufügen“ und ein eigenes Formular. Die Wiederholungszahl hat im geprüften Bild kein sichtbares Feldlabel, obwohl ein Accessibility-Label vorhanden ist.

**Vorschlag:** Standardzeile mit Satznummer, Vorwert, Gewicht, Wiederholungen und Abschlussknopf. RPE/RIR optional als schmale Spalte; Satztyp, Absicht und Scheibenrechner über aufklappbare Details. Alternativ zunächst einen kompakten aktiven Editor mit dauerhaft erreichbarem Bestätigen testen. Ein vollständiges Tabellenlayout ist eine Designhypothese, keine bereits validierte IronLog-Lösung.

**Abnahme:** Gewicht/Wdh. eingeben und bestätigen ohne Scrollen am Referenzgerät; aktive Tastatur berücksichtigen. Abgeschlossene und nächste Sätze bleiben auffindbar. Große Schrift, Touch-Ziele und VoiceOver/TalkBack müssen bei Umsetzung geprüft werden. Korrektur ändert denselben Satz und sein Volumen; kein versehentlich zusätzlicher Satz.

Belege: [IronLog nach Gewichtseingabe](./evidence/ironlog-android-weight.png), [Hevy Satzzeilen](./evidence/hevy-previous-values.png), [Strong Korrektur](./evidence/strong-corrected.png).

### P1 · Übungen in einer Auswahlrunde hinzufügen

**Android und iOS UI:** Eine Übung wählen schließt den Picker. Android: Bankdrücken, Rudern und Kniebeuge benötigten drei Öffnungen. Android hat bereits Muskelgruppenfilter; im iOS-Auswahlbild waren entsprechende Filter nicht sichtbar.

**Vorschlag:** Bestehende Suche/Filter um Mehrfachauswahl, Auswahlzähler und „3 Übungen hinzufügen“ ergänzen. Auswahl über Suchwechsel behalten. iOS-Auswahlansicht funktional angleichen. Anschließend Reihenfolge und Supersets bearbeiten.

**Abnahme:** Die drei Testübungen in einer Auswahlrunde übernehmen; Suchwechsel verlieren keine Auswahl. Ausgewählte Einträge klar markiert; Reihenfolge vorhersehbar. Eigene Übungen weiter erreichbar.

Hevy, Strong und JEFIT haben diesen Ablauf praktisch bestanden. [Hevy Auswahl](./evidence/hevy-picker-squat.png), [JEFIT Auswahl](./evidence/jefit-search-row.png). Android-Code: [PlanEditorViewModel](../../../feature/plans/src/main/java/com/ironlog/app/presentation/plans/PlanEditorViewModel.kt), `addExercise` setzt `showExercisePicker=false`.

### P1 · Teilabschluss bewusst machen und Ergebnis zeigen

**Beide Plattformen UI:** Allgemeine Bestätigung ohne offenen Umfang; danach Rückkehr zur Startseite. Android hatte 2 von 9 geplanten Sätzen, iOS 1 von 3 erfasst. Speicherung war jeweils korrekt, die Oberfläche bestätigt das Ergebnis aber nicht direkt.

**Vorschlag:** „Noch 7 geplante Sätze offen“ mit Weitertrainieren/Trotzdem beenden. Nur tatsächlich erfasste Sätze speichern. Danach kompakte Zusammenfassung: Übungen, Sätze, Volumen, Dauer, relevante Rekorde und direkter Verlaufseinstieg. Plan- oder Progressionsänderungen getrennt und nachvollziehbar anzeigen.

**Abnahme:** Offener Umfang korrekt auch bei Warmups/Zusatzsätzen; kein automatisch erfundener Satz. Ergebnis stimmt mit gespeichertem Verlauf überein. Teiltraining bleibt erlaubt. Keine verpflichtende Bewertung oder Teilen-Seite.

Referenz: [Strong Teilabschluss](./evidence/strong-finish-partial.png). Gegenbeispiel: Hevy zeigte im Test ebenfalls keine gesonderte Warnung, jedoch eine prüfbare Ergebnisvorschau. Beleg IronLog: [Abschlussdialog](./evidence/ironlog-android-finish.png), [persistierte Details](./evidence/ironlog-android-history-detail.png).

### P2 · Einzelne Satzvorgaben planen

**UI plus Code:** IronLogs geprüfte Planeditoren arbeiten mit Satzanzahl, Wiederholungen und Gewicht je Übung. Individuelle Warmup-, Topset- und Backoff-Ziele sind darin nicht als einzelne Vorgabezeilen vorhanden. Satztypen im Logging sind bereits vorhanden; das ist eine andere Fähigkeit.

**Vorschlag:** „3×10“ als einfachen Einstieg behalten; optional in einzelne Zeilen auflösen, beispielsweise Warmup 20×10, Warmup 40×5, Arbeit 60×8, Backoff 55×10. Vorgegebene Werte, tatsächliche Leistung und Progressionsregel getrennt speichern/erklären.

**Abnahme:** Unterschiedliche Sollwerte bleiben nach Speichern/Neustart und Trainingsstart erhalten. Warmups zählen nicht versehentlich als normale Progressionsarbeitssätze. Hevy, Strong, JEFIT und FitNotes zeigen individuelle Satzzeilen bereits praktisch; FitNotes zusätzlich unterschiedliche Vorbelegungsmodi. [FitNotes-Routinen](https://www.fitnotesapp.com/routines/).

### P2 · Training und Vorlage gezielt wiederverwenden

Strong fragte im ersten Teilabschluss konkret nach entfernten Übungen/Sätzen und erlaubte „Keep original“. Hevy und JEFIT zeigen dagegen standardmäßig aktivierte Übernahmeoptionen für Werte; Hevy bewahrte im geprüften Fall die unvollständig absolvierten Vorlageübungen.

**IronLog-Status:** Plan duplizieren, aus Historie erstellen und für heute ersetzen wurden nicht appweit vollständig geprüft. Im untersuchten Editor kein vollständiger solcher Ablauf gefunden; deshalb **Prüfkandidaten, keine sicher bewiesenen fehlenden Funktionen**.

**Vorschlag:** Wiederverwendung und „Gerät belegt → Übung ersetzen“ als ausdrückliche Aktionen prüfen. Vorlagenänderungen als nachvollziehbaren Unterschied darstellen: nur heute, nur Werte übernehmen oder Struktur ändern. Keine neue automatische Planänderung aus diesem Audit ableiten.

**Abnahme:** Ein vorzeitig beendetes Training löscht keine geplanten Übungen ohne bewusste Entscheidung. Beim Übungstausch bleibt die ursprüngliche Vorlage auf Wunsch unverändert. Vorhandene Progression bleibt erklärbar und editierbar.

## 3. Funktionen über den Kernablauf hinaus

| Bereich | IronLog-Befund | Konkurrenznachweis | Einordnung |
|---|---|---|---|
| Satztypen, Supersets, RPE/RIR, Timer, Scheibenrechner | Im aktuellen Code vorhanden; Android-Timer/Scheibenanzeige/Korrektur praktisch gesehen | Ebenfalls verbreitet; nicht jedes Detail ausgeführt | **Keine pauschale Funktionslücke.** Zugänglichkeit und Plattformdetails verbessern. |
| Progression, Deload, Meta-Plan-Rotation | Aktueller Code vorhanden | Fitbod generiert Vorschau; langfristige Anpassung nicht getestet | Bestehende Stärke. Kein Beleg, dass IronLog die Trainingslogik der Konkurrenz kopieren muss. |
| Health, Health Connect, Watch, Live Activities | Keine einschlägigen Integrationssymbole in gezielter Suche der Produktquellen | Hevy/JEFIT/Fitbod bieten Health-Verbindung im Onboarding; native iOS-Funktionen bei Hevy/GymBook dokumentiert | Wahrscheinliche Ökosystemlücke; tatsächliche Synchronisation und Watch-Logging nicht getestet. |
| Gerätespezifische Planumgebung | Kategorien vorhanden; eigenes Geräteprofil im geprüften Ablauf nicht gesehen | Fitbod-Equipment und Gewichtsverfügbarkeit tatsächlich gesehen | Potenziell nützlich für Homegym/Gym-Wechsel und Ersatzvorschläge. |
| Zeit-/Distanzsätze | Geprüftes WorkoutSet-Modell enthält Gewicht/Wdh., keine Satzdauer/Distanz | Anbieter-Funktionsumfänge ergänzend prüfen | Konkrete Modellgrenze für Planks/Cardio; Sessiontimer ist keine Satzdauer. |
| Übungsanleitungen/Medien | Im geprüften Übungsmodell keine Medienreferenz; Notizen schon vorhanden | Strong Preferences mit Erklärung/Abbildung/Videoeinstieg; JEFIT mit großer Suchauswahl | Inhaltslücke wahrscheinlich. Videos nicht abgespielt und Qualität nicht bewertet. |
| Fremdformat-Import/Migration | Kein dedizierter Fremd-CSV-Importer in gezielter Suche gefunden | Ökosystem-/Exportangebote dokumentiert | Offener Produktprüfpunkt; ein IronLog-Backup ersetzt keinen Wechselimport. |
| Community | Für IronLog kein entsprechender Ablauf geprüft | Hevy Visibility und JEFIT Community-Schalter direkt gesehen | Nicht automatisch priorisieren. Im Test vor Veröffentlichung deaktiviert/privat gesetzt. |

Quelltextanker: [WorkoutSet](../../../core/model/src/main/java/com/ironlog/app/domain/model/WorkoutSet.kt), [iOS-Satzeditor](../../../iosApp/IronLogIOS/Workout/IOSWorkoutSetEditor.swift). Suchbefunde sind auf den untersuchten Arbeitsbaum und Begriffe begrenzt.

**Dokumentierte iOS-Ergänzung:** GymBooks Store nennt QuickLog, differenzierte Satzvorgaben, Apple Watch, Live Activities und Health/Strava sowie CSV/XML-Export. Das sind mögliche Vergleichspunkte, keine getesteten Integrationen oder bestätigten kostenlosen Funktionen. [GymBook App Store](https://apps.apple.com/us/app/gymbook-strength-training/id650113307).

Fitbod beschreibt Ersatzübungen, Equipment-/Nutzungsfilter, Umordnen und Supersets; Gewichte innerhalb einer Übung vereinheitlichen ist laut Anleitung nur auf iOS verfügbar. Auch bei der Konkurrenz darf Plattformgleichheit deshalb nicht unterstellt werden. [Fitbod: Editing Workouts, Juni 2026](https://help.fitbod.me/hc/en-us/articles/360006335593-Editing-Workouts-in-Fitbod).

## 4. Empfohlene Reihenfolge

1. **Logging im Alltag:** letzte Eingabe sinnvoll anbieten, Bestätigen im sichtbaren Bereich, kompakte Satzübersicht, sichtbare Beschriftung auf iOS.
2. **Planen und Abschließen:** Mehrfachauswahl, offene Sätze und gespeichertes Ergebnis anzeigen.
3. **Fortgeschrittene Planung:** einzelne Satzvorgaben, kontrollierte Wiederverwendung und Ersatzübung nach weiterem Bestandscheck.
4. **Ökosystem nach Nutzerbedarf:** Health/Watch, Geräteprofile, Inhalte und Migration einzeln priorisieren. Umfangreiche Community und automatische Planerzeugung ergeben sich daraus nicht als Pflicht.

Für den ersten Entwurf sollten identische Aufgaben in aktuellem und vorgeschlagenem IronLog gemessen werden: Plan mit drei Übungen, drei identische Sätze, eine Korrektur, Teilabschluss. Messen: Eingaben, Scrollen, Rückwege, Fehlinterpretationen. Noch keine behaupteten Einsparprozente, keine Produktionsänderung durch dieses Audit.

## Offene Testgrenzen

Fitbod-Logging benötigt in der konkreten Installation einen freigegebenen Zugang hinter der Abo-Seite. Native iOS-Konkurrenztests benötigen ein echtes Gerät oder Hersteller-Simulator-Builds. Nicht vollständig geprüft: Premiumfeatures, Warmup-/Drop-/Failure-Kombinationen, Superset-Ausführung, tatsächliche Ersatzübung, Offline- und Prozessabbruch während aktiver Eingabe, Barrierefreiheit, Cloud-/Gerätesynchronisation und langfristige Progression. Einzelne sichtbare Menüs werden hierfür ausdrücklich nicht als bestandener Test gezählt.
