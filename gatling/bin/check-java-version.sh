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
    version=$("$_java" -version 2>&1 | sed -n ';s/.* version "\(.*\)\.\(.*\)\..*"/\1\2/p;')
    echo version "$version"
    if [[ "$version" > "17" ]]; then
        echo Java version is greater than or equal to 1.8.
    else
        echo Java version is older than 1.8. Please upgrade.
        exit 1
    fi
fi
