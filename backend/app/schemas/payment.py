from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class CoinPackageItem(BaseModel):
    id: UUID
    sku: str
    name: str
    coins: int
    bonus_coins: int
    price_usd: Decimal
    is_best_value: bool
    is_active: bool
    sort_order: int

    model_config = {"from_attributes": True}


class PlayPurchaseVerifyRequest(BaseModel):
    sku: str
    purchase_token: str = Field(min_length=10)
    is_subscription: bool = False


class AdRewardValidateRequest(BaseModel):
    """Manual reward validation for a server-verified AdMob SSV transaction."""
    placement: str = Field(default="chapter_unlock")
    chapter_id: UUID | None = None
    device_fingerprint: str = Field(min_length=8, max_length=255)
    ssv_transaction_id: str | None = None


class AdRewardResult(BaseModel):
    coins_credited: int
    new_balance: int
    next_available_at: datetime | None


class UnlockRequest(BaseModel):
    idempotency_key: str | None = Field(default=None, max_length=128)


class UnlockResponse(BaseModel):
    chapter_id: UUID
    unlock_method: str
    coins_spent: int
    new_balance: int


class SubscriptionStatus(BaseModel):
    tier: str
    expires_at: datetime | None
    auto_renewing: bool
    state: str
