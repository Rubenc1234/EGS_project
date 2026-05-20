#!/usr/bin/env sh
set -e

env_file="${ENV_FILE:-/app/iam_service/.env}"

if [ -f "$env_file" ]; then
  set -a
  . "$env_file"
  set +a
else
  echo "Env file not found: $env_file; using existing environment" >&2
fi

exec python -m iam_service.app_iam
