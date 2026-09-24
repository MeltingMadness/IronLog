#!/bin/bash
# SessionStart-Hook fuer Claude Code im Web: richtet JDK 17, Android SDK und
# Gradle ein, damit `./gradlew test lintDebug assembleDebug` sofort laufen.
# Idempotent: bereits installierte Teile werden uebersprungen.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
JAVA_HOME_17=/usr/lib/jvm/java-17-openjdk-amd64
SDK_ROOT="$HOME/android-sdk"
# Muss zu den Versionen in app/build.gradle.kts und AGENTS.md passen.
CMDLINE_TOOLS_ZIP=commandlinetools-linux-13114758_latest.zip
SDK_PACKAGES=("platforms;android-35" "platforms;android-36" "build-tools;36.0.0" "platform-tools")

log() { echo "[session-start] $*" >&2; }

# 1) JDK 17 (CI-Paritaet; das Image bringt nur JDK 21 mit)
if [ ! -x "$JAVA_HOME_17/bin/java" ]; then
  log "Installiere OpenJDK 17"
  export DEBIAN_FRONTEND=noninteractive
  apt-get install -y -q openjdk-17-jdk-headless >/dev/null 2>&1 \
    || { apt-get update -q >/dev/null 2>&1 && apt-get install -y -q openjdk-17-jdk-headless >/dev/null; }
fi
export JAVA_HOME="$JAVA_HOME_17"

# 2) Android SDK (benoetigt Netzwerkzugriff auf dl.google.com)
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "Installiere Android command-line tools"
  tmp="$(mktemp -d)"
  curl -sSLo "$tmp/clt.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  unzip -q -o "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi
missing=()
for pkg in "${SDK_PACKAGES[@]}"; do
  [ -d "$SDK_ROOT/${pkg//;//}" ] || missing+=("$pkg")
done
if [ ${#missing[@]} -gt 0 ]; then
  log "Installiere SDK-Pakete: ${missing[*]}"
  yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" "${missing[@]}" >/dev/null
fi

# 3) local.properties (git-ignoriert)
if ! grep -qs "^sdk.dir=$SDK_ROOT$" "$PROJECT_DIR/local.properties"; then
  echo "sdk.dir=$SDK_ROOT" > "$PROJECT_DIR/local.properties"
fi

# 4) Maven-Central-Spiegel: repo.maven.apache.org antwortet hinter dem
#    Cloud-Proxy bei vielen parallelen Downloads mit HTTP 429.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle.kts" <<'KTS'
// Lokal (nur Cloud-Session): Google-gehosteten Maven-Central-Spiegel bevorzugen.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2"
fun RepositoryHandler.preferMirror() {
    val repo = maven(mirror)
    remove(repo)
    addFirst(repo)
}
settingsEvaluated {
    pluginManagement.repositories.preferMirror()
    dependencyResolutionManagement.repositories.preferMirror()
}
KTS

# 5) Umgebungsvariablen fuer die Session
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=$JAVA_HOME_17"
    echo "export ANDROID_HOME=$SDK_ROOT"
    echo "export ANDROID_SDK_ROOT=$SDK_ROOT"
  } >> "$CLAUDE_ENV_FILE"
fi

# 6) Abhaengigkeiten vorwaermen, damit der Container-Cache sie enthaelt.
#    Fehler hier blockieren die Session nicht.
cd "$PROJECT_DIR"
log "Waerme Gradle-Abhaengigkeiten vor (assembleDebug + Unit-Test-Kompilierung)"
if ! ./gradlew --no-daemon --max-workers=2 -q assembleDebug compileDebugUnitTestKotlin >/dev/null 2>&1; then
  log "Vorwaermen fehlgeschlagen; Gradle laedt beim ersten Build nach."
fi
log "Fertig"
