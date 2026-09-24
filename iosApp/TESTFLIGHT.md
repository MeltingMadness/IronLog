# IronLog über TestFlight testen

Stand: 11. September 2026. Verteilung ist noch nicht freigeschaltet: Es besteht laut Nutzer noch keine Mitgliedschaft im Apple Developer Program; lokal gibt es kein gültiges Signierzertifikat und kein eingetragenes Entwicklerteam. Es wurde kein Build hochgeladen und keine Einladung verschickt.

## Bereits vorbereitete App

- Bundle-ID im Projekt: `com.ironlog.ios` (Verfügbarkeit/Zuordnung im späteren Team noch nicht bestätigt).
- Mindestversion: iOS 17; native SwiftUI-App mit gemeinsamem Kotlin-Kern.
- Version: 1.1.3, Build 2.
- Privacy-Manifest beschreibt app-eigene UserDefaults für Einstellungen und Timer mit `CA92.1`.
- Simulator-Builds sind keine auf iPhones installierbaren TestFlight-Builds. Ein unsigniertes Gerätearchiv ist ebenfalls noch kein hochladbarer TestFlight-Build.

## Nach Aktivierung der Mitgliedschaft

1. Den zugehörigen Apple-Account in Xcode → Settings → Accounts anmelden und das Entwicklerteam auswählen.
2. Bundle-ID im Team prüfen/registrieren und den App-Eintrag für IronLog in App Store Connect anlegen. Keine fremde Bundle-ID überschreiben.
3. In Xcode die Scheme IronLogIOS und Any iOS Device wählen, unter Signing & Capabilities das Team setzen. Anschließend Product → Archive. Bei Projekt-Neugenerierung wird das aktuell noch leere Team aus dem Generator übernommen; vor automatisierten Builds daher `DEVELOPMENT_TEAM=<TEAM_ID>` ausdrücklich übergeben oder die lokale Team-Konfiguration vorher pflegen.
4. Im Organizer das signierte Archiv validieren und über App Store Connect hochladen. Nicht „TestFlight Internal Only“ wählen, da Freunde externe Tester sein sollen.
5. Nach Verarbeitung des Builds Export-Compliance beantworten, Beta-Beschreibung und tatsächliche Kontaktangaben ergänzen. Die App benötigt keinen Login; Zugangsdaten sind für die Prüfung nicht erforderlich.
6. Eine externe Testgruppe anlegen, Build hinzufügen und zur Beta-Prüfung einreichen. Erst nach Freigabe einen Einladungslink bereitstellen oder die vom Nutzer benannten Tester einladen.

Keine erfundenen Kontaktdaten oder pauschalen Compliance-Antworten eintragen. Die Mitgliedschaft, Anmeldung und Apple-Freigabe lassen sich durch ein lokal erzeugtes IPA nicht ersetzen.

## Entwurf für „Was soll getestet werden?“

Bitte testet Trainingspläne und Meta-Rotationen, Satzlogging und RPE-Eingabe, Pausentimer einschließlich Deload, Verlauf und Muskelgruppen-Wochenvolumen sowie Progressionsvorschläge. Achtet besonders auf die Gewichtsreihe 4 / 5,5 / 7 / 8,5 kg und das gezielte Steigern des Gewichts. Bei Problemen bitte Bildschirm, Schritte und erwartetes Ergebnis nennen. Für Import-/Wiederherstellungstests zunächst ein Backup erstellen und möglichst Testdaten verwenden.

## Apple-Quellen

- [Voraussetzungen für Beta-Verteilung](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)
- [Externe TestFlight-Tester](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers)
- [UserDefaults-Grund CA92.1](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)
