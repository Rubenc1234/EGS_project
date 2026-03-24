package handlers

import (
	"net/http"
	"os"
	"strconv"

	"egs-notifications/internal/auth"
	"egs-notifications/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type ClientTokenRequest struct {
	UserID string `json:"user_id" binding:"required"`
}

// GenerateClientToken creates a scoped, short-lived token for the web app frontend.
// @Summary Generate Subscriber Token
// @Description Generates a JWT for the frontend browser and returns the VAPID Public Key required to prompt Web Push.
// @Tags auth
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body ClientTokenRequest true "User ID Payload"
// @Success 200 {object} map[string]interface{} "token, expires_in, vapid_public_key"
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /auth/token [post]
func GenerateClientToken(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var req ClientTokenRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		// Context injected by RequireComposer middleware
		clientID := c.MustGet("clientID").(uint)
		secret := os.Getenv("JWT_SECRET")

		// Parse the expiration from environment, default to 15 if missing or invalid
		expMinutesStr := os.Getenv("JWT_EXPIRATION_MINUTES")
		expMinutes, err := strconv.Atoi(expMinutesStr)
		if err != nil || expMinutes <= 0 {
			expMinutes = 15
		}

		// Retrieve Vapid Public Key to give to the frontend
		var client models.Client
		if err := db.Select("vapid_public_key").First(&client, clientID).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch client settings"})
			return
		}

		token, err := auth.GenerateSubscriberToken(secret, clientID, req.UserID, expMinutes)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to generate client token"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"token":            token,
			"expires_in":       expMinutes * 60, // Standard JSON APIs return expires_in in seconds
			"vapid_public_key": client.VapidPublicKey,
		})
	}
}
