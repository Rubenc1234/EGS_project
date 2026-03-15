package main

import (
	"log"
	"net/http"
	"os"
	"time"

	"egs-notifications/internal/server"
	"egs-notifications/internal/sse"

	"github.com/joho/godotenv"

	// Import the generated docs folder (will error until you run 'swag init')
	_ "egs-notifications/docs"
)

// @title Notifications API
// @version 1.0
// @description SSE Notifications Service built with Gin
// @host localhost:5003
// @BasePath /
func main() {
	// Load .env file if it exists
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, relying on system environment variables")
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "5003" // default fallback
	}

	// Initialize the SSE broker
	broker := sse.NewBroker()

	// Start the broker's event loop in a separate goroutine
	go broker.Start()

	// Set up our Gin router
	router := server.SetupRoutes(broker)

	// Define the HTTP server with sensible timeouts
	srv := &http.Server{
		Addr:    ":" + port,
		Handler: router, // Pass the Gin Engine directly as the Handler
		// ReadTimeout covers the time to read the request headers/body.
		ReadTimeout:  10 * time.Second,
		// WriteTimeout is explicitly set to 0 (infinite) because SSE requires keeping the connection open.
		WriteTimeout: 0,
	}

	log.Printf("Notification service running on http://localhost:%s\n", port)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("Server failed to start: %v", err)
	}
}
