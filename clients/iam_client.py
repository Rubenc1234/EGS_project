import time
import threading
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

class IAMClient:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        retries = Retry(total=2, backoff_factor=0.5, status_forcelist=[500,502,503,504])
        self.session.mount("http://", HTTPAdapter(max_retries=retries))
        self._service_token = None
        self._service_token_exp = 0
        self._lock = threading.Lock()

    def get_service_token(self):
        # memoize with lock to avoid stampede
        with self._lock:
            # consider token valid until its expiry; keep small safety window (1s)
            if self._service_token and time.time() < self._service_token_exp - 1:
                return {"service_token": self._service_token, "expires_in": int(self._service_token_exp - time.time())}

            res = self.session.post(f"{self.base_url}/v1/tokens", json={"service":"composer"}, timeout=3)
            res.raise_for_status()
            data = res.json()
            token = data.get("service_token") or data.get("access_token")
            # se o IAM devolver expires_in, usa-o; se não, usa TTL curto
            expires_in = data.get("expires_in", 300)
            self._service_token = token
            self._service_token_exp = time.time() + expires_in
            return {"service_token": token, "expires_in": expires_in}

    def introspect(self, token):
        res = self.session.patch(f"{self.base_url}/v1/tokens", json={"token": token}, timeout=3)
        res.raise_for_status()
        return res.json().get("active", False)

    def get_user_token(self, username: str, password: str):
        res = self.session.post(f"{self.base_url}/v1/tokens", json={"username": username, "password": password}, timeout=5)
        res.raise_for_status()
        data = res.json()
        # return full data dict so caller can read expires_in if present
        return data

    def get_user(self, user_id: str, auth_header: str):
        headers = {"Authorization": auth_header}
        res = self.session.get(f"{self.base_url}/v1/users/{user_id}", headers=headers, timeout=5)
        res.raise_for_status()
        return res.json(), res.status_code