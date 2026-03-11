from payment_service.models.payment import Payment, PaymentStatus
from payment_service.repository import payment_repository
from payment_service.services.providers.mock_provider import MockPaymentProvider

# Trocar por SibsPaymentProvider() quando estiver pronto
_provider = MockPaymentProvider()


def create_payment(user_id: str, amount: float) -> Payment:
    payment = Payment(user_id=user_id, amount=amount)
    payment = _provider.initiate_payment(payment)
    payment_repository.save(payment)
    return payment


def get_payment(payment_id: str) -> Payment | None:
    return payment_repository.find_by_id(payment_id)


def update_status(payment_id: str, status: str) -> Payment | None:
    payment = payment_repository.find_by_id(payment_id)
    if not payment:
        return None
    payment.status = PaymentStatus(status)
    payment_repository.update(payment)
    return payment

