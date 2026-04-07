import os
import secrets
import hashlib
import hmac
from datetime import datetime, timedelta
from io import BytesIO

import stripe
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

from payment_service.models.payment import Payment, PaymentStatus
from payment_service.models.payment_otp import PaymentOTP
from payment_service.repository import payment_repository
from payment_service.repository import otp_repository
from payment_service.services.providers.stripe_provider import StripePaymentProvider
from payment_service.config import NOTIFICATIONS_BASE_URL, NOTIFICATIONS_API_KEY

_provider = StripePaymentProvider()

try:
    from clients.notifications_client import NotificationsClient
    _notifications = NotificationsClient(NOTIFICATIONS_BASE_URL, NOTIFICATIONS_API_KEY)
except ImportError:
    import sys, os
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__)))))
    from clients.notifications_client import NotificationsClient
    _notifications = NotificationsClient(NOTIFICATIONS_BASE_URL, NOTIFICATIONS_API_KEY)


def create_payment(
    user_id: str,
    amount: float,
    phone_number: str | None = None,
    wallet_id: str | None = None,
    redirect_url: str | None = None,
    payment_method_id: str | None = None,
) -> Payment:
    payment = Payment(
        user_id=user_id,
        amount=amount,
        phone_number=phone_number,
        wallet_id=wallet_id,
        redirect_url=redirect_url,
    )

    if payment_method_id:
        # Use an existing saved card — get Stripe Customer for this user
        from payment_service.services import user_service
        customer_id = user_service.get_or_create_stripe_customer(user_id)
        try:
            intent = stripe.PaymentIntent.create(
                amount=round(amount * 100),
                currency="eur",
                payment_method=payment_method_id,
                customer=customer_id,
                metadata={"payment_id": payment.id},
            )
            payment.stripe_payment_intent_id = intent.id
            payment.stripe_client_secret = intent.client_secret
            payment.status = PaymentStatus.PENDING
        except stripe.error.StripeError as e:
            raise RuntimeError(str(e)) from e
    else:
        payment = _provider.initiate_payment(payment)

    payment_repository.save(payment)
    return payment


def get_payment(payment_id: str) -> Payment | None:
    return payment_repository.find_by_id(payment_id)


def generate_receipt(payment_id: str) -> bytes | None:
    payment = payment_repository.find_by_id(payment_id)
    if payment is None:
        return None

    buf = BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    _, height = A4

    c.setFont("Helvetica-Bold", 18)
    c.drawString(50, height - 60, "Payment Receipt")

    c.setFont("Helvetica-Bold", 11)
    c.setFillColorRGB(0.4, 0.4, 0.4)
    c.drawString(50, height - 82, f"ID: {payment.id}")
    c.setFillColorRGB(0, 0, 0)

    c.setFont("Helvetica", 12)
    fields = [
        ("Date", payment.created_at.strftime("%Y-%m-%d %H:%M UTC") if payment.created_at else "N/A"),
        ("Amount", f"EUR {payment.amount:.2f}"),
        ("Status", payment.status),
        ("To Wallet", payment.wallet_id or "N/A"),
        ("User ID", payment.user_id),
    ]
    y = height - 115
    for label, value in fields:
        c.drawString(50, y, f"{label}:")
        c.drawString(200, y, str(value))
        y -= 24

    c.save()
    return buf.getvalue()


def get_user_payments(user_id: str) -> list[Payment]:
    return payment_repository.find_by_user_id(user_id)


def update_status(payment_id: str, status: str) -> Payment | None:
    payment = payment_repository.find_by_id(payment_id)
    if not payment:
        return None
    payment.status = PaymentStatus(status).value
    payment_repository.update(payment)

    if status == PaymentStatus.CONCLUDED.value:
        _notifications.send_event(
            user_id=payment.user_id,
            data={
                "event": "payment_concluded",
                "payment_id": payment.id,
                "wallet_id": payment.wallet_id,
                "amount": payment.amount,
            },
        )

    return payment


def handle_webhook(payload: bytes, sig_header: str) -> None:
    secret = os.environ.get("STRIPE_WEBHOOK_SECRET", "")
    event = _provider.construct_webhook_event(payload, sig_header, secret)

    if event["type"] == "payment_intent.succeeded":
        intent_id = event["data"]["object"]["id"]
        payment = payment_repository.find_by_stripe_intent_id(intent_id)
        if payment and payment.status == PaymentStatus.PENDING.value:
            update_status(payment.id, PaymentStatus.CONCLUDED.value)

    elif event["type"] == "payment_intent.payment_failed":
        intent_id = event["data"]["object"]["id"]
        payment = payment_repository.find_by_stripe_intent_id(intent_id)
        if payment and payment.status == PaymentStatus.PENDING.value:
            update_status(payment.id, PaymentStatus.CANCELLED.value)


def send_otp(payment_id: str, user_id: str) -> None:
    payment = payment_repository.find_by_id(payment_id)
    if not payment or payment.user_id != user_id:
        raise ValueError("payment_not_found")

    from payment_service.services import user_service, twilio_service
    profile = user_service.get_or_create_profile(user_id)
    if not profile.phone_number:
        raise ValueError("no_phone_number")

    otp_repository.invalidate_previous_otps(payment_id)

    code = f"{secrets.randbelow(1000000):06d}"
    code_hash = hashlib.sha256(code.encode()).hexdigest()
    expires_at = datetime.utcnow() + timedelta(minutes=5)

    otp = PaymentOTP(payment_id=payment_id, code_hash=code_hash, expires_at=expires_at)
    otp_repository.save_otp(otp)

    twilio_service.send_otp_whatsapp(profile.phone_number, code)


def verify_otp(payment_id: str, user_id: str, code: str) -> bool:
    payment = payment_repository.find_by_id(payment_id)
    if not payment or payment.user_id != user_id:
        raise ValueError("payment_not_found")

    otp = otp_repository.get_latest_otp(payment_id)
    if not otp or otp.used or otp.expires_at < datetime.utcnow():
        return False

    code_hash = hashlib.sha256(code.encode()).hexdigest()
    if not hmac.compare_digest(otp.code_hash, code_hash):
        return False

    otp.used = True
    otp_repository.save_otp(otp)
    return True
