#!/bin/bash

APP_NAME="eoffice-be"

ARGS=()
# bash set các biến trong file môi trường (.env.staging)
while IFS='=' read -r key value
do
    [[ -z "$key" || "$key" =~ ^# ]] && continue

    ARGS+=("$key=$value")
done < .env.staging

heroku config:set "${ARGS[@]}" -a "$APP_NAME"