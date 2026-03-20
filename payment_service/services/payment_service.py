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
