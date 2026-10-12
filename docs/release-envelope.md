# Release Envelope

## Was CI liefert und was es nicht ist

Der `release-build`-Job in `.github/workflows/android-ci.yml` läuft automatisch auf Pushes zu `main` und auf `v*`-Tags sowie bei einem bewusst manuell gestarteten `workflow_dispatch`, nie automatisch auf Pull Requests oder Feature-Branches. Er wartet auf die Debug- und Connected-Gates, baut danach `:app:assembleRelease` und `:app:bundleRelease` ohne Signing-Secrets, schlägt bei fehlenden Kernartefakten (APK, AAB, R8-Mapping, gemergtes Manifest) fehl und erzeugt eine SHA-256-Manifestdatei (`.github/scripts/release-artifacts.py`).

**Diese Artefakte sind unsignierte CI-Evidence, kein Release.** Der Job prüft bewusst keine Signatur; wer sie als Release installiert, bekommt eine unsignierte APK. Sie dienen als Freigabegrundlage (Artefakte, Hashes, Mapping für Crash-Stacktraces), nicht als Auslieferung.

Für iOS baut die CI (`.github/workflows/ios-ci.yml`) nur für den Simulator und ohne Signierung. Archive für TestFlight und App Store entstehen manuell in Xcode, siehe [`iosApp/TESTFLIGHT.md`](../iosApp/TESTFLIGHT.md).

## Neue App-IDs

Thrym hat eigene IDs: `com.thrym.app` (Android) und `com.thrym.ios` (iOS). Für jeden Kanal ist das ein **neuer Store-Eintrag**, kein Update von IronLog. Bestehende IronLog-Installationen bekommen kein Update auf Thrym; beide Apps können nebeneinander installiert sein. Nutzer ziehen per Backup-Export in IronLog und Import in Thrym um. Einstellungen sind nicht Teil des Backups (siehe [`architektur.md`](architektur.md), Abschnitt Backup).

## Release-Kette (Android)

1. **Gates grün.** Alle sieben PR-Gates aus [`quality-gates.md`](quality-gates.md) müssen auf dem Release-Kandidaten bestanden sein, einschließlich `connectedDebugAndroidTest` mit Zero-Tests-Guard und der iOS-CI.
2. **Versionierung prüfen.** `versionCode` in `app/build.gradle.kts` muss monoton steigen und darf in keiner veröffentlichten Variante zurückgehen. `versionName` muss dem Tag (`v<versionName>`) und dem Eintrag in `CHANGELOG.md` entsprechen. Für F-Droid gehört je Sprache ein kurzer Änderungstext nach `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` und `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`.
3. **Release-Evidence aus CI ziehen.** APK, AAB, R8-Mapping, gemergtes Manifest und `SHA256SUMS.txt` aus dem `release-build`-Lauf herunterladen und die Hashes gegen die Manifestdatei prüfen.
4. **Lokal signiert bauen.** Die Signierung läuft außerhalb von CI:
   - `keystore.properties` aus [`keystore.properties.example`](../keystore.properties.example) anlegen und auf den Release-Keystore zeigen lassen. `keystore.properties` und der Keystore sind git-ignoriert.
   - `./gradlew :app:assembleRelease` baut mit vorhandener `keystore.properties` die signierte APK `app/build/outputs/apk/release/app-release.apk`.
   - `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` (aus `build-tools/36.0.0`) muss bestehen, und der Zertifikat-Fingerprint muss dem festgelegten Release-Zertifikat entsprechen.
   - Den SHA-256 der APK berechnen (`shasum -a 256` bzw. `sha256sum`) und mit dem Release veröffentlichen.

## Distribution

Geplant sind drei Kanäle:

- **F-Droid:** Titel, Beschreibungen, Änderungstexte und Icon liegen im Repository unter `fastlane/metadata/android/` (`en-US`, `de-DE`). F-Droid baut die App selbst aus dem Quellcode. **Offen** ist, wie signiert wird:
  - F-Droid signiert mit eigenem Schlüssel. Einfach, aber F-Droid-Installationen und APKs aus GitHub Releases haben dann verschiedene Signaturen und lassen sich nicht gegenseitig aktualisieren.
  - Reproduzierbarer Build mit eigener Signatur: F-Droid prüft, dass sein Build bitgleich zur selbst signierten APK ist, und verteilt dann diese. Alle Kanäle haben dieselbe Signatur, dafür muss der Build reproduzierbar sein.

  Ebenfalls zu klären: F-Droid baut ohne vorgebaute Binärdateien aus dem Netz. Weil `:shared` auch iOS als Ziel hat, könnte ein Linux-Android-Build die Kotlin/Native-Toolchain (`~/.konan`) laden. Der Android-CI-Job `unit-lint-build` protokolliert das in einem Diagnoseschritt, ohne zu scheitern.
- **GitHub Releases:** Die signierte APK aus Schritt 4 mit SHA-256 am Tag `v<versionName>`.
- **iOS:** TestFlight und danach App Store über App Store Connect. Dafür braucht es eine Mitgliedschaft im Apple Developer Program und einen neuen App-Eintrag für `com.thrym.ios`. Ablauf: [`iosApp/TESTFLIGHT.md`](../iosApp/TESTFLIGHT.md).

Google Play ist derzeit nicht vorgesehen. Käme es dazu, wären Developer-Konto, Data-Safety-Formular und Play App Signing (getrennter Upload- und App-Signing-Key) zu klären.

## Keystore, Signatur und Recovery

