import os
import stripe

from payment_service.models.user_profile import UserProfile, SavedCard
from payment_service.repository import user_repository
from payment_service.database import db

stripe.api_key = os.environ.get("STRIPE_Secret_key")


def get_or_create_profile(user_id: str) -> UserProfile:
    profile = user_repository.get_profile(user_id)
    if profile is None:
        profile = user_repository.create_profile(user_id)
    return profile


def update_phone(user_id: str, phone_number: str | None) -> UserProfile:
    profile = get_or_create_profile(user_id)
    profile.phone_number = phone_number
    user_repository.update_profile(profile)
    return profile


def get_profile_with_cards(user_id: str) -> dict:
    profile = get_or_create_profile(user_id)
    cards = user_repository.get_cards(user_id)
    return {
        "user_id": profile.user_id,
        "phone_number": profile.phone_number,
        "cards": [c.to_dict() for c in cards],
    }


def get_or_create_stripe_customer(user_id: str) -> str:
    profile = get_or_create_profile(user_id)
    if profile.stripe_customer_id:
        return profile.stripe_customer_id
    customer = stripe.Customer.create(metadata={"user_id": user_id})
    profile.stripe_customer_id = customer.id
    user_repository.update_profile(profile)
    return customer.id


def save_card(user_id: str, stripe_pm_id: str) -> SavedCard:
    # Dedup — same PM already saved
    existing = user_repository.get_card_by_pm_id(stripe_pm_id)
    if existing:
        return existing

    customer_id = get_or_create_stripe_customer(user_id)

    # Attach PM to customer (safe to call even if already attached)
    try:
        stripe.PaymentMethod.attach(stripe_pm_id, customer=customer_id)
    except stripe.error.InvalidRequestError:
        pass  # already attached

    pm = stripe.PaymentMethod.retrieve(stripe_pm_id)
    card_data = pm.card

    existing_cards = user_repository.get_cards(user_id)
    is_first = len(existing_cards) == 0

    card = SavedCard(
        user_id=user_id,
        stripe_payment_method_id=stripe_pm_id,
        last4=card_data.last4,
        brand=card_data.brand,
        exp_month=card_data.exp_month,
        exp_year=card_data.exp_year,
        is_default=is_first,
    )
    user_repository.save_card(card)
    return card


def delete_card(user_id: str, card_id: str) -> None:
    card = user_repository.get_card(card_id)
    if card is None or card.user_id != user_id:
        raise ValueError("card_not_found")

    try:
        stripe.PaymentMethod.detach(card.stripe_payment_method_id)
    except stripe.error.StripeError:
        pass  # detach best-effort

    was_default = card.is_default
    user_repository.delete_card(card)

    if was_default:
        remaining = user_repository.get_cards(user_id)
        if remaining:
            remaining[0].is_default = True
            db.session.commit()
