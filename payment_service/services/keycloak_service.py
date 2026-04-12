import logging
import requests
import urllib.parse
from payment_service.config import KEYCLOAK_URL, KEYCLOAK_PUBLIC_URL, REALM, CLIENT_ID, CLIENT_SECRET

log = logging.getLogger(__name__)


def get_login_url(callback_url: str, state: str) -> str:
    return (
        f"{KEYCLOAK_PUBLIC_URL}/realms/{REALM}/protocol/openid-connect/auth?"
        f"client_id={urllib.parse.quote(CLIENT_ID)}&"
        f"response_type=code&"
        f"redirect_uri={urllib.parse.quote(callback_url)}&"
        f"state={urllib.parse.quote(state)}&"
        f"scope=openid%20profile%20email&"
        f"prompt=login&max_age=0"
    )


def get_signup_url(callback_url: str, state: str) -> str:
    return (
        f"{KEYCLOAK_PUBLIC_URL}/realms/{REALM}/protocol/openid-connect/registrations?"
        f"client_id={urllib.parse.quote(CLIENT_ID)}&"
        f"response_type=code&"
        f"redirect_uri={urllib.parse.quote(callback_url)}&"
        f"state={urllib.parse.quote(state)}&"
        f"scope=openid+profile+email"
    )


def exchange_code_for_token(code: str, redirect_uri: str) -> dict:
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token"
    data = {
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET,
        "code": code,
        "redirect_uri": redirect_uri,
    }
    res = requests.post(url, data=data, timeout=5)
    res.raise_for_status()
    js = res.json()
    return {"access_token": js.get("access_token"), "expires_in": js.get("expires_in")}


def introspect_token(token: str) -> bool:
    """
    Validates the token by calling Keycloak's /userinfo endpoint.
    This works for both public and confidential clients without needing a client secret.
    Returns True if the token is valid and active.
    """
    url = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/userinfo"
    try:
        res = requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=5)
        log.warning("Userinfo HTTP %s", res.status_code)
        return res.status_code == 200
    except Exception as exc:
        log.error("introspect_token error: %s", exc)
        return False
