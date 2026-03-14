from flask import jsonify, request
from iam_service.services.keycloak_service import introspect_token, get_user_token

# Base de dados fake para teste
USERS = {
    "1": {"id": "1", "name": "Alice", "email": "alice@email.com"},
    "2": {"id": "2", "name": "Bob", "email": "bob@email.com"},
}

def require_token(f):
    """Decorator para proteger rotas usando Keycloak"""
    from functools import wraps

    @wraps(f)
    def decorated(*args, **kwargs):

        auth_header = request.headers.get("Authorization")

        if not auth_header:
            return jsonify({"error": "Authorization header missing"}), 401

        parts = auth_header.split()

        if len(parts) != 2 or parts[0] != "Bearer":
            return jsonify({"error": "Invalid Authorization header"}), 401

        token = parts[1]

        if not introspect_token(token):
            return jsonify({"error": "Invalid or expired token"}), 401

        return f(*args, **kwargs)

    return decorated


def register_routes(app):

    # ----------------------
    # CREATE TOKEN
    # ----------------------
    @app.route("/v1/tokens", methods=["POST"])
    def create_token():
        data = request.get_json() or {}

        # Token de utilizador via username/password
        username = data.get("username")
        password = data.get("password")
        if username and password:
            try:
                token_data = get_user_token(username, password)
                return jsonify({"access_token": token_data.get("access_token"), "expires_in": token_data.get("expires_in"), "token_type": "bearer"}), 201
            except Exception as e:
                return jsonify({"error": "failed_to_get_user_token", "detail": str(e)}), 502

        # Pedido de service token (client credentials)
        service = data.get("service")
        if service:
            try:
                # try to obtain service token from Keycloak
                from iam_service.services.keycloak_service import get_service_token

                token_data = get_service_token()
                # token_data expected to be dict with access_token and expires_in
                return jsonify({
                    "service_token": token_data.get("access_token"),
                    "expires_in": token_data.get("expires_in", 300),
                    "service": service
                }), 201
            except Exception as e:
                # fallback: generate a dev token if Keycloak not available
                import secrets, time, logging

                logging.warning("Keycloak unavailable: returning dev fallback service token")
                fallback = secrets.token_urlsafe(32)
                return jsonify({
                    "service_token": fallback,
                    "expires_in": 300,
                    "service": service,
                    "dev": True,
                    "note": "fallback-token; keycloak unavailable",
                }), 201

        return jsonify({"error": "username and password or service required"}), 400

    # ----------------------
    # VALIDATE TOKEN
    # ----------------------
    @app.route("/v1/tokens", methods=["PATCH"])
    def validate_token():

        data = request.json
        token = data.get("token")

        active = introspect_token(token)

        return jsonify({
            "active": active
        }), 200

    # ----------------------
    # GET USER
    # ----------------------
    @app.route("/v1/users/<id>", methods=["GET"])
    @require_token
    def get_user(id):

        user = USERS.get(id)

        if not user:
            return jsonify({"error": "user not found"}), 404

        return jsonify(user), 200