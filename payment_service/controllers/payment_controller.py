from flask import jsonify, request
from payment_service.services import payment_service


def register_routes(app):

    @app.route("/v1/payments", methods=["POST"])
    def create_payment():
        data = request.json or {}
        user_id = data.get("user_id")
        amount = data.get("amount")

        if not user_id or amount is None:
            return jsonify({"error": "user_id and amount are required"}), 400

        payment = payment_service.create_payment(user_id, float(amount))
        return jsonify(payment.to_dict()), 201

    @app.route("/v1/payments/<payment_id>", methods=["GET"])
    def get_payment(payment_id):
        payment = payment_service.get_payment(payment_id)
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        return jsonify(payment.to_dict()), 200

    @app.route("/v1/payments/<payment_id>", methods=["PATCH"])
    def update_payment(payment_id):
        data = request.json or {}
        status = data.get("status")

        valid_statuses = {"cancelled", "pending", "concluded"}
        if status not in valid_statuses:
            return jsonify({"error": f"Invalid status. Must be one of {valid_statuses}"}), 400

        payment = payment_service.update_status(payment_id, status)
        if not payment:
            return jsonify({"error": "Payment not found"}), 404
        return jsonify(payment.to_dict()), 200
