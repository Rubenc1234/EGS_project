from payment_service.database import db
from payment_service.models.payment import Payment


def save(payment: Payment) -> None:
    db.session.add(payment)
    db.session.commit()


def find_by_id(payment_id: str) -> Payment | None:
    return db.session.get(Payment, payment_id)


def update(payment: Payment) -> None:
    db.session.commit()
