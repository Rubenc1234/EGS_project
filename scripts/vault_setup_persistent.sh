#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VAULT_COMPOSE="$ROOT_DIR/docker-compose.vault.yml"
VAULT_ADDR="http://127.0.0.1:8200"
INIT_FILE="$ROOT_DIR/.vault/init.json"

run_vault_cmd() {
  local cmd="$1"
  docker-compose -f "$VAULT_COMPOSE" exec -T vault sh -lc "export VAULT_ADDR=$VAULT_ADDR; $cmd"
}

mkdir -p "$ROOT_DIR/.vault"

docker-compose -f "$VAULT_COMPOSE" up -d vault >/dev/null

status_json="$(run_vault_cmd "vault status -format=json" || true)"
if [[ -z "$status_json" ]]; then
  echo "Vault is not responding. Check container logs." >&2
  exit 1
fi

initialized="$(python3 - <<'PY' <<<"$status_json"
import json,sys
print(json.load(sys.stdin).get("initialized", False))
PY
)"

if [[ "$initialized" != "True" && "$initialized" != "true" ]]; then
  echo "Initializing Vault..."
  run_vault_cmd "vault operator init -key-shares=1 -key-threshold=1 -format=json" > "$INIT_FILE"
  echo "Init output saved to $INIT_FILE. Store it securely."
fi

sealed="$(python3 - <<'PY' <<<"$status_json"
import json,sys
print(json.load(sys.stdin).get("sealed", True))
PY
)"

if [[ "$sealed" == "True" || "$sealed" == "true" ]]; then
  if [[ -n "${VAULT_UNSEAL_KEY:-}" ]]; then
    unseal_key="$VAULT_UNSEAL_KEY"
  elif [[ -f "$INIT_FILE" ]]; then
    unseal_key="$(python3 - <<'PY' "$INIT_FILE"
import json,sys
with open(sys.argv[1],"r") as f:
    data=json.load(f)
print(data["unseal_keys_b64"][0])
PY
)"
  else
    echo "Vault is sealed and no unseal key was provided. Set VAULT_UNSEAL_KEY or restore $INIT_FILE." >&2
    exit 1
  fi

  run_vault_cmd "vault operator unseal $unseal_key" >/dev/null
  echo "Vault unsealed."
fi

if [[ -z "${VAULT_TOKEN:-}" ]]; then
  if [[ -f "$INIT_FILE" ]]; then
    VAULT_TOKEN="$(python3 - <<'PY' "$INIT_FILE"
import json,sys
with open(sys.argv[1],"r") as f:
    data=json.load(f)
print(data["root_token"])
PY
)"
  else
    echo "VAULT_TOKEN is not set and $INIT_FILE is missing." >&2
    exit 1
  fi
fi
export VAULT_TOKEN

run_vault_cmd "vault secrets enable -path=secret kv-v2" >/dev/null 2>&1 || true
run_vault_cmd "vault auth enable approle" >/dev/null 2>&1 || true

mkdir -p \
  "$ROOT_DIR/.vault/secrets/transactions" \
  "$ROOT_DIR/.vault/secrets/iam" \
  "$ROOT_DIR/.vault/secrets/notifications" \
  "$ROOT_DIR/.vault/secrets/payment" \
  "$ROOT_DIR/.vault/secrets/transaction-postgres" \
  "$ROOT_DIR/.vault/secrets/notifications-postgres" \
  "$ROOT_DIR/.vault/secrets/payment-postgres" \
  "$ROOT_DIR/.vault/secrets/keycloak" \
  "$ROOT_DIR/.vault/secrets/payment-keycloak"

VAULT_TOKEN="$VAULT_TOKEN" bash "$ROOT_DIR/scripts/vault_bootstrap.sh"
VAULT_TOKEN="$VAULT_TOKEN" bash "$ROOT_DIR/scripts/vault_export_approle_files.sh"

echo "Vault persistent setup completed."
