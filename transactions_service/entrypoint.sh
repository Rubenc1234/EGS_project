#!/usr/bin/env sh
set -e

env_file="${ENV_FILE:-/app/.env}"

if [ -f "$env_file" ]; then
  set -a
  . "$env_file"
  set +a
else
  echo "Env file not found: $env_file; using existing environment" >&2
fi

exec java -jar /app/app.jar
