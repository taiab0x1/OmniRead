from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class UserPublic(BaseModel):
    id: UUID
    username: str
    avatar_url: str | None
    is_guest: bool
    is_verified: bool
    coin_balance: int
    subscription_tier: str
    subscription_expires_at: datetime | None
    reading_streak: int
    preferred_genres: list[str] | None
    locale: str

    model_config = {"from_attributes": True}


class UserUpdate(BaseModel):
    username: str | None = Field(default=None, min_length=3, max_length=50, pattern=r"^[a-zA-Z0-9_.]+$")
    avatar_url: str | None = None
    preferred_genres: list[str] | None = None
    locale: str | None = None
    region: str | None = None


class CoinBalance(BaseModel):
    balance: int
    subscription_tier: str
    subscription_expires_at: datetime | None


class CoinTransactionItem(BaseModel):
    id: UUID
    amount: int
    type: str
    description: str | None
    balance_after: int
    created_at: datetime

    model_config = {"from_attributes": True}


class FcmRegisterRequest(BaseModel):
    token: str = Field(min_length=10, max_length=255)
    platform: str = "android"
    app_version: str | None = None


class NotificationItem(BaseModel):
    id: UUID
    type: str
    title: str | None
    body: str | None
    data: dict | None
    is_read: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class StreakInfo(BaseModel):
    current_streak: int
    last_read_at: datetime | None
    next_reward_in_days: int
