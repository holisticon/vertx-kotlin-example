#!/bin/bash
mvn jgitflow:release-start -DallowSnapshots=true -P no-docker -Dmaven.javadoc.skip=true
