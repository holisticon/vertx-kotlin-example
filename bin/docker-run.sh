#!/bin/bash
docker run --name kotlin-example-coroutines \
 -it --rm -d \
 -p 8081:8081 \
 -p 8443:8443 \
 weinschenker/kotlin-example-coroutines:1.0.19-SNAPSHOT