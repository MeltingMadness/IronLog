# Thrym über TestFlight testen

Stand der Verteilung: Es wurde noch kein Build über TestFlight verteilt. Voraussetzung ist eine aktive Mitgliedschaft im Apple Developer Program mit eingetragenem Team und gültigem Signierzertifikat. Unter der früheren Bundle-ID `com.ironlog.ios` wurde nie ein Build hochgeladen, es gibt also keinen App-Eintrag, der weitergeführt werden müsste.

## Bereits vorbereitete App

- Bundle-ID: `com.thrym.ios` (aus `iosApp/Config/Signing.xcconfig`, `THRYM_BUNDLE_ID`). In App Store Connect ist dafür ein **neuer App-Eintrag** nötig. Verfügbarkeit und Zuordnung zum späteren Team sind noch nicht bestätigt.
- Mindestversion: iOS 17; native SwiftUI-App mit gemeinsamem Kotlin-Kern.
- Version und Build: `CFBundleShortVersionString` und `CFBundleVersion` in `iosApp/ThrymIOS/Info.plist`. Jeder Upload braucht eine höhere Build-Nummer als der vorige.
- Sprachen: Englisch und Deutsch.
- Privacy-Manifest (`iosApp/ThrymIOS/PrivacyInfo.xcprivacy`) beschreibt app-eigene UserDefaults für Einstellungen und Timer mit `CA92.1`.
- Simulator-Builds sind keine auf iPhones installierbaren TestFlight-Builds. Ein unsigniertes Gerätearchiv ist ebenfalls noch kein hochladbarer TestFlight-Build.

## Ersten TestFlight-Build hochladen

1. Den zugehörigen Apple-Account in Xcode → Settings → Accounts anmelden und die Team-ID notieren.
2. `iosApp/Local.xcconfig` aus `iosApp/Local.xcconfig.example` anlegen (git-ignoriert) und dort `DEVELOPMENT_TEAM` setzen. `THRYM_BUNDLE_ID` für die offizielle App nicht überschreiben, damit `com.thrym.ios` aus `Signing.xcconfig` gilt. Die Team-ID gehört nicht ins Repository.
3. Bundle-ID `com.thrym.ios` im Team registrieren und den App-Eintrag für Thrym in App Store Connect anlegen. Keine fremde Bundle-ID überschreiben.
4. `python3 iosApp/generate_project.py` ausführen, in Xcode die Scheme `ThrymIOS` und „Any iOS Device“ wählen und unter Signing & Capabilities prüfen, dass Team und Bundle-ID stimmen. Anschließend Product → Archive. `JAVA_HOME` muss auf JDK 17 zeigen, weil der Build `Shared.framework` mit Gradle erzeugt.
5. Im Organizer das signierte Archiv validieren und über App Store Connect hochladen. Nicht „TestFlight Internal Only“ wählen, wenn externe Tester eingeladen werden sollen.
6. Nach Verarbeitung des Builds Export-Compliance beantworten, Beta-Beschreibung und tatsächliche Kontaktangaben ergänzen. Die App benötigt keinen Login; Zugangsdaten sind für die Prüfung nicht erforderlich.
7. Eine externe Testgruppe anlegen, Build hinzufügen und zur Beta-Prüfung einreichen. Erst nach Freigabe einen Einladungslink bereitstellen oder die vom Maintainer benannten Tester einladen.

Keine erfundenen Kontaktdaten oder pauschalen Compliance-Antworten eintragen. Die Mitgliedschaft, Anmeldung und Apple-Freigabe lassen sich durch ein lokal erzeugtes IPA nicht ersetzen.

## Entwurf für „Was soll getestet werden?“

Bitte testet Trainingspläne und Meta-Rotationen, Satzlogging und RPE-Eingabe, Pausentimer einschließlich Deload, Verlauf und Muskelgruppen-Wochenvolumen sowie Progressionsvorschläge. Achtet besonders auf die Gewichtsreihe 4 / 5,5 / 7 / 8,5 kg und das gezielte Steigern des Gewichts. Die App gibt es auf Englisch und Deutsch; meldet fehlende oder falsche Übersetzungen. Bei Problemen bitte Bildschirm, Schritte und erwartetes Ergebnis nennen. Für Import-/Wiederherstellungstests zunächst ein Backup erstellen und möglichst Testdaten verwenden. Wer IronLog genutzt hat: dort ein Backup exportieren und in Thrym importieren. Einstellungen sind nicht im Backup und müssen neu gesetzt werden.

## Apple-Quellen

- [Voraussetzungen für Beta-Verteilung](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)
- [Externe TestFlight-Tester](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers)
- [UserDefaults-Grund CA92.1](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)
