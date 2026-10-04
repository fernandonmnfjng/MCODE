#!/bin/sh

APP_HOME=$( cd -P "${0%"${0##*/}"}" > /dev/null && printf '%s\n' "$PWD" ) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
elif [ -x "/usr/lib/jvm/java-21-openjdk-amd64/bin/java" ]; then
    JAVACMD=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
else
    JAVACMD=java
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=${0##*/}" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
