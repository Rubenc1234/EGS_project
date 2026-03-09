from flask import jsonify, request
from iam_service.services.keycloak_service import introspect_token

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
        try:
            #token = auth_header.split()[1]  # Bearer <token>
            parts = auth_header.split()

            if len(parts) != 2 or parts[0] != "Bearer":
                return jsonify({"error": "Invalid Authorization header"}), 401

            token = parts[1]
        except IndexError:
            return jsonify({"error": "Invalid Authorization header"}), 401

        if not introspect_token(token):
            return jsonify({"error": "Invalid or expired token"}), 401

        return f(*args, **kwargs)
    return decorated

def register_routes(app):
    """Registra todas as rotas do IAM Service"""
    @app.route("/v1/users/<id>", methods=["GET"])
    @require_token
    def get_user(id):
        user = USERS.get(id)
        if not user:
            return jsonify({"error": "user not found"}), 404
        return jsonify(user), 200