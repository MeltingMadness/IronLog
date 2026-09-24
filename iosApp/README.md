# IronLog iOS

Dieses Verzeichnis enthält die native SwiftUI-iPhone-App für IronLog. Die
Swift-Dateien werden direkt aus `IronLogIOS/` und `IronLogIOSTests/` inventarisiert;
neue Swift-Dateien müssen deshalb nicht in einer zweiten Liste gepflegt werden.

## Projekt generieren

Die reproduzierbare Generierung benötigt nur Python 3 aus macOS:

```sh
cd iosApp
./generate_project.py
open IronLogIOS.xcodeproj
```

`project.yml` bleibt als lesbare XcodeGen-Projektbeschreibung erhalten. Die
dependency-freie Generierung spiegelt daraus die aktuellen Optionen
(iOS 17, Swift 5.10, Bundle-ID und Shared-Framework-Pfad) in
`IronLogIOS.xcodeproj/project.pbxproj`. Sie erzeugt keine Abhängigkeiten und
führt keinen Build aus. Nach neuen Swift-Dateien genügt ein erneuter Aufruf.

Das KMP-Framework wird über die Build-Phase `Build Shared Framework` erzeugt.
`iosApp/scripts/build-shared.sh` sucht zuerst ein gültiges `JAVA_HOME` für JDK
17, danach `/usr/libexec/java_home -v 17` und vorhandene Homebrew-JDK-17-Pfade.
Es installiert nichts. Das erzeugte `Shared.framework` wird nur verlinkt; für
das statische Framework gibt es keine zusätzliche Embed-/Copy-Phase.

## Native Checks mit vollständigem Xcode

Die folgenden Befehle sind die überprüfbaren Voraussetzungen und Checks auf
dem Mac, auf dem Xcode installiert ist:

```sh
xcode-select -p
xcodebuild -version
xcrun simctl list devices available
```

Projekt generieren und einen signierungsfreien Simulator-Build starten:

```sh
cd iosApp
./generate_project.py
xcodebuild \
  -project IronLogIOS.xcodeproj \
  -scheme IronLogIOS \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Für einen gezielten Testlauf zunächst eine verfügbare Simulator-ID ausgeben
und die für die Änderung relevante Testklasse auswählen, zum Beispiel:

```sh
SIMULATOR_UDID="$(xcrun simctl list devices available | awk -F '[()]' '/iPhone/ { print $2; exit }')"
test -n "$SIMULATOR_UDID"
xcodebuild \
  -project IronLogIOS.xcodeproj \
  -scheme IronLogIOS \
  -destination "id=$SIMULATOR_UDID" \
  CODE_SIGNING_ALLOWED=NO \
  -only-testing:IronLogIOSTests/BackupBridgeTests \
  test
```

Am 9. September 2026 wurden Build und Start mit Xcode 26.6 im
iPhone-17-Simulator (iOS 26.5) bestätigt. Den konkreten Funktionsstand und die
Grenzen der Abnahme dokumentiert [ios-feature-parity.md](../docs/ios-feature-parity.md).
In einer Umgebung mit ausschließlich CommandLineTools lässt sich das Projekt
erzeugen, aber nicht als iOS-App bauen oder ausführen.

Für TestFlight werden zusätzlich Apple-Team, Bundle-ID, Provisioning und
App-Store-Connect-Zugang benötigt.
