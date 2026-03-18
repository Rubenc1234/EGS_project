package sse

import (
	"fmt"
	"log"

	"egs-notifications/internal/models"
)

type SubscriberEvent struct {
	clientID uint
	userID   string
	c        chan models.Notification
}

type MessageEvent struct {
	clientID     uint
	userIDs      []string
	notification models.Notification
}

// Broker manages active SSE clients.
type Broker struct {
	// Key is "{clientID}:{userID}" to guarantee tenant isolation
	clients            map[string]map[chan models.Notification]bool
	newSubscribers     chan SubscriberEvent
	defunctSubscribers chan SubscriberEvent
	messages           chan MessageEvent
}

func NewBroker() *Broker {
	return &Broker{
		clients:            make(map[string]map[chan models.Notification]bool),
		newSubscribers:     make(chan SubscriberEvent),
		defunctSubscribers: make(chan SubscriberEvent),
		messages:           make(chan MessageEvent),
	}
}

func getRoutingKey(clientID uint, userID string) string {
	return fmt.Sprintf("%d:%s", clientID, userID)
}

func (b *Broker) Start() {
	for {
		select {
		case evt := <-b.newSubscribers:
			key := getRoutingKey(evt.clientID, evt.userID)
			if b.clients[key] == nil {
				b.clients[key] = make(map[chan models.Notification]bool)
			}
			b.clients[key][evt.c] = true
			log.Printf("Client added for Route %s. Active connections: %d", key, len(b.clients[key]))

		case evt := <-b.defunctSubscribers:
			key := getRoutingKey(evt.clientID, evt.userID)
			if connections, ok := b.clients[key]; ok {
				delete(connections, evt.c)
				close(evt.c)
				log.Printf("Client removed for Route %s. Remaining connections: %d", key, len(connections))

				if len(connections) == 0 {
					delete(b.clients, key)
				}
			}

		case evt := <-b.messages:
			for _, uid := range evt.userIDs {
				key := getRoutingKey(evt.clientID, uid)
				if connections, ok := b.clients[key]; ok {
					for c := range connections {
						select {
						case c <- evt.notification:
						default:
							log.Printf("Skipping connection for Route %s, buffer full", key)
						}
					}
				}
			}
		}
	}
}

func (b *Broker) AddSubscriber(clientID uint, userID string, c chan models.Notification) {
	b.newSubscribers <- SubscriberEvent{clientID: clientID, userID: userID, c: c}
}

func (b *Broker) RemoveSubscriber(clientID uint, userID string, c chan models.Notification) {
	b.defunctSubscribers <- SubscriberEvent{clientID: clientID, userID: userID, c: c}
}

func (b *Broker) Notify(clientID uint, userIDs []string, notification models.Notification) {
	b.messages <- MessageEvent{clientID: clientID, userIDs: userIDs, notification: notification}
}
