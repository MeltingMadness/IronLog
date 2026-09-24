# IronLog – Paritätskorrekturen vom 11.09.2026

Ausgangspunkt: [Android/iOS-Audit](2026-09-11-android-ios-parity.md). Dieser Bericht beschreibt die Korrekturen der gefundenen Fehler; er behauptet keine vollständige optische Identität oder erneute vollständige Geräteprüfung.

## Korrigierte Fehlerbereiche

| Bereich | Plattform | Änderung |
|---|---|---|
| Intensität beim Satzbearbeiten | iOS | Ausgeblendete RPE-Werte bleiben erhalten; sichtbare leere Eingaben können gelöscht werden; RPE/RIR-Pläne erzwingen eine passende Eingabemöglichkeit. |
| Konflikt mit aktiver Sitzung | Shared/iOS | Angegebener Plan und Metaplan müssen zur aktiven Sitzung passen; allgemeines Fortsetzen bleibt möglich. |
| Veraltete Progressionsentscheidung | Android | Ein neuerer offener Vorschlag verhindert die Einzelübernahme eines älteren Vorschlags für denselben Planslot. |
| Wiederholte Rotationspositionen | Android | A/B/A bleibt beim Laden, Bearbeiten und Speichern erhalten; Entfernen erfolgt positionsbezogen. |
| Timer-Endbedingung | Android | Normale Arbeitssätze und effektive Deload-Ziele bestimmen, ob eine weitere Pause nötig ist. |
| Historische Statistik | Android | Abgeschlossene Sitzungen und obere Zeitgrenzen schließen aktive oder zukünftige Daten aus; ein zukünftiger letzter Datensatz verdeckt ältere gültige Trainings nicht. |
| Backoff | Android | Der Coach verwendet den konfigurierten Prozentsatz. |
| Readiness | Android | Anzeige entspricht 100 minus Ermüdung; unauffällig ergibt 100 %, fehlende Evidenz bleibt als Fragezeichen erkennbar. Muskelgruppenreihenfolge folgt Shared Analytics. |
| Datumsgrenzen und Timer-Wiederherstellung | iOS/Android | Historienfilter beginnen an lokalen Tagesgrenzen; Android-Timer bleiben über ViewModel-/Prozessneustarts gespeichert. Sitzungsschlüssel und serialisierte Mutationen schützen vor veralteter Wiederherstellung. |
| Nachbereitung nach Import | Android | Fehlende Progressionsauswertungen werden erzeugt und abgeglichen. Ein Nachbereitungsfehler macht einen bereits erfolgreichen Import nicht rückwirkend zum Fehler. |

Zusätzlich: 600 Sekunden als Android-Pausenoption; iOS-Privacy-Manifest für UserDefaults; Versionsstände Android 1.1.3 (5), iOS 1.1.3 (2).

## Gezielte Validierung

| Prüfung | Ergebnis |
|---|---|
| ActiveWorkoutViewModelTest | 92 erfolgreich |
| DashboardViewModelTest | 42 erfolgreich |
| MetaPlanEditorViewModelTest | 5 erfolgreich |
| SettingsViewModelTest | 21 erfolgreich |
| ExerciseStatsViewModelTest | 8 erfolgreich |
| ProgressionRepositoryImplTest | 52 erfolgreich |
| StatisticsRepositoryImplTest | 2 erfolgreich |
| DeloadRepositoryImplTest | 5 erfolgreich; feste Testuhr ergänzt |
| SharedStateStoreTest, nur StartWorkout-Fälle | 2 erfolgreich |
| IronLogIOSTests, gezielte Klasse | 5 erfolgreich |
| Neue SQL-Abfragen mit SQLite-Testdaten | Grenzen, aktive Sitzungen und zukünftige Daten geprüft; kein Ersatz für Android-Room-Instrumentation |
| iOS Release-Archiv für echtes Gerät | Erfolgreich gebaut, arm64, unsigniert |

