package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
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
	// Configure the default structured logger to output JSON
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	if err := godotenv.Load(); err != nil {
		slog.Warn("No .env file found, reading system environment variables")
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		slog.Error("DATABASE_URL environment variable is required")
		os.Exit(1)
	}

	redisURL := os.Getenv("REDIS_URL")
	if redisURL == "" {
		redisURL = "localhost:6379"
	}

	if os.Getenv("MASTER_ADMIN_SECRET") == "" || os.Getenv("JWT_SECRET") == "" {
		slog.Error("MASTER_ADMIN_SECRET and JWT_SECRET are required in environment")
		os.Exit(1)
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

	go func() {
		slog.Info("Service running", "port", port, "url", "http://localhost:"+port)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("Server failed", "error", err)
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	<-quit
	slog.Info("Interrupt signal received. Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("Server forced to shutdown", "error", err)
		os.Exit(1)
	}

	slog.Info("Server gracefully exited")
}
