import requests


class NotificationsClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def send_event(self, user_id: str, data: dict) -> bool:
        """Send a notification event to a user. Returns True on success."""
        try:
            res = requests.post(
                f"{self.base_url}/v1/events",
                json={"user_id": user_id, "data": data},
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=5,
            )
            return res.status_code in (200, 201)
        except Exception:
            return False
