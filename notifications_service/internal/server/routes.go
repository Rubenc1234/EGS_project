package server

import (
	"net/http"

	"egs-notifications/internal/handlers"
	"egs-notifications/internal/middleware"
	"egs-notifications/internal/sse"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	"gorm.io/gorm"
)

func SetupRoutes(db *gorm.DB, broker *sse.Broker) *gin.Engine {
	router := gin.Default()

	// 1. The underlying swagger UI files (we hide this path from the clients)
	router.GET("/docs/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	// 2. The clean URL you give to your clients. Instantly redirects them to the UI.
	router.GET("/docs", func(c *gin.Context) {
		c.Redirect(http.StatusMovedPermanently, "/docs/index.html")
	})

	// 3. API Versioning Group
	v1 := router.Group("/v1")

	// Admin Route (To manage Client Apps & API Keys)
	adminOnly := v1.Group("/admin")
	adminOnly.Use(middleware.RequireMasterAdmin())
	{
		adminOnly.POST("/clients", handlers.CreateClient(db))
		adminOnly.GET("/clients", handlers.ListClients(db))
		adminOnly.PATCH("/clients/:id/key", handlers.RegenerateClientKey(db))
		adminOnly.DELETE("/clients/:id", handlers.DeleteClient(db))
	}

	// Composer Routes (SaaS Customers pushing notifications)
	composerOnly := v1.Group("/")
	composerOnly.Use(middleware.RequireComposer(db))
	{
		composerOnly.POST("/auth/token", handlers.GenerateClientToken())
		composerOnly.POST("/events", handlers.HandleNotify(db, broker))
		composerOnly.PUT("/users/:user_id/email", handlers.SetUserEmail())
		composerOnly.GET("/users/:user_id/email", handlers.GetUserEmail())
		composerOnly.DELETE("/users/:user_id/email", handlers.DeleteUserEmail())
	}

	// Subscriber Routes (End-user Web Browsers)
	subscriberOnly := v1.Group("/events")
	subscriberOnly.Use(middleware.RequireSubscriber())
	{
		subscriberOnly.GET("", handlers.HandleSSE(db, broker))
		subscriberOnly.PATCH("/:id", handlers.MarkAsRead(db))
	}

	return router
}
