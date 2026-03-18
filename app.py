from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from flask_swagger_ui import get_swaggerui_blueprint
import os
from dotenv import load_dotenv
import stripe
from clients.iam_client import IAMClient
import requests

load_dotenv(os.path.join(os.path.dirname(__file__), "payment_service", ".env"))

app = Flask(__name__)
CORS(app)

# endereço do IAM (pode ser localhost ou container)
IAM_BASE_URL = "http://localhost:5000"

# IAM client com cache e retries
iam = IAMClient(IAM_BASE_URL)

# endereço do Payment Service (composer -> payment_service)
PAYMENT_BASE_URL = "http://localhost:5002"
# endereço do Notifications Service (composer -> notifications_service)
NOTIFICATIONS_BASE_URL = "http://localhost:5003"
# endereço do Transactions Service (composer -> transactions_service)
TRANSACTIONS_BASE_URL = "http://localhost:8081"
# wallet do banco (origem das transações geradas por pagamentos)
BANK_WALLET = os.environ.get("BANK_WALLET", "bank-wallet-default")
STRIPE_WEBHOOK_SECRET = os.environ.get("STRIPE_WEBHOOK_SECRET", "")

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


def _trigger_transaction_for_payment(payment_id: str) -> None:
    """Fetch full payment and create a blockchain transaction. Best-effort."""
    try:
        pay_res = requests.get(f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}", timeout=5)
        if pay_res.status_code != 200:
            return
        payment = pay_res.json()
        to_wallet = payment.get("to_wallet")
        amount = payment.get("amount")
        if not to_wallet or amount is None:
            return
        tx_payload = {
            "from_wallet": BANK_WALLET,
            "to_wallet": to_wallet,
            "amount": amount,
            "asset": "EUR",
        }
        requests.post(
            f"{TRANSACTIONS_BASE_URL}/v1/transactions/",
            json=tx_payload,
            headers={"Content-Type": "application/json"},
            timeout=5,
        )
    except Exception:
        pass  # best-effort: falha na transação não deve reverter o update de status


@app.route("/v1/composer/payments", methods=["POST"])
def composer_create_payment():
    data = request.get_json() or {}
    user_id = data.get("user_id")
    amount = data.get("amount")
    to_wallet = data.get("to_wallet")

    if not user_id or amount is None or not to_wallet:
        return jsonify({"error": "user_id, amount and to_wallet required"}), 400

    try:
        headers = {"Content-Type": "application/json"}
        res = requests.post(
            f"{PAYMENT_BASE_URL}/v1/payments",
            json={"user_id": user_id, "amount": amount, "to_wallet": to_wallet},
            headers=headers,
            timeout=5,
        )
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}

        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502


# Webhook Stripe — deve ficar ANTES da rota /<payment_id> para Flask não tratar "webhook" como payment_id
@app.route("/v1/composer/payments/webhook", methods=["POST"])
def composer_payment_webhook():
    """
    Stripe faz POST aqui quando o estado de um PaymentIntent muda.
    Eventos tratados: payment_intent.succeeded, payment_intent.payment_failed
    """
    payload = request.get_data()
    sig_header = request.headers.get("Stripe-Signature", "")

    try:
        event = stripe.Webhook.construct_event(payload, sig_header, STRIPE_WEBHOOK_SECRET)
    except ValueError:
        return jsonify({"error": "invalid payload"}), 400
    except stripe.error.SignatureVerificationError:
        return jsonify({"error": "invalid signature"}), 400

    STATUS_MAP = {
        "payment_intent.succeeded": "concluded",
        "payment_intent.payment_failed": "cancelled",
    }
    internal_status = STATUS_MAP.get(event["type"])
    if not internal_status:
        return jsonify({"received": True}), 200

    payment_intent = event["data"]["object"]
    payment_id = (payment_intent.get("metadata") or {}).get("payment_id")
    if not payment_id:
        return jsonify({"error": "missing payment_id in metadata"}), 400

    try:
        headers = {"Content-Type": "application/json"}
        res = requests.patch(
            f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}",
            json={"status": internal_status},
            headers=headers,
            timeout=5,
        )
        if res.status_code == 200 and internal_status == "concluded":
            _trigger_transaction_for_payment(payment_id)
        return jsonify({"received": True}), 200
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502


# -----------------------------
# NOTIFICATIONS (composer -> notifications_service)
# -----------------------------


