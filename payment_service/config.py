import os

KEYCLOAK_URL = os.getenv("PAYMENT_KEYCLOAK_URL", "http://localhost:8083")
# URL público do Keycloak (acessível pelo browser) — pode diferir do interno em Docker
KEYCLOAK_PUBLIC_URL = os.getenv("PAYMENT_KEYCLOAK_PUBLIC_URL", KEYCLOAK_URL)
PAYMENT_PUBLIC_URL = os.getenv("PAYMENT_PUBLIC_URL", "http://localhost:5174")
REALM = os.getenv("PAYMENT_REALM", "payments-realm")
CLIENT_ID = os.getenv("PAYMENT_CLIENT_ID", "payments-client")
CLIENT_SECRET = os.getenv("PAYMENT_CLIENT_SECRET", "")

NOTIFICATIONS_BASE_URL = os.getenv("NOTIFICATIONS_BASE_URL", "http://localhost:8082")
NOTIFICATIONS_API_KEY = os.getenv("NOTIFICATIONS_API_KEY", "")

TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID", "")
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "")
TWILIO_FROM_NUMBER = os.getenv("TWILIO_FROM_NUMBER", "whatsapp:+14155238886")

TRANSACTIONS_SERVICE_URL = os.getenv("TRANSACTIONS_SERVICE_URL", "http://localhost:8081")
