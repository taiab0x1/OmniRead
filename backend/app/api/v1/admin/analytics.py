from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import and_, func, select
from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.db.session import get_db
from app.dependencies import require_role
from app.models import (
    AdminUser,
    AIGenerationJob,
    Chapter,
    CoinTransaction,
    Purchase,
    ReadingProgress,
    Story,
    Subscription,
    User,
    UserChapterUnlock,
)
from app.schemas.common import ok

router = APIRouter()


@router.get("/dashboard")
def dashboard(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics", "editor", "moderator")),
):
    now = datetime.now(timezone.utc)
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    yesterday = today - timedelta(days=1)
    week_ago = today - timedelta(days=7)

    dau = db.scalar(
        select(func.count(func.distinct(User.id))).where(User.last_login_at >= today)
    )
    new_users_today = db.scalar(select(func.count(User.id)).where(User.created_at >= today))
    stories_published = db.scalar(select(func.count(Story.id)).where(Story.status == "published"))
    stories_today = db.scalar(
        select(func.count(Story.id)).where(
            and_(Story.status == "published", Story.published_at >= today)
        )
    )
    active_subs = db.scalar(
        select(func.count(Subscription.id)).where(
            and_(Subscription.state.in_(["active", "in_grace_period"]), Subscription.expires_at > now)
        )
    )
    purchases_today_total = db.scalar(
        select(func.coalesce(func.sum(Purchase.coins_credited), 0)).where(Purchase.created_at >= today)
    )
    ai_pending = db.scalar(
        select(func.count(AIGenerationJob.id)).where(AIGenerationJob.status.in_(["pending", "running"]))
    )
    ai_failed_today = db.scalar(
        select(func.count(AIGenerationJob.id)).where(
            and_(AIGenerationJob.status == "failed", AIGenerationJob.created_at >= today)
        )
    )

    return ok(
        {
            "dau": dau or 0,
            "new_users_today": new_users_today or 0,
            "stories_published": stories_published or 0,
            "stories_published_today": stories_today or 0,
            "active_subscriptions": active_subs or 0,
            "coins_credited_today": purchases_today_total or 0,
            "ai_jobs_pending": ai_pending or 0,
            "ai_jobs_failed_today": ai_failed_today or 0,
            "as_of": now,
        }
    )


@router.get("/revenue")
def revenue(
    days: int = Query(30, ge=1, le=180),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics")),
):
    since = datetime.now(timezone.utc) - timedelta(days=days)
    rows = db.execute(
        select(
            func.date_trunc("day", Purchase.created_at).label("d"),
            func.count(Purchase.id),
            func.coalesce(func.sum(Purchase.price_amount_micros), 0),
        )
        .where(Purchase.created_at >= since, Purchase.coins_credited > 0)
        .group_by("d")
        .order_by("d")
    ).all()
    return ok(
        [
            {"date": r[0].date().isoformat(), "purchases": r[1], "micros": int(r[2])}
            for r in rows
        ]
    )


@router.get("/retention")
def retention(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics")),
):
    now = datetime.now(timezone.utc)
    buckets = {}
    for window in (1, 3, 7, 14, 30):
        start = now - timedelta(days=window + 30)
        end = now - timedelta(days=window)
        cohort_size = db.scalar(
            select(func.count(User.id)).where(and_(User.created_at >= start, User.created_at < end))
        ) or 0
        active = db.scalar(
            select(func.count(User.id)).where(
                and_(
                    User.created_at >= start,
                    User.created_at < end,
                    User.last_login_at >= now - timedelta(days=window),
                )
            )
        ) or 0
        buckets[f"d{window}"] = {
            "cohort_size": cohort_size,
            "active": active,
            "rate": round(active / cohort_size, 4) if cohort_size else 0.0,
        }
    return ok(buckets)


@router.get("/content-performance")
def content_performance(
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics", "editor")),
):
    rows = list(
        db.scalars(
            select(Story).where(Story.status == "published").order_by(Story.view_count.desc()).limit(limit)
        )
    )
    return ok(
        [
            {
                "id": str(s.id),
                "title": s.title,
                "genre": s.genre,
                "views": s.view_count,
                "likes": s.like_count,
                "bookmarks": s.bookmark_count,
                "completion_count": s.completion_count,
                "avg_rating": float(s.avg_rating),
                "total_ratings": s.total_ratings,
            }
            for s in rows
        ]
    )


@router.get("/chapter-dropoff/{story_id}")
def chapter_dropoff(
    story_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics", "editor")),
):
    """Readers reached and completion rate per chapter for a story.

    `readers_reached` = unique users who saved progress at this chapter.
    `dropoff_rate` is relative to the previous chapter (or 0 for chapter 1).
    """
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError("Story not found")

    chapters = list(
        db.scalars(
            select(Chapter)
            .where(Chapter.story_id == story_id, Chapter.status == "published")
            .order_by(Chapter.chapter_number)
        )
    )

    # readers_reached per chapter via reading_progress
    counts = dict(
        db.execute(
            select(
                ReadingProgress.chapter_id,
                func.count(func.distinct(ReadingProgress.user_id)),
            )
            .where(ReadingProgress.story_id == story_id)
            .group_by(ReadingProgress.chapter_id)
        ).all()
    )
    completions = dict(
        db.execute(
            select(
                ReadingProgress.chapter_id,
                func.count(func.distinct(ReadingProgress.user_id)),
            )
            .where(
                ReadingProgress.story_id == story_id,
                ReadingProgress.completed.is_(True),
            )
            .group_by(ReadingProgress.chapter_id)
        ).all()
    )

    rows = []
    prev_reached = None
    for ch in chapters:
        reached = int(counts.get(ch.id, 0))
        completed = int(completions.get(ch.id, 0))
        dropoff = 0.0
        if prev_reached is not None and prev_reached > 0:
            dropoff = round(1.0 - (reached / prev_reached), 4)
        rows.append(
            {
                "chapter_id": str(ch.id),
                "chapter_number": ch.chapter_number,
                "title": ch.title,
                "is_free": ch.is_free,
                "readers_reached": reached,
                "completed": completed,
                "completion_rate": round(completed / reached, 4) if reached else 0.0,
                "dropoff_rate": dropoff,
            }
        )
        prev_reached = reached

    return ok({"story_id": str(story_id), "story_title": story.title, "chapters": rows})
