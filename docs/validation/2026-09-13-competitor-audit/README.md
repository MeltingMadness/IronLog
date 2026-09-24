# IronLog – Vergleich von Trainingsloggern

Stand: 13. September 2026. **Vier Android-Konkurrenten mit echten Trainingslogs geprüft; Fitbod bis zur Abo-Seite. IronLog auf Android und iOS praktisch geprüft. Native iOS-Konkurrenztests stehen aus.**

[Visueller Vergleich](./vergleich.html) · [Befunde und Prioritäten](./befunde.md) · [Android-Prüfprotokoll](./evidence/android-protokoll.md) · [IronLog-iOS-Protokoll](./evidence/ironlog-ios-protokoll.md)

## Tatsächlich geprüfter Umfang

| App | Installierte Android-Version | Planerstellung | Trainingslogging | Weitere Nachweise |
|---|---|---|---|---|
| Hevy | 3.1.14 | Eigene Routine, 3 Übungen, Mehrfachauswahl, individuelle Bench-Sätze | Zwei Sessions gespeichert, Vorwerte, bestätigten Satz korrigiert | Kaltstart, Superset eingerichtet, private Sichtbarkeit, Routine-Übernahme |
| Strong | 6.2.4 | Eigene Vorlage, 3 Übungen, Mehrfachauswahl, individuelle Bench-Sätze | Zwei Sessions gespeichert, Vorwerte, Korrektur mit erneutem Abhaken | Resttimer, Teilabschluss, Vorlage bewusst behalten, Kaltstart |
| JEFIT | 17.4.4 | Eigener Plan/Tag, 3 Übungen, Filter und Mehrfachauswahl | Zwei Bench-Sätze gespeichert, Folgewertübernahme | Resttimer/Skip, Abschluss/Volumen, Übernahmeoptionen, Community-Schalter |
| FitNotes | 25.1 | Routine/Tag, 3 Übungen, unterschiedliche Vorbelegungsmodi | Tageslogs gespeichert, gleicher Satz erneut, Korrektur | Kaltstart; Standardmodus ohne getrennte aktive Session |
| Fitbod | 8.33.0-0 | Onboarding, Equipment, Kraftwerte, erzeugter Plan | **Nicht erreichbar: Abo-Seite** | Google-Profil erstellt; Zurück/Neustart beseitigen Abo-Seite nicht |
| IronLog Android | 1.2.0 (6), frisch gebaut | Eigener Plan, 3 Übungen, Einzelwahl mit Filtern | Zwei Sätze, Folgesatz leer, Korrektur, Teilabschluss | Kaltstart/Verlauf: 60×10 + 60×9 = 1140kg |
| IronLog iOS | aktueller Simulator-Build | Eigener Plan, 1 Übung, Einzelwahl | Ein Satz, zweiter Editor mit 0kg, Teilabschluss | Verlauf: 1 Satz, 600kg |

Kein Gleichsetzen von 'Menü gesehen' mit 'Funktion ausgeführt'. Grenzen je App im Protokoll. Nicht jede Funktion, Kombination oder Premiumstufe ist getestet. Keine belastbare Zeit- oder Tap-Rangliste: Onboarding, Einheiten und Vorgaben waren nicht vollständig identisch. Für Warmups, alternierende Superset-Ausführung, Offline-/Crash-Recovery, Gerätewechsel und Langzeitprogression bleiben ergänzende Tests offen.

## Auswahl und Evidenz

Hevy und JEFIT: jeweils 5Mio.+ Google-Play-Downloads; Strong, Fitbod und FitNotes: jeweils 1Mio.+ im recherchierten Store-Snapshot. Das sind Auswahlindikatoren, keine Rangliste aktiver Nutzer oder 'der beliebtesten' Apps auf iOS. FitNotes von James Gay ist Android-spezifisch. GymBook von Appwise dient ergänzend als dokumentierte native iOS-Referenz, nicht als behaupteter Marktführer. Store-Links unten.

**UI** bedeutet selbst bedient, mit XML/Screenshot im evidence-Ordner. **Code** bezeichnet den lokalen IronLog-Arbeitsbaum. **Dokumentation** stammt aus offiziellen Anleitungen/Store-Seiten. **Offen** bedeutet fehlenden Nachweis oder eine konkret beobachtete Zugangsschranke. Anbieterangaben sind keine selbst durchgeführten Tests.

## Umgebung und Zugang

Android neu eingerichtet: Emulator37.1.11, Google-Play-ARM64 API35 ImageRevision9, Pixel7-Profil, AVD IronLog_Competitor_API35, emulator-5554. Alle fünf Konkurrenten letztlich aus dem offiziellen Play Store installiert, Installer com.android.vending; Versionsbeleg in [android-installed-versions.json](./evidence/android-installed-versions.json). Die zuvor erfolglosen APKMirror-/Uptodown-Versuche waren danach nicht mehr erforderlich.

