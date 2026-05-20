from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class AdminLoginRequest(BaseModel):
    email: str
    password: str
    totp_code: str | None = None


class AdminTokenResponse(BaseModel):
    access_token: str
    expires_at: datetime
    requires_totp_setup: bool = False


class AdminTotpSetupResponse(BaseModel):
    secret: str
    otpauth_url: str


class AdminMe(BaseModel):
    id: UUID
    email: str
    name: str | None
    role: str
    permissions: dict | None
    last_login_at: datetime | None

    model_config = {"from_attributes": True}


class StoryCreate(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    genre: str
    tags: list[str] | None = None
    tone: str | None = None
    age_rating: str = "13+"
    summary: str | None = None
    hook_line: str | None = Field(default=None, max_length=500)
    cover_url: str | None = None
    free_chapters: int = Field(default=3, ge=0)
    is_premium: bool = False
    language: str = "en"
    author_name: str = "AI Author"


class StoryUpdate(BaseModel):
    title: str | None = None
    genre: str | None = None
    tags: list[str] | None = None
    tone: str | None = None
    age_rating: str | None = None
    summary: str | None = None
    hook_line: str | None = None
    cover_url: str | None = None
    free_chapters: int | None = None
    is_featured: bool | None = None
    is_trending: bool | None = None
    is_premium: bool | None = None
    display_order: int | None = None


class ScheduleStoryRequest(BaseModel):
    scheduled_at: datetime


class StoryReorderRequest(BaseModel):
    """Bulk reorder request — id-ordered list defines new display_order (0..n)."""
    order: list[UUID] = Field(min_length=1, max_length=200)


class BulkChapterPublishRequest(BaseModel):
    story_id: UUID
    chapter_ids: list[UUID] | None = None  # None = publish all drafts for story


class AdminReplyRequest(BaseModel):
    chapter_id: UUID
    parent_id: UUID | None = None
    content: str = Field(min_length=1, max_length=2000)


class ContentImportRow(BaseModel):
    """A single row in a bulk content import (story or chapter)."""
    kind: str = Field(pattern=r"^(story|chapter)$")
    # Story fields
    title: str | None = None
    genre: str | None = None
    summary: str | None = None
    hook_line: str | None = None
    tags: list[str] | None = None
    is_premium: bool | None = None
    free_chapters: int | None = None
    # Chapter fields — story_slug used to bind chapter to story (created in same import)
    story_slug: str | None = None
    chapter_number: int | None = None
    content: str | None = None
    is_free: bool | None = None
    coin_cost: int | None = None


class ContentImportRequest(BaseModel):
    dry_run: bool = True
    rows: list[ContentImportRow] = Field(min_length=1, max_length=500)


class ChapterCreate(BaseModel):
    story_id: UUID
    chapter_number: int = Field(ge=1)
    title: str | None = None
    content: str = Field(min_length=1)
    is_free: bool = False
    coin_cost: int = Field(default=5, ge=0)
    has_cliffhanger: bool = False
    cliffhanger_preview: str | None = None


class ChapterUpdate(BaseModel):
    title: str | None = None
    content: str | None = None
    is_free: bool | None = None
    coin_cost: int | None = None
    has_cliffhanger: bool | None = None
    cliffhanger_preview: str | None = None


class AdminStoryItem(BaseModel):
    id: UUID
    title: str
    slug: str
    genre: str
    status: str
    is_featured: bool
    is_trending: bool
    is_premium: bool
    display_order: int = 0
    total_chapters: int
    view_count: int
    avg_rating: Decimal
    cover_url: str | None = None
    created_at: datetime
    published_at: datetime | None
    scheduled_at: datetime | None

    model_config = {"from_attributes": True}


class GenerateStoryRequest(BaseModel):
    genre: str
    tone: str | None = None
    setting: str | None = None
    characters: list[dict] | None = None
    plot_style: str | None = None
    chapter_count: int = Field(default=20, ge=1, le=50)
    free_chapters: int = Field(default=3, ge=0)
    cliffhanger_frequency: str = "every_chapter"
    notes: str | None = None
    model: str | None = None


class GenerateChapterRequest(BaseModel):
    story_id: UUID
    chapter_number: int
    cliffhanger_type: str = "emotional"
    word_count_target: int = Field(default=750, ge=300, le=2000)
    notes: str | None = None
    model: str | None = None


class ConfigUpdate(BaseModel):
    value: dict


class BanUserRequest(BaseModel):
    reason: str = Field(min_length=3, max_length=500)


class CoinAdjustRequest(BaseModel):
    delta: int
    reason: str = Field(min_length=3, max_length=200)


class CoinPackageCreate(BaseModel):
    sku: str = Field(min_length=1, max_length=80)
    name: str = Field(min_length=1, max_length=80)
    coins: int = Field(ge=1)
    bonus_coins: int = Field(default=0, ge=0)
    price_usd: Decimal = Field(gt=0)
    is_best_value: bool = False
    is_active: bool = True
    sort_order: int = 0


class CoinPackageUpdate(BaseModel):
    sku: str | None = Field(default=None, min_length=1, max_length=80)
    name: str | None = Field(default=None, min_length=1, max_length=80)
    coins: int | None = Field(default=None, ge=1)
    bonus_coins: int | None = Field(default=None, ge=0)
    price_usd: Decimal | None = Field(default=None, gt=0)
    is_best_value: bool | None = None
    is_active: bool | None = None
    sort_order: int | None = None


class BroadcastRequest(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    body: str = Field(min_length=1, max_length=500)
    type: str = "broadcast"
    data: dict | None = None
    target_genre: str | None = None
    segment_id: UUID | None = None


class ReportResolveRequest(BaseModel):
    action: str
    notes: str | None = None


# ---------- User Segments ----------

class SegmentFilter(BaseModel):
    """A segment filter is a set of optional conditions ANDed together."""
    subscription_tier: list[str] | None = None  # e.g. ["free"] or ["vip"]
    is_guest: bool | None = None
    preferred_genres_any: list[str] | None = None  # match any
    locale: list[str] | None = None
    region: list[str] | None = None
    min_reading_streak: int | None = Field(default=None, ge=0)
    min_coin_balance: int | None = Field(default=None, ge=0)
    max_coin_balance: int | None = Field(default=None, ge=0)
    days_since_signup_min: int | None = Field(default=None, ge=0)
    days_since_signup_max: int | None = Field(default=None, ge=0)
    days_since_last_login_max: int | None = Field(default=None, ge=0)


class SegmentCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: str | None = None
    filter: SegmentFilter


class SegmentUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    description: str | None = None
    filter: SegmentFilter | None = None


# ---------- Experiments ----------

class ExperimentVariant(BaseModel):
    key: str = Field(min_length=1, max_length=80)
    weight: int = Field(ge=0, le=100)
    config: dict | None = None


class ExperimentCreate(BaseModel):
    key: str = Field(min_length=1, max_length=80, pattern=r"^[a-z][a-z0-9_]*$")
    name: str = Field(min_length=1, max_length=160)
    description: str | None = None
    variants: list[ExperimentVariant] = Field(min_length=2, max_length=10)
    segment_id: UUID | None = None


class ExperimentUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=160)
    description: str | None = None
    status: str | None = Field(default=None, pattern=r"^(draft|running|paused|completed)$")
    variants: list[ExperimentVariant] | None = None
    segment_id: UUID | None = None


# ---------- Refunds ----------

class RefundCreate(BaseModel):
    purchase_id: UUID
    amount_micros: int = Field(ge=1)
    currency: str | None = Field(default=None, max_length=8)
    reason: str | None = Field(default=None, max_length=80)
    notes: str | None = None
