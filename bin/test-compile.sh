#!/bin/bash
source ../.env
source ./check-java-version.sh
JAVA_VERSION_COMMAND="$JAVA_HOME/bin/java -version 2>&1 | head -n 1 | awk -F '\"' '{print $2}'"
eval $JAVA_VERSION_COMMAND
mvn clean jacoco:prepare-agent package jacoco:report \
  -Dexec.mainClass="apodrating.MainKt" \
  -Dmdep.outputFile=classpath.txt \
  -DargLine="" \
  -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory \
  -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector \
  -f ../pom.xml -U
