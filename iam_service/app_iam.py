from flask import Flask, send_from_directory
from flask_cors import CORS
from flask_swagger_ui import get_swaggerui_blueprint
from iam_service.controllers.iam_controller import register_routes
import os
import secrets

app = Flask(__name__)
CORS(app)

# Configure session to store state securely (needed for OIDC state validation)
app.config['SESSION_TYPE'] = 'filesystem'
app.secret_key = secrets.token_hex(32)  # Generate random secret key for session signing

# Swagger UI
SWAGGER_URL_IAM = "/iam/docs"
API_URL_IAM = "/iam_service/static/openapi.yaml"
swagger_iam = get_swaggerui_blueprint(SWAGGER_URL_IAM, API_URL_IAM, blueprint_name="iam_swagger")
app.register_blueprint(swagger_iam, url_prefix=SWAGGER_URL_IAM)

# Static files
@app.route("/iam_service/static/<path:filename>")
def iam_static(filename):
    return send_from_directory(
        os.path.join(app.root_path, "static"),
        filename
    )

# Registrar rotas IAM
register_routes(app)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