Der Nutzer hat Google Play selbst angemeldet sowie Google-Anmeldungen und kostenlose Testprofile freigegeben. Keine Abos gestartet. Health-, Kontakte- und Social-Veröffentlichungen wurden nicht beauftragt; entsprechende Schritte übersprungen bzw. deaktiviert. Testprofile und Testtrainings existieren weiter in den Apps.

iOS: Runtime26.5, eigener iPhone17-Simulator IronLog-Competitor-Audit, UDID4850735A-26BB-4E86-99E8-8153574036C5. Normale App-Store-Geräte-Binaries können nicht als Simulator-Builds behandelt werden. **Kein iOS-Konkurrent installiert oder nativ bedient.** Dafür wird ein echtes Gerät oder ein Hersteller-Simulator-Build benötigt. Aus Android wird keine iOS-Parität abgeleitet.

IronLog-Checkout: HEAD a0b72ea3d4ec94961b7a544f9ee94a84ca1b2893 mit umfangreichen bereits vorhandenen lokalen Änderungen. Nur Auditdateien bearbeitet. Android mit JDK17/:app:assembleDebug erfolgreich frisch gebaut und installiert; /tmp/ironlog-audit-android-build.log. iOS über XcodeBuildMCP frisch gebaut und auf isoliertem Simulator gestartet; [Runtime-Beleg](./evidence/ironlog-ios-runtime.json). Dieses Audit ist keine CI-/Release-Freigabe.

## Noch blockierte Arbeit

- **Fitbod-Logging:** Die konkrete Installation fordert einen siebentägigen Testzugang mit anschließend109,99€/Jahr. Ein kostenloses Profil autorisiert kein solches Probeabo. Die aktuell sichtbare Abo-Seite ist [belegt](./evidence/fitbod-paywall-back.png).
- **Native iOS-Konkurrenzprüfung:** Gerätezugang oder Hersteller-Simulator-Build fehlt. GymBook bleibt reine Dokumentationsreferenz.

## Quellen

- [Hevy – Google Play](https://play.google.com/store/apps/details?id=com.hevy&hl=en)
- [Strong – Google Play](https://play.google.com/store/apps/details?id=io.strongapp.strong&hl=en)
- [JEFIT – Google Play](https://play.google.com/store/apps/details?id=je.fit&hl=en)
- [Fitbod – Google Play](https://play.google.com/store/apps/details?id=com.fitbod.fitbod&hl=en)
- [FitNotes – Google Play](https://play.google.com/store/apps/details?id=com.github.jamesgay.fitnotes&hl=en)
- [GymBook – App Store](https://apps.apple.com/us/app/gymbook-strength-training/id650113307)
- [Hevy: vorherige Leistung](https://www.hevyapp.com/features/track-exercises/)
- [Hevy: Funktionsübersicht](https://www.hevyapp.com/features/)
- [Strong: Templates](https://help.strongapp.io/article/105-about-templates) – Anleitung zuletzt 2021 aktualisiert; aktuelle UI gesondert prüfen.
- [Strong: Template nach Training aktualisieren](https://help.strongapp.io/article/177-update-template) – gleicher Altersvorbehalt.
- [FitNotes: Routinen](https://www.fitnotesapp.com/routines/)
- [Fitbod: Training bearbeiten](https://help.fitbod.me/hc/en-us/articles/360006335593-Editing-Workouts-in-Fitbod) – aktualisiert Juni 2026; nennt auch Unterschiede Android/iOS.
- [JEFIT: Eigene Pläne](https://www.jefit.com/blog/how-to-create-custom-workout-plans) – Anleitung aus 2022, keine aktuelle UI-Evidenz.
- [Apple: App auf simulierten oder echten Geräten ausführen](https://developer.apple.com/documentation/Xcode/running-your-app-on-simulated-or-physical-devices)

- [Fitbod: aktuelle Probezeit](https://help.fitbod.me/hc/en-us/articles/30542136101527-How-the-Trial-Works) – die ältere Werbung für drei freie Workouts ist kein Nachweis eines zugänglichen freien Pfads dieser Installation.

## Prüfung der Berichtdateien

51 lokale Bild-/Dateiverweise in README, Befunden und HTML geprüft, keine fehlenden Ziele. Ausgewählte Original-Screenshots visuell geprüft. HTML für lokale Darstellung und Druck aufgebaut; eine Browser-Darstellungsprüfung war nicht möglich: Die Browser-URL-Sicherheitsrichtlinie hat das Öffnen der lokalen file-URL abgewiesen. Kein Umgehungsversuch. Darstellung, Drucklayout und Responsive-Verhalten deshalb nicht als im Browser getestet ausgewiesen.

## Entscheidung vom 14. September 2026

Der Nutzer verzichtet ausdrücklich auf das Fitbod-Probeabo. Fitbods Logging bleibt außerhalb des praktisch geprüften Umfangs; dafür wird kein Abo gestartet. Die Emulatoren wurden vom Nutzer beendet und der Mac neu gestartet. Die Auswertung verwendet die gespeicherten Testbelege vom 13. September; es wurde keine neue Emulatorprüfung begonnen.
