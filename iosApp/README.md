# IronLog iOS

Native SwiftUI-App für das iPhone. Sie bindet das KMP-Framework aus `:shared` ein.

## Stand

Das ist ein **Gerüst**, keine nutzbare Trainings-App:

- **Umgesetzt:** Einstellungen (Anzeige, Training, Erinnerungen, Diagnose, Incident-Report). Sie werden in `NSUserDefaults` gespeichert.
- **Platzhalter:** Dashboard, Workout, Verlauf und Pläne zeigen nur einen Titel.
- **Backup-Export/-Import** ist in der Oberfläche vorhanden. Die iOS-Implementierung in `shared/src/iosMain/.../IosSettingsFeature.kt` meldet aber „noch nicht unterstützt“.
- Es gibt keine lokale Datenbank auf iOS.

## Lokale Generierung (macOS)

1. `brew install xcodegen`
2. `cd iosApp`
3. `xcodegen generate`
4. `open IronLogIOS.xcodeproj`

Das Projekt (`project.yml`, iOS 17) bindet `shared/build/xcode-frameworks/.../Shared.framework` über eine Build-Script-Phase ein.

## Build-Hinweise

- Für Simulator-Builds kann `CODE_SIGNING_ALLOWED=NO` verwendet werden.
- Für TestFlight sind Apple-Team, Bundle ID, Provisioning und App-Store-Connect-Secrets erforderlich.
- iOS wird in der CI nicht gebaut.
