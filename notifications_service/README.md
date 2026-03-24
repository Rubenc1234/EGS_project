# Real-Time Multi-Tenant Web Push Notifications Service

A production-ready, white-labeled Web Push notification microservice built in Go. This service allows SaaS platforms to broadcast desktop/mobile push notifications directly to end-users' browsers via Service Workers, even when the website is closed.

## Key Features

* **True White-labeling:** Notifications appear natively from the client's (Composer's) domain.

* **Multi-Tenant VAPID:** Each client receives unique cryptographic keys generated on-demand, ensuring strict tenant isolation.

* **High-Performance Caching:** Integrated with Redis to minimize PostgreSQL queries during mass subscriber broadcasts.

* **Configurable Security:** Short-lived JWTs for frontend operations with environment-based expiration (default 15 minutes).

* **Brave & Firefox Optimized:** Built-in detection for Brave's privacy shields and support for Mozilla's Autopush relay.

## Project Structure

```
notifications_service/
├── cmd/api/main.go               # Entry point & Swagger definitions
├── internal/
│   ├── auth/
│   │   ├── apikey.go             # API Key hashing (SHA-256)
│   │   └── jwt.go                # Subscriber JWT generation (Configurable exp)
│   ├── cache/
│   │   └── redis.go              # Redis connection & health check
│   ├── db/
│   │   └── postgres.go           # GORM init & Auto-migrations
│   ├── handlers/
│   │   ├── admin.go              # Tenant management (Master Key protected)
│   │   ├── auth.go               # Token vending (Client Key protected)
│   │   ├── client.go             # Client profile/info (/v1/client)
│   │   └── notification.go       # Push Subscriptions & Broadcasting
│   ├── middleware/
│   │   └── auth.go               # RBAC (Admin, Composer, Subscriber)
│   ├── models/                   # Database Entities
│   │   ├── client.go
│   │   ├── notification.go
│   │   └── subscription.go
│   └── server/
│       └── routes.go             # Gin Router & CORS configuration
├── webagent/
│   └── worker.js                 # The Service Worker (Browser-side)
├── docs/                         # Auto-generated Swagger UI
├── docker-compose.yml            # API, Postgres, Redis orchestration
└── .env                          # Environment configuration
```

## Environment Variables

| Variable | Description | Default |
| ----- | ----- | ----- |
| `PORT` | API Port | `8080` |
| `DATABASE_URL` | PostgreSQL Connection DSN | Required |
| `REDIS_URL` | Redis Address | `localhost:6379` |
| `MASTER_ADMIN_SECRET` | Secret for platform /admin routes | Required |
| `JWT_SECRET` | Secret for signing Subscriber JWTs | Required |
| `JWT_EXPIRATION_MINUTES` | Validity duration of Frontend JWT | `15` |

## Integration Flow

### 1. Provisioning (Platform Admin)

* Call `POST /v1/admin/clients` with `Authorization: Bearer <MASTER_ADMIN_SECRET>`.

* Store the returned `api_key` and `client_id` securely.

### 2. Handshake (Composer Backend)

* When a user logs in to your site, call `POST /v1/auth/token` using your `api_key`.

* The service returns a short-lived `token` (JWT) and your `vapid_public_key`.

* Pass these values to your web frontend.

### 3. Subscription (Browser Frontend)

* **Register Agent:** The frontend registers the `worker.js`.

* **Prompt User:** Use the `vapid_public_key` to prompt the user for notification permissions via the browser's `pushManager`.

* **Submit Subscription:** Send the resulting Browser Subscription Object to `POST /v1/events/subscribe` using the short-lived `token`.

### 4. Broadcasting (Composer Backend)

* To notify users, call `POST /v1/events` with your `api_key`, a `message`, and an array of `user_ids`.

* The service retrieves all active browser endpoints, encrypts the payload, and delivers it to Google (FCM), Apple (APNs), or Mozilla.

## Important Browser Considerations

### Service Worker Proxying

Browsers enforce a **Same-Origin policy** for Service Workers. To ensure white-labeling and functionality:

* You **must** proxy your website's `/worker.js` path to point to the API's `/v1/events/agent.js` endpoint.

* Alternatively, host the `worker.js` file yourself at the root of your domain.

### Brave Browser

Brave disables Google Push Services by default, which blocks notifications for most users. For notifications to work:

1. The user must go to `brave://settings/privacy`.

2. Enable **"Use Google services for push messaging"**.

3. **Relaunch** the browser completely.

4. Disable **Brave Shields** for the specific domain if cross-origin errors persist.

## Development & Testing

**Start the Infrastructure:**

```
docker-compose up --build -d
```

**Interactive Documentation:**
Navigate to `http://localhost:8080/docs` to test endpoints directly via Swagger.

**Automated Testing:**
Use the provided `.http` files in the `tests/` directory within an IDE HTTP client for a sequential walkthrough of the API lifecycle.
