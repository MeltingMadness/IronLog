# IronLog iOS

Dieses Verzeichnis enthaelt die native SwiftUI-iPhone-App fuer IronLog.

## Lokale Generierung

1. `brew install xcodegen`
2. `cd iosApp`
3. `xcodegen generate`
4. `open IronLogIOS.xcodeproj`

Das Projekt bindet das KMP-Framework aus `:shared` ueber einen Build-Script-Phase ein.

## Build-Hinweise

- Fuer Simulator-Builds kann `CODE_SIGNING_ALLOWED=NO` verwendet werden.
- Fuer TestFlight sind Apple-Team, Bundle ID, Provisioning und App Store Connect Secrets erforderlich.
