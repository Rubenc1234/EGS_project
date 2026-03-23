#!/bin/bash

# stop_all.sh - Stops all microservices, databases, and the frontend.

# 1. Stop background processes
if [ -f .service_pids ]; then
    echo "Stopping background services from .service_pids..."
    PIDS=$(cat .service_pids)
    for PID in $PIDS; do
        if kill -0 $PID 2>/dev/null; then
            # Kill the process and its children by killing the process group if possible
            # But since we didn't start them in a PGID, we'll just kill the PID
            kill $PID 2>/dev/null
            echo "Sent SIGTERM to process $PID"
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

# 2. Forcefully free the specific ports if still in use
echo "Cleaning up ports (5000, 5001, 5002, 5003, 8081, 5173, 5174)..."
for PORT in 5000 5001 5002 5003 8081 5173 5174; do
    # Try to find and kill processes using these ports
    PID=$(lsof -t -i:$PORT)
    if [ ! -z "$PID" ]; then
        echo "Port $PORT is still in use by $PID. Killing..."
        kill -9 $PID 2>/dev/null
    fi
done

# 3. Stop Docker containers
echo "Stopping Docker containers..."
(cd iam_service && sudo docker-compose down -v)
(cd payment_service && sudo docker-compose down -v)
(cd transactions_service && sudo docker-compose down -v)

echo "All services stopped."
