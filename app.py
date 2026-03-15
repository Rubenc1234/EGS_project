from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from flask_swagger_ui import get_swaggerui_blueprint
import os
from clients.iam_client import IAMClient
import requests

app = Flask(__name__)
CORS(app)

# endereço do IAM (pode ser localhost ou container)
IAM_BASE_URL = "http://localhost:5000"

# IAM client com cache e retries
iam = IAMClient(IAM_BASE_URL)

# endereço do Payment Service (composer -> payment_service)
PAYMENT_BASE_URL = "http://localhost:5002"

# Swagger UI para composer
SWAGGER_URL_COMPOSER = "/composer/docs"
API_URL_COMPOSER = "/composer_service/static/openapi.yaml"
swagger_comp = get_swaggerui_blueprint(SWAGGER_URL_COMPOSER, API_URL_COMPOSER, blueprint_name="composer_swagger")
app.register_blueprint(swagger_comp, url_prefix=SWAGGER_URL_COMPOSER)


@app.route("/composer_service/static/<path:filename>")
def composer_static(filename):
    return send_from_directory(
        os.path.join(app.root_path, "composer_service", "static"),
        filename
    )


# -----------------------------
# LOGIN
# -----------------------------
@app.route("/v1/composer/login", methods=["POST"])
def composer_login():

    data = request.get_json() or {}

    username = data.get("username")
    password = data.get("password")

    if not username or not password:
        return jsonify({"error": "username and password required"}), 400

    try:
        token_data = iam.get_user_token(username, password)
        token = token_data.get("access_token") or token_data.get("service_token")
        if not token:
            return jsonify({"error": "authentication_failed"}), 401
        # optional: introspect to check active
        active = iam.introspect(token)
        return jsonify({"access_token": token, "expires_in": token_data.get("expires_in"), "active": active}), 200
    except Exception as e:
        return jsonify({"error": "authentication_failed", "detail": str(e)}), 401


# -----------------------------
# TOKENS (introspect)
# -----------------------------
@app.route("/v1/composer/tokens", methods=["PATCH"])
def validate_user_token():

    data = request.get_json() or {}

    token = data.get("token")

    if not token:
        return jsonify({"error": "token required"}), 400

    try:
        active = iam.introspect(token)
        return jsonify({"active": active}), 200
    except Exception as e:
        return jsonify({"error": "iam_unreachable", "detail": str(e)}), 502


# -----------------------------
# PAYMENTS (composer -> payment_service)
# -----------------------------


@app.route("/v1/composer/payments", methods=["POST"])
def composer_create_payment():
    data = request.get_json() or {}
    user_id = data.get("user_id")
    amount = data.get("amount")

    if not user_id or amount is None:
        return jsonify({"error": "user_id and amount required"}), 400

    try:
        # get service token to call payment_service (may be unused by current payment_service)
        token_data = iam.get_service_token()
        service_token = token_data.get("service_token") or token_data.get("access_token")

        headers = {}
        if service_token:
            headers["Authorization"] = f"Bearer {service_token}"

        res = requests.post(f"{PAYMENT_BASE_URL}/v1/payments", json={"user_id": user_id, "amount": amount}, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}

        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502
    except Exception as e:
        return jsonify({"error": "internal_error", "detail": str(e)}), 500


@app.route("/v1/composer/payments/<payment_id>", methods=["GET"])
def composer_get_payment(payment_id):
    try:
        token_data = iam.get_service_token()
        service_token = token_data.get("service_token") or token_data.get("access_token")
        headers = {}
        if service_token:
            headers["Authorization"] = f"Bearer {service_token}"

        res = requests.get(f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}", headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502


@app.route("/v1/composer/payments/<payment_id>", methods=["PATCH"])
def composer_update_payment(payment_id):
    data = request.get_json() or {}
    status = data.get("status")

    if status is None:
        return jsonify({"error": "status required"}), 400

    try:
        token_data = iam.get_service_token()
        service_token = token_data.get("service_token") or token_data.get("access_token")
        headers = {"Content-Type": "application/json"}
        if service_token:
            headers["Authorization"] = f"Bearer {service_token}"

        res = requests.patch(f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}", json={"status": status}, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502


# -----------------------------
# GET USER
# -----------------------------
@app.route("/v1/composer/users/<id>", methods=["GET"])
def get_user(id):

    token = request.headers.get("Authorization")

    if not token:
        return jsonify({"error": "Authorization header missing"}), 401

    try:
        body, status = iam.get_user(id, token)
        return jsonify(body), status
    except Exception as e:
        return jsonify({"error": "iam_unreachable", "detail": str(e)}), 502


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001)