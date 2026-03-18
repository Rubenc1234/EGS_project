package handlers

import (
	"net/http"

	"egs-notifications/internal/models"
	"egs-notifications/internal/sse"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

type NotificationPayload struct {
	UserIDs []string `json:"user_ids" binding:"required"`
	Message string   `json:"message" binding:"required"`
}

type NotifyResponse struct {
	Status        string                `json:"status"`
	Targets       []string              `json:"targets"`
	Notifications []models.Notification `json:"notifications"`
}

// HandleNotify processes POST requests to trigger notifications.
// @Summary Send a notification
// @Description Saves and broadcasts a notification to specific users of the authenticated client.
// @Tags events
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body NotificationPayload true "Notification payload"
// @Success 200 {object} NotifyResponse
// @Failure 400 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /events [post]
func HandleNotify(db *gorm.DB, broker *sse.Broker) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.MustGet("clientID").(uint) // Injected securely by RequireComposer

		var payload NotificationPayload
		if err := c.ShouldBindJSON(&payload); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		if len(payload.UserIDs) == 0 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "at least one user_id must be provided"})
			return
		}

		createdNotifications := make([]models.Notification, 0, len(payload.UserIDs))

		for _, uid := range payload.UserIDs {
			notif := models.Notification{
				ClientID: clientID,
				UserID:   uid,
				Message:  payload.Message,
				IsRead:   false,
			}

			if err := db.Create(&notif).Error; err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save notification"})
				return
			}

			createdNotifications = append(createdNotifications, notif)
			broker.Notify(clientID, []string{uid}, notif)
		}

		c.JSON(http.StatusOK, NotifyResponse{
			Status:        "notifications queued and saved",
			Targets:       payload.UserIDs,
			Notifications: createdNotifications,
		})
	}
}

// MarkAsRead allows clients to acknowledge they received a notification.
// @Summary Mark notification as read
// @Description Updates the notification status to read. Strict IDOR protection ensures users only update their own notifications.
// @Tags events
// @Security BearerAuth
// @Param id path int true "Notification ID"
// @Produce json
// @Success 200 {object} map[string]string
// @Failure 401 {object} map[string]interface{}
// @Failure 403 {object} map[string]interface{}
// @Failure 404 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /events/{id} [patch]
func MarkAsRead(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		id := c.Param("id")
		authClientID := c.MustGet("authClientID").(uint) // from Subscriber JWT
		authUserID := c.MustGet("authUserID").(string)   // from Subscriber JWT

		result := db.Model(&models.Notification{}).
			Where("id = ? AND client_id = ? AND user_id = ?", id, authClientID, authUserID).
			Update("is_read", true)

		if result.Error != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update notification"})
			return
		}

		if result.RowsAffected == 0 {
			c.JSON(http.StatusNotFound, gin.H{"error": "notification not found or does not belong to you"})
			return
		}

		c.JSON(http.StatusOK, gin.H{"status": "marked as read"})
	}
}

// MarkAllAsRead acknowledges all unread notifications for the authenticated subscriber.
// @Summary Mark all notifications as read
// @Description Updates all unread notifications to read for the authenticated user within their client scope.
// @Tags events
// @Security BearerAuth
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Failure 401 {object} map[string]interface{}
// @Failure 403 {object} map[string]interface{}
// @Failure 500 {object} map[string]interface{}
// @Router /events [patch]
func MarkAllAsRead(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		authClientID := c.MustGet("authClientID").(uint) // from Subscriber JWT
		authUserID := c.MustGet("authUserID").(string)   // from Subscriber JWT

		result := db.Model(&models.Notification{}).
			Where("client_id = ? AND user_id = ? AND is_read = ?", authClientID, authUserID, false).
			Update("is_read", true)

		if result.Error != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update notifications"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"status":        "all notifications marked as read",
			"updated_count": result.RowsAffected,
		})
	}
}
