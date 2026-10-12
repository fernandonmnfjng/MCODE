#!/bin/sh
# MCODE targets Minecraft 1.21.1 and should run its Gradle 8.14.3 wrapper on JDK 21.
# Gradle 8.14.x does not officially support running on Java 25 (class file major 69).
# Keep this check in the wrapper so `./gradlew build` works as well as the helper script.

APP_HOME=$( CDPATH= cd -P "$(dirname -- "$0")" >/dev/null 2>&1 && pwd ) || exit 1

is_java21_home() {
    candidate=$1
    [ -n "$candidate" ] || return 1
    [ -x "$candidate/bin/java" ] || return 1
    "$candidate/bin/java" -version 2>&1 | grep -Eq 'version "21([.]|" )|openjdk 21([.]| )'
}

JAVA21_HOME=""

# Explicit override for non-standard installations; then common environment variables.
for candidate in "${MCODE_JAVA21_HOME:-}" "${JAVA_HOME_21:-}" "${JAVA_HOME_21_X64:-}" "${JAVA_HOME:-}"; do
    if is_java21_home "$candidate"; then
        JAVA21_HOME=$candidate
        break
    fi
done

# macOS provides a reliable JDK locator, including installations outside standard folders.
if [ -z "$JAVA21_HOME" ] && [ "$(uname -s 2>/dev/null || true)" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
    candidate=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if is_java21_home "$candidate"; then
        JAVA21_HOME=$candidate
    fi
fi

# Common Linux/macOS user-managed JDK locations.
if [ -z "$JAVA21_HOME" ]; then
    for candidate in \
        "$HOME"/.jdks/*21* \
        "$HOME"/.sdkman/candidates/java/21* \
        "$HOME"/.local/share/mise/installs/java/21* \
        /usr/lib/jvm/*21* \
        /opt/jdk-21* \
        /opt/java/openjdk-21* \
        /Library/Java/JavaVirtualMachines/*21*.jdk/Contents/Home; do
        if is_java21_home "$candidate"; then
            JAVA21_HOME=$candidate
            break
        fi
    done
fi

if [ -z "$JAVA21_HOME" ]; then
    echo "ERROR: MCODE requiere JDK 21 para compilar Minecraft 1.21.1 con Gradle 8.14.3." >&2
    echo "No se encontró un JDK 21. Java 25 produce 'Unsupported class file major version 69'." >&2
    echo "Instala JDK 21 o define MCODE_JAVA21_HOME con la ruta de su carpeta raíz." >&2
    echo "Ubuntu/Debian: sudo apt update && sudo apt install openjdk-21-jdk" >&2
    echo "Consulta SOLUCION_JAVA25.md para configurar Linux, macOS, Windows o tu IDE." >&2
    exit 2
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD=$JAVA_HOME/bin/java

echo "MCODE: ejecutando Gradle con JDK 21: $JAVA_HOME" >&2
exec "$JAVACMD" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
    "-Dorg.gradle.appname=${0##*/}" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
