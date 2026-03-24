package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"egs-notifications/internal/models"

	"github.com/SherClockHolmes/webpush-go"
	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
)

type NotificationPayload struct {
	UserIDs            []string `json:"user_ids" binding:"required,min=1"`
	Message            string   `json:"message" binding:"required"`
	Title              string   `json:"title,omitempty"`
	Icon               string   `json:"icon,omitempty"`
	URL                string   `json:"url,omitempty"`
	Image              string   `json:"image,omitempty"`
	Badge              string   `json:"badge,omitempty"`

	Dir                string   `json:"dir,omitempty" binding:"omitempty,oneof=auto ltr rtl"`
	Lang               string   `json:"lang,omitempty"`
	Tag                string   `json:"tag,omitempty"`
	Renotify           bool     `json:"renotify,omitempty"`
	RequireInteraction bool     `json:"requireInteraction,omitempty"`
	Silent             bool     `json:"silent,omitempty"`
	Vibrate            []int    `json:"vibrate,omitempty"`

	Timestamp          int64    `json:"timestamp,omitempty"`

	Actions            []Action `json:"actions,omitempty" binding:"omitempty,dive"`
}

type Action struct {
	Action    string `json:"action"`
	Title     string `json:"title"`
	Icon      string `json:"icon,omitempty"`
	ActionURL string `json:"action_url,omitempty"`
}

type PushSubscriptionRequest struct {
	Endpoint string `json:"endpoint" binding:"required"`
	Keys     struct {
		P256dh string `json:"p256dh" binding:"required"`
		Auth   string `json:"auth" binding:"required"`
	} `json:"keys" binding:"required"`
}

// ServeWebAgent returns the raw Service Worker JS file.
// @Summary Get Web Agent Script
// @Description IMPORTANT: Composers MUST proxy this file so it is served from their own domain, or browsers will block registration.
// @Tags events
// @Produce application/javascript
// @Success 200 {string} string
// @Router /events/agent.js [get]
func ServeWebAgent() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.File("webagent/worker.js")
	}
}

// Subscribe processes POST requests to save a user's browser push subscription.
// @Summary Subscribe to Web Push
// @Description Saves a Web Push Subscription object from the browser.
// @Tags events
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body PushSubscriptionRequest true "Push Subscription object"
// @Success 200 {object} map[string]interface{}
// @Router /events/subscribe [post]
func Subscribe(db *gorm.DB, rdb *redis.Client) gin.HandlerFunc {
	return func(c *gin.Context) {
		// Use the IDs extracted from the Subscriber JWT
		clientID := c.MustGet("authClientID").(uint)
		userID := c.MustGet("authUserID").(string)

		var req PushSubscriptionRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		sub := models.PushSubscription{
			ClientID: clientID,
			UserID:   userID,
			Endpoint: req.Endpoint,
			P256dh:   req.Keys.P256dh,
			Auth:     req.Keys.Auth,
		}

		// Upsert: update if endpoint exists, otherwise create
		if err := db.Where("endpoint = ?", req.Endpoint).Assign(sub).FirstOrCreate(&sub).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save subscription"})
			return
		}

		cacheKey := fmt.Sprintf("user:%d:%s:subs", clientID, userID)
		rdb.Del(context.Background(), cacheKey)

		c.JSON(http.StatusOK, gin.H{"status": "Subscribed successfully"})
	}
}


// Notify pushes a web notification to users.
// @Summary Send a Web Push notification
// @Description Broadcasts a notification via the browser Push API to specified users.
// @Tags events
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body NotificationPayload true "Notification payload"
// @Success 200 {object} map[string]interface{}
// @Router /events [post]
func Notify(db *gorm.DB, rdb *redis.Client) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.MustGet("clientID").(uint)

		var payload NotificationPayload
		if err := c.ShouldBindJSON(&payload); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		// Fetch Client for VAPID keys
		var client models.Client
		if err := db.First(&client, clientID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "Client not found"})
			return
		}

		workerPayload := payload
		workerPayload.UserIDs = nil
		wpPayloadBytes, _ := json.Marshal(workerPayload)

		pushedCount := 0
		ctx := context.Background()

		for _, uid := range payload.UserIDs {
			cacheKey := fmt.Sprintf("user:%d:%s:subs", clientID, uid)
			var subs []models.PushSubscription

			// 1. Try Redis
			val, err := rdb.Get(ctx, cacheKey).Result()
			if err == nil {
				json.Unmarshal([]byte(val), &subs)
			} else {
				// 2. Try DB
				if err := db.Where("client_id = ? AND user_id = ?", clientID, uid).Find(&subs).Error; err != nil {
					continue
				}
				// 3. Cache it (even if empty to prevent DB slamming, but only for 1 hour)
				cacheData, _ := json.Marshal(subs)
				rdb.Set(ctx, cacheKey, cacheData, time.Hour)
			}

			// 4. Send Web Push
			for _, sub := range subs {
				wpSub := &webpush.Subscription{
					Endpoint: sub.Endpoint,
					Keys: webpush.Keys{
						P256dh: sub.P256dh,
						Auth:   sub.Auth,
					},
				}

				res, err := webpush.SendNotification(wpPayloadBytes, wpSub, &webpush.Options{
					Subscriber:      "mailto:" + client.AdminEmail,
					VAPIDPublicKey:  client.VapidPublicKey,
					VAPIDPrivateKey: client.VapidPrivateKey,
					TTL:             30,
				})

				if err == nil {
					pushedCount++
					res.Body.Close()
				} else {
					fmt.Printf("WEBPUSH ERROR for %s: %v\n", uid, err)
				}
			}
		}

		c.JSON(http.StatusOK, gin.H{
			"status":            "processing completed",
			"targets":           payload.UserIDs,
			"devices_triggered": pushedCount,
		})
	}
}
