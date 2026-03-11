package server

import (
	"egs-notifications/internal/sse"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

// SetupRoutes wires up the HTTP endpoints using Gin.
// No inline logic here; handlers are cleanly separated.
func SetupRoutes(broker *sse.Broker) *gin.Engine {
	router := gin.Default()

	// Connect to SSE stream. Expects a parameter: /events/user123
	router.GET("/events/:userID", broker.HandleSSE)

	// Trigger a notification. Expects JSON payload.
	router.POST("/notify", broker.HandleNotify)

	// Mount the Swagger UI on /swagger/index.html
	router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	return router
}
