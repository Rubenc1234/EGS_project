#!/bin/bash


# Function to stop all processes on exit if something fails
cleanup() {
    echo "Stopping all services..."
    ./stop_all.sh
    exit
}

# 1. Start Databases and Keycloak via Docker Compose
echo "Starting Databases and Keycloak..."
(cd iam_service && sudo docker-compose up -d)
(cd payment_service && sudo docker-compose up -d)
(cd transactions_service && sudo docker-compose up -d)

echo "Waiting for databases to be ready..."
sleep 10

# 2. Start IAM Service (Python)
echo "Starting IAM Service on port 5000..."
python3 -m iam_service.app_iam > iam_service.log 2>&1 &
IAM_PID=$!

# 3. Start Payment Service (Python)
echo "Starting Payment Service on port 5002..."
python3 -m payment_service.app_payment > payment_service.log 2>&1 &
PAYMENT_PID=$!

# 4. Start Notifications Service (Go)
echo "Starting Notifications Service on port 5003..."
export PORT=5003
export DATABASE_URL="host=localhost user=postgres password=postgres dbname=notifications port=5432 sslmode=disable"
export MASTER_ADMIN_SECRET="super_secret_master_key"
export JWT_SECRET="another_super_secret_jwt_key"
(cd notifications_service && go run cmd/api/main.go) > notifications_service.log 2>&1 &
NOTIFICATIONS_PID=$!

# 5. Start Transactions Service (Java/Spring Boot)
echo "Starting Transactions Service on port 8081..."
(cd transactions_service && ./mvnw spring-boot:run) > transactions_service.log 2>&1 &
TRANSACTIONS_PID=$!

# 6. Start Composer Service (Python)
echo "Starting Composer Service (root app.py) on port 5001..."
python3 app.py > app.log 2>&1 &
COMPOSER_PID=$!

# 7. Start Frontend (Vite)
echo "Starting Frontend on port 5173..."
(cd frontend && npm run dev) > frontend.log 2>&1 &
FRONTEND_PID=$!

echo "--------------------------------------------------"
echo "All services started in background!"
echo "IAM: http://localhost:5000"
echo "Composer: http://localhost:5001"
echo "Payment: http://localhost:5002"
echo "Notifications: http://localhost:5003"
echo "Transactions: http://localhost:8081"
echo "Frontend: http://localhost:5173"
echo "--------------------------------------------------"
echo "Logs are available in *.log files."
echo "Use ./stop_all.sh to stop everything."

# Save PIDs to a file for stop_all.sh
echo "$IAM_PID $PAYMENT_PID $NOTIFICATIONS_PID $TRANSACTIONS_PID $COMPOSER_PID $FRONTEND_PID" > .service_pids
