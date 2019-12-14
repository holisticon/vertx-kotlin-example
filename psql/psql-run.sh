#!/bin/bash
source ../.env
docker run --name apod-postgres \
    -p 54320:5432 \
    -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    -e POSTGRES_USER=$POSTGRES_USER \
    -v $POSTGRES_DATA_DIR:/var/lib/postgresql/data \
    weinschenker/apod-db:latest