# AGENTS.md

## Cursor Cloud specific instructions

IronLog is an Android app (Kotlin Multiplatform; the `shared` module also targets iOS) built with Gradle, AGP `9.0.0`, Kotlin `2.3.10`, and Jetpack Compose. The Android app (`:app`, package `com.ironlog.app`) is the primary product — a workout/training logger.

### Environment (pre-provisioned by the startup update script + VM snapshot)
- Build JDK is **17** (matches CI). `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`, and the SDK tools are exported from the agent's `~/.bashrc`, so interactive shells already have them. If you run Gradle from a non-login/non-interactive shell and hit "SDK location not found" or a Java version error, export them first:
  - `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
  - `export ANDROID_HOME=$HOME/android-sdk`
- The Android SDK lives at `$HOME/android-sdk` (installed: `platforms;android-35`, `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`). `local.properties` (git-ignored) points `sdk.dir` there. The SDK, JDK 17, and `local.properties` persist in the VM snapshot — the update script does not reinstall them.

### Build / test / lint (standard commands; see `.github/workflows/android-ci.yml` and `docs/quality-gates.md`)
- Build the app (debug APK): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- Unit tests: `./gradlew test` (or `:app:testDebugUnitTest`).
- Lint: `./gradlew lintDebug` (reports under `<module>/build/reports/lint-results-debug.html`).
- The four PR gates are: `test`, `lintDebug`, `assembleDebug`, and `connectedDebugAndroidTest` (see below).

### Non-obvious gotchas
- **No emulator here.** `/dev/kvm` is not available in the Cloud VM, so `connectedDebugAndroidTest` and any GUI/emulator run are not possible. Validate changes with unit tests (`./gradlew test`) and `assembleDebug`. Instrumentation smoke tests run in GitHub Actions CI (`android-emulator-runner`), not locally.
- Use `./gradlew --no-daemon ...` for one-off CI-parity runs (this is how CI invokes Gradle).
- Some unit test names are written in German (e.g. `logSet erstellt Satz korrekt`); this is expected, not a bug.
