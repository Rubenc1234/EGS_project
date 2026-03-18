package models

import (
	"time"
)

// Client represents an external company/app that bought an API key.
type Client struct {
	ID         uint      `gorm:"primaryKey" json:"id"`
	Name       string    `gorm:"not null" json:"name"`
	APIKeyHash string    `gorm:"uniqueIndex;not null" json:"-"` // Never return the hash in JSON
	CreatedAt  time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt  time.Time `gorm:"autoUpdateTime" json:"-"`
}
