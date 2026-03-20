import os

KEYCLOAK_URL = os.getenv("PAYMENT_KEYCLOAK_URL", "http://localhost:8080")
REALM = os.getenv("PAYMENT_REALM", "payments-realm")
CLIENT_ID = os.getenv("PAYMENT_CLIENT_ID", "payments-client")
CLIENT_SECRET = os.getenv("PAYMENT_CLIENT_SECRET", "")

NOTIFICATIONS_BASE_URL = os.getenv("NOTIFICATIONS_BASE_URL", "http://localhost:8082")
NOTIFICATIONS_API_KEY = os.getenv("NOTIFICATIONS_API_KEY", "")
