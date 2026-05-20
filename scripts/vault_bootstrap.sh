#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# This script seeds Vault with the secrets the stack needs.
# It is safe to run again when you want to refresh the stored values.
source "$ROOT_DIR/set_env.sh"

read_env_value() {
  local file_path="$1"
  local key_name="$2"

  awk -v key="$key_name" '
    $0 ~ "^[[:space:]]*" key "[[:space:]]*=" {
      sub("^[[:space:]]*" key "[[:space:]]*=[[:space:]]*", "", $0)
      gsub(/^"/, "", $0)
      gsub(/"$/, "", $0)
      print
      exit
    }
  ' "$file_path"
}

quote_value() {
  printf '%q' "$1"
}

require_file_value() {
  local file_path="$1"
  local key_name="$2"
  local value
  value="$(read_env_value "$file_path" "$key_name")"
  if [[ -z "$value" ]]; then
    echo "Missing $key_name in $file_path" >&2
    exit 1
  fi
  printf '%s' "$value"
}

vault_up() {
  # Keep Vault on the shared app network and start the dev container if needed.
  if ! docker network inspect egs-network >/dev/null 2>&1; then
    docker network create egs-network >/dev/null
  fi
  docker-compose -f docker-compose.vault.yml up -d vault >/dev/null
}

wait_for_vault() {
  # Wait until the dev Vault accepts commands before writing secrets.
  local attempts=0
  until docker-compose -f docker-compose.vault.yml exec -T -e VAULT_TOKEN vault sh -lc 'export VAULT_ADDR=http://127.0.0.1:8200; export VAULT_TOKEN=${VAULT_TOKEN:-root}; vault status >/dev/null 2>&1'; do
    attempts=$((attempts + 1))
    if [[ $attempts -ge 30 ]]; then
      echo "Vault did not become ready in time" >&2
      exit 1
    fi
    sleep 1
  done
}

vault_put() {
  # Store one KV v2 secret path in Vault using the root token.
  local path="$1"
  shift

  docker-compose -f docker-compose.vault.yml exec -T -e VAULT_TOKEN vault sh -s <<EOF
set -eu
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=${VAULT_TOKEN:-root}
vault kv put $path $*
EOF
}

vault_up
wait_for_vault

iam_client_secret="$(require_file_value "$ROOT_DIR/.env" "CLIENT_SECRET")"
payment_stripe_secret="$(require_file_value "$ROOT_DIR/payment_service/.env" "STRIPE_SECRET_KEY")"
payment_stripe_webhook_secret="$(require_file_value "$ROOT_DIR/payment_service/.env" "STRIPE_WEBHOOK_SECRET")"
payment_client_secret="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_CLIENT_SECRET")"
notifications_db_url="$(require_file_value "$ROOT_DIR/notifications_service/.env" "DATABASE_URL")"
notifications_port="$(require_file_value "$ROOT_DIR/notifications_service/.env" "PORT")"
payment_keycloak_url="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_KEYCLOAK_URL")"
payment_keycloak_public_url="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_KEYCLOAK_PUBLIC_URL")"
payment_public_url="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_PUBLIC_URL")"
payment_realm="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_REALM")"
payment_client_id="$(require_file_value "$ROOT_DIR/payment_service/.env" "PAYMENT_CLIENT_ID")"
twilio_account_sid="$(require_file_value "$ROOT_DIR/payment_service/.env" "TWILIO_ACCOUNT_SID")"
twilio_auth_token="$(require_file_value "$ROOT_DIR/payment_service/.env" "TWILIO_AUTH_TOKEN")"
twilio_from_number="$(require_file_value "$ROOT_DIR/payment_service/.env" "TWILIO_FROM_NUMBER")"
payment_transactions_service_url="$(require_file_value "$ROOT_DIR/payment_service/.env" "TRANSACTIONS_SERVICE_URL")"
transaction_db_password="mypassword"
notifications_db_password="postgres"
payment_db_password="paypassword"

