# Gezielte Validierung der Readiness-Integration

## Hauptcheckout

- Gemeinsamer Kern, portable Daten, Store-Persistenz und Projektion: 83 Tests, 0 Fehler in sieben Klassen. `build/readiness-2026-09-11/shared-final-tests.log`.
- Android-Dashboard einschließlich Abbrechen, Fehlerzustand, asynchroner Planauflösung und Tageswechsel: 53 Tests, 0 Fehler. `build/readiness-2026-09-11/dashboard-final-tests.log`.
- iOS: acht fokussierte Simulator-Tests für lokalen Tageswechsel und Deload-Startkontext erfolgreich. Der gesamte App- und Testtarget wurde dabei gebaut.
- iOS 1.2.0 (3) im iPhone-17-Simulator gebaut, installiert und gestartet. Kompakte Trainingskarte mit fehlender Evidenz zeigt einen Strich; alle Details bleiben aufklappbar.
- Live-Check-in: zuerst alle Felder offen; nur Energie=4 gewählt und gespeichert. Erneutes Öffnen zeigt Energie=4, Schlaf und Stress bleiben offen. Die persistierte JSON-Datei enthält entsprechende null-Werte und einen datierten Datensatz. Testdatensatz danach gezielt entfernt; bestehender Trainingsgraph erhalten.
- Tagesmuskelbezug im Simulator: Brust als geplant markiert, historische andere Muskeln getrennt. Last und rollierende sieben Tage werden explizit benannt.
- Screenshots: `build/readiness-2026-09-11/ios-dashboard-1.2.0.jpg` und `ios-checkin-1.2.0.jpg`.

- Android abschließend im Hauptcheckout: 44 gezielte Daten-/Backup-/Präferenztests und 155 Tests für Workout-Repository, aktives Training, History und Backup-Validator, jeweils ohne Fehler. Darin sieben Historytests einschließlich nachträglicher Absicht ohne Änderung des abgeschlossenen Trainings und Fehlerbehandlung. `build/readiness-2026-09-11/android-final-build-tests.log`.
- Keine breite Gesamtsuite oder vollständige CI-Freigabe behauptet. Nur die für diese Änderung relevanten Prüfungen wurden im Hauptcheckout ausgeführt.

## Grenzen

- Android Debug- und signierter Release-Build erfolgreich. APK 1.2.0 (6), Signatur SHA-256 `69593f52950e1e081630bbc09b37800bd4a26ce03b84800cf8529196f6292a41`, identisch zum bisherigen Release. APK SHA-256 `ca99ef491475d98fbdf03bebcb0cfedaf18a4b1fbbcf5e1439d33a00f2ceb192`. Datei: `/Users/maert/Documents/IronLog-Analysen/2026-09-11/release/IronLog-1.2.0.apk`. Buildprotokoll: `build/readiness-2026-09-11/android-release-build.log`.
- Android-Gerät 24090RA29G am 11.09.2026 um 23:37 Uhr aktualisiert: `adb install -r` von 1.1.3 (5) auf 1.2.0 (6) erfolgreich. Zertifikate vor Installation identisch; installierte APK danach mit identischem SHA-256 zum Release geprüft. Erstinstallation weiterhin 24.03.2026 20:18:29, keine Appdaten gelöscht. Kaltstart erfolgreich; Startseite zeigt vorhandenen Plan Tag1 und analysierte Trainingshistorie. Damit ist der reale Upgrade-/Datenbankstartpfad geprüft; keine vollständige Prüfung aller historischen Datensätze. Im anschließenden Prozesslog keine Fatal-, Exception-, SQLite-, Migrations- oder Crash-Treffer. Screenshot: `build/readiness-2026-09-11/phone-install/dashboard.png`.
- TestFlight/physisches iOS-Gerät wurden nicht geprüft; keine signierte Distribution erstellt.
- HTML-Entwürfe separat geliefert und statisch geprüft. Automatisierte Browser-Darstellung wurde von der URL-Sicherheitsprüfung blockiert; kein visuelles Browser-QA behauptet.
