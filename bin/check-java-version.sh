#!/bin/bash
if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    echo found java executable in JAVA_HOME
    _java="$JAVA_HOME/bin/java"

elif type -p java; then
    echo found java executable in PATH
    _java=java
else
    echo "no java"
fi

if [[ "$_java" ]]; then
    version=$("$_java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo version "$version"
    if [[ "$version" > "$JAVA_VERSION" ]]; then
        printf "Java version is greater than or equal to $JAVA_VERSION.\n"
    else
        printf "Java version is older than $JAVA_VERSION. Please upgrade.\n"
        exit 1
    fi
fi
