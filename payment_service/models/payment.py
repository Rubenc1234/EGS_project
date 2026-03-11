import uuid
from dataclasses import dataclass, field
from enum import Enum

class PaymentStatus(str, Enum):
    PENDING = "pending"
    CONCLUDED = "concluded"
    CANCELLED = "cancelled"

@dataclass
class Payment:
    user_id: str
    amount: float
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    status: PaymentStatus = PaymentStatus.PENDING

    def to_dict(self):
        return {
            "id": self.id,
            "user_id": self.user_id,
            "amount": self.amount,
            "status": self.status.value,
        }