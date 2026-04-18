#!/bin/bash

# stop_all.sh - Stops all microservices, databases, and the frontend.

# 1. Stop the unified Docker Compose stack
echo "Stopping unified Docker stack..."
docker-compose down

# 2. Forcefully free the specific ports if still in use
echo "Cleaning up ports (5000, 5001, 5002, 5003, 8080, 8081, 8082, 8083, 5174, 5175, 5432, 5433, 6379)..."
for PORT in 5000 5001 5002 5003 8080 8081 8082 8083 5174 5175 5432 5433 6379; do
    PID=$(lsof -t -i:$PORT 2>/dev/null)
    if [ ! -z "$PID" ]; then
        echo "Port $PORT is still in use by $PID. Killing..."
        kill -9 $PID 2>/dev/null
    fi
done

echo "All services stopped."
