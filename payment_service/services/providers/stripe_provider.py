import os

import stripe

from payment_service.models.payment import Payment, PaymentStatus
from payment_service.services.providers.base_provider import BasePaymentProvider

stripe.api_key = os.environ.get("STRIPE_Secret_key")


class StripePaymentProvider(BasePaymentProvider):

    def initiate_payment(self, payment: Payment) -> Payment:
        try:
            intent = stripe.PaymentIntent.create(
                amount=round(payment.amount * 100),  # Stripe espera cêntimos
                currency="eur",
                metadata={"payment_id": payment.id},
            )
            payment.stripe_payment_intent_id = intent.id
            payment.stripe_client_secret = intent.client_secret
            payment.status = PaymentStatus.PENDING
            return payment
        except stripe.error.StripeError as e:
            raise RuntimeError(str(e)) from e
