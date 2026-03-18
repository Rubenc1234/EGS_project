package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type SetUserEmailRequest struct {
	Email string `json:"email" binding:"required,email"`
}

// SetUserEmail sets or updates the notification email for a user in the authenticated client scope.
// @Summary Set user notification email
// @Description Placeholder endpoint to create/update a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param user_id path string true "User ID"
// @Param payload body SetUserEmailRequest true "Email payload"
// @Success 501 {object} map[string]interface{}
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Router /users/{user_id}/email [put]
func SetUserEmail() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.Param("user_id")

		var req SetUserEmailRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		c.JSON(http.StatusNotImplemented, gin.H{
			"error":   "not implemented",
			"message": "user email storage is not implemented yet",
			"user_id": userID,
			"email":   req.Email,
		})
	}
}

// GetUserEmail returns the notification email for a user in the authenticated client scope.
// @Summary Get user notification email
// @Description Placeholder endpoint to retrieve a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Produce json
// @Param user_id path string true "User ID"
// @Success 501 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Router /users/{user_id}/email [get]
func GetUserEmail() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusNotImplemented, gin.H{
			"error":   "not implemented",
			"message": "user email retrieval is not implemented yet",
			"user_id": c.Param("user_id"),
		})
	}
}

// DeleteUserEmail removes the notification email for a user in the authenticated client scope.
// @Summary Delete user notification email
// @Description Placeholder endpoint to delete a user's notification email for the authenticated client.
// @Tags users
// @Security BearerAuth
// @Produce json
// @Param user_id path string true "User ID"
// @Success 501 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Router /users/{user_id}/email [delete]
func DeleteUserEmail() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusNotImplemented, gin.H{
			"error":   "not implemented",
			"message": "user email deletion is not implemented yet",
			"user_id": c.Param("user_id"),
		})
	}
}
