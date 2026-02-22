---
description: Build and deploy IronLog to a connected Android device
---
// turbo-all

## Deploy IronLog auf ein verbundenes Android-Gerät

### Voraussetzung
- USB-Debugging auf dem Handy aktiviert
- Handy per USB-Kabel verbunden

### Schritte

1. Prüfe ob ein Gerät verbunden ist:
```
& "C:\Users\maert\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
```

2. Baue die Debug-APK und installiere sie direkt auf dem Gerät:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat installDebug
```

3. Starte die App auf dem Gerät:
```
& "C:\Users\maert\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n "com.ironlog.app/.MainActivity"
```

### Hinweise
- `installDebug` baut die APK UND installiert sie in einem Schritt
- Bei mehreren verbundenen Geräten (z.B. Emulator + Handy): `adb -s <device-id> install ...`
- Für schnellere Iterationen: Nur `installDebug` reicht — es baut nur was sich geändert hat
