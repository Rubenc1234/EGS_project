from flask import jsonify, request
from payment_service.services import payment_service
from payment_service.controllers.auth_controller import require_token


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

        if not user_id or amount is None:
            return jsonify({"error": "user_id and amount are required"}), 400

        try:
            payment = payment_service.create_payment(
                user_id,
                float(amount),
                phone_number=phone_number,
                wallet_id=wallet_id,
                redirect_url=redirect_url,
            )
        except RuntimeError as exc:
            return jsonify({"error": "payment_provider_error", "detail": str(exc)}), 502
        return jsonify(payment.to_dict()), 201

    @app.route("/v1/payments/<payment_id>", methods=["GET"])
    @require_token
    def get_payment(payment_id):
        payment = payment_service.get_payment(payment_id)
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        return jsonify(payment.to_dict()), 200

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
