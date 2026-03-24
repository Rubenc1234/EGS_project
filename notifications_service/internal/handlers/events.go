package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"sync/atomic"
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

type UnsubscribeRequest struct {
	Endpoint string `json:"endpoint" binding:"required"`
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
			slog.Error("Failed to save subscription", "error", err, "client_id", clientID, "user_id", userID)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save subscription"})
			return
		}

		cacheKey := fmt.Sprintf("user:%d:%s:subs", clientID, userID)
		rdb.Del(context.Background(), cacheKey)

		c.JSON(http.StatusOK, gin.H{"status": "Subscribed successfully"})
	}
}

// Unsubscribe removes a user's browser push subscription.
// @Summary Unsubscribe from Web Push
// @Description Deletes a Web Push Subscription object, usually called on user logout.
// @Tags events
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body UnsubscribeRequest true "Unsubscribe payload"
// @Success 200 {object} map[string]interface{}
// @Router /events/subscribe [delete]
func Unsubscribe(db *gorm.DB, rdb *redis.Client) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.MustGet("authClientID").(uint)
		userID := c.MustGet("authUserID").(string)

		var req UnsubscribeRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		result := db.Where("client_id = ? AND user_id = ? AND endpoint = ?", clientID, userID, req.Endpoint).Delete(&models.PushSubscription{})
		if result.Error != nil {
			slog.Error("Failed to delete subscription", "error", result.Error, "client_id", clientID, "user_id", userID)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to delete subscription"})
			return
		}

		if result.RowsAffected == 0 {
			c.JSON(http.StatusNotFound, gin.H{"error": "Subscription not found"})
			return
		}

		cacheKey := fmt.Sprintf("user:%d:%s:subs", clientID, userID)
		rdb.Del(context.Background(), cacheKey)

		c.JSON(http.StatusOK, gin.H{"status": "Unsubscribed successfully"})
	}
}

// Notify pushes a web notification to users asynchronously.
// @Summary Send a Web Push notification
// @Description Broadcasts a notification via the browser Push API to specified users.
// @Tags events
// @Security BearerAuth
// @Accept json
// @Produce json
// @Param payload body NotificationPayload true "Notification payload"
// @Success 202 {object} map[string]interface{}
// @Router /events [post]
func Notify(db *gorm.DB, rdb *redis.Client) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID := c.MustGet("clientID").(uint)

		var payload NotificationPayload
		if err := c.ShouldBindJSON(&payload); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		// Quickly fetch Client to get VAPID keys before releasing the HTTP connection
		var client models.Client
		if err := db.First(&client, clientID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "Client not found"})
			return
		}

		workerPayload := payload
		workerPayload.UserIDs = nil
		wpPayloadBytes, _ := json.Marshal(workerPayload)

		// Google FCM and Apple APNs strictly drop encrypted payloads > 4096 bytes.
		// Reject it early instead of letting the background worker fail silently for 50k users.
		if len(wpPayloadBytes) > 4000 {
			c.JSON(http.StatusRequestEntityTooLarge, gin.H{
				"error":       "Payload too large",
				"message":     "The push payload exceeds the strict 4000 byte limit imposed by Web Push services. Remove heavy base64 images or reduce text.",
				"actual_size": len(wpPayloadBytes),
			})
			return
		}

		// Immediately return 202 Accepted. The caller knows we've received the job.
		c.JSON(http.StatusAccepted, gin.H{
			"status": "processing",
			"target_count": len(payload.UserIDs),
		})

		// Spin up the background worker
		go processBroadcastAsync(db, rdb, client, payload)
	}
}

