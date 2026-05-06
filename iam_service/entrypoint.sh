#!/usr/bin/env sh
set -e

env_file="${ENV_FILE:-/vault/secrets/iam.env}"
attempts=0

while [ ! -f "$env_file" ]; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 30 ]; then
    echo "Env file not found: $env_file" >&2
    exit 1
  fi
  sleep 1
done

set -a
. "$env_file"
set +a

exec python -m iam_service.app_iam
