from flask import jsonify, request
from iam_service.services.keycloak_service import introspect_token, get_user_token
from iam_service.config import KEYCLOAK_URL, REALM, CLIENT_ID
import urllib.parse

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
    # LOGIN URL (OAuth Authorization Code Flow)
    # ----------------------
    @app.route("/v1/login", methods=["GET"])
    def get_login_url():
        """
        Retorna a URL de redirecionamento para o Keycloak.
        O frontend deve redirecionar o utilizador para esta URL.
        """
        # callback_url deve ser onde o frontend espera ser redirecionado após login
        callback_url = request.args.get("redirect_uri", "http://localhost:5173/callback")
        
        state = request.args.get("state", "state123")  # em produção, gerar aleatoriamente
        
        login_url = (
            f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/auth?"
            f"client_id={urllib.parse.quote(CLIENT_ID)}&"
            f"response_type=code&"
            f"redirect_uri={urllib.parse.quote(callback_url)}&"
            f"state={urllib.parse.quote(state)}&"
            f"scope=openid%20profile%20email"
        )
        
        return jsonify({"login_url": login_url}), 200

    # ----------------------
    # CALLBACK (trocar código por token)
    # ----------------------
    @app.route("/v1/callback", methods=["POST"])
    def handle_callback():
        """
        Recebe o código do Keycloak e troca-o por um token.
        O frontend envia o código que recebeu após o redirecionamento.
        """
        from iam_service.services.keycloak_service import exchange_code_for_token
        
        data = request.get_json() or {}
        code = data.get("code")
        redirect_uri = data.get("redirect_uri", "http://localhost:5173/callback")
        
        if not code:
            return jsonify({"error": "code required"}), 400
        
        try:
            token_data = exchange_code_for_token(code, redirect_uri)
            return jsonify({
                "access_token": token_data.get("access_token"),
                "expires_in": token_data.get("expires_in"),
                "token_type": "bearer"
            }), 200
        except Exception as e:
            return jsonify({"error": "failed_to_exchange_code", "detail": str(e)}), 502

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