// processBroadcastAsync handles the N+1 optimization and actual HTTP delivery.
func processBroadcastAsync(db *gorm.DB, rdb *redis.Client, client models.Client, payload NotificationPayload) {
	ctx := context.Background() // Independent context so it doesn't die when the HTTP request finishes

	cacheKeys := make([]string, len(payload.UserIDs))
	for i, uid := range payload.UserIDs {
		cacheKeys[i] = fmt.Sprintf("user:%d:%s:subs", client.ID, uid)
	}

	var subsToSend []models.PushSubscription
	var missedUIDs []string

	// 1. Bulk MGET from Redis
	// MGet returns values in the same order as the keys provided.
	vals, err := rdb.MGet(ctx, cacheKeys...).Result()
	if err != nil {
		// If Redis utterly fails, we fallback to hitting the DB for everyone
		slog.Warn("Redis MGET failed, falling back to full DB query", "error", err)
		missedUIDs = payload.UserIDs
	} else {
		for i, val := range vals {
			uid := payload.UserIDs[i]
			if val == nil {
				missedUIDs = append(missedUIDs, uid)
				continue
			}

			strVal, ok := val.(string)
			if !ok {
				missedUIDs = append(missedUIDs, uid)
				continue
			}

			var cachedSubs []models.PushSubscription
			if err := json.Unmarshal([]byte(strVal), &cachedSubs); err == nil {
				subsToSend = append(subsToSend, cachedSubs...)
			} else {
				missedUIDs = append(missedUIDs, uid)
			}
		}
	}

	// 2. Fetch all cache misses from Postgres in ONE query
	if len(missedUIDs) > 0 {
		var dbSubs []models.PushSubscription

		// FIX: Chunk the query into batches of 1,000 to prevent Postgres from
		// rejecting queries with more than 65,535 parameters.
		batchSize := 1000
		for i := 0; i < len(missedUIDs); i += batchSize {
			end := i + batchSize
			if end > len(missedUIDs) {
				end = len(missedUIDs)
			}

			batch := missedUIDs[i:end]
			var batchSubs []models.PushSubscription
			if err := db.Where("client_id = ? AND user_id IN ?", client.ID, batch).Find(&batchSubs).Error; err != nil {
				slog.Error("Failed bulk DB query for missed subscriptions batch", "error", err)
			} else {
				dbSubs = append(dbSubs, batchSubs...)
			}
		}

		// Group DB results by UserID to cache them properly
		subsByUID := make(map[string][]models.PushSubscription)
		for _, sub := range dbSubs {
			subsByUID[sub.UserID] = append(subsByUID[sub.UserID], sub)
			subsToSend = append(subsToSend, sub)
		}

		// 3. Pipeline the cache updates
		pipe := rdb.Pipeline()
		for _, uid := range missedUIDs {
			// Even if a user has 0 subscriptions (empty slice), we cache it!
			// This prevents negative cache misses from hammering the DB over and over.
			userSubs := subsByUID[uid]
			if userSubs == nil {
				userSubs = []models.PushSubscription{}
			}
			cacheData, _ := json.Marshal(userSubs)
			pipe.Set(ctx, fmt.Sprintf("user:%d:%s:subs", client.ID, uid), cacheData, 1*time.Hour)
		}

		if _, err := pipe.Exec(ctx); err != nil {
			slog.Error("Failed to pipeline cache updates", "error", err)
		}
	}

	// Prepare payload (strip the user_ids slice so it doesn't inflate the webpush payload size)
	workerPayload := payload
	workerPayload.UserIDs = nil
	wpPayloadBytes, _ := json.Marshal(workerPayload)

	var pushedCount atomic.Int32

	// 4. Send pushes concurrently with a bounded worker pool
	// Firing off 50,000 raw goroutines is dangerous (socket exhaustion).
	// 50 concurrent workers is a safe balance for throughput.
	const numWorkers = 50
	jobs := make(chan models.PushSubscription, len(subsToSend))
	var wg sync.WaitGroup

	for w := 0; w < numWorkers; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for sub := range jobs {
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

				if res != nil {
					// 410 Gone or 404 Not Found means the browser unsubscribed natively or the push server dropped it.
					if res.StatusCode == http.StatusGone || res.StatusCode == http.StatusNotFound {
						slog.Info("Device unsubscribed, cleaning up endpoint", "user_id", sub.UserID, "endpoint", sub.Endpoint)
						db.Where("client_id = ? AND endpoint = ?", client.ID, sub.Endpoint).Delete(&models.PushSubscription{})
						rdb.Del(ctx, fmt.Sprintf("user:%d:%s:subs", client.ID, sub.UserID))
					} else if res.StatusCode >= 200 && res.StatusCode < 300 {
						pushedCount.Add(1)
					} else {
						slog.Warn("WebPush returned non-success status", "status", res.StatusCode, "user_id", sub.UserID)
					}
					res.Body.Close()
				} else if err != nil {
					slog.Error("WebPush delivery critical error", "error", err, "user_id", sub.UserID)
				}
			}
		}()
	}

	// Feed the jobs queue
	for _, sub := range subsToSend {
		jobs <- sub
	}
	close(jobs)

	// Wait for all workers to finish
	wg.Wait()

	slog.Info("Async broadcast completed", "client_id", client.ID, "targets_found", len(subsToSend), "devices_triggered", pushedCount.Load())
}
