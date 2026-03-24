package handlers

import (
	"net/http"

	"egs-notifications/internal/auth"
	"egs-notifications/internal/models"

	"github.com/SherClockHolmes/webpush-go"
	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type CreateClientRequest struct {
	Name string `json:"name" binding:"required"`
}

// CreateClient generates a new API key and VAPID keys for a new customer/app.
// @Summary Create a new client tenant
// @Description Registers a new client app and generates their forever API Key. This raw key is returned ONLY ONCE.
// @Tags admin
// @Security MasterAuth
// @Accept json
// @Produce json
// @Param payload body CreateClientRequest true "Client Name"
// @Success 201 {object} map[string]interface{}
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /admin/clients [post]
func CreateClient(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var req CreateClientRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		rawKey, hash := auth.GenerateAPIKey()

		// Generate Web Push VAPID keys for multi-tenant isolation
		privateKey, publicKey, err := webpush.GenerateVAPIDKeys()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to generate VAPID keys"})
			return
		}

		client := models.Client{
			Name:            req.Name,
			APIKeyHash:      hash,
			VapidPublicKey:  publicKey,
			VapidPrivateKey: privateKey,
		}

		if err := db.Create(&client).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to create client"})
			return
		}

		c.JSON(http.StatusCreated, gin.H{
			"message":          "Client created successfully. SAVE THIS API KEY NOW.",
			"client_id":        client.ID,
			"api_key":          rawKey,
			"vapid_public_key": publicKey,
		})
	}
}

// RegenerateClientKey overwrites the old API key.
// @Summary Regenerate client API Key
// @Description Revokes the old API key and generates a new one. The old key will immediately stop working.
// @Tags admin
// @Security MasterAuth
// @Produce json
// @Param id path int true "Client ID"
// @Success 200 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Router /admin/clients/{id}/key [patch]
func RegenerateClientKey(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.Param("id")

		var client models.Client
		if err := db.First(&client, clientID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "Client not found"})
			return
		}

		rawKey, hash := auth.GenerateAPIKey()
		client.APIKeyHash = hash

		if err := db.Save(&client).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update client API key"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"message":     "Key regenerated successfully.",
			"client_id":   client.ID,
			"new_api_key": rawKey,
		})
	}
}

// ListClients returns a list of all registered clients.
// @Summary List all clients
// @Tags admin
// @Security MasterAuth
// @Produce json
// @Success 200 {array} models.Client
// @Router /admin/clients [get]
func ListClients(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var clients []models.Client
		if err := db.Find(&clients).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to fetch clients"})
			return
		}
		c.JSON(http.StatusOK, clients)
	}
}

// DeleteClient removes a client.
// @Summary Revoke and delete client
// @Tags admin
// @Security MasterAuth
// @Produce json
// @Param id path int true "Client ID"
// @Success 200 {object} map[string]interface{}
// @Router /admin/clients/{id} [delete]
func DeleteClient(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.Param("id")
		if err := db.Delete(&models.Client{}, clientID).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete client"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"message": "Client revoked and deleted"})
	}
}
