package models

import (
	"time"
)

// Notification represents the ORM structure for a single notification.
// We only keep it as a ledger of what was sent.
type Notification struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	ClientID  uint      `gorm:"index;not null" json:"client_id"` // Tenant isolation
	UserID    string    `gorm:"index;not null" json:"user_id"`
	Message   string    `gorm:"not null" json:"message"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time `gorm:"autoUpdateTime" json:"-"`

	// Foreign key relation (optional, but good for DB integrity)
	Client Client `gorm:"foreignKey:ClientID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
}
