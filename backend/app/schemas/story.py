from datetime import datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class StorySummary(BaseModel):
    id: UUID
    title: str
    slug: str
    author_name: str
    cover_url: str | None
    hook_line: str | None
    genre: str
    tags: list[str] | None = None
    age_rating: str
    is_premium: bool
    total_chapters: int
    free_chapters: int
    view_count: int
    like_count: int
    avg_rating: Decimal
    estimated_read_time: int | None
    is_bookmarked: bool = False
    is_liked: bool = False
    published_at: datetime | None

    model_config = {"from_attributes": True}


class StoryDetail(StorySummary):
    summary: str | None
    bookmark_count: int
    total_ratings: int
    completion_count: int
    user_rating: int | None = None


class ChapterListItem(BaseModel):
    id: UUID
    chapter_number: int
    title: str | None
    word_count: int | None
    is_free: bool
    coin_cost: int
    has_cliffhanger: bool
    cliffhanger_preview: str | None
    is_unlocked: bool = False
    published_at: datetime | None

    model_config = {"from_attributes": True}


class ChapterContent(BaseModel):
    id: UUID
    story_id: UUID
    chapter_number: int
    title: str | None
    content: str
    word_count: int | None
    is_free: bool
    coin_cost: int
    has_cliffhanger: bool
    cliffhanger_preview: str | None
    is_unlocked: bool = True
    next_chapter_id: UUID | None = None
    prev_chapter_id: UUID | None = None
    published_at: datetime | None

    model_config = {"from_attributes": True}


class ChapterPreview(BaseModel):
    """Returned when a chapter is locked: only metadata + first 100 words."""
    id: UUID
    story_id: UUID
    chapter_number: int
    title: str | None
    preview: str
    is_free: bool
    coin_cost: int
    is_unlocked: bool = False


class StorySearchQuery(BaseModel):
    q: str | None = None
    genre: str | None = None
    tags: list[str] | None = None
    cursor: str | None = None
    limit: int = Field(default=20, ge=1, le=50)


class FeedQuery(BaseModel):
    cursor: str | None = None
    limit: int = Field(default=20, ge=1, le=50)
    genre: str | None = None


class RatingRequest(BaseModel):
    rating: int = Field(ge=1, le=5)
    review: str | None = Field(default=None, max_length=2000)


class ProgressUpdate(BaseModel):
    chapter_id: UUID
    scroll_position: int = Field(ge=0)
    completed: bool = False


class CommentCreate(BaseModel):
    content: str = Field(min_length=1, max_length=2000)
    parent_id: UUID | None = None
    is_spoiler: bool = False


class CommentItem(BaseModel):
    id: UUID
    user_id: UUID | None = None
    admin_id: UUID | None = None
    username: str | None = None
    is_admin: bool = False
    parent_id: UUID | None
    content: str
    like_count: int
    is_spoiler: bool
    created_at: datetime

    model_config = {"from_attributes": True}
