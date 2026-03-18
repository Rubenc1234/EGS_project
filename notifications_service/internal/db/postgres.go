package db

import (
	"log"
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
		log.Fatalf("Failed to connect to database: %v", err)
	}

	log.Println("Running database migrations...")
	// Migrate Client first since Notification depends on it
	err = db.AutoMigrate(&models.Client{}, &models.Notification{})
	if err != nil {
		log.Fatalf("Failed to migrate database: %v", err)
	}

	return db
}
