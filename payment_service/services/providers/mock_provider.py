from payment_service.models.payment import Payment, PaymentStatus
from payment_service.services.providers.base_provider import BasePaymentProvider

class MockPaymentProvider(BasePaymentProvider):

    def initiate_payment(self, payment: Payment) -> Payment:
        # Simula aprovação imediata
        payment.status = PaymentStatus.PENDING
        return payment