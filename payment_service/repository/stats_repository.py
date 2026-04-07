from datetime import datetime, timedelta

from sqlalchemy import func

from payment_service.database import db
from payment_service.models.payment import Payment
from payment_service.models.payment_otp import PaymentOTP
from payment_service.models.user_profile import SavedCard, UserProfile


def get_payment_counts_by_status() -> dict:
    rows = (
        db.session.query(Payment.status, func.count(Payment.id))
        .group_by(Payment.status)
        .all()
    )
    return {status: count for status, count in rows}


def get_payment_totals() -> dict:
    row = db.session.query(
        func.count(Payment.id),
        func.count(func.distinct(Payment.user_id)),
        func.sum(Payment.amount),
        func.avg(Payment.amount),
        func.min(Payment.amount),
        func.max(Payment.amount),
    ).one()
    return {
        "total_payments": row[0] or 0,
        "total_users": row[1] or 0,
        "total_revenue_eur": float(row[2] or 0),
        "avg_transaction_amount": float(row[3] or 0),
        "min_transaction_amount": float(row[4] or 0),
        "max_transaction_amount": float(row[5] or 0),
    }


def get_concluded_revenue() -> float:
    result = (
        db.session.query(func.sum(Payment.amount))
        .filter(Payment.status == "concluded")
        .scalar()
    )
    return float(result or 0)


def get_payments_per_day(days: int = 30) -> list:
    since = datetime.utcnow() - timedelta(days=days)
    rows = (
        db.session.query(
            func.date(Payment.created_at).label("day"),
            func.count(Payment.id).label("count"),
            func.sum(Payment.amount).label("revenue"),
        )
        .filter(Payment.created_at >= since)
        .group_by(func.date(Payment.created_at))
        .order_by(func.date(Payment.created_at))
        .all()
    )
    return [
        {"date": str(row.day), "count": row.count, "revenue": float(row.revenue or 0)}
        for row in rows
    ]


def get_payments_by_hour() -> dict:
    rows = (
        db.session.query(
            func.extract("hour", Payment.created_at).label("hour"),
            func.count(Payment.id).label("count"),
        )
        .group_by(func.extract("hour", Payment.created_at))
        .all()
    )
    return {int(row.hour): row.count for row in rows}


def get_payments_by_weekday() -> dict:
    # PostgreSQL: 0=Sunday, 1=Monday, ..., 6=Saturday
    day_names = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
    rows = (
        db.session.query(
            func.extract("dow", Payment.created_at).label("dow"),
            func.count(Payment.id).label("count"),
        )
        .group_by(func.extract("dow", Payment.created_at))
        .all()
    )
    return {day_names[int(row.dow)]: row.count for row in rows}


def get_card_stats() -> dict:
    total_cards = db.session.query(func.count(SavedCard.id)).scalar() or 0
    users_with_cards = (
        db.session.query(func.count(func.distinct(SavedCard.user_id))).scalar() or 0
    )
    brand_rows = (
        db.session.query(SavedCard.brand, func.count(SavedCard.id))
        .group_by(SavedCard.brand)
        .order_by(func.count(SavedCard.id).desc())
        .all()
    )
    brand_distribution = {brand: count for brand, count in brand_rows}
    most_popular_brand = brand_rows[0][0] if brand_rows else None
    return {
        "total_saved_cards": total_cards,
        "users_with_saved_cards": users_with_cards,
        "avg_cards_per_user": round(total_cards / users_with_cards, 2) if users_with_cards else 0,
        "card_brand_distribution": brand_distribution,
        "most_popular_brand": most_popular_brand,
    }


def get_user_profile_stats() -> dict:
    total_profiles = db.session.query(func.count(UserProfile.user_id)).scalar() or 0
    with_phone = (
        db.session.query(func.count(UserProfile.user_id))
        .filter(UserProfile.phone_number.isnot(None))
        .scalar()
        or 0
    )
    with_stripe = (
        db.session.query(func.count(UserProfile.user_id))
        .filter(UserProfile.stripe_customer_id.isnot(None))
        .scalar()
        or 0
    )
    return {
        "total_profiles": total_profiles,
        "users_with_phone": with_phone,
        "users_with_stripe_customer": with_stripe,
    }


def get_otp_stats() -> dict:
    total_sent = db.session.query(func.count(PaymentOTP.id)).scalar() or 0
    total_verified = (
        db.session.query(func.count(PaymentOTP.id))
        .filter(PaymentOTP.used.is_(True))
        .scalar()
        or 0
    )
    return {
        "total_otps_sent": total_sent,
        "total_otps_verified": total_verified,
        "otp_success_rate_pct": round(total_verified / total_sent * 100, 1) if total_sent else 0,
    }
