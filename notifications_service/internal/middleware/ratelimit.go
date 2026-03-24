package middleware

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
)

// RateLimitEvents uses a fast, fixed-window counter in Redis to throttle requests per client.
func RateLimitEvents(rdb *redis.Client, maxReqPerSec int64) gin.HandlerFunc {
	return func(c *gin.Context) {
		clientID, exists := c.Get("clientID")
		if !exists {
			// Fail open if RequireComposer didn't inject the ID properly
			c.Next()
			return
		}

		// Fixed window: Use the current epoch second as the bucket
		currentSec := time.Now().Unix()
		key := fmt.Sprintf("ratelimit:client:%d:sec:%d", clientID.(uint), currentSec)

		pipe := rdb.Pipeline()
		incr := pipe.Incr(context.Background(), key)
		pipe.Expire(context.Background(), key, 2*time.Second)

		if _, err := pipe.Exec(context.Background()); err != nil {
			slog.Warn("Rate limiter Redis pipeline failed, bypassing", "error", err)
			c.Next() // Fail open to avoid blocking legitimate traffic during Redis blips
			return
		}

		if incr.Val() > maxReqPerSec {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"error": fmt.Sprintf("Rate limit exceeded. Maximum %d requests per second.", maxReqPerSec),
			})
			return
		}

		c.Next()
	}
}
