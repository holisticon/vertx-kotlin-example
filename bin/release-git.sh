#!/bin/bash
source .env
source bin/check-java-version.sh
mvn -B gitflow:release-start gitflow:release-finish

