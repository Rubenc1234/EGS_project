package handlers

import (
	"net/http"

	"egs-notifications/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// GetClientInfo returns public settings for the authenticated Composer.
// @Summary Get client information
// @Description Returns the client's name and VAPID public key. Useful if the composer wants to cache or hardcode their VAPID key instead of fetching it on every token request.
// @Tags client
// @Security BearerAuth
// @Produce json
// @Success 200 {object} map[string]interface{} "name, vapid_public_key"
// @Failure 401 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Router /info [get]
func GetClientInfo(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.MustGet("clientID").(uint) // Injected securely by RequireComposer

		var client models.Client
		// Only select safe fields. NEVER return the private VAPID key or API key hash.
		if err := db.Select("name, vapid_public_key").First(&client, clientID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "Client not found"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"name":             client.Name,
			"vapid_public_key": client.VapidPublicKey,
		})
	}
}
