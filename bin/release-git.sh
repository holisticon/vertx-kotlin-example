#!/bin/bash
source .env
source bin/check-java-version.sh
mvn -B -X gitflow:release-start gitflow:release-finish

