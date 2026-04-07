from payment_service.repository import stats_repository


def get_stats() -> dict:
    totals = stats_repository.get_payment_totals()
    status_counts = stats_repository.get_payment_counts_by_status()

    concluded = status_counts.get("concluded", 0)
    cancelled = status_counts.get("cancelled", 0)
    decided = concluded + cancelled
    success_rate = round(concluded / decided * 100, 1) if decided else 0

    user_stats = stats_repository.get_user_profile_stats()

    avg_payments_per_user = (
        round(totals["total_payments"] / totals["total_users"], 2)
        if totals["total_users"]
        else 0
    )

    return {
        "overview": {
            "total_users": totals["total_users"],
            "total_payments": totals["total_payments"],
            "total_revenue_eur": round(totals["total_revenue_eur"], 2),
            "avg_transaction_amount": round(totals["avg_transaction_amount"], 2),
            "min_transaction_amount": round(totals["min_transaction_amount"], 2),
            "max_transaction_amount": round(totals["max_transaction_amount"], 2),
            "avg_payments_per_user": avg_payments_per_user,
        },
        "payments_by_status": {
            "pending": status_counts.get("pending", 0),
            "concluded": concluded,
            "cancelled": cancelled,
            "success_rate_pct": success_rate,
        },
        "daily_trends_last_30_days": stats_repository.get_payments_per_day(days=30),
        "cards": stats_repository.get_card_stats(),
        "user_profiles": user_stats,
        "otps": stats_repository.get_otp_stats(),
        "activity_patterns": {
            "payments_by_hour": stats_repository.get_payments_by_hour(),
            "payments_by_weekday": stats_repository.get_payments_by_weekday(),
        },
    }
