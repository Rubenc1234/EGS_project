package handlers

import (
	"net/http"

	"egs-notifications/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type SetUserEmailRequest struct {
	Email string `json:"email" binding:"required,email"`
}

// SetUserEmail sets or updates the notification email for a user in the authenticated client scope.
// @Summary Set user notification email
// @Description Creates or updates a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param user_id path string true "User ID"
// @Param payload body SetUserEmailRequest true "Email payload"
// @Success 200 {object} map[string]interface{}
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /users/{user_id}/email [put]
func SetUserEmail(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.Param("user_id")
		clientID := c.MustGet("clientID").(uint)

		var req SetUserEmailRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		userEmail := models.UserEmail{
			ClientID: clientID,
			UserID:   userID,
			Email:    req.Email,
		}

		// Update if exists, otherwise create
		if err := db.Where(models.UserEmail{ClientID: clientID, UserID: userID}).Assign(models.UserEmail{Email: req.Email}).FirstOrCreate(&userEmail).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to set user email"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"message": "Email updated successfully",
			"user_id": userID,
			"email":   userEmail.Email,
		})
	}
}

// GetUserEmail returns the notification email for a user in the authenticated client scope.
// @Summary Get user notification email
// @Description Retrieves a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Produce json
// @Param user_id path string true "User ID"
// @Success 200 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Router /users/{user_id}/email [get]
func GetUserEmail(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.Param("user_id")
		clientID := c.MustGet("clientID").(uint)

		var userEmail models.UserEmail
		if err := db.Where("client_id = ? AND user_id = ?", clientID, userID).First(&userEmail).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "User email not found"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"user_id": userEmail.UserID,
			"email":   userEmail.Email,
		})
	}
}

// DeleteUserEmail removes the notification email for a user in the authenticated client scope.
// @Summary Delete user notification email
// @Description Deletes a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Produce json
// @Param user_id path string true "User ID"
// @Success 200 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /users/{user_id}/email [delete]
func DeleteUserEmail(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.Param("user_id")
		clientID := c.MustGet("clientID").(uint)

		result := db.Where("client_id = ? AND user_id = ?", clientID, userID).Delete(&models.UserEmail{})
		if result.Error != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete user email"})
			return
		}

		if result.RowsAffected == 0 {
			c.JSON(http.StatusNotFound, gin.H{"error": "User email not found"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"message": "User email deleted successfully",
			"user_id": userID,
		})
	}
}
