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
    if [[ "$version" > "11" ]]; then
        echo Java version is greater than or equal to 11.
    else
        echo Java version is older than 11. Please upgrade.
        exit 1
    fi
fi
