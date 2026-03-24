package models

import "time"

// PushSubscription represents a web push endpoint for a specific user.
type PushSubscription struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	ClientID  uint      `gorm:"index;not null" json:"client_id"`
	UserID    string    `gorm:"index;not null" json:"user_id"`
	Endpoint  string    `gorm:"uniqueIndex;not null" json:"endpoint"` // Web Push Endpoint URL
	P256dh    string    `gorm:"not null" json:"p256dh"`               // Public key for encryption
	Auth      string    `gorm:"not null" json:"auth"`                 // Auth secret
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`

	Client Client `gorm:"foreignKey:ClientID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
}
