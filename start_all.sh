#!/bin/bash


# Function to stop all processes on exit if something fails
cleanup() {
    echo "Stopping all services..."
    bash ./stop_all.sh
    exit
}

trap cleanup ERR INT TERM


# Bootstrap Vault with the current secrets and export them for the compose run.
bash ./scripts/vault_bootstrap.sh
bash ./scripts/vault_export_approle_files.sh

# 1. Start the unified Docker Compose stack
echo "Starting unified Docker stack..."
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
