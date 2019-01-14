#!/bin/bash
mvn gitflow:release-finish -DallowSnapshots=true -P no-docker -Dmaven.javadoc.skip=true