- **Der Release-Schlüssel für `com.thrym.app` wird einmal vor der ersten öffentlichen APK festgelegt und danach nie gewechselt.** Android installiert Updates nur mit derselben Signatur, und F-Droid pinnt bei reproduzierbaren Builds das Zertifikat. Ein Schlüsselwechsel bedeutet für alle Nutzer Deinstallation und Datenumzug per Backup.
- Der Release-Keystore existiert nur lokal. CI kennt keine Signing-Secrets. `keystore.properties` entscheidet, ob Gradle überhaupt eine Signing-Config aktiviert; sie darf nie als CI-Secret liegen und nie ins Repository. `release-keystore/`, `*.jks` und `*.keystore` sind git-ignoriert.
- Keystore, Passwörter und Alias müssen außerhalb des Repos gesichert werden (verschlüsselt, offline, mit dokumentierter Recovery). Ohne den Keystore ist kein Update für bestehende Installationen möglich.
- iOS-Releases werden über das Apple-Team des Maintainers signiert. Team-IDs gehören nicht ins Repository; Mitwirkende setzen ihr eigenes Team und eine eigene Bundle-ID in `iosApp/Local.xcconfig` (git-ignoriert, Vorlage `iosApp/Local.xcconfig.example`).
- Forks veröffentlichen mit eigenem Namen, eigenem Icon, eigener `applicationId` und Bundle-ID und eigenem Schlüssel. Name, Logo und App-Icon sind nicht Teil der Apache-Lizenz.

## Datenschutzangaben

Die App hält alle Trainingsdaten lokal auf dem Gerät. Es gibt keinen Server, kein Konto, keine Analyse und kein Tracking. Das App-Manifest (`app/src/main/AndroidManifest.xml`) deklariert selbst nur `POST_NOTIFICATIONS`. Im gemergten Manifest ergänzt WorkManager (Erinnerungen) `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED` und `FOREGROUND_SERVICE`, AndroidX Core die app-interne Signatur-Berechtigung `com.thrym.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Eine Internet-Berechtigung (`android.permission.INTERNET`) gibt es nicht; vor jedem Release ist das im gemergten Manifest aus der CI-Evidence zu prüfen. Daraus folgen:

- App Store: Datenschutzangabe „Keine Daten erfasst“. Der Incident-Report verlässt das Gerät nur, wenn Nutzer ihn selbst teilen. Das Privacy-Manifest (`iosApp/ThrymIOS/PrivacyInfo.xcprivacy`) deklariert nur app-eigene `UserDefaults` mit Grund `CA92.1`.
- F-Droid: keine Anti-Features wegen Tracking oder Netzwerkdiensten. Zu klären ist, ob F-Droid das nicht frei lizenzierte App-Icon als Anti-Feature `NonFreeAssets` einstuft.

Diese Aussagen gelten nur, solange Backup und Export keine Server-Ziele erhalten. Vor jeder solchen Änderung sind die Angaben neu zu bewerten.

## Test, Rollout und Rollback

1. **Vorab testen:** Die signierte APK zuerst selbst und in einer kleinen Gruppe installieren (Update über eine vorhandene Installation und Neuinstallation). Auf iOS zuerst interne, dann externe TestFlight-Tester.
2. **Veröffentlichen:** GitHub Release am Tag anlegen. Bei F-Droid erscheint die Version erst nach dessen eigenem Build, abhängig von der Update-Einstellung in den F-Droid-Metadaten. Auf iOS ist eine phasenweise Veröffentlichung im App Store möglich.
3. **Rollback:** Ein Downgrade auf einen älteren `versionCode` bzw. Build ist in keinem Kanal ein Update-Pfad. Ein Fehler wird mit einer neuen, höheren Version behoben.

## Artefakt-Hashes

`SHA256SUMS.txt` aus dem Release-Job enthält SHA-256-Hashes der APK, des AAB, des R8-Mappings und des gemergten Manifests. Vor jeder Signierung oder Verteilung sind die heruntergeladenen Dateien mit dieser Datei abzugleichen. Ein geänderter Hash nach dem Download bedeutet: nicht freigeben, Ursache klären.

## Branch Protection (externe manuelle Konfiguration)

Branch Protection für `main` ist eine manuelle GitHub-Einstellung und wird nicht von diesem Repository verwaltet. Einzurichten ist mindestens: alle sieben PR-Gates als Required Checks, also die Jobs `unit-lint-build` und `android-smoke` aus `android-ci.yml` sowie „Build + unit tests (iOS Simulator)“ (`simulator-build-test`) aus `ios-ci.yml`, keine direkten Pushes (oder nur mit Review), Linear History optional. Ohne diese Konfiguration läuft CI zwar, aber nichts erzwingt die Gates vor dem Merge.

## Repository-Einstellungen (externe manuelle Konfiguration)

Auch diese GitHub-Einstellungen verwaltet das Repository nicht selbst. Sie sind vor dem öffentlichen Start und nach jeder Änderung der Repository-Einstellungen zu prüfen:

- **Private vulnerability reporting** (Settings → Code security → Private vulnerability reporting) muss eingeschaltet sein. `SECURITY.md` und `CODE_OF_CONDUCT.md` nennen den Button „Report a vulnerability“ im Security-Tab als privaten Meldeweg für Sicherheitslücken und Verhaltensverstöße, und `.github/ISSUE_TEMPLATE/config.yml` verweist darauf. Ist die Einstellung aus, fehlt der Button; dann bleibt nur der Notweg aus `SECURITY.md`, ein Issue, das nur nach einem privaten Kontakt fragt. Prüfen mit `gh api repos/MeltingMadness/Thrym/private-vulnerability-reporting`; erwartet ist `"enabled": true`.
- **Beschreibung und Topics** des Repositorys sollen zum README passen: Thrym, offline, Android und iOS. Ändern mit `gh repo edit MeltingMadness/Thrym --description "…"` und `--add-topic …`.
