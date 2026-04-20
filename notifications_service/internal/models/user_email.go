package models

import "time"

// UserEmail stores the notification email address for a specific user under a client tenant.
type UserEmail struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	ClientID  uint      `gorm:"uniqueIndex:idx_client_user;not null" json:"client_id"`
	UserID    string    `gorm:"uniqueIndex:idx_client_user;not null" json:"user_id"`
	Email     string    `gorm:"not null" json:"email"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt time.Time `gorm:"autoUpdateTime" json:"updated_at"`

	Client Client `gorm:"foreignKey:ClientID;constraint:OnUpdate:CASCADE,OnDelete:CASCADE;" json:"-"`
}