vault_put secret/egs/global \
  master_key_secret="$(quote_value "$MASTER_KEY_SECRET")" \
  blockchain_node_url="$(quote_value "$BLOCKCHAIN_NODE_URL")" \
  notifications_api_key="$(quote_value "$NOTIFICATIONS_API_KEY")" \
  notifications_api_key_payments="$(quote_value "$NOTIFICATIONS_API_KEY_PAYMENTS")"

vault_put secret/egs/iam \
  client_secret="$(quote_value "$iam_client_secret")" \
  keycloak_admin_password="$(quote_value "admin")"

vault_put secret/egs/transactions \
  master_key_for_wallet="$(quote_value "$MASTER_KEY_FOR_WALLET")" \
  transaction_db_password="$(quote_value "$transaction_db_password")"

docker-compose -f docker-compose.vault.yml exec -T -e VAULT_TOKEN vault sh -s <<'EOF'
set -eu
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=${VAULT_TOKEN:-root}
vault auth enable approle || true
vault policy write egs-transactions - <<'POLICY'
path "secret/data/egs/transactions" {
  capabilities = ["read"]
}

path "secret/data/egs/global" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-iam - <<'POLICY'
path "secret/data/egs/iam" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-notifications - <<'POLICY'
path "secret/data/egs/notifications" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-payment - <<'POLICY'
path "secret/data/egs/payment" {
  capabilities = ["read"]
}

path "secret/data/egs/global" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-transaction-postgres - <<'POLICY'
path "secret/data/egs/transactions" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-notifications-postgres - <<'POLICY'
path "secret/data/egs/notifications" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-payment-postgres - <<'POLICY'
path "secret/data/egs/payment" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-keycloak - <<'POLICY'
path "secret/data/egs/iam" {
  capabilities = ["read"]
}
POLICY
vault policy write egs-payment-keycloak - <<'POLICY'
path "secret/data/egs/payment" {
  capabilities = ["read"]
}
POLICY
vault write auth/approle/role/transactions \
  token_policies="egs-transactions" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/iam \
  token_policies="egs-iam" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/notifications \
  token_policies="egs-notifications" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/payment \
  token_policies="egs-payment" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/transaction-postgres \
  token_policies="egs-transaction-postgres" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/notifications-postgres \
  token_policies="egs-notifications-postgres" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/payment-postgres \
  token_policies="egs-payment-postgres" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/keycloak \
  token_policies="egs-keycloak" \
  token_ttl="1h" \
  token_max_ttl="4h"
vault write auth/approle/role/payment-keycloak \
  token_policies="egs-payment-keycloak" \
  token_ttl="1h" \
  token_max_ttl="4h"
EOF

vault_put secret/egs/notifications \
  port="$(quote_value "$notifications_port")" \
  master_admin_secret="$(quote_value "$MASTER_ADMIN_SECRET")" \
  jwt_secret="$(quote_value "$JWT_SECRET")" \
  database_url="$(quote_value "$notifications_db_url")" \
  notifications_db_password="$(quote_value "$notifications_db_password")"

vault_put secret/egs/payment \
  payment_keycloak_url="$(quote_value "$payment_keycloak_url")" \
  payment_keycloak_public_url="$(quote_value "$payment_keycloak_public_url")" \
  payment_public_url="$(quote_value "$payment_public_url")" \
  payment_realm="$(quote_value "$payment_realm")" \
  payment_client_id="$(quote_value "$payment_client_id")" \
  stripe_secret_key="$(quote_value "$payment_stripe_secret")" \
  stripe_webhook_secret="$(quote_value "$payment_stripe_webhook_secret")" \
  payment_client_secret="$(quote_value "$payment_client_secret")" \
  twilio_account_sid="$(quote_value "$twilio_account_sid")" \
  twilio_auth_token="$(quote_value "$twilio_auth_token")" \
  twilio_from_number="$(quote_value "$twilio_from_number")" \
  transactions_service_url="$(quote_value "$payment_transactions_service_url")" \
  keycloak_admin_password="$(quote_value "admin")" \
  payment_db_password="$(quote_value "$payment_db_password")"

echo "Vault bootstrap completed."
