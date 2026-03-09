from flask import request, jsonify

# base de dados fake
USERS = {
    "1": {
        "id": "1",
        "name": "Alice",
        "email": "alice@email.com"
    }
}


def register_routes(app):

    VALID_TOKEN = "service-token-123"

    @app.route("/v1/tokens", methods=["POST"])
    def create_service_token():
        # retorna token default
        return jsonify({"service_token": VALID_TOKEN}), 201

    @app.route("/v1/tokens", methods=["PATCH"])
    def validate_token():
        data = request.get_json()
        token = data.get("token")

        if token == VALID_TOKEN:
            return jsonify({"valid": True}), 200

        return jsonify({"valid": False}), 401


    @app.route("/v1/users/<id>", methods=["GET"])
    def get_user(id):

        user = USERS.get(id)

        if not user:
            return jsonify({"error": "user not found"}), 404

        return jsonify(user), 200