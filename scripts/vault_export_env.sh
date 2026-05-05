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

export_secret MASTER_KEY_SECRET secret/egs/global master_key_secret
export_secret BLOCKCHAIN_NODE_URL secret/egs/global blockchain_node_url
export_secret NOTIFICATIONS_API_KEY secret/egs/global notifications_api_key
export_secret NOTIFICATIONS_API_KEY_PAYMENTS secret/egs/global notifications_api_key_payments
export_secret IAM_CLIENT_SECRET secret/egs/iam client_secret
export_secret KEYCLOAK_ADMIN_PASSWORD secret/egs/iam keycloak_admin_password
export_secret KEYCLOAK_CLIENT_SECRET secret/egs/transactions keycloak_client_secret
export_secret MASTER_KEY_FOR_WALLET secret/egs/transactions master_key_for_wallet
export_secret APP_INTERNAL_API_KEY secret/egs/transactions app_internal_api_key
export_secret TRANSACTION_DB_PASSWORD secret/egs/transactions transaction_db_password
export_secret MASTER_ADMIN_SECRET secret/egs/notifications master_admin_secret
export_secret JWT_SECRET secret/egs/notifications jwt_secret
export_secret NOTIFICATIONS_DB_PASSWORD secret/egs/notifications notifications_db_password
export_secret PAYMENT_STRIPE_SECRET_KEY secret/egs/payment stripe_secret_key
export_secret PAYMENT_STRIPE_WEBHOOK_SECRET secret/egs/payment stripe_webhook_secret
export_secret PAYMENT_CLIENT_SECRET secret/egs/payment payment_client_secret
export_secret PAYMENT_KEYCLOAK_ADMIN_PASSWORD secret/egs/payment keycloak_admin_password
export_secret PAYMENT_DB_PASSWORD secret/egs/payment payment_db_password
