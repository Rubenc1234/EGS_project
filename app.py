from flask import Flask, send_from_directory
from flask_swagger_ui import get_swaggerui_blueprint
from flask_cors import CORS
from iam_service.controllers.iam_controller import register_routes
import os

app = Flask(__name__)
CORS(app)

# -------------------------------
# Swagger UI - IAM Service
# -------------------------------
SWAGGER_URL_IAM = "/iam/docs"
API_URL_IAM = "/iam_service/static/openapi.yaml"
swagger_iam = get_swaggerui_blueprint(SWAGGER_URL_IAM, API_URL_IAM)
app.register_blueprint(swagger_iam, url_prefix=SWAGGER_URL_IAM)


# -------------------------------
# Static route - IAM Service
# -------------------------------
@app.route("/iam_service/static/<path:filename>")
def iam_static(filename):
    return send_from_directory(
        os.path.join(app.root_path, "iam_service", "static"),
        filename
    )

# -------------------------------
# Rotas do IAM Service
# -------------------------------
register_routes(app)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)