# Napkin

## Corrections
| Date | Source | What Went Wrong | What To Do Instead |
|------|--------|----------------|-------------------|
| 2026-02-16 | self | Started exploration before ensuring local napkin existed in this worktree. | At session start in each worktree: verify `.claude/napkin.md` exists and read it first. |

## User Preferences
- Sprache und Output fuer Audit/Sprints auf Deutsch halten.
- Schrittweise Umsetzung nach priorisierten Sprint-Phasen (Sprint 1, dann Sprint 2, dann Sprint 3).

## Patterns That Work
- Android/Gradle im Worktree mit explizitem `JAVA_HOME` und `ANDROID_HOME` ausfuehren.
- Sprint-Arbeit in dediziertem Worktree-Branch reduziert Risiko fuer Haupt-Workspace.

## Patterns That Don't Work
- Auf `local.properties` im Worktree vertrauen; Datei ist oft nicht vorhanden.

## Domain Notes
- IronLog: zentrale Invarianten sind "maximal eine aktive Session" und konsistente Plan-/Workout-Daten.
- Sprint 2 Fokus: P1-Themen (Sortierung/Zeithandling, N+1-Reduktion, einheitliches Error-Handling).
| 2026-02-16 | self | In neuen Tests falsche Enum-Namen (LEGS/BACK/KOERPERGEWICHT) verwendet; Build schlug vor RED fehl. | Vor Testdaten immer die Domain-Enums im Projekt gegenpruefen. |
| 2026-02-16 | self | WorkoutHistory-VM-Test blieb auf Initial-State, weil stateIn(WhileSubscribed) ohne Collector nicht voranlaeuft. | In Tests fuer solche Flows immer einen Collector starten (ackgroundScope.launch { uiState.collect() }). |
| 2026-02-16 | self | Regex-Replacement hat in Kotlin-Datei zuerst literal ` 
 ` eingebracht. | Nach automatischen String-Replacements die betroffene Stelle direkt mit Zeilenansicht pruefen. |
| 2026-02-16 | self | In parallelen Tool-Calls auf Ordnerebene geschrieben, bevor der Ordner sicher existierte. | Bei create+write Abhaengigkeit erst sequentiell Ordner erstellen, dann schreiben. |
| 2026-02-16 | self | In App-Modul BuildConfig im Utility nicht verlasslich verfuegbar angenommen; Compile-Fehler. | Fuer Logging-Gates lieber Log.isLoggable(...) oder explizite Build-Feature-Pruefung nutzen. |
| 2026-02-16 | self | Bottom-Nav-Smoke mit UI-Texten waere fragil (Encoding/Locale). | Fuer Compose-Navigationstests stabile 	estTag-IDs an Nav-Items vergeben und darueber klicken. |
| 2026-02-16 | self | Shell-Tool lief mit implizitem Default-CWD auf 'Der Verzeichnisname ist ungültig'. | Bei diesem Setup in jedem Shell-Call den workdir explizit setzen. |
| 2026-02-16 | self | Bekannte create+write-Abhängigkeit erneut in parallelen Tool-Calls ausgelöst (DirectoryNotFound beim zweiten File). | Bei Datei-Anlage mit neuem Ordner immer strikt sequentiell arbeiten. |
| 2026-02-16 | self | PowerShell -replace mit Backtick-Newline hat literal ` 
 ` in Gradle-Dateien geschrieben. | Bei strukturellen Build-Datei-Edits komplette Datei neu schreiben statt fragiler Inline-Replacements. |
| 2026-02-16 | self | Erneut string-basierte -replace Edits mit Escape-Sequenzen in Kotlin-Datei gemacht und Format zerstoert. | Fuer Kotlin/Gradle-Edits mit mehreren Zeilen direkt komplette Datei schreiben statt Inline-Replacement. |
| 2026-02-16 | self | ActiveWorkout-Datei per fragiler String-Replacements korrupt gemacht. | Bei vielen gekoppelten Signatur-Aenderungen Datei direkt komplett neu schreiben und sofort gegenlesen. |
| 2026-02-16 | self | Mehrfach -replace/.Replace fuer Imports und Feldlisten produziert literal Escape-Reste in Tests. | Bei Testdatei-Aenderungen >2 Stellen: komplette Datei neu schreiben. |
| 2026-02-16 | self | Neue Repositories nutzten BuildConfig, aber AGP-Setup generierte BuildConfig nicht automatisch. | In AGP9 beim App-Modul explizit uildFeatures { buildConfig = true } setzen. |
| 2026-02-16 | self | connectedDebugAndroidTest bricht ohne aktives Device/Emulator mit DeviceException ab. | Vor Instrumentation-Lauf erst db devices/Emulator-Status sicherstellen; sonst nur compileDebugAndroidTestKotlin als CI-Compile-Gate nutzen. |
| 2026-02-16 | self | Environment lieferte CWD `C:\MeltingMadness\IronLog`, tatsaechliche Arbeit lief aber im Worktree `C:\Users\maert\IronLog\.worktrees\sprint1-audit-fixes`; Shell-Calls schlugen mit `Der Verzeichnisname ist ungueltig` fehl. | In diesem Setup jeden Shell-Call mit explizitem, verifiziertem Worktree-`workdir` starten. |
| 2026-02-16 | self | Gradle-Compile ohne gesetztes `ANDROID_HOME` gestartet; Build brach vor echten Codefehlern ab. | Vor jedem Android-Gradle-Lauf immer `JAVA_HOME` und `ANDROID_HOME` explizit setzen. |
| 2026-02-16 | self | `apply_patch` auf diesem Setup mit absolutem Pfad genutzt; Tool lief nicht mit gueltigem Arbeitsverzeichnis. | Bei diesem Repo-Setup Datei-Edits per `Set-Content` im expliziten Workdir durchfuehren. |
| 2026-02-16 | self | In Compose irrtuemlich `import androidx.compose.foundation.layout.weight` gesetzt; fuehrte zu internem API-Fehler. | `Modifier.weight(...)` nur im jeweiligen `RowScope`/`ColumnScope` verwenden, ohne diesen Import. |
