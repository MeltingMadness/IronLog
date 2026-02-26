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
| 2026-02-16 | self | Shell-Tool lief mit implizitem Default-CWD auf 'Der Verzeichnisname ist ung�ltig'. | Bei diesem Setup in jedem Shell-Call den workdir explizit setzen. |
| 2026-02-16 | self | Bekannte create+write-Abh�ngigkeit erneut in parallelen Tool-Calls ausgel�st (DirectoryNotFound beim zweiten File). | Bei Datei-Anlage mit neuem Ordner immer strikt sequentiell arbeiten. |
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


## Session Notes
- 2026-02-24 | self | Started with repo listing before reading `.claude/napkin.md` in this worktree. | In every session, open napkin first before any inspection command.
- 2026-02-24 | user | I mixed up context from a different assistant/session instead of validating the current IronLog state first. | For crash recovery, first verify the active repo/branch/worktree and summarize that state before referencing past memory.
- 2026-02-24 | self | Paging-Refresh-Error im History-Screen wurde als Empty-State behandelt, weil nur auf `!Loading && itemCount == 0` verzweigt wurde. | Fuer Paging-Listen den Content-State explizit zwischen Loading/Empty/Error/Content aufteilen und Error mit Retry rendern.
- 2026-02-24 | self | `rg -n "�|\\uFFFD" app core data feature` ist ein schneller Qualitaetscheck nach Zeichen-/Encoding-Fixes.
- 2026-02-24 | self | Nach sporadischem Windows-Dateilock bei undleLibCompileToJarDebug hat ./gradlew --stop den Build stabilisiert. | Bei FileSystemException auf uild/intermediates/*.jar zuerst Daemons stoppen und dann Tests mit --rerun-tasks verifizieren.
- 2026-02-24 | self | Meta-Plan-Feature wird ohne DB/Repository-Aenderung als UI-internes In-Memory-Store umgesetzt. | Im Abschluss explizit kennzeichnen, dass Persistenz/robuste Historisierung vom Daten-Layer abhaengt.
- 2026-02-24 | self | Zu frueh einen UI-In-Memory-Store fuer Meta-Plaene gebaut, obwohl bereits MetaTrainingPlan-Repository/Domain im Projekt existiert. | Vor Architekturentscheidungen immer zuerst per g auf vorhandene Domain-/Repository-Typen pruefen (hier: MetaTrainingPlanRepository, metaPlanId).
- 2026-02-24 | self | Full unit test run failed immediately because JAVA_HOME was missing in this shell. | Before every Gradle run in this environment, set JAVA_HOME and ANDROID_HOME explicitly in the same command.
- 2026-02-24 | self | Shell startet weiterhin oft ohne JAVA_HOME, obwohl local.properties den SDK-Pfad liefert. | Fuer Build-Checks immer JAVA_HOME=`C:\Program Files\Android\Android Studio\jbr` und ANDROID_HOME aus local.properties explizit setzen.
- 2026-02-24 | self | Einen `rg`-Call ohne `workdir` gestartet; Suche lief außerhalb des Repos ins Leere. | In diesem Setup jeden Shell-Call mit explizitem `workdir` ausführen.
- 2026-02-24 | self | Mehrere `rg`-Patterns in PowerShell mit falschem Quoting escaped; Commands schlugen fehl. | In PowerShell für Regex/JSON-Muster konsequent Single-Quotes nutzen und Backslashes sparsam einsetzen.
- 2026-02-24 | self | ViewModel tests with init collectors can race and overwrite error assertions before action-specific assertions run. | After creating such ViewModels in tests, call advanceUntilIdle() once before triggering the behavior under test.
- 2026-02-24 | self | Meta-plan sequence based on completed count can drift from actual last performed subplan when history is edited/imported. | Derive next subplan from latest completed subplan id instead of modulo count.
- 2026-02-24 | self | While refactoring ExercisePickerSheet, temporarily replaced direct list selection with an extra per-item action button, breaking the established selection flow. | Keep picker item interaction aligned with existing list-item click behavior and only add explicit actions where needed.
- 2026-02-24 | self | In new ViewModel tests I used `collect()` without lambda; this Kotlin setup requires an explicit collector block and failed compilation. | In test collectors always use `collect { }` (or `launchIn`) to avoid signature mismatches.
- 2026-02-24 | self | Migration 6->7 erstellt einen Index auf `exercises.isArchived`, während die Room-Entity kein entsprechendes `@Index` deklariert. | Bei Room-Migrationen Schema strikt mit Entity-Definition synchron halten; bei Index-Änderungen Entity + exportiertes Schema + Folgemigration gemeinsam anpassen.
- 2026-02-24 | self | Tried to read tests under feature/*/src/test, but this repo keeps the relevant VM tests under app/src/test. | For test discovery in IronLog, start with rg --files app/src/test before probing feature module test folders.
- 2026-02-24 | self | Assumed adb is on PATH; command failed and blocked immediate crash-log capture. | Use "C:\Users\maert\AppData\Local\\Android\\Sdk\\platform-tools\\adb.exe" directly in this environment.
- 2026-02-24 | self | Parsed sdk.dir from local.properties naively; escaped Windows path (C\\:\\...) broke Join-Path. | Prefer LOCALAPPDATA-based SDK path or unescape local.properties values before path joins.
- 2026-02-24 | self | Crash reports on phone were reproducible by feature entry points but stacktraces were unavailable (no connected adb device). | When device logs are unavailable, harden both data-flow exception handling and runtime DB schema compatibility for legacy pre-release installs.
- 2026-02-24 | self | WLAN-debugging device appears as mDNS TLS target (_adb-tls-connect) and is installable directly via :app:installDebug. | For quick deploys, verify with db devices -l then run Gradle install without extra pairing if status is device.
- 2026-02-24 | self | Real device crash was `IllegalArgumentException: Key "3" already used` from Compose lazy keys in MetaPlan editor order list. | For any lazy list that may contain repeated IDs (legacy/corrupt or intentional duplicates), use composite keys (id+index) and normalize duplicate IDs in VM state.
- 2026-02-24 | self | Earlier phone crash `NoSuchMethodError` pointed to `ExposedDropdownMenuBox(...)` binary signature mismatch at runtime. | Avoid ExposedDropdownMenuBox in this app and use stable `OutlinedTextField + DropdownMenu` pattern for compatibility.
- 2026-02-24 | self | After dropdown refactor, missed `androidx.compose.foundation.clickable` import and broke compile. | After UI API swaps, immediately run a compile gate before deeper test runs.
- 2026-02-24 | self | `adb devices -l` war leer, obwohl WLAN-Debugging aktiv war; `adb mdns services` zeigte `_adb._tcp` und `adb connect host:port` stellte die Verbindung sofort wieder her. | Wenn `adb devices` leer ist, erst mDNS-Service suchen und dann explizit verbinden, danach `:app:installDebug` starten.
- 2026-02-24 | self | APK-Hash parallel zum Copy-Schritt berechnet; Hash-Call konnte Datei temporär nicht finden. | Copy + nachgelagerte Verifikation (Hash/Signatur) bei Dateiartefakten immer sequenziell ausführen.
- 2026-02-25 | self | UI-wide redesign with transparent Screen Scaffolds works reliably when global background is applied in MainActivity; preserves feature logic while changing look. | Keep structural behavior in ViewModels/Navigation untouched and verify with :app:compileDebugKotlin after multi-screen styling updates.
- 2026-02-25 | self | mcp__claude_mem__save_observation can return Worker 500 (u.syncObservation) even when the observation is actually persisted. | After save errors, verify via mcp__claude_mem__search + get_observations before retrying to avoid duplicate entries.
- 2026-02-25 | self | mcp__claude_mem__search mit Query kann fehlschlagen, wenn semantic search lokal nicht verfuegbar ist. | Dann per Filter-Suche (dateStart/orderBy) IDs holen und mit get_observations Details laden.
- 2026-02-25 | self | Schnellcheck nach Device-Deploy fehlte in frueheren Runs gelegentlich. | Fuer Runtime-Sanity db logcat -c, App starten, 3-5s warten und auf FATAL EXCEPTION|AndroidRuntime filtern.
- 2026-02-25 | self | Session erneut mit Repo-Checks gestartet, bevor napkin gelesen wurde. | In jedem Turn zuerst .claude/napkin.md lesen, dann erst Status/Dateisuche starten.
- 2026-02-25 | self | connectedDebugAndroidTest/installDebug auf MIUI-Device kann mit INSTALL_FAILED_USER_RESTRICTED abbrechen. | Vor Device-QA auf dem Handy USB-Install/Debugging-Bestaetigung aktiv bestaetigen und ggf. Sicherheitsdialog offen lassen.
- 2026-02-25 | self | PowerShell behandelt komplexe g-Regex mit |/Klammern in Double-Quotes unzuverlaessig. | Fuer XML-Suchen in PowerShell lieber Select-String oder Single-Quoted Patterns nutzen.
- 2026-02-25 | self | Nach bestaetigter USB-Install-Freigabe liefen `:app:installDebug` und `connectedDebugAndroidTest` wieder stabil auf 24090RA29G. | Bei Device-QA erst Freigabe bestaetigen, dann Install + Test direkt hintereinander ausfuehren.
- 2026-02-25 | self | `adb logcat -d | rg ...` liefert Exit 1, wenn keine Treffer gefunden werden (hier: keine Crash-Signaturen). | Exit-Code bei Crash-Filter immer als Signalwert interpretieren: 1 kann "keine Fehler gefunden" bedeuten.
- 2026-02-25 | self | `uiautomator dump` kann auf Xiaomi trotz fokussierter App nur Overlay-XML liefern (`could not get idle state`). | Fuer manuelle QA dann direkt per `screencap` + Koordinaten-Navigation pruefen statt auf dump-Textmatching zu bauen.
- 2026-02-25 | self | In PowerShell ist `$PID` read-only und kollidiert mit App-PID-Variablen. | Fuer ADB-Prozess-ID immer eigene Namen wie `$appPid` verwenden.
- 2026-02-25 | self | Interpolierte Regex-Strings mit verschachtelten Quotes in PowerShell (`[regex]::Match(...)`) fuehrten zu ParserError. | Regex-Pattern als Single-Quoted Format-String aufbauen (`'...{0}...' -f [regex]::Escape(...)`).
- 2026-02-25 | self | Cleanup mit `cmd ... & rmdir ...` wurde in PowerShell als Job/Parameter-Mix fehlinterpretiert. | Bei kombinierten Windows-CMD-Befehlen immer `cmd /c "... && ..."` verwenden.
- 2026-02-25 | self | Parallel ADB-Kommandos (db start-server + db mdns services) erzeugten einen Daemon-Start-Race. | ADB-Server-Steuerung immer sequentiell ausfuehren: kill-server -> start-server -> devices/mdns.
- 2026-02-25 | self | Trotz verbundenem USB-Device schlug auch db install -r mit INSTALL_FAILED_USER_RESTRICTED fehl. | Auf MIUI vor Install immer Display entsperrt lassen und Sicherheitsdialog Per USB installieren/Installieren aktiv bestaetigen.
- 2026-02-25 | self | PowerShell hat die Gradle-Property fuer Instrumentation (-Pandroid.testInstrumentationRunnerArguments.class=...) ohne Quotes fehlgeparsed und als Task behandelt. | In PowerShell solche -P...-Argumente immer einzeln quoten.
- 2026-02-25 | self | Nach connectedDebugAndroidTest-Fehler mit INSTALL_FAILED_USER_RESTRICTED war die App auf dem Device nicht mehr installiert (Start schlug mit Error type 3 fehl). | Nach fehlgeschlagenem Instrumentation-Install den App-Installstatus neu pruefen und ggf. manuell neu installieren.
- 2026-02-26 | user | Performance-Plan sollte bewertet, aber explizit nicht ausgefuehrt werden. | Bei Plan-Reviews nur Analyse/Validierung liefern, keine Code- oder Build-Aktionen starten.
