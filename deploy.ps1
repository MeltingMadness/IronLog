<#
  IronLog Deploy
  Baut und installiert die App auf dein Handy.

  Nutzung:
    .\deploy.ps1            # Baut + installiert
    .\deploy.ps1 -Launch    # Baut + installiert + startet die App
#>
param([switch]$Launch)

$ADB = "C:\Users\maert\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Handy finden (kein Emulator)
$device = (& $ADB devices | Select-String "device$" | Where-Object { $_ -notmatch "emulator" } | Select-Object -First 1) -replace "\s+device$", ""
if (-not $device) {
    Write-Host "`n  Kein Handy gefunden!" -ForegroundColor Red
    Write-Host "  Pruefe: WLAN-Debugging aktiv? Gleiches Netzwerk?" -ForegroundColor Yellow
    Write-Host ""
    & $ADB devices
    exit 1
}
Write-Host "`n  Geraet: $device" -ForegroundColor Cyan

# Build + Install
$env:ANDROID_SERIAL = $device
Write-Host "  Baue und installiere..." -ForegroundColor Cyan
& .\gradlew.bat installDebug --console=plain 2>&1 | ForEach-Object {
    if ($_ -match "BUILD SUCCESSFUL") { Write-Host "  $_" -ForegroundColor Green }
    elseif ($_ -match "FAILED") { Write-Host "  $_" -ForegroundColor Red }
}
if ($LASTEXITCODE -ne 0) { Write-Host "`n  Build fehlgeschlagen!" -ForegroundColor Red; exit 1 }

# App starten
if ($Launch) {
    & $ADB -s $device shell am start -n "com.ironlog.app/.MainActivity" | Out-Null
    Write-Host "  App gestartet!" -ForegroundColor Green
}
Write-Host ""
