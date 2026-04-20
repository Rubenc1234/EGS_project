import uuid
import base64
import json
import logging
from functools import wraps
from flask import jsonify, request, session
from payment_service.services.keycloak_service import (
    get_login_url,
    get_signup_url,
    exchange_code_for_token,
    introspect_token,
)
from payment_service import config

log = logging.getLogger(__name__)

INTERNAL_KEY = config.NOTIFICATIONS_API_KEY
DEFAULT_CALLBACK_URL = f"{config.PAYMENT_PUBLIC_URL.rstrip('/')}/callback"


def get_user_id_from_token(token: str) -> str:
    """Decode JWT payload (no re-verification) and return the 'sub' claim."""
    payload_b64 = token.split(".")[1]
    # Add padding in case it's missing
    payload_b64 += "=" * (-len(payload_b64) % 4)
    payload = json.loads(base64.b64decode(payload_b64))
    return payload["sub"]


def _get_token_roles(token: str) -> list:
    """Decode JWT payload and return realm_access roles."""
    try:
        payload_b64 = token.split(".")[1]
        payload_b64 += "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.b64decode(payload_b64))
        return payload.get("realm_access", {}).get("roles", [])
    except Exception:
        return []


def require_token(f):
    """Decorator to protect routes using the payments Keycloak realm.
    Allows bypass if a valid X-Internal-Key is provided (for service-to-service calls).
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        # 1. Check for Internal API Key bypass
        internal_key = request.headers.get("X-Internal-Key")
        if internal_key and internal_key == INTERNAL_KEY and INTERNAL_KEY:
            log.info("Bypassing token check via internal API key")
            return f(*args, **kwargs)

        # 2. Regular OIDC/Keycloak check
        auth_header = request.headers.get("Authorization")
        if not auth_header:
            return jsonify({"error": "Authorization header missing"}), 401
        parts = auth_header.split()
        if len(parts) != 2 or parts[0] != "Bearer":
            return jsonify({"error": "Invalid Authorization header"}), 401
        token = parts[1]
        active = introspect_token(token)
        log.warning("introspect_token result: %s (token prefix: %s...)", active, token[:20])
        if not active:
            return jsonify({"error": "Invalid or expired token"}), 401
        return f(*args, **kwargs)
    return decorated


def require_operator(f):
    """Decorator that requires the 'operator' realm role (on top of a valid token)."""
    @wraps(f)
    def decorated(*args, **kwargs):
        # Allow internal bypass for operators too if needed, 
        # but usually internal calls are for specific actions.
        internal_key = request.headers.get("X-Internal-Key")
        if internal_key and internal_key == INTERNAL_KEY and INTERNAL_KEY:
            return f(*args, **kwargs)

        auth_header = request.headers.get("Authorization")
        if not auth_header:
            return jsonify({"error": "Authorization header missing"}), 401
        parts = auth_header.split()
        if len(parts) != 2 or parts[0] != "Bearer":
            return jsonify({"error": "Invalid Authorization header"}), 401
        token = parts[1]
        if not introspect_token(token):
            return jsonify({"error": "Invalid or expired token"}), 401
        if "operator" not in _get_token_roles(token):
            return jsonify({"error": "Forbidden: operator role required"}), 403
        return f(*args, **kwargs)
    return decorated


def register_routes(app):

    @app.route("/v1/pay/login", methods=["GET"])
    def get_login():
        callback_url = request.args.get("redirect_uri", DEFAULT_CALLBACK_URL)
        state = str(uuid.uuid4())
        session["oidc_state"] = state
        login_url = get_login_url(callback_url, state)
        return jsonify({"login_url": login_url}), 200

    @app.route("/v1/pay/signup", methods=["GET"])
    def get_signup():
        callback_url = request.args.get("redirect_uri", DEFAULT_CALLBACK_URL)
        state = str(uuid.uuid4())
        session["oidc_state"] = state
        signup_url = get_signup_url(callback_url, state)
        return jsonify({"signup_url": signup_url}), 200

    @app.route("/v1/pay/callback", methods=["POST"])
    def handle_callback():
        data = request.get_json() or {}
        code = data.get("code")
        redirect_uri = data.get("redirect_uri", DEFAULT_CALLBACK_URL)
        if not code:
            return jsonify({"error": "code required"}), 400
        try:
            token_data = exchange_code_for_token(code, redirect_uri)
            return jsonify({
                "access_token": token_data.get("access_token"),
                "expires_in": token_data.get("expires_in"),
                "token_type": "bearer",
            }), 200
        except Exception as e:
            return jsonify({"error": "failed_to_exchange_code", "detail": str(e)}), 502
