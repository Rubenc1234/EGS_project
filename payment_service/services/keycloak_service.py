import base64
import json
import logging
import requests
import urllib.parse
import jwt
from jwt import PyJWKClient
from payment_service.config import KEYCLOAK_URL, KEYCLOAK_PUBLIC_URL, REALM, CLIENT_ID, CLIENT_SECRET

log = logging.getLogger(__name__)

_JWKS_URL = f"{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/certs"
_jwks_client = PyJWKClient(_JWKS_URL)
_EXPECTED_ISSUER = f"{KEYCLOAK_PUBLIC_URL.rstrip('/')}/realms/{REALM}"


def _decode_jwt_payload(token: str) -> dict:
    parts = token.split('.')
    if len(parts) < 2:
        return {}
    payload_b64 = parts[1] + '=' * (-len(parts[1]) % 4)
    try:
        return json.loads(base64.b64decode(payload_b64))
    except Exception:
        return {}


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
    log.warning(
        "Exchanging authorization code for token: token_url=%s redirect_uri=%s code_prefix=%s",
        url,
        redirect_uri,
        code[:12],
    )
    res = requests.post(url, data=data, timeout=5)
    log.warning(
        "Token endpoint response: status=%s content_type=%s body_prefix=%s",
        res.status_code,
        res.headers.get("content-type"),
        res.text[:200].replace('\n', ' '),
    )
    res.raise_for_status()
    js = res.json()
    access_token = js.get("access_token")
    if access_token:
        payload = _decode_jwt_payload(access_token)
        log.warning(
            "Token received: sub=%s iss=%s aud=%s exp=%s azp=%s scope=%s",
            payload.get("sub"),
            payload.get("iss"),
            payload.get("aud"),
            payload.get("exp"),
            payload.get("azp"),
            payload.get("scope"),
        )
    return {"access_token": access_token, "expires_in": js.get("expires_in")}


def introspect_token(token: str) -> bool:
    """
    Validates the token locally using Keycloak JWKS and standard JWT checks.
    Returns True if the token is valid and active.
    """
    try:
        payload = _decode_jwt_payload(token)
        log.warning(
            "Validating token locally: jwks_url=%s token_prefix=%s sub=%s iss=%s aud=%s exp=%s azp=%s",
            _JWKS_URL,
            token[:20],
            payload.get("sub"),
            payload.get("iss"),
            payload.get("aud"),
            payload.get("exp"),
            payload.get("azp"),
        )
        signing_key = _jwks_client.get_signing_key_from_jwt(token)
        jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            issuer=_EXPECTED_ISSUER,
            options={"verify_aud": False},
        )
        if payload.get("azp") and payload.get("azp") != CLIENT_ID:
            log.warning("Token azp mismatch: expected=%s actual=%s", CLIENT_ID, payload.get("azp"))
            return False
        return True
    except Exception as exc:
        log.error("jwt validation error: %s", exc)
        return False