@app.route("/v1/composer/notifications", methods=["POST"])
def composer_send_notification():
    data = request.get_json() or {}
    # expected fields: message, user_ids (list)
    message = data.get("message")
    user_ids = data.get("user_ids")

    if not message or not user_ids:
        return jsonify({"error": "message and user_ids required"}), 400

    try:
        # Dev: forward notification without requesting a service token
        headers = {"Content-Type": "application/json"}
        res = requests.post(f"{NOTIFICATIONS_BASE_URL}/notify", json={"message": message, "user_ids": user_ids}, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "notifications_service_unreachable", "detail": str(e)}), 502


@app.route("/v1/composer/events/<user_id>", methods=["GET"])
def composer_events_sse(user_id):
    """Proxy SSE stream from notifications service to the client.
    This keeps the composer as the single service communicating with other services.
    """
    try:
        # Dev: proxy SSE without adding a service token
        # open a streaming request to the notifications service
        upstream = requests.get(f"{NOTIFICATIONS_BASE_URL}/events/{user_id}", stream=True, timeout=(5, None))
        upstream.raise_for_status()

        def generate():
            try:
                for chunk in upstream.iter_lines(decode_unicode=True):
                    if chunk:
                        # yield exactly what upstream sent (SSE formatted)
                        yield chunk + "\n\n"
            finally:
                try:
                    upstream.close()
                except Exception:
                    pass

        return app.response_class(generate(), mimetype="text/event-stream")
    except requests.RequestException as e:
        return jsonify({"error": "notifications_service_unreachable", "detail": str(e)}), 502
    except Exception as e:
        return jsonify({"error": "internal_error", "detail": str(e)}), 500


@app.route("/v1/composer/payments/<payment_id>", methods=["GET"])
def composer_get_payment(payment_id):
    try:
        # Dev: call payment service without requesting a service token
        res = requests.get(f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}", timeout=5)
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
        headers = {"Content-Type": "application/json"}
        res = requests.patch(f"{PAYMENT_BASE_URL}/v1/payments/{payment_id}", json={"status": status}, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}

        if res.status_code == 200 and status == "concluded":
            _trigger_transaction_for_payment(payment_id)

        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "payment_service_unreachable", "detail": str(e)}), 502


# -----------------------------
# TRANSACTIONS (composer -> transactions_service)
# -----------------------------


@app.route("/v1/composer/transactions", methods=["POST"])
def composer_create_transaction():
    data = request.get_json() or {}
    # expected fields: from_wallet, to_wallet, amount, asset
    from_wallet = data.get("from_wallet")
    to_wallet = data.get("to_wallet")
    amount = data.get("amount")

    if not from_wallet or not to_wallet or amount is None:
        return jsonify({"error": "from_wallet, to_wallet and amount required"}), 400

    try:
        # Development mode: do not request a service token from IAM for transactions.
        # Composer forwards requests directly to the transactions service for now.
        headers = {"Content-Type": "application/json"}

        res = requests.post(f"{TRANSACTIONS_BASE_URL}/v1/transactions/", json=data, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}

        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "transactions_service_unreachable", "detail": str(e)}), 502


@app.route("/v1/composer/transactions", methods=["GET"])
def composer_list_transactions():
    wallet_id = request.args.get("wallet_id")
    status = request.args.get("status")
    limit = request.args.get("limit")
    offset = request.args.get("offset")

    params = {}
    if wallet_id:
        params["wallet_id"] = wallet_id
    if status:
        params["status"] = status
    if limit:
        params["limit"] = limit
    if offset:
        params["offset"] = offset

    try:
        # Dev: skip service-token step and call transactions service directly
        headers = {}
        res = requests.get(f"{TRANSACTIONS_BASE_URL}/v1/transactions/", params=params, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "transactions_service_unreachable", "detail": str(e)}), 502


@app.route("/v1/composer/transactions/<wallet_id>/balance", methods=["GET"])
def composer_get_balance(wallet_id):
    try:
        # Dev: call transactions service without requesting a service token
        res = requests.get(f"{TRANSACTIONS_BASE_URL}/v1/transactions/{wallet_id}/balance", timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "transactions_service_unreachable", "detail": str(e)}), 502


@app.route("/v1/composer/transactions/refund", methods=["POST"])
def composer_refund_transaction():
    data = request.get_json() or {}
    original_tx_id = data.get("original_tx_id")

    if not original_tx_id:
        return jsonify({"error": "original_tx_id required"}), 400

    try:
        # Dev: forward refund request without adding a service token
        headers = {"Content-Type": "application/json"}
        res = requests.post(f"{TRANSACTIONS_BASE_URL}/v1/transactions/refund", json={"original_tx_id": original_tx_id}, headers=headers, timeout=5)
        try:
            body = res.json()
        except Exception:
            body = {"detail": res.text}
        return jsonify(body), res.status_code
    except requests.RequestException as e:
        return jsonify({"error": "transactions_service_unreachable", "detail": str(e)}), 502


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
