#!/bin/bash


# Function to stop all processes on exit if something fails
cleanup() {
    echo "Stopping all services..."
    bash ./stop_all.sh
    exit
}

trap cleanup ERR INT TERM

ENV_SCRIPT="./set_env.sh"
VAULT_ENV_FILE="./.vault-env"

if [[ -f "$ENV_SCRIPT" ]]; then
    echo "✅ Environment script '$ENV_SCRIPT' encontrado."
    echo "Carregando variáveis de ambiente..."
    # shellcheck disable=SC1090
    source "$ENV_SCRIPT"
else
    echo "⚠️  Environment script '$ENV_SCRIPT' não encontrado!"
    echo "Por favor, crie o arquivo e defina as variáveis necessárias com export."
    echo "Exemplo:"
    echo "  export NOTIFICATIONS_API_KEY=sk_live_XXXX"
    echo "Abortando script..."
    exit 1
fi

# Bootstrap Vault with the current secrets and export them for the compose run.
bash ./scripts/vault_bootstrap.sh
bash ./scripts/vault_export_env.sh > "$VAULT_ENV_FILE"

# shellcheck disable=SC1090
source "$VAULT_ENV_FILE"

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
echo "--------------------------------------------------"
echo "Logs are available in *.log files."
echo "Use ./stop_all.sh to stop everything."
