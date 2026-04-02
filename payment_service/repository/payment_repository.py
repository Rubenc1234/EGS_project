from payment_service.database import db
from payment_service.models.payment import Payment


def save(payment: Payment) -> None:
    db.session.add(payment)
    db.session.commit()


def find_by_id(payment_id: str) -> Payment | None:
    return db.session.get(Payment, payment_id)


def update(payment: Payment) -> None:
    db.session.commit()


def find_by_stripe_intent_id(stripe_payment_intent_id: str) -> Payment | None:
    return Payment.query.filter_by(stripe_payment_intent_id=stripe_payment_intent_id).first()


def find_by_user_id(user_id: str) -> list[Payment]:
    return Payment.query.filter_by(user_id=user_id).all()
