# In-memory store — substituir por DB no futuro
from payment_service.models.payment import Payment


_payments: dict[str, "Payment"] = {}

def save(payment: Payment) -> None:
    _payments[payment.id] = payment

def find_by_id(payment_id: str) -> Payment | None:
    return _payments.get(payment_id)

def update(payment: Payment) -> None:
    _payments[payment.id] = payment