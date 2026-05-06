#!/usr/bin/env bash
set -euo pipefail

export_secret() {
  local env_name="$1"
  local vault_path="$2"
  local field_name="$3"
  local value

  value="$(docker-compose -f docker-compose.vault.yml exec -T vault sh -lc "export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=root; vault kv get -field=${field_name} ${vault_path}")"
  printf 'export %s=%q\n' "$env_name" "$value"
}

export_approle_role_id() {
  local env_name="$1"
  local role_name="$2"
  local value

  value="$(docker-compose -f docker-compose.vault.yml exec -T vault sh -lc "export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=root; vault read -field=role_id auth/approle/role/${role_name}/role-id")"
  printf 'export %s=%q\n' "$env_name" "$value"
}

export_approle_secret_id() {
  local env_name="$1"
  local role_name="$2"
  local value

  value="$(docker-compose -f docker-compose.vault.yml exec -T vault sh -lc "export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=root; vault write -f -field=secret_id auth/approle/role/${role_name}/secret-id")"
  printf 'export %s=%q\n' "$env_name" "$value"
}

export_secret KEYCLOAK_ADMIN_PASSWORD secret/egs/iam keycloak_admin_password
export_secret TRANSACTION_DB_PASSWORD secret/egs/transactions transaction_db_password
export_secret NOTIFICATIONS_DB_PASSWORD secret/egs/notifications notifications_db_password
export_secret PAYMENT_KEYCLOAK_ADMIN_PASSWORD secret/egs/payment keycloak_admin_password
export_secret PAYMENT_DB_PASSWORD secret/egs/payment payment_db_password
export_approle_role_id TRANSACTIONS_VAULT_ROLE_ID transactions
export_approle_secret_id TRANSACTIONS_VAULT_SECRET_ID transactions
export_approle_role_id IAM_VAULT_ROLE_ID iam
export_approle_secret_id IAM_VAULT_SECRET_ID iam
export_approle_role_id NOTIFICATIONS_VAULT_ROLE_ID notifications
export_approle_secret_id NOTIFICATIONS_VAULT_SECRET_ID notifications
export_approle_role_id PAYMENT_VAULT_ROLE_ID payment
export_approle_secret_id PAYMENT_VAULT_SECRET_ID payment
