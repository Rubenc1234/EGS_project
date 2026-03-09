import requests
from iam_service.config import KEYCLOAK_URL, REALM, CLIENT_ID, CLIENT_SECRET

def get_user_token(username: str, password: str) -> str:
    print(KEYCLOAK_URL, REALM, CLIENT_ID, CLIENT_SECRET)
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token"

    data = {
        "grant_type": "password",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "username": username,
        "password": password,
        "scope": "openid",
    }

    res = requests.post(url, data=data)

    print("STATUS:", res.status_code)
    print("BODY:", res.text)

    res.raise_for_status()

    return res.json()["access_token"]


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