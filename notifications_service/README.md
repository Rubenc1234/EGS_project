# Resilient Multi-Tenant SaaS Notifications

A production-ready, resilient, Server-Sent Events (SSE) notification microservice built in Go.
This service functions as a standalone SaaS platform (similar to Pusher/Ably). It supports multiple independent client applications (tenants), issuing permanent API Keys for backends and scoped JWTs for frontend browsers.

## Architectural Capabilities

- Multi-Tenancy: Complete database and stream isolation. A client app cannot accidentally push or read notifications from another client app's users.
- Hashed API Keys: Client API keys (sk_live_...) are hashed using SHA-256 before being stored in PostgreSQL. If the database leaks, the raw keys remain secure. Key rotation/regeneration is supported.
- Resiliency: Every message is saved to PostgreSQL before transmission. Disconnected users instantly receive missed messages upon reconnecting.
- SaaS Token Vending:
  - API Keys (Composers): Long-lived, database-verified keys used by your clients' backend servers to push data.
  - JWT (Subscribers): 4-hour, scoped tokens vended for end-user Web Browsers to listen strictly to their own stream.

## Project Structure

```
notifications_service/
├── cmd/api/main.go               # Application entry point (Swagger base definitions)
├── internal/
│   ├── auth/
│   │   ├── apikey.go             # API Key generation & SHA-256 hashing
│   │   └── jwt.go                # Subscriber JWT creation & validation
│   ├── db/postgres.go            # GORM initialization and migration
│   ├── handlers/                 # HTTP Handlers
│   │   ├── admin.go              # Platform admin (CRUD & Key Management)
│   │   ├── auth.go               # Token vending machine
│   │   ├── notification.go       # Send and ACK notification handlers
│   │   └── sse.go                # SSE connection handler
│   ├── middleware/auth.go        # Admin, Composer, & Subscriber RBAC
│   ├── models/                   # GORM Entities
│   │   ├── client.go             # Tenant App
│   │   └── notification.go       # Stored event
│   ├── server/routes.go          # Gin routing
│   └── sse/broker.go             # Tenant-isolated SSE channel manager
├── docs/                         # Swagger generated documentation
├── .env                          # Environment configurations
└── go.mod / go.sum               # Dependencies
```

## Endpoints Overview

*Full interactive documentation is available via Swagger at ``/docs/index.html`` after running the application.*

### 1. Platform Admin (``MasterAuth``)

Requires ``Authorization: Bearer <MASTER_ADMIN_SECRET>``. Used by the platform owner to manage tenants.

- ``POST /v1/admin/clients``: Register a new client and generate their initial API Key.
- ``GET /v1/admin/clients``: List all registered clients.
- ``POST /v1/admin/clients/{id}/regenerate-key``: Invalidate the old API key and instantly issue a new one.
- ``DELETE /v1/admin/clients/{id}``: Revoke a client and permanently delete their notification data.

### 2. Client Apps/Composers (``BearerAuth``)

Requires ``Authorization: Bearer <CLIENT_API_KEY>``. Used by your customers' backend servers.

- ``POST /v1/auth/token``: Generates a 4-hour listener token for the client's frontend.
- ``POST /v1/events``: Saves and broadcasts a notification to specific users.

### 3. End Users/Subscribers (``BearerAuth``)

Requires Authorization: ``Bearer <4_HOUR_JWT>`` or ``?token=<4_HOUR_JWT>``. Used strictly by end-users.

- ``GET /v1/events``: Opens an SSE connection for the authenticated subscriber. Automatically pushes unread notifications.
- ``PATCH /v1/events/:id``: ACKs a notification. Marks it as read. Strict IDOR protection ensures users only affect their own data.
- ``PATCH /v1/events``: Marks all unread notifications as read for the authenticated user.

## Setup & Initialization

### 1. Environment Setup (``.env``)

```env
PORT=8080
DATABASE_URL=host=localhost user=postgres password=postgres dbname=notifications port=5432 sslmode=disable TimeZone=UTC
# Used to create/manage clients
MASTER_ADMIN_SECRET=super_secret_master_key
# Used to sign the short-lived subscriber tokens
JWT_SECRET=another_super_secret_jwt_key
```

### 2. Database

Ensure your PostgreSQL instance is clean. If updating from a non-tenant version, Drop your old tables before running this to allow GORM to build the foreign key constraints (``client_id``).

### 3. Build Swagger Docs & Run

```bash
go mod tidy
swag init -g cmd/api/main.go
go run cmd/api/main.go
```

## Test Flow (The "Buy-In" Process)

### Step 1: You (Admin) create a Client App

```bash
curl -X POST http://localhost:8080/v1/admin/clients \
  -H "Authorization: Bearer super_secret_master_key" \
  -H "Content-Type: application/json" \
  -d '{"name": "My Cool Startup App"}'
```

*Save the ``api_key`` it returns (e.g., ``sk_live_1234abcd...``). You hand this to your customer.*

### Step 2: Customer Backend asks for a Frontend Token

```bash
curl -X POST http://localhost:8080/v1/auth/token \
  -H "Authorization: Bearer <sk_live_...>" \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user123"}'
```

### Step 3: End User Connects

```bash
curl -N "http://localhost:8080/v1/events?token=<4_HOUR_JWT>"
```

### Step 4: Customer Backend Pushes Notification

```bash
curl -X POST http://localhost:8080/v1/events \
  -H "Authorization: Bearer <sk_live_...>" \
  -H "Content-Type: application/json" \
  -d '{"user_ids": ["user123"], "message": "Welcome to My Cool Startup!"}'
```

### Step 5: Mark Notification as Read

```bash
curl -X PATCH http://localhost:8080/v1/events/<notification_id> \
  -H "Authorization Bearer <4_HOUR_JWT>" \
  -H "Content-Type: application/json"
```

### Step 6: Mark All Notifications as Read

```bash
curl -X PATCH http://localhost:8080/v1/events \
  -H "Authorization Bearer <4_HOUR_JWT>" \
  -H "Content-Type: application/json"
```

### Extra: You can also test the admin's ability to regenerate API keys or delete clients, ensuring that old keys are invalidated and data is properly isolated.

1. Regenerate Key:

```bash
curl -X POST http://localhost:8080/v1/admin/clients/<client_id>/regenerate-key \
  -H "Authorization Bearer super_secret_master_key" \
  -H "Content-Type: application/json"
```

2. Delete Client:

```bash
curl -X DELETE http://localhost:8080/v1/admin/clients/<client_id> \
  -H "Authorization Bearer super_secret_master_key" \
  -H "Content-Type: application/json"
```
