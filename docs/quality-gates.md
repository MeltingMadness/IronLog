# Quality Gates und SLOs

## Verbindliche PR-Gates

Die sieben Gates gelten für Pull Requests und Debug-Builds. Sie entscheiden über den Merge, nicht über die Release-Freigabe.

**Android** (`.github/workflows/android-ci.yml`):

1. **Unit-Tests:** `./gradlew test` und `./gradlew :shared:testAndroidHostTest` müssen grün sein. Die KMP-Tests von `:shared` laufen nicht über `test`; die CI führt beide im selben Schritt aus.
2. **Lint:** `./gradlew lintDebug` darf keine Lint-Errors enthalten. Dazu gehört `MissingTranslation`: Jeder String in `res/values/` (Englisch) braucht eine Entsprechung in `res/values-de/` (Deutsch).
3. **Übersetzungen vollständig:** `python3 .github/scripts/check-translations.py` muss grün sein. Es prüft, dass jeder Text in Englisch und Deutsch vorliegt und die Platzhalter (`%1$s`, `%d`, `%@` …) in beiden Sprachen übereinstimmen; Plurale brauchen mindestens `one` und `other`. Der Android-Job `unit-lint-build` prüft damit `values/` gegen `values-de/` (`--platform android`), der iOS-Job den String-Katalog (`--platform ios`). Lokal prüft der Aufruf ohne Option beide Plattformen.
4. **Build:** `./gradlew assembleDebug` muss erfolgreich bauen.
5. **Instrumentation-Smoke:** `connectedDebugAndroidTest` muss in CI (Emulator API 35, Animationen aus) grün sein und nachweislich Tests ausgeführt haben. Der Zero-Tests-Guard (`.github/scripts/guard-zero-tests.py`) schlägt bei fehlenden Reports, 0 ausgeführten Tests, Fehlern oder einer nur teilweise gelaufenen Smoke-Suite fehl: Jede Klasse aus `EXPECTED_TEST_CLASSES` muss mindestens einen Test ausgeführt haben, insgesamt mindestens `MIN_TEST_COUNT` (15). Wer eine dieser Testklassen umbenennt oder entfernt, passt die Liste im Skript an.

**iOS** (`.github/workflows/ios-ci.yml`, Job `simulator-build-test`, Runner `macos-26` mit Xcode 26.6):

6. **Generator-Drift:** `python3 iosApp/generate_project.py` darf am eingecheckten `iosApp/ThrymIOS.xcodeproj` nichts ändern. Wer Swift-Dateien oder Ressourcen hinzufügt, entfernt oder umbenennt, committet das neu erzeugte Projekt mit.
7. **Build und XCTest:** `xcodebuild test -project iosApp/ThrymIOS.xcodeproj -scheme ThrymIOS -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO` muss grün sein. Der Build erzeugt dabei `Shared.framework` mit Gradle und JDK 17. Wie auf Android schlägt der Job fehl, wenn kein Test lief (Zero-Tests-Guard über das `xcresult`-Bundle).

Merge-fähig ist nur, wer alle sieben Gates grün bekommt. Branch Protection ist als manuelle GitHub-Einstellung einzurichten (siehe [`release-envelope.md`](release-envelope.md)).

## Metrik-Ziele (teamweit)

- Coverage wird aktuell weder gemessen noch in CI erzwungen. Das Ziel von mindestens 60 % Unit-Test-Coverage auf Domain-, Repository- und ViewModel-Schicht ist eine Absicht, kein bestehendes Gate.
- Kritische Flows (Navigation, Workout-Datenpfad) sollen Smoke-Tests besitzen. Der aktuelle Instrumentation-Smoke ist der nachgewiesene Kern davon.
- UI-Tests lösen Texte über Ressourcen bzw. den String-Katalog auf, damit sie in beiden Sprachen laufen.
- P0/P1-Defects nach Release: 0 offene Defects nach 5 Werktagen.

## Defect-SLO

- P0: Fix/Hotfix innerhalb von 24 h.
- P1: Fix innerhalb von 3 Werktagen.
- P2: Fix innerhalb von 1 Sprint.
- P3: Backlog, priorisiert nach Nutzen/Risiko.

## Nachweis in CI

- Pflichtartefakte: Unit-Test-Report, Lint-Report und AndroidTest-Report (Android) sowie das Testergebnis `ThrymIOS.xcresult` mit Zusammenfassung (iOS).
- Debug-PR-Gates und Release-Evidence sind getrennt: Auf `main` und bei `v*`-Tags baut der `release-build`-Job zusätzlich `:app:assembleRelease` und `:app:bundleRelease` **ohne** Signing-Secrets. Die daraus hochgeladenen APK/AAB/Mapping/Manifest-Artefakte sind unsignierte CI-Evidence, kein Release. Signierung und Veröffentlichung sind manuelle Schritte (siehe [`release-envelope.md`](release-envelope.md)).
- Merge und Release-Freigabe sind zwei verschiedene Entscheidungen: Grüne Debug-Gates erlauben den Merge, eine Freigabe erfordert zusätzlich die Release-Evidence und die signierte, versionierte Auslieferung.
