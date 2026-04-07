from twilio.rest import Client
from payment_service.config import TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER


def send_otp_whatsapp(to_phone: str, code: str) -> None:
    client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
    client.messages.create(
        from_=TWILIO_FROM_NUMBER,
        to=f"whatsapp:{to_phone}",
        body=f"Your payment verification code is: {code}. Valid for 5 minutes.",
    )
