#!/bin/bash
mvn gitflow:release-start -DallowSnapshots=true -P no-docker -Dmaven.javadoc.skip=true
