package handlers

import (
	"net/http"
	"os"

	"egs-notifications/internal/auth"

	"github.com/gin-gonic/gin"
)

type ClientTokenRequest struct {
	UserID string `json:"user_id" binding:"required"`
}

// GenerateClientToken creates a scoped, short-lived token for the web app frontend.
// The Composer calls this using its forever API key.
// @Summary Generate Subscriber Token
// @Description Generates a 4-hour JWT for the frontend browser to connect to the SSE stream.
// @Tags auth
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body ClientTokenRequest true "User ID Payload"
// @Success 200 {object} map[string]interface{} "token, expires_in"
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /auth/token [post]
func GenerateClientToken() gin.HandlerFunc {
	return func(c *gin.Context) {
		var req ClientTokenRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		// Context injected by RequireComposer middleware
		clientID := c.MustGet("clientID").(uint)
		secret := os.Getenv("JWT_SECRET")

		token, err := auth.GenerateSubscriberToken(secret, clientID, req.UserID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to generate client token"})
			return
		}

		c.JSON(http.StatusOK, gin.H{"token": token, "expires_in": 14400}) // 4 hours
	}
}
