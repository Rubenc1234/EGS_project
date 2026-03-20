import uuid
import logging
from functools import wraps
from flask import jsonify, request, session
from payment_service.services.keycloak_service import (
    get_login_url,
    get_signup_url,
    exchange_code_for_token,
    introspect_token,
)

log = logging.getLogger(__name__)


def require_token(f):
    """Decorator to protect routes using the payments Keycloak realm."""
    @wraps(f)
    def decorated(*args, **kwargs):
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


def register_routes(app):

    @app.route("/v1/pay/login", methods=["GET"])
    def get_login():
        callback_url = request.args.get("redirect_uri", "http://localhost:5174/callback")
        state = str(uuid.uuid4())
        session["oidc_state"] = state
        login_url = get_login_url(callback_url, state)
        return jsonify({"login_url": login_url}), 200

    @app.route("/v1/pay/signup", methods=["GET"])
    def get_signup():
        callback_url = request.args.get("redirect_uri", "http://localhost:5174/callback")
        state = str(uuid.uuid4())
        session["oidc_state"] = state
        signup_url = get_signup_url(callback_url, state)
        return jsonify({"signup_url": signup_url}), 200

    @app.route("/v1/pay/callback", methods=["POST"])
    def handle_callback():
        data = request.get_json() or {}
        code = data.get("code")
        redirect_uri = data.get("redirect_uri", "http://localhost:5174/callback")
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
