package main

import (
	"log"
	"net/http"
	"os"
	"time"

	"egs-notifications/internal/cache"
	"egs-notifications/internal/db"
	"egs-notifications/internal/server"

	"github.com/joho/godotenv"

	_ "egs-notifications/docs"
)

// @title Notifications API
// @version 1.0
// @description Multi-Tenant Web Push Notifications Service
// @BasePath /v1
// @securityDefinitions.apikey MasterAuth
// @in header
// @name Authorization
// @description Master Admin Secret for platform management (Format: Bearer <secret>)
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Composer API Key OR 4-hour Subscriber JWT (Format: Bearer <token>)
func main() {
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, reading system environment variables")
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		log.Fatal("DATABASE_URL environment variable is required")
	}

	redisURL := os.Getenv("REDIS_URL")
	if redisURL == "" {
		redisURL = "localhost:6379"
	}

	if os.Getenv("MASTER_ADMIN_SECRET") == "" || os.Getenv("JWT_SECRET") == "" {
		log.Fatal("MASTER_ADMIN_SECRET and JWT_SECRET are required in environment")
	}

	database := db.InitDB(dsn)
	redisCache := cache.InitRedis(redisURL)

	router := server.SetupRoutes(database, redisCache)

	srv := &http.Server{
		Addr:         ":" + port,
		Handler:      router,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	log.Printf("Service running on http://localhost:%s\n", port)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Server failed: %v", err)
	}
}
