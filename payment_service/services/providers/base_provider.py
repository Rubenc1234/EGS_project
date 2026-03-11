from abc import ABC, abstractmethod
from payment_service.models.payment import Payment

class BasePaymentProvider(ABC):

    @abstractmethod
    def initiate_payment(self, payment: Payment) -> Payment:
        """Inicia o pagamento no provider externo."""
        ...