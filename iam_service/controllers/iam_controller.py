from flask import jsonify, request, session
from iam_service.services.keycloak_service import introspect_token, get_user_token
from iam_service.config import KEYCLOAK_URL, KEYCLOAK_PUBLIC_URL, REALM, CLIENT_ID
import urllib.parse
import uuid

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
        Usa session para armazenar o state de forma segura (como em Java).
        """
        # callback_url deve ser onde o frontend espera ser redirecionado após login
        # Use transactions_service server callback by default (same as signup)
        callback_url = request.args.get("redirect_uri", "http://app.pt/callback")
        
        # Generate a unique state per login request and store in session
        generated_state = str(uuid.uuid4())
        session['oidc_state'] = generated_state
        session['post_login_redirect'] = callback_url
        
        login_url = (
            f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/auth?"
            f"client_id={urllib.parse.quote(CLIENT_ID)}&"
            f"response_type=code&"
            f"redirect_uri={urllib.parse.quote(callback_url)}&"
            f"state={urllib.parse.quote(generated_state)}&"
            f"scope=openid%20profile%20email&"
            f"prompt=login&max_age=0"
        )
        
        return jsonify({"login_url": login_url}), 200
    

        # ----------------------
    # SIGNUP URL (self-service registration)
    # ----------------------
    @app.route("/v1/signup", methods=["GET"])
    def get_signup_url():
        """
        Retorna a URL de redirecionamento para a página de registo do Keycloak.
        O frontend deve redirecionar o utilizador para esta URL.
        Usa /registrations endpoint (mais confiável que kc_action=register).
        Armazena state na sessão de forma segura (como em Java).
        """
        callback_url = request.args.get("redirect_uri", "http://app.pt/callback")
        
        # Generate a unique state per signup request and store in session
        generated_state = str(uuid.uuid4())
        session['oidc_state'] = generated_state
        session['post_login_redirect'] = callback_url
        
        # Use Keycloak's dedicated /registrations endpoint for user self-service signup
        # This is more reliable than /auth?kc_action=register
        signup_url = (
            f"{KEYCLOAK_PUBLIC_URL}/realms/{REALM}/protocol/openid-connect/registrations?"
            f"client_id={urllib.parse.quote(CLIENT_ID)}&"
            f"response_type=code&"
            f"redirect_uri={urllib.parse.quote(callback_url)}&"
            f"state={urllib.parse.quote(generated_state)}&"
            f"scope=openid+profile+email"
        )
        
        return jsonify({"signup_url": signup_url}), 200

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
        redirect_uri = data.get("redirect_uri", "http://app.pt/callback")
        
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
