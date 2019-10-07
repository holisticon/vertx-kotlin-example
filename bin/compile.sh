#!/bin/bash
source ../.env
source ./check-java-version.sh
JAVA_VERSION_COMMAND="$JAVA_HOME/bin/java -version 2>&1 | head -n 1 | awk -F '\"' '{print $2}'"
eval $JAVA_VERSION_COMMAND
mvn test-compile -DskipTests=true -Dmaven.javadoc.skip=true -B -V -P codacycoverage \
  -f ../pom.xml
mvn clean jacoco:prepare-agent package jacoco:report  -P codacycoverage \
  -f ../pom.xml
