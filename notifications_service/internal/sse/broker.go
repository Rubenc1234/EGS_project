package sse

import (
	"io"
	"log"
	"net/http"

	"github.com/gin-gonic/gin"
)

// ClientEvent represents a client connecting or disconnecting.
type ClientEvent struct {
	userID string
	c      chan string
}

// MessageEvent represents a message to be sent to specific users.
type MessageEvent struct {
	userIDs []string
	msg     string
}

// Broker manages active SSE clients and pushes messages to specific users.
type Broker struct {
	// clients maps userID to a map of active client channels.
	// We use a nested map because one user might have multiple tabs/devices open.
	clients        map[string]map[chan string]bool
	newClients     chan ClientEvent
	defunctClients chan ClientEvent
	messages       chan MessageEvent
}

// NewBroker initializes a new Broker instance.
func NewBroker() *Broker {
	return &Broker{
		clients:        make(map[string]map[chan string]bool),
		newClients:     make(chan ClientEvent),
		defunctClients: make(chan ClientEvent),
		messages:       make(chan MessageEvent),
	}
}

// Start runs the broker's main event loop.
func (b *Broker) Start() {
	for {
		select {
		case evt := <-b.newClients:
			// Initialize the inner map if this is the user's first connection
			if b.clients[evt.userID] == nil {
				b.clients[evt.userID] = make(map[chan string]bool)
			}
			b.clients[evt.userID][evt.c] = true
			log.Printf("Client added for user %s. Active connections for user: %d", evt.userID, len(b.clients[evt.userID]))

		case evt := <-b.defunctClients:
			if connections, ok := b.clients[evt.userID]; ok {
				delete(connections, evt.c)
				close(evt.c)
				log.Printf("Client removed for user %s. Remaining connections: %d", evt.userID, len(connections))

				// If the user has no more open connections, clean up their map entry
				// to prevent slow memory leaks over time.
				if len(connections) == 0 {
					delete(b.clients, evt.userID)
				}
			}

		case evt := <-b.messages:
			// Unicast / Multicast logic
			for _, uid := range evt.userIDs {
				if connections, ok := b.clients[uid]; ok {
					// Send to all active devices for this user
					for c := range connections {
						select {
						case c <- evt.msg:
						default:
							log.Printf("Skipping connection for user %s, buffer full", uid)
						}
					}
				}
			}
		}
	}
}

// Notify queues a message to be sent to specific users.
func (b *Broker) Notify(userIDs []string, msg string) {
	b.messages <- MessageEvent{userIDs: userIDs, msg: msg}
}

// HandleSSE handles incoming HTTP requests and establishes the SSE connection.
// @Summary Connect to SSE stream
// @Description Establishes a Server-Sent Events connection for a specific user.
// @Tags events
// @Param userID path string true "User ID"
// @Produce text/event-stream
// @Success 200 {string} string "SSE Stream connected"
// @Router /events/{userID} [get]
func (b *Broker) HandleSSE(c *gin.Context) {
	userID := c.Param("userID")
	if userID == "" {
		c.String(http.StatusBadRequest, "User ID is required")
		return
	}

	c.Writer.Header().Set("Access-Control-Allow-Origin", "*")

	messageChan := make(chan string, 10)
	clientEvt := ClientEvent{userID: userID, c: messageChan}

	b.newClients <- clientEvt

	defer func() {
		b.defunctClients <- clientEvt
	}()

	c.Stream(func(w io.Writer) bool {
		select {
		case <-c.Request.Context().Done():
			return false
		case msg := <-messageChan:
			c.SSEvent("message", msg)
			return true
		}
	})
}

// NotificationPayload defines the expected JSON structure for sending notifications.
type NotificationPayload struct {
	UserIDs []string `json:"user_ids" binding:"required"`
	Message string   `json:"message" binding:"required"`
}

// HandleNotify processes POST requests to trigger notifications.
// @Summary Send a notification
// @Description Queues a notification to be sent to specified users.
// @Tags notifications
// @Accept json
// @Produce json
// @Param payload body NotificationPayload true "Notification payload containing targets and message"
// @Success 200 {object} map[string]interface{} "status: notifications queued"
// @Failure 400 {object} map[string]interface{} "error details"
// @Router /notify [post]
func (b *Broker) HandleNotify(c *gin.Context) {
	var payload NotificationPayload

	// ShouldBindJSON handles validation based on the struct tags
	if err := c.ShouldBindJSON(&payload); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if len(payload.UserIDs) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "at least one user_id must be provided"})
		return
	}

	b.Notify(payload.UserIDs, payload.Message)
	c.JSON(http.StatusOK, gin.H{"status": "notifications queued", "targets": payload.UserIDs})
}
