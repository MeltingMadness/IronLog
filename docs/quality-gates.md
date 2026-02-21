# Quality Gates und SLOs

## Verbindliche PR-Gates
1. `./gradlew test` muss gruen sein.
2. `./gradlew lintDebug` darf keine Lint-Errors enthalten.
3. `./gradlew assembleDebug` muss erfolgreich bauen.
4. Instrumentation-Smoke (`connectedDebugAndroidTest`) muss in CI gruen sein.

## Metrik-Ziele (Team-weit)
- Unit-Test-Coverage Ziel: >= 60% auf Domain + Repository + ViewModel Schicht.
- Kritische Flows (Navigation/Workout-Datenpfad) muessen Smoke-Tests besitzen.
- P0/P1 Defects nach Release: 0 offene Defects nach 5 Werktagen.

## Defect-SLO
- P0: Fix/Hotfix innerhalb von 24h.
- P1: Fix innerhalb von 3 Werktagen.
- P2: Fix innerhalb von 1 Sprint.
- P3: Backlog, priorisiert nach Nutzen/Risiko.

## Nachweis in CI
- Pflichtartefakte: Unit-Test-Report, Lint-Report, AndroidTest-Report.
- Merge nur bei komplett gruenen Pflicht-Gates.
