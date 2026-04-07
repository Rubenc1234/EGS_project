import uuid
from payment_service.database import db


class PaymentOTP(db.Model):
    __tablename__ = "payment_otps"

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    payment_id = db.Column(db.String(36), db.ForeignKey("payments.id"), nullable=False)
    code_hash = db.Column(db.String, nullable=False)  # SHA256 hex
    expires_at = db.Column(db.DateTime, nullable=False)  # UTC
    used = db.Column(db.Boolean, nullable=False, default=False)
