from payment_service.database import db
from payment_service.models.payment_otp import PaymentOTP


def save_otp(otp: PaymentOTP) -> None:
    db.session.add(otp)
    db.session.commit()


def get_latest_otp(payment_id: str) -> PaymentOTP | None:
    return (
        PaymentOTP.query
        .filter_by(payment_id=payment_id)
        .order_by(PaymentOTP.expires_at.desc())
        .first()
    )


def invalidate_previous_otps(payment_id: str) -> None:
    PaymentOTP.query.filter_by(payment_id=payment_id, used=False).update({"used": True})
    db.session.commit()
