# Quality Gates und SLOs

## Verbindliche PR-Gates
Die vier Gates gelten fuer Pull Requests und Debug-Builds; sie entscheiden
ueber Merge, nicht ueber Release-Freigabe:
1. `./gradlew test` muss gruen sein.
2. `./gradlew lintDebug` darf keine Lint-Errors enthalten.
3. `./gradlew assembleDebug` muss erfolgreich bauen.
4. Instrumentation-Smoke (`connectedDebugAndroidTest`) muss in CI gruen sein
   und nachweislich Tests ausgefuehrt haben (Zero-Tests-Guard faellt bei
   fehlenden Reports oder 0 ausgefuehrten Tests).

Merge-faehig ist nur, wer alle vier Gates gruen bekommt (Branch Protection
ist als manuelle GitHub-Konfiguration einzurichten).

## Metrik-Ziele (Team-weit)
- Coverage wird aktuell weder gemessen noch in CI erzwungen; das Team-Ziel
  von >= 60% Unit-Test-Coverage auf Domain-, Repository- und
  ViewModel-Schicht ist eine Absicht, kein bestehendes Gate.
- Kritische Flows (Navigation/Workout-Datenpfad) sollen Smoke-Tests besitzen;
  der aktuelle Instrumentation-Smoke ist der nachgewiesene Kern davon.
- P0/P1 Defects nach Release: 0 offene Defects nach 5 Werktagen.

## Defect-SLO
- P0: Fix/Hotfix innerhalb von 24h.
- P1: Fix innerhalb von 3 Werktagen.
- P2: Fix innerhalb von 1 Sprint.
- P3: Backlog, priorisiert nach Nutzen/Risiko.

## Nachweis in CI
- Pflichtartefakte: Unit-Test-Report, Lint-Report, AndroidTest-Report.
- Debug-PR-Gates und Release-Evidence sind getrennt: Auf main und bei
  `v*`-Tags baut der `release-build`-Job zusaetzlich `:app:assembleRelease`
  und `:app:bundleRelease` **ohne** Signing-Secrets. Die daraus
  hochgeladenen APK/AAB/Mapping/Manifest-Artefakte sind unsignierte
  CI-Evidence, kein Release. Signierung und Publikation sind manuelle
  Schritte (siehe `docs/release-envelope.md`).
- Merge und Release-Freigabe sind zwei verschiedene Entscheidungen:
  gruene Debug-Gates erlauben Merge, eine Freigabe erfordert zusaetzlich
  den Release-Evidence-Schnitt und die signierte, versionierte Auslieferung.
