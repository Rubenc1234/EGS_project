import uuid
from payment_service.database import db


class UserProfile(db.Model):
    __tablename__ = "user_profiles"

    user_id = db.Column(db.String, primary_key=True)  # Keycloak sub
    phone_number = db.Column(db.String, nullable=True)
    stripe_customer_id = db.Column(db.String, nullable=True)

    def to_dict(self):
        return {
            "user_id": self.user_id,
            "phone_number": self.phone_number,
        }


class SavedCard(db.Model):
    __tablename__ = "saved_cards"

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = db.Column(db.String, db.ForeignKey("user_profiles.user_id"), nullable=False)
    stripe_payment_method_id = db.Column(db.String, nullable=False, unique=True)
    last4 = db.Column(db.String(4), nullable=False)
    brand = db.Column(db.String, nullable=False)
    exp_month = db.Column(db.Integer, nullable=False)
    exp_year = db.Column(db.Integer, nullable=False)
    is_default = db.Column(db.Boolean, nullable=False, default=False)

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "stripe_payment_method_id": self.stripe_payment_method_id,
            "last4": self.last4,
            "brand": self.brand,
            "exp_month": self.exp_month,
            "exp_year": self.exp_year,
            "is_default": self.is_default,
        }
