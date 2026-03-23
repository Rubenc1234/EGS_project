#!/bin/bash

# stop_all.sh - Stops all microservices, databases, and the frontend.

# 1. Stop background processes
if [ -f .service_pids ]; then
    echo "Stopping background services from .service_pids..."
    PIDS=$(cat .service_pids)
    for PID in $PIDS; do
        if kill -0 $PID 2>/dev/null; then
            kill -TERM -$PID
            echo "Killed process $PID"
        fi
    done
    rm .service_pids
else
    echo "No .service_pids file found. Trying to kill by name patterns..."
    pkill -f "python3 -m iam_service.app_iam"
    pkill -f "python3 -m payment_service.app_payment"
    pkill -f "python3 app.py"
    pkill -f "go run cmd/api/main.go"
    pkill -f "spring-boot:run"
    pkill -f "npm run dev"
fi

# 2. Stop Docker containers
echo "Stopping Docker containers..."
(cd iam_service && sudo docker-compose down)
(cd payment_service && sudo docker-compose down)
(cd transactions_service && sudo docker-compose down)

echo "All services stopped."
