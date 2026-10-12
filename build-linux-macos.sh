#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

is_java21() {
    local candidate="$1"
    [[ -x "$candidate/bin/java" ]] || return 1
    "$candidate/bin/java" -version 2>&1 | grep -Eq 'version "21([.]|\\")|openjdk 21([.]| )'
}

JAVA21_HOME=""

# Preferred explicit environment variables.
for candidate in "${JAVA_HOME_21:-}" "${JAVA_HOME_21_X64:-}" "${JAVA_HOME:-}"; do
    if [[ -n "$candidate" ]] && is_java21 "$candidate"; then
        JAVA21_HOME="$candidate"
        break
    fi
done

# Common Linux and macOS installation paths.
if [[ -z "$JAVA21_HOME" ]]; then
    for candidate in \
        /usr/lib/jvm/*21* \
        /opt/jdk-21* \
        /Library/Java/JavaVirtualMachines/*21*.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home; do
        if [[ -d "$candidate" ]] && is_java21 "$candidate"; then
            JAVA21_HOME="$candidate"
            break
        fi
    done
fi

# Last resort: use java from PATH only if it is actually Java 21.
if [[ -z "$JAVA21_HOME" ]]; then
    JAVA_BIN="$(command -v java || true)"
    if [[ -n "$JAVA_BIN" ]]; then
        JAVA_REAL="$(readlink -f "$JAVA_BIN" 2>/dev/null || printf '%s' "$JAVA_BIN")"
        CANDIDATE_HOME="$(cd -- "$(dirname -- "$JAVA_REAL")/.." && pwd)"
        if is_java21 "$CANDIDATE_HOME"; then
            JAVA21_HOME="$CANDIDATE_HOME"
        fi
    fi
fi

if [[ -z "$JAVA21_HOME" ]]; then
    echo "ERROR: No encontré un JDK 21 instalado." >&2
    echo "Minecraft 1.21.1 y este proyecto deben compilarse con Java 21." >&2
    echo "En Ubuntu/Debian instala uno con: sudo apt update && sudo apt install openjdk-21-jdk" >&2
    echo "Después vuelve a ejecutar este script. También puedes definir JAVA_HOME_21 con la ruta del JDK 21." >&2
    echo >&2
    java -version 2>&1 || true
    exit 2
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT"

echo "Usando JDK: $JAVA_HOME"
java -version
exec ./gradlew clean build "$@"
