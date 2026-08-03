<#
  IronLog Release Build
  Baut eine signierte Release-APK und verifiziert die Signatur.

  Voraussetzungen:
    - release-keystore/ironlog-release.jks
    - keystore.properties

  Nutzung:
    .\release.ps1
#>

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\maert\AppData\Local\Android\Sdk"

$keystoreProperties = Join-Path $PSScriptRoot "keystore.properties"
if (-not (Test-Path $keystoreProperties)) {
    Write-Host ""
    Write-Host "  keystore.properties fehlt." -ForegroundColor Red
    Write-Host "  Vorlage: keystore.properties.example" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

$buildToolsDir = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1

if (-not $buildToolsDir) {
    throw "Keine Android Build-Tools unter $env:ANDROID_HOME\\build-tools gefunden."
}

$apkSigner = Join-Path $buildToolsDir.FullName "apksigner.bat"

Write-Host ""
Write-Host "  Baue signierte Release-APK..." -ForegroundColor Cyan
& .\gradlew.bat :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "  Release-Build fehlgeschlagen." -ForegroundColor Red
    exit 1
}

$apk = Resolve-Path "app\build\outputs\apk\release\app-release.apk" -ErrorAction SilentlyContinue
if (-not $apk) {
    throw "Signierte APK wurde nicht gefunden. Erwartet: app\\build\\outputs\\apk\\release\\app-release.apk"
}

Write-Host ""
Write-Host "  Verifiziere Signatur..." -ForegroundColor Cyan
& $apkSigner verify --print-certs $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "  Signatur-Pruefung fehlgeschlagen." -ForegroundColor Red
    exit 1
}

$hash = Get-FileHash $apk -Algorithm SHA256

Write-Host ""
Write-Host "  Fertig." -ForegroundColor Green
Write-Host "  APK:  $($hash.Path)" -ForegroundColor Green
Write-Host "  SHA:  $($hash.Hash)" -ForegroundColor Green
Write-Host ""
