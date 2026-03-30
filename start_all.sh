#!/bin/bash


# Function to stop all processes on exit if something fails
cleanup() {
    echo "Stopping all services..."
    ./stop_all.sh
    exit
}

ENV_SCRIPT="./set_env.sh"

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

# 1. Start Databases and Keycloak via Docker Compose
echo "Starting Databases and Keycloak..."
(cd iam_service && sudo docker-compose up -d)
(cd payment_service && sudo docker-compose up -d)
(cd transactions_service && sudo docker-compose up -d)
echo "Necessário fazer 1o --build antes de subir pela primeira vez."
(cd notifications_service && sudo docker-compose up -d)

echo "Waiting for databases to be ready..."
sleep 10

# 2. Start IAM Service (Python)
echo "Starting IAM Service on port 5000..."
source venv/bin/activate
python3 -m iam_service.app_iam > iam_service.log 2>&1 &
IAM_PID=$!

# 3. Start Payment Service (Python)
echo "Starting Payment Service on port 5002..."
python3 -m payment_service.app_payment > payment_service.log 2>&1 &
PAYMENT_PID=$!

# 4. Start Notifications Service (Go)
echo "Starting Notifications Service on port 5003..."
(cd notifications_service && \
  setsid env \
    PORT=5003 \
    DATABASE_URL="host=localhost user=postgres password=postgres dbname=notifications port=5434 sslmode=disable" \
    MASTER_ADMIN_SECRET="${MASTER_ADMIN_SECRET:?Erro: MASTER_ADMIN_SECRET não definido}" \
    JWT_SECRET="${JWT_SECRET:?Erro: JWT_SECRET não definido}" \
    go run cmd/api/main.go \
) > notifications_service.log 2>&1 &
NOTIFICATIONS_PID=$!

# 5. Start Transactions Service (Java/Spring Boot)
echo "Starting Transactions Service on port 8081..."
(cd transactions_service && source /home/rubencc/4ano/EGS/project_egs/set_env.sh && ./mvnw spring-boot:run) > transactions_service.log 2>&1 &
TRANSACTIONS_PID=$!

# 6. Start Composer Service (Python)
#echo "Starting Composer Service (root app.py) on port 5001..."
#python3 app.py > app.log 2>&1 &
#COMPOSER_PID=$!

# 7. Start Main Frontend (Vite)
echo "Starting Main Frontend on port 5175..."
(cd frontend && npm run dev -- --port 5175) > frontend.log 2>&1 &
FRONTEND_PID=$!

# 8. Start Payments Frontend (Vite)
echo "Starting Payments Frontend on port 5174..."
(cd payment_service/frontend && npm run dev -- --port 5174) > payment_frontend.log 2>&1 &
PAYMENT_FRONTEND_PID=$!

echo "--------------------------------------------------"
echo "All services started in background!"
echo "IAM: http://localhost:5000"
echo "Payment: http://localhost:5002"
echo "Notifications: http://localhost:5003"
echo "Transactions: http://localhost:8081"
echo "Main Frontend: http://localhost:5175"
echo "Payments Frontend: http://localhost:5174"
echo "--------------------------------------------------"
echo "Logs are available in *.log files."
echo "Use ./stop_all.sh to stop everything."

# Save PIDs to a file for stop_all.sh
echo "$IAM_PID $PAYMENT_PID $NOTIFICATIONS_PID $TRANSACTIONS_PID $COMPOSER_PID $FRONTEND_PID $PAYMENT_FRONTEND_PID" > .service_pids
