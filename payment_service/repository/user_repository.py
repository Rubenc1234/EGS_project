from payment_service.database import db
from payment_service.models.user_profile import UserProfile, SavedCard


def get_profile(user_id: str) -> UserProfile | None:
    return UserProfile.query.get(user_id)


def create_profile(user_id: str) -> UserProfile:
    profile = UserProfile(user_id=user_id)
    db.session.add(profile)
    db.session.commit()
    return profile


def update_profile(profile: UserProfile) -> None:
    db.session.commit()


def get_cards(user_id: str) -> list[SavedCard]:
    return SavedCard.query.filter_by(user_id=user_id).all()


def get_card(card_id: str) -> SavedCard | None:
    return SavedCard.query.get(card_id)


def get_card_by_pm_id(stripe_pm_id: str) -> SavedCard | None:
    return SavedCard.query.filter_by(stripe_payment_method_id=stripe_pm_id).first()


def save_card(card: SavedCard) -> None:
    db.session.add(card)
    db.session.commit()


def delete_card(card: SavedCard) -> None:
    db.session.delete(card)
    db.session.commit()
