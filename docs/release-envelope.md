# Release Envelope (Gate 4)

## Was CI liefert und was es nicht ist

Der `release-build`-Job in `.github/workflows/android-ci.yml` laeuft automatisch
auf Pushes zu `main` und auf `v*`-Tags sowie bei einem bewusst manuell
gestarteten `workflow_dispatch` - nie automatisch auf Pull Requests oder
Feature-Branches. Er wartet auf die Debug- und Connected-Gates und baut danach
`:app:assembleRelease` und
`:app:bundleRelease` ohne Signing-Secrets, faellt bei fehlenden
Kernartefakten (APK, AAB, R8-Mapping, gemergtes Manifest) und erzeugt eine
SHA-256-Manifestdatei.

**Diese Artefakte sind unsignierte CI-Evidence, kein Release.** Der Job
assertet bewusst keine Signatur; wer sie als Release installiert, bekommt
eine unsignierte APK. Er dient als Freigabegrundlage (Artefakte + Hashes +
Mapping fuer Crash-Stacktraces), nicht als Auslieferung.

## Release-Kette

1. **Gate1-3 + Connected-Tests gruen.** `test`, `lintDebug`,
   `assembleDebug` und `connectedDebugAndroidTest` (mit Zero-Tests-Guard)
   muessen auf dem Release-Kandidaten bestanden haben.
2. **Release-Evidence aus CI ziehen.** APK, AAB, R8-Mapping, gemergtes
   Manifest und `SHA256SUMS.txt` aus dem `release-build`-Artifact downloaden
   und die Hashes gegen die Manifestdatei pruefen.
3. **Manuell signieren.** Signierung laeuft ausserhalb von CI mit dem
   Release-Keystore (siehe unten). `apksigner verify` ist der Abschlusscheck
   fuer die signierte APK.
4. **Versionierung pruefen.** `versionCode` muss monoton steigen und darf in
   keiner veroeffentlichten Variante zurueckgehen; `versionName` muss dem
   Tag/Changelog entsprechen.

## Distribution: Play vs. Sideload (bewusst offen)

Die Entscheidung zwischen Play-Store-Auslieferung und Sideload/verwalteter
Verteilung ist **nicht gefallen** und wird bewusst nicht in CI vorweggenommen.
Beide Wege haben unterschiedliche Konsequenzen:

- **Play Store:** Benoetigt ein Developer-Konto, Data-Safety-Formular,
  internal testing vor staged rollout, und die Keystore-Recovery-Frage
  entscheidet sich mit Play App Signing.
- **Sideload:** Einfacher, aber Signaturwechsel oder unsignierte Builds
  verhindern Updates; Ablaeufe fuer Checksummen-Verteilung und
  Rollback/Support sind selbst zu betreiben.

Bis zur Entscheidung liefert CI identische unsignierte Artefakte fuer beide
Wege.

## Keystore, Signatur und Recovery

- Der Release-Keystore existiert nur lokal/manuell; CI kennt keine
  Signing-Secrets. `keystore.properties` steuert, ob Gradle ueberhaupt einen
  Signing-Config aktiviert - sie darf niemals als CI-Secret liegen.
- Keystore, Passwoerter und Alias muessen ausserhalb des Repos gesichert
  werden (verschluesselt, offline, dokumentierte Recovery). Ohne den
  Keystore ist ein Update fuer Bestandsinstallationen nicht moeglich.
- Bei Play Store ist **Play App Signing** zu pruefen: Upload-Key und
  App-Signing-Key sind dann getrennt, Recovery laeuft ueber den
  Upload-Key, und der App-Signing-Key ist bei Google gesichert.

## Datenschutz- und Data-Safety-Angaben

Die App haelt Workout-/Trainingdaten lokal auf dem Geraet; ohne Cloud- oder
Server-Komponente sind im Play Data-Safety-Formular keine Datenuebertragung
und keine Datensammlung anzugeben. Diese Aussage gilt nur, solange die
Backup-/Export-Funktion keine Server-Ziele erhaelt - vor jeder solchen
Aenderung ist das Formular neu zu bewerten.

## Internal Test, Rollout und Rollback

1. **Internal test:** Die signierte Variante zuerst an eine kleine
   interne Gruppe geben (Play: Internal Testing Track).
2. **Staged rollout:** Erst nach gruener Internalphase mit kleinem
   Prozentanteil (z.B. 10%) veroeffentlichen.
3. **Rollback:** Wenn der aktuelle Release weiterhin die hoechste
   funktionsfaehige Version ist, kann der Rollout auf 0% zurueckgezogen
   werden; ein Downgrade auf eine aeltere `versionCode` ist ueber Play nicht
   moeglich und muss im Planungsfall wie ein neuer Release behandelt werden.

## Artefakt-Hashes

`SHA256SUMS.txt` aus dem Release-Job enthaelt SHA-256-Hashes der APK, des
AAB, des R8-Mappings und des gemergten Manifests. Vor jeder Signierung oder
Verteilung sind die heruntergeladenen Dateien mit dieser Datei abzugleichen.
Ein geaenderter Hash nach dem Download bedeutet: nicht freigeben, Ursache
klaeren.

## Branch Protection (externe manuelle Konfiguration)

Branch Protection fuer `main` ist eine manuelle GitHub-Einstellung und wird
nicht von diesem Repository verwaltet. Einzurichten ist mindestens: alle
vier PR-Gates als required checks, keine direkten Pushes (oder nur mit
Review), Linear History optional. Ohne diese Konfiguration laeuft CI zwar,
aber nichts erzwingt die Gates vor dem Merge.
