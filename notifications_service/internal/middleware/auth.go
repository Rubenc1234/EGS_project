package middleware

import (
	"net/http"
	"os"
	"strings"

	"egs-notifications/internal/auth"
	"egs-notifications/internal/models"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func extractToken(c *gin.Context) string {
	authHeader := c.GetHeader("Authorization")
	if authHeader != "" {
		parts := strings.Split(authHeader, " ")
		if len(parts) == 2 && parts[0] == "Bearer" {
			return parts[1]
		}
	}
	return c.Query("token")
}

// RequireMasterAdmin protects routes that generate API keys for clients.
func RequireMasterAdmin() gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenString := extractToken(c)
		masterSecret := os.Getenv("MASTER_ADMIN_SECRET")

		if tokenString == "" || tokenString != masterSecret {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Unauthorized: Invalid Master Admin Key"})
			return
		}
		c.Next()
	}
}

// RequireComposer validates the API key against the database.
func RequireComposer(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		apiKey := extractToken(c)
		if apiKey == "" || !strings.HasPrefix(apiKey, "sk_live_") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Missing or invalid API Key format"})
			return
		}

		hash := auth.HashAPIKey(apiKey)
		var client models.Client

		if err := db.Where("api_key_hash = ?", hash).First(&client).Error; err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid API Key"})
			return
		}

		// Inject the Client ID into the context so Handlers know WHICH tenant this is
		c.Set("clientID", client.ID)
		c.Next()
	}
}

// RequireSubscriber validates the time-limited JWT token
func RequireSubscriber() gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenString := extractToken(c)
		if tokenString == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Missing token"})
			return
		}

		secret := os.Getenv("JWT_SECRET")
		claims, err := auth.ValidateAndExtract(tokenString, secret)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid or expired token"})
			return
		}

		role, _ := claims["role"].(string)
		if role != "subscriber" {
			c.AbortWithStatusJSON(http.StatusForbidden, gin.H{"error": "Subscriber access required"})
			return
		}

		// float64 is used because JSON numbers parse as float64 in generic maps
		tokenClientID := uint(claims["clientID"].(float64))
		tokenUserID, _ := claims["userID"].(string)
		if tokenUserID == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Invalid token: missing subscriber user ID"})
			return
		}

		c.Set("authClientID", tokenClientID)
		c.Set("authUserID", tokenUserID)
		c.Next()
	}
}
