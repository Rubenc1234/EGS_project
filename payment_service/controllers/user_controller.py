from flask import jsonify, request
from payment_service.controllers.auth_controller import require_token, get_user_id_from_token
from payment_service.services import user_service


def _user_id() -> str:
    token = request.headers.get("Authorization", "").split(" ", 1)[-1]
    return get_user_id_from_token(token)


def register_routes(app):

    @app.route("/v1/users/profile", methods=["GET"])
    @require_token
    def get_profile():
        data = user_service.get_profile_with_cards(_user_id())
        return jsonify(data), 200

    @app.route("/v1/users/profile", methods=["PUT"])
    @require_token
    def update_profile():
        data = request.get_json(silent=True) or {}
        phone = data.get("phone_number")
        profile = user_service.update_phone(_user_id(), phone)
        return jsonify({"user_id": profile.user_id, "phone_number": profile.phone_number}), 200

    @app.route("/v1/users/cards", methods=["POST"])
    @require_token
    def add_card():
        data = request.get_json(silent=True) or {}
        pm_id = data.get("stripe_payment_method_id")
        if not pm_id:
            return jsonify({"error": "stripe_payment_method_id required"}), 400
        try:
            card = user_service.save_card(_user_id(), pm_id)
            return jsonify(card.to_dict()), 201
        except Exception as e:
            return jsonify({"error": str(e)}), 502

    @app.route("/v1/users/cards/<card_id>", methods=["DELETE"])
    @require_token
    def delete_card(card_id):
        try:
            user_service.delete_card(_user_id(), card_id)
            return jsonify({"deleted": True}), 200
        except ValueError as e:
            return jsonify({"error": str(e)}), 404
        except Exception as e:
            return jsonify({"error": str(e)}), 502
