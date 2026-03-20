import uuid
from enum import Enum
from payment_service.database import db


class PaymentStatus(str, Enum):
    PENDING = "pending"
    CONCLUDED = "concluded"
    CANCELLED = "cancelled"


class Payment(db.Model):
    __tablename__ = "payments"

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = db.Column(db.String, nullable=False)
    amount = db.Column(db.Float, nullable=False)
    status = db.Column(db.String, nullable=False, default=PaymentStatus.PENDING.value)
    phone_number = db.Column(db.String, nullable=True)
    stripe_payment_intent_id = db.Column(db.String, nullable=True)
    stripe_client_secret = db.Column(db.String, nullable=True)
    wallet_id = db.Column(db.String, nullable=True)
    redirect_url = db.Column(db.String, nullable=True)

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "amount": self.amount,
            "status": self.status,
            "phone_number": self.phone_number,
            "stripe_payment_intent_id": self.stripe_payment_intent_id,
            "stripe_client_secret": self.stripe_client_secret,
            "wallet_id": self.wallet_id,
            "redirect_url": self.redirect_url,
        }
