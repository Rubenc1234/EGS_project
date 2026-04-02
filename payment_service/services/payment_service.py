import os
from io import BytesIO

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

from payment_service.models.payment import Payment, PaymentStatus
from payment_service.repository import payment_repository
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
) -> Payment:
    payment = Payment(
        user_id=user_id,
        amount=amount,
        phone_number=phone_number,
        wallet_id=wallet_id,
        redirect_url=redirect_url,
    )
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