Drei Fehler im ersten Android-Testlauf wurden aufgeklärt: Millisekundenpräzision des gespeicherten Timers, fehlender Flow-Collector im Race-Test und eine Fehler-Injektion auf der alten Statistikabfrage. Der erneute Lauf von Training und Dashboard ist erfolgreich. Der iOS-Gerätebuild enthält auch die anschließende Weitergabe des effektiven Intensitätssystems an Satzzeilen.

Insgesamt 229 gezielte Kotlin-/Android-Tests und 5 iOS-Tests erfolgreich; konsolidierte Kotlin-XML-Ergebnisse unter `test-results-final`.

Test- und Buildprotokolle liegen unter `build/parity-2026-09-11/`. Frühere XML-Ergebnisse wurden in `test-results-initial` erhalten; für Training und Dashboard gilt der erfolgreiche erneute Lauf, nicht dessen anfängliche Fehler. Kein vollständiger Test-/Lint-/CI-Durchlauf und keine erneute umfassende Sichtprüfung behauptet.

## Auslieferungsgrenzen

- Android: Signierter Release-Build erfolgreich (1.1.3, Code 5); Zertifikat stimmt mit der zuvor vom Handy gesicherten APK überein (SHA-256 `69593f52950e1e081630bbc09b37800bd4a26ce03b84800cf8529196f6292a41`). Die Installation ist auf ausdrücklichen Nutzerhinweis verschoben: Das Handy wurde aus dem WLAN-Debugging genommen. Kein Update auf dem Handy behauptet. Beim Wiederverbinden nur datenbewahrendes Update und Abgleich der Signatur.
- iOS: `build/parity-2026-09-11/IronLog-1.1.3-unsigned.xcarchive` wurde erfolgreich erstellt. Das Archiv ist unsigniert und keine installierbare TestFlight-Datei. Laut Nutzer besteht bisher nur ein kostenloser Entwickleraccount; kostenpflichtige Mitgliedschaft, Xcode-Anmeldung, Team und Signierung fehlen. Es wurde nichts zu App Store Connect hochgeladen. Weitere Schritte: [TestFlight-Anleitung](../../iosApp/TESTFLIGHT.md).
- Geräteprüfung: Android-Room-Instrumentation, aktualisierte Screenshots sowie die reale Installation/der Kaltstart bleiben bis zur Rückkehr des Nutzers offen.
- App Store: Wiederkehrender Fehler vor dem Download im Apple-Media-Services-Authentifizierungsablauf. App- und Dienstneustart ohne Erfolg; erneute interaktive App-Store-Anmeldung steht aus. Kein Account und keine Caches wurden gelöscht.

Android-Artefakt: `/Users/maert/Documents/IronLog-Analysen/2026-09-11/release/IronLog-1.1.3.apk`

APK SHA-256: `5897414a2139616a8da241c80bf6101b8532b2101fc379cbe5ee60e8f30838a8`

## Nachtrag: Android-Installation abgeschlossen

11.09.2026, 19:53 Uhr: Nutzer hat dasselbe Handy erneut freigegeben. Kopplung erfolgreich; Verbindungsport per Bonjour ermittelt. Signatur der frisch vom Handy gelesenen 1.1.2-APK stimmt mit 1.1.3 überein. `adb install -r` meldet Success. Installiert: Version 1.1.3, Code 5. firstInstallTime unverändert 24.03.2026 20:18:29; lastUpdateTime 11.09.2026 19:53:32. Installierter APK-SHA-256 stimmt mit dem bereitgestellten Artefakt überein.

Kaltstart Status ok, 416 ms; Prozess bleibt aktiv, keine Fatal-/AndroidRuntime-/SQLiteException-Treffer im geprüften Prozesslog. Screenshot `build/parity-2026-09-11/android-installed-1.1.3.png` zeigt die Startseite mit vorhandenem Plan, Trainingszahlen und Readiness-Kreis (100 %). Das ist eine Startseiten-/Startprüfung, kein vollständiger Funktionstest und keine vollständige Prüfung aller gespeicherten Datensätze. Die vorherige Verschiebung der Installation ist damit erledigt; Room-Instrumentation und TestFlight bleiben separat offen.
