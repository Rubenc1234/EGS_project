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

        token = get_user_token("john.doe", "password1234")

        return jsonify({
            "service_token": token
        }), 201

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