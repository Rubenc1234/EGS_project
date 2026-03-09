import requests
from iam_service.services.keycloak_service import get_user_token

# pedir token ao Keycloak
token = get_user_token("john.doe", "password1234")
print("TOKEN:", token)

# usar token para chamar API protegida
headers = {
    "Authorization": f"Bearer {token}"
}

res = requests.get(
    "http://localhost:5000/v1/users/1",
    headers=headers
)

print("STATUS:", res.status_code)
print("BODY:", res.text)