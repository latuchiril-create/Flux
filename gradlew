#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit
APP_NAME="Gradle"

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

if ! "$JAVA_EXE" -version >/dev/null 2>&1; then
    echo "ERROR: Java is not available." >&2
    exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

eval "set -- $DEFAULT_JVM_OPTS \"-Dorg.gradle.appname=$APP_NAME\" -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"\$@\""
exec "$JAVA_EXE" "$@"
