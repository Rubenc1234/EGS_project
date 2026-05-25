#!/usr/bin/env sh
set -e

env_file="${ENV_FILE:-}"

# Only source an env file if ENV_FILE is explicitly provided. This avoids
# overwriting environment variables injected by Kubernetes (envFrom).
if [ -n "$env_file" ]; then
  if [ -f "$env_file" ]; then
    set -a
    . "$env_file"
    set +a
  else
    echo "Env file not found: $env_file; using existing environment" >&2
  fi
fi

exec python payment_service/app_payment.py
