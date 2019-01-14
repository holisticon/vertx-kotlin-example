#!/bin/bash
mvn jgitflow:release-finish -DallowSnapshots=true -P no-docker -Dmaven.javadoc.skip=true
