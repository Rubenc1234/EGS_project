#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPROLE_DIR="$ROOT_DIR/.vault/approle"

mkdir -p "$APPROLE_DIR"

write_file() {
  local path="$1"
  local content="$2"
  mkdir -p "$(dirname "$path")"
  printf '%s' "$content" > "$path"
}

read_role_id() {
  local role_name="$1"
  docker-compose -f "$ROOT_DIR/docker-compose.vault.yml" exec -T vault sh -lc \
    "export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=root; vault read -field=role_id auth/approle/role/${role_name}/role-id"
}

write_secret_id() {
  local role_name="$1"
  docker-compose -f "$ROOT_DIR/docker-compose.vault.yml" exec -T vault sh -lc \
    "export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=root; vault write -f -field=secret_id auth/approle/role/${role_name}/secret-id"
}

create_approle_files() {
  local role_name="$1"
  local target_dir="$APPROLE_DIR/$role_name"
  local role_id
  local secret_id

  role_id="$(read_role_id "$role_name")"
  secret_id="$(write_secret_id "$role_name")"

  write_file "$target_dir/role_id" "$role_id"
  write_file "$target_dir/secret_id" "$secret_id"
}

create_approle_files transactions
create_approle_files iam
create_approle_files notifications
create_approle_files payment
create_approle_files transaction-postgres
create_approle_files notifications-postgres
create_approle_files payment-postgres
create_approle_files keycloak
create_approle_files payment-keycloak
