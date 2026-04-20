package server

import (
	"net/http"

	"egs-notifications/internal/handlers"
	"egs-notifications/internal/middleware"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	"gorm.io/gorm"
)

func SetupRoutes(db *gorm.DB, rdb *redis.Client) *gin.Engine {
	router := gin.Default()

	router.Use(func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, accept, origin, Cache-Control, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT, PATCH, DELETE")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	})

	// Swagger Docs
	router.GET("/docs/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
	router.GET("/docs", func(c *gin.Context) {
		c.Redirect(http.StatusMovedPermanently, "/docs/index.html")
	})

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
		composerOnly.GET("/client/info", handlers.GetClientInfo(db))
		composerOnly.POST("/auth/token", handlers.GenerateClientToken(db))
		composerOnly.POST("/events", middleware.RateLimitEvents(rdb, 10), handlers.Notify(db, rdb))
		composerOnly.PUT("/users/:user_id/email", handlers.SetUserEmail(db))
		composerOnly.GET("/users/:user_id/email", handlers.GetUserEmail(db))
		composerOnly.DELETE("/users/:user_id/email", handlers.DeleteUserEmail(db))
	}

	// Unprotected static route to fetch Web Agent
	v1.GET("/events/agent.js", handlers.ServeWebAgent())

	// Subscriber Routes (End-user Web Browsers)
	subscriberOnly := v1.Group("/events")
	subscriberOnly.Use(middleware.RequireSubscriber())
	{
		subscriberOnly.POST("/subscribe", handlers.Subscribe(db, rdb))
		subscriberOnly.DELETE("/subscribe", handlers.Unsubscribe(db, rdb))
	}

	return router
}
