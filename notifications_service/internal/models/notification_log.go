package models

import (
	"time"
)

type NotificationLog struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	ClientID  uint      `gorm:"index" json:"client_id"`
	UserID    string    `gorm:"index" json:"user_id"`
	Title     string    `json:"title"`
	Channel   string    `json:"channel"` // "web_push" or "email"
	Status    string    `json:"status"`  // "sent", "failed"
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
}
