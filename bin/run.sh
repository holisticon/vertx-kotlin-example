#!/bin/bash
source ../.env
source ./check-java-version.sh
JAVA_VERSION_COMMAND="$JAVA_HOME/bin/java -version 2>&1 | head -n 1 | awk -F '\"' '{print $2}'"
eval $JAVA_VERSION_COMMAND

$JAVA_HOME/bin/java -jar ../target/application.jar \
  -D exec.mainClass="apodrating.MainKt"