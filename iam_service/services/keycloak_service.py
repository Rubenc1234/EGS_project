import requests
from iam_service.config import KEYCLOAK_URL, REALM, CLIENT_ID, CLIENT_SECRET

def get_user_token(username: str, password: str) -> str:
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token"

    data = {
        "grant_type": "password",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "username": username,
        "password": password,
        "scope": "openid",
    }

    res = requests.post(url, data=data, timeout=5)
    res.raise_for_status()
    js = res.json()
    # return structured data so callers can access expires_in
    return {"access_token": js.get("access_token"), "expires_in": js.get("expires_in")}


def introspect_token(token: str) -> bool:
    """
    Valida token JWT no Keycloak (introspection endpoint).
    Retorna True se ativo, False se inválido ou expirado.
    """
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token/introspect"
    res = requests.post(url, data={
        "token": token,
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
    })
    if res.status_code != 200:
        return False
    return res.json().get("active", False)


def get_service_token() -> dict:
    """
    Obtém um token via client_credentials do Keycloak.
    Retorna dict com keys: access_token, expires_in
    Pode lançar exceção se Keycloak estiver indisponível.
    """
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token"
    data = {
        "grant_type": "client_credentials",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
    }
    res = requests.post(url, data=data, timeout=5)
    res.raise_for_status()
    js = res.json()
    return {"access_token": js.get("access_token"), "expires_in": js.get("expires_in")}