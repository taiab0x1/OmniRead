from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError, NotFoundError
from app.core.rate_limit import fixed_window
from app.db.session import get_db
from app.dependencies import get_current_user, get_optional_user
from app.models import Bookmark, Chapter, Story, StoryLike, StoryRating, User
from app.schemas.common import Meta, ok
from app.schemas.story import (
    ChapterListItem,
    CommentItem,
    RatingRequest,
    StoryDetail,
    StorySummary,
)
from app.services import story_service

router = APIRouter()


def _summary(s: Story, *, user_id=None, db=None, with_flags: bool = False) -> dict:
    payload = StorySummary.model_validate(s, from_attributes=True).model_dump()
    if with_flags and db is not None:
        payload["is_bookmarked"] = story_service.is_bookmarked(db, user_id, s.id)
        payload["is_liked"] = story_service.is_liked(db, user_id, s.id)
    return payload


@router.get("")
def list_feed(
    cursor: str | None = None,
    limit: int = Query(20, ge=1, le=50),
    genre: str | None = None,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    rows, next_cursor = story_service.feed_page(
        db, cursor=cursor, limit=limit, genre=genre, user_id=user.id if user else None
    )
    items = [_summary(s) for s in rows]
    return ok(items, meta=Meta(per_page=limit, next_cursor=next_cursor))


@router.get("/trending")
def trending(
    limit: int = Query(20, ge=1, le=50),
    genre: str | None = None,
    db: Session = Depends(get_db),
):
    return ok([_summary(s) for s in story_service.trending(db, limit=limit, genre=genre)])


@router.get("/new")
def newest(
    limit: int = Query(20, ge=1, le=50),
    genre: str | None = None,
    db: Session = Depends(get_db),
):
    return ok([_summary(s) for s in story_service.newest(db, limit=limit, genre=genre)])


@router.get("/recommended")
def recommended(
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    genres = user.preferred_genres if user else None
    return ok([_summary(s) for s in story_service.recommended(db, user_genres=genres, limit=limit)])


@router.get("/genres")
def list_genres(db: Session = Depends(get_db)):
    rows = db.execute(
        select(Story.genre, Story.cover_url)
        .where(Story.status == "published")
        .order_by(Story.genre)
    ).all()
    seen = {}
    for genre, cover in rows:
        seen.setdefault(genre, cover)
    return ok([{"genre": g, "sample_cover": c} for g, c in seen.items()])


@router.get("/search")
def search(
    q: str | None = None,
    genre: str | None = None,
    tag: list[str] | None = Query(None),
    cursor: str | None = None,
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    fixed_window(f"rl:search:{user.id if user else 'anon'}", limit=30, period_seconds=60)
    rows, next_cursor = story_service.search(db, q=q, genre=genre, tags=tag, cursor=cursor, limit=limit)
    return ok([_summary(s) for s in rows], meta=Meta(per_page=limit, next_cursor=next_cursor))


@router.get("/{story_id}")
def detail(
    story_id: UUID,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    story = story_service.get_published(db, story_id)
    story_service.increment_view(db, story)
    db.commit()
    payload = StoryDetail.model_validate(story, from_attributes=True).model_dump()
    payload["is_bookmarked"] = story_service.is_bookmarked(db, user.id if user else None, story.id)
    payload["is_liked"] = story_service.is_liked(db, user.id if user else None, story.id)
    if user:
        rating = db.scalar(
            select(StoryRating.rating).where(
                StoryRating.user_id == user.id, StoryRating.story_id == story.id
            )
        )
        payload["user_rating"] = rating
    return ok(payload)


@router.get("/{story_id}/chapters")
def chapter_list(
    story_id: UUID,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    story = story_service.get_published(db, story_id)
    chapters = story_service.chapters_for_story(db, story.id)
    items = []
    for ch in chapters:
        item = ChapterListItem.model_validate(ch, from_attributes=True).model_dump()
        if ch.is_free:
            item["is_unlocked"] = True
        elif user and (
            user.subscription_tier != "free"
            and user.subscription_expires_at
            and user.subscription_expires_at > __import__("datetime").datetime.now(__import__("datetime").timezone.utc)
        ):
            item["is_unlocked"] = True
        else:
            item["is_unlocked"] = False
        items.append(item)
    return ok(items)


@router.get("/{story_id}/related")
def related(
    story_id: UUID,
    limit: int = Query(10, ge=1, le=20),
    db: Session = Depends(get_db),
):
    story = story_service.get_published(db, story_id)
    return ok([_summary(s) for s in story_service.related(db, story, limit=limit)])


@router.post("/{story_id}/like")
def like(
    story_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    story = story_service.get_published(db, story_id)
    existing = db.scalar(
        select(StoryLike).where(StoryLike.user_id == user.id, StoryLike.story_id == story.id)
    )
    if existing:
        db.delete(existing)
        story.like_count = max(0, (story.like_count or 0) - 1)
        liked = False
    else:
        db.add(StoryLike(user_id=user.id, story_id=story.id))
        story.like_count = (story.like_count or 0) + 1
        liked = True
    db.commit()
    return ok({"liked": liked, "like_count": story.like_count})


@router.post("/{story_id}/bookmark")
def bookmark(
    story_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    story = story_service.get_published(db, story_id)
    existing = db.scalar(
        select(Bookmark).where(Bookmark.user_id == user.id, Bookmark.story_id == story.id)
    )
    if existing:
        return ok({"bookmarked": True})
    db.add(Bookmark(user_id=user.id, story_id=story.id))
    story.bookmark_count = (story.bookmark_count or 0) + 1
    db.commit()
    return ok({"bookmarked": True})


@router.delete("/{story_id}/bookmark")
def unbookmark(
    story_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    res = db.execute(
        delete(Bookmark).where(Bookmark.user_id == user.id, Bookmark.story_id == story_id)
    )
    if res.rowcount:
        story = db.get(Story, story_id)
        if story:
            story.bookmark_count = max(0, (story.bookmark_count or 0) - 1)
    db.commit()
    return ok({"bookmarked": False})


@router.post("/{story_id}/rate")
def rate(
    story_id: UUID,
    body: RatingRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    story = story_service.get_published(db, story_id)
    existing = db.scalar(
        select(StoryRating).where(StoryRating.user_id == user.id, StoryRating.story_id == story.id)
    )
    if existing:
        old = existing.rating
        existing.rating = body.rating
        existing.review = body.review
        new_avg = (
            float(story.avg_rating) * story.total_ratings - old + body.rating
        ) / max(1, story.total_ratings)
        story.avg_rating = round(new_avg, 2)
    else:
        db.add(StoryRating(user_id=user.id, story_id=story.id, rating=body.rating, review=body.review))
        new_total = story.total_ratings + 1
        story.avg_rating = round(
            (float(story.avg_rating) * story.total_ratings + body.rating) / new_total, 2
        )
        story.total_ratings = new_total
    db.commit()
    return ok({"avg_rating": float(story.avg_rating), "total_ratings": story.total_ratings})


@router.get("/{story_id}/comments")
def story_comments(story_id: UUID, db: Session = Depends(get_db)):
    return ok([])
