import os
import sys
import secrets

# Ensure the project root is in sys.path so that payment_service.* and clients.* are importable
# regardless of the working directory from which this file is executed.
_project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)

from dotenv import load_dotenv
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

from flask import Flask, send_from_directory
from flask_cors import CORS
from flask_swagger_ui import get_swaggerui_blueprint
from payment_service.controllers.auth_controller import register_routes as register_auth_routes
from payment_service.controllers.payment_controller import register_routes as register_payment_routes
from payment_service.database import init_db

app = Flask(__name__)
CORS(app, supports_credentials=True)
app.secret_key = os.environ.get("FLASK_SECRET_KEY", secrets.token_hex(32))

app.config["SQLALCHEMY_DATABASE_URI"] = os.environ.get(
    "DATABASE_URL", "postgresql://payuser:paypassword@localhost:5433/payment_db"
)
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
init_db(app)

# Swagger UI
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

register_auth_routes(app)
register_payment_routes(app)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5002)
