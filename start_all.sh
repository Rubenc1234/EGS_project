#!/bin/bash


# Function to stop all processes on exit if something fails
cleanup() {
    echo "Stopping all services..."
    bash ./stop_all.sh
    exit
}

trap cleanup ERR INT TERM

start_vault() {
    docker-compose -f docker-compose.vault.yml up -d vault >/dev/null
}

start_vault

export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

mkdir -p \
    .vault/secrets/transactions \
    .vault/secrets/iam \
    .vault/secrets/notifications \
    .vault/secrets/payment \
    .vault/secrets/transaction-postgres \
    .vault/secrets/notifications-postgres \
    .vault/secrets/payment-postgres \
    .vault/secrets/keycloak \
    .vault/secrets/payment-keycloak

if [[ ! -d "./.vault/approle" ]]; then
    read -r -p "No Vault AppRole files found. Run Vault bootstrap now? [y/N] " RUN_VAULT_BOOTSTRAP
    case "$RUN_VAULT_BOOTSTRAP" in
        y|Y|yes|YES)
            # Bootstrap Vault with the current secrets and export the AppRole files for the compose run.
            bash ./scripts/vault_bootstrap.sh
            bash ./scripts/vault_export_approle_files.sh
            ;;
        *)
            echo "Run ./scripts/vault_bootstrap.sh once before starting the stack." >&2
            exit 1
            ;;
    esac
fi

# 1. Start the unified Docker Compose stack
echo "Starting unified Docker stack..."
# --build
docker-compose up -d --build

echo "--------------------------------------------------"
echo "All services started in background!"
echo "Vault: http://localhost:8200 (internal container; used for secret bootstrap)"
echo "Traefik: http://localhost"
echo "App: http://app.pt"
echo "Payment UI: http://payment.pt"
echo "IAM: http://iam.pt"
echo "Transactions: http://transactions.pt"
echo "Notifications: http://notifications.pt"
echo "Grafana: http://grafana.pt"
echo "--------------------------------------------------"
echo "Logs are available in *.log files."
echo "Use ./stop_all.sh to stop everything."
