package handlers

import (
	"encoding/json"
	"io"

	"egs-notifications/internal/models"
	"egs-notifications/internal/sse"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// HandleSSE establishes the SSE connection and flushes unread notifications upon connection.
// @Summary Connect to SSE stream
// @Description Establishes a Server-Sent Events connection and sends unread notifications.
// @Tags events
// @Security BearerAuth
// @Param token query string false "JWT Token (Alternative to Bearer header for web browsers)"
// @Produce text/event-stream
// @Success 200 {string} string "SSE Stream connected"
// @Failure 401 {object} map[string]interface{}
// @Router /events [get]
func HandleSSE(db *gorm.DB, broker *sse.Broker) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.MustGet("authUserID").(string)   // Injected by Subscriber JWT
		clientID := c.MustGet("authClientID").(uint) // Injected by Subscriber JWT

		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")

		var unread []models.Notification
		db.Where("client_id = ? AND user_id = ? AND is_read = ?", clientID, userID, false).
			Order("created_at asc").Find(&unread)

		messageChan := make(chan models.Notification, 10)
		broker.AddSubscriber(clientID, userID, messageChan)

		defer func() {
			broker.RemoveSubscriber(clientID, userID, messageChan)
		}()

		for _, notif := range unread {
			select {
			case messageChan <- notif:
			default:
			}
		}

		c.Stream(func(w io.Writer) bool {
			select {
			case <-c.Request.Context().Done():
				return false
			case notif := <-messageChan:
				data, _ := json.Marshal(notif)
				c.SSEvent("message", string(data))
				return true
			}
		})
	}
}
