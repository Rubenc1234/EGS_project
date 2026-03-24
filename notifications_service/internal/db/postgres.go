package db

import (
	"log/slog"
	"os"

	"egs-notifications/internal/models"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func InitDB(dsn string) *gorm.DB {
	logLevel := logger.Info
	if os.Getenv("GIN_MODE") == "release" {
		logLevel = logger.Error
	}

	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logLevel),
	})
	if err != nil {
		slog.Error("Failed to connect to database", "error", err)
		os.Exit(1)
	}

	slog.Info("Running database migrations...")
	err = db.AutoMigrate(&models.Client{}, &models.PushSubscription{})
	if err != nil {
		slog.Error("Failed to migrate database", "error", err)
		os.Exit(1)
	}

	return db
}
