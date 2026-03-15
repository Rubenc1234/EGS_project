from flask import Flask, send_from_directory
from flask_cors import CORS
from flask_swagger_ui import get_swaggerui_blueprint
from iam_service.controllers.iam_controller import register_routes
from payment_service.controllers.payment_controller import register_routes as register_payment_routes
import os

app = Flask(__name__)
CORS(app)

# Swagger UI para payment
SWAGGER_URL_PAY = "/payment/docs"
API_URL_PAY = "/payment_service/static/openapi.yaml"
swagger_pay = get_swaggerui_blueprint(SWAGGER_URL_PAY, API_URL_PAY, blueprint_name="payment_swagger")
app.register_blueprint(swagger_pay, url_prefix=SWAGGER_URL_PAY)

@app.route("/payment_service/static/<path:filename>")
def payment_static(filename):
    return send_from_directory(
        os.path.join(app.root_path, "static"),
        filename
    )

#Registrar rotas Payment
register_payment_routes(app)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5002)