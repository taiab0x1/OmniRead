from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import and_, desc, select
from sqlalchemy.orm import Session

from app.core.exceptions import (
    ConflictError,
    ForbiddenError,
    NotFoundError,
    PaymentRequiredError,
)
from app.models import Chapter, ReadingProgress, Story, User, UserChapterUnlock
from app.services import coin_service


def _published_chapter(db: Session, chapter_id: UUID) -> Chapter:
    chapter = db.scalar(
        select(Chapter).where(Chapter.id == chapter_id, Chapter.status == "published")
    )
    if not chapter:
        raise NotFoundError("Chapter not found")
    return chapter


def _is_unlocked(db: Session, user: User | None, chapter: Chapter) -> bool:
    if chapter.is_free:
        return True
    if not user:
        return False
    if user.subscription_tier != "free":
        sub_active = (
            user.subscription_expires_at is not None
            and user.subscription_expires_at > datetime.now(timezone.utc)
        )
        if sub_active:
            return True
    exists = db.scalar(
        select(UserChapterUnlock).where(
            UserChapterUnlock.user_id == user.id,
            UserChapterUnlock.chapter_id == chapter.id,
        )
    )
    return exists is not None


def is_unlocked(db: Session, user: User | None, chapter: Chapter) -> bool:
    return _is_unlocked(db, user, chapter)


def _adjacent_ids(db: Session, chapter: Chapter) -> tuple[UUID | None, UUID | None]:
    next_ch = db.scalar(
        select(Chapter)
        .where(
            Chapter.story_id == chapter.story_id,
            Chapter.status == "published",
            Chapter.chapter_number > chapter.chapter_number,
        )
        .order_by(Chapter.chapter_number.asc())
        .limit(1)
    )
    prev_ch = db.scalar(
        select(Chapter)
        .where(
            Chapter.story_id == chapter.story_id,
            Chapter.status == "published",
            Chapter.chapter_number < chapter.chapter_number,
        )
        .order_by(Chapter.chapter_number.desc())
        .limit(1)
    )
    return (next_ch.id if next_ch else None, prev_ch.id if prev_ch else None)


def get_chapter_for_user(db: Session, *, chapter_id: UUID, user: User | None):
    chapter = _published_chapter(db, chapter_id)
    unlocked = _is_unlocked(db, user, chapter)
    next_id, prev_id = _adjacent_ids(db, chapter)
    return chapter, unlocked, next_id, prev_id


def preview_text(content: str, words: int = 100) -> str:
    parts = content.split()
    return " ".join(parts[:words]) + ("…" if len(parts) > words else "")


def unlock_with_coins(
    db: Session,
    *,
    user: User,
    chapter_id: UUID,
    idempotency_key: str | None,
) -> tuple[Chapter, int]:
    chapter = _published_chapter(db, chapter_id)
    if chapter.is_free:
        return chapter, user.coin_balance
    existing = db.scalar(
        select(UserChapterUnlock).where(
            UserChapterUnlock.user_id == user.id,
            UserChapterUnlock.chapter_id == chapter.id,
        )
    )
    if existing:
        return chapter, user.coin_balance

    cost = chapter.coin_cost or 0
    idem = idempotency_key or f"unlock:{user.id}:{chapter.id}"
    coin_service.debit(
        db,
        user_id=user.id,
        amount=cost,
        type_="chapter_unlock",
        description=f"Unlocked chapter {chapter.chapter_number}",
        reference_id=chapter.id,
        idempotency_key=idem,
    )
    db.add(
        UserChapterUnlock(
            user_id=user.id,
            chapter_id=chapter.id,
            unlock_method="coins",
            coins_spent=cost,
        )
    )
    db.flush()
    new_balance = coin_service.get_balance(db, user.id)
    return chapter, new_balance


def unlock_via_ad(
    db: Session,
    *,
    user: User,
    chapter_id: UUID,
) -> Chapter:
    chapter = _published_chapter(db, chapter_id)
    if chapter.is_free:
        return chapter
    existing = db.scalar(
        select(UserChapterUnlock).where(
            UserChapterUnlock.user_id == user.id,
            UserChapterUnlock.chapter_id == chapter.id,
        )
    )
    if existing:
        return chapter
    db.add(
        UserChapterUnlock(
            user_id=user.id,
            chapter_id=chapter.id,
            unlock_method="ad",
            coins_spent=0,
        )
    )
    db.flush()
    return chapter


def upsert_progress(
    db: Session,
    *,
    user: User,
    story_id: UUID,
    chapter_id: UUID,
    scroll_position: int,
    completed: bool,
) -> ReadingProgress:
    existing = db.scalar(
        select(ReadingProgress).where(
            ReadingProgress.user_id == user.id, ReadingProgress.story_id == story_id
        )
    )
    now = datetime.now(timezone.utc)
    if existing:
        existing.chapter_id = chapter_id
        existing.scroll_position = scroll_position
        existing.last_read_at = now
        if completed:
            existing.completed = True
        prog = existing
    else:
        prog = ReadingProgress(
            user_id=user.id,
            story_id=story_id,
            chapter_id=chapter_id,
            scroll_position=scroll_position,
            completed=completed,
        )
        db.add(prog)
    if user.last_read_at is None or (now - user.last_read_at).total_seconds() > 86400 / 2:
        user.last_read_at = now
    db.flush()
    return prog
