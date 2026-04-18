import stripe
from flask import jsonify, request, Response
from payment_service.services import payment_service, stats_service
from payment_service.controllers.auth_controller import require_token, require_operator, get_user_id_from_token


def _user_id() -> str:
    token = request.headers.get("Authorization", "").split(" ", 1)[-1]
    return get_user_id_from_token(token)


def register_routes(app):

    @app.route("/v1/payments", methods=["POST"])
    @require_token
    def create_payment():
        data = request.get_json(silent=True) or {}
        user_id = data.get("user_id")
        amount = data.get("amount")
        phone_number = data.get("phone_number")
        wallet_id = data.get("wallet_id") or data.get("to_wallet")
        redirect_url = data.get("redirect_url")

        payment_method_id = data.get("payment_method_id")

        if not user_id or amount is None:
            return jsonify({"error": "user_id and amount are required"}), 400

        try:
            payment = payment_service.create_payment(
                user_id,
                float(amount),
                phone_number=phone_number,
                wallet_id=wallet_id,
                redirect_url=redirect_url,
                payment_method_id=payment_method_id,
            )
        except ValueError as exc:
            reason = str(exc)
            if reason.startswith("minimum_amount_eur_"):
                return jsonify({"error": reason, "detail": "Minimum payment amount is €0.50."}), 400
            return jsonify({"error": reason}), 400
        except RuntimeError as exc:
            return jsonify({"error": "payment_provider_error", "detail": str(exc)}), 502
        return jsonify(payment.to_dict()), 201

    @app.route("/v1/payments", methods=["GET"])
    @require_token
    def list_user_payments():
        user_id = request.args.get("user_id")
        if not user_id:
            return jsonify({"error": "user_id is required"}), 400
        payments = payment_service.get_user_payments(user_id)
        return jsonify([p.to_dict() for p in payments]), 200

    @app.route("/v1/payments/stats", methods=["GET"])
    @require_operator
    def get_stats():
        return jsonify(stats_service.get_stats()), 200

    @app.route("/v1/payments/<payment_id>", methods=["GET"])
    @require_token
    def get_payment(payment_id):
        payment = payment_service.get_payment(payment_id)
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        return jsonify(payment.to_dict()), 200

    @app.route("/v1/payments/<payment_id>/receipt", methods=["GET"])
    @require_token
    def get_payment_receipt(payment_id):
        pdf_bytes = payment_service.generate_receipt(payment_id)
        if pdf_bytes is None:
            return jsonify({"error": "Payment not found"}), 404
        return Response(
            pdf_bytes,
            mimetype="application/pdf",
            headers={"Content-Disposition": f"attachment; filename=receipt_{payment_id}.pdf"},
        )

    @app.route("/v1/payments/<payment_id>", methods=["PATCH"])
    @require_token
    def update_payment(payment_id):
        data = request.get_json(silent=True) or {}
        status = data.get("status")

        valid_statuses = {"cancelled", "pending", "concluded"}
        if status not in valid_statuses:
            return jsonify({"error": f"Invalid status. Must be one of {valid_statuses}"}), 400

        payment = payment_service.update_status(payment_id, status)
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        return jsonify(payment.to_dict()), 200

    @app.route("/v1/payments/<payment_id>/send-otp", methods=["POST"])
    @require_token
    def send_otp(payment_id):
        try:
            payment_service.send_otp(payment_id, _user_id())
            return jsonify({"sent": True}), 200
        except ValueError as e:
            reason = str(e)
            if reason == "no_phone_number":
                return jsonify({"error": "no_phone_number"}), 422
            return jsonify({"error": reason}), 404
        except Exception as e:
            return jsonify({"error": "otp_send_failed", "detail": str(e)}), 502

    @app.route("/v1/payments/<payment_id>/verify", methods=["POST"])
    @require_token
    def verify_otp(payment_id):
        data = request.get_json(silent=True) or {}
        code = data.get("code", "")
        try:
            ok = payment_service.verify_otp(payment_id, _user_id(), code)
            if ok:
                return jsonify({"verified": True}), 200
            return jsonify({"verified": False, "error": "invalid_or_expired_code"}), 400
        except ValueError as e:
            return jsonify({"error": str(e)}), 404

    @app.route("/v1/payments/webhook", methods=["POST"])
    def stripe_webhook():
        payload = request.get_data()
        sig_header = request.headers.get("Stripe-Signature")
        try:
            payment_service.handle_webhook(payload, sig_header)
            return jsonify({"status": "ok"}), 200
        except ValueError:
            return jsonify({"error": "Invalid payload"}), 400
        except stripe.error.SignatureVerificationError:
            return jsonify({"error": "Invalid signature"}), 400
