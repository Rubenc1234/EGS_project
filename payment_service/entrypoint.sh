#!/usr/bin/env sh
set -e

env_file="${ENV_FILE:-/vault/secrets/payment.env}"

if [ -f "$env_file" ]; then
  # Vault-injected secrets file present (Kubernetes deployment)
  set -a
  . "$env_file"
  set +a
elif [ -n "$ENV_FILE" ]; then
  # Explicit path given but file missing — wait up to 30 s (Vault agent slow start)
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
else
  # Default vault path missing — assume env vars already injected (Docker Compose)
  echo "Vault env file not present at $env_file; using environment variables from container" >&2
fi

exec python payment_service/app_payment.py
