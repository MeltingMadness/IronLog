#!/usr/bin/env bash
set -euo pipefail

# Xcode invokes this script from a generated project. Keep all discovery
# local and deterministic: installing a JDK or downloading anything is out of
# scope for an Xcode build phase.

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)

die() {
    printf 'IronLog iOS shared framework: %s\n' "$1" >&2
    exit 1
}

is_jdk17_home() {
    local candidate="$1"
    local version

    [[ -x "$candidate/bin/java" ]] || return 1
    version=$("$candidate/bin/java" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')
    [[ "$version" == 17.* ]]
}

java_home=
if [[ -n "${JAVA_HOME:-}" ]]; then
    if is_jdk17_home "$JAVA_HOME"; then
        java_home="$JAVA_HOME"
    else
        die "JAVA_HOME must point to a JDK 17 installation (received: $JAVA_HOME)"
    fi
fi

if [[ -z "$java_home" && -x /usr/libexec/java_home ]]; then
    candidate=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
    if [[ -n "$candidate" ]] && is_jdk17_home "$candidate"; then
        java_home="$candidate"
    fi
fi

if [[ -z "$java_home" ]]; then
    for candidate in \
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home"; do
        if is_jdk17_home "$candidate"; then
            java_home="$candidate"
            break
        fi
    done
fi

if [[ -z "$java_home" ]]; then
    die "No JDK 17 found. Set JAVA_HOME or install/configure one before building the iOS app."
fi

export JAVA_HOME="$java_home"
cd "$repo_root"
exec ./gradlew :shared:embedAndSignAppleFrameworkForXcode
