from typing import Iterable
from uuid import UUID

from sqlalchemy import and_, desc, func, or_, select
from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.models import Bookmark, Chapter, Story, StoryLike
from app.utils.cursor import decode_cursor, encode_cursor, parse_cursor_dt


def get_published(db: Session, story_id: UUID) -> Story:
    story = db.scalar(
        select(Story).where(Story.id == story_id, Story.status == "published")
    )
    if not story:
        raise NotFoundError("Story not found")
    return story


def get_published_by_slug(db: Session, slug: str) -> Story:
    story = db.scalar(select(Story).where(Story.slug == slug, Story.status == "published"))
    if not story:
        raise NotFoundError("Story not found")
    return story


def feed_page(
    db: Session,
    *,
    cursor: str | None,
    limit: int,
    genre: str | None = None,
    user_id: UUID | None = None,
) -> tuple[list[Story], str | None]:
    cur = decode_cursor(cursor) or {}
    cur_dt = parse_cursor_dt(cur.get("p"))
    cur_id = cur.get("i")

    stmt = select(Story).where(Story.status == "published")
    if genre:
        stmt = stmt.where(Story.genre == genre)
    if cur_dt and cur_id:
        stmt = stmt.where(
            or_(
                Story.published_at < cur_dt,
                and_(Story.published_at == cur_dt, Story.id < cur_id),
            )
        )
    stmt = stmt.order_by(desc(Story.published_at), desc(Story.id)).limit(limit + 1)

    rows = list(db.scalars(stmt))
    next_cursor = None
    if len(rows) > limit:
        last = rows[limit - 1]
        rows = rows[:limit]
        next_cursor = encode_cursor({"p": last.published_at.isoformat() if last.published_at else None, "i": str(last.id)})
    return rows, next_cursor


def trending(db: Session, *, limit: int = 20, genre: str | None = None) -> list[Story]:
    stmt = select(Story).where(Story.status == "published", Story.is_trending.is_(True))
    if genre:
        stmt = stmt.where(Story.genre == genre)
    stmt = stmt.order_by(desc(Story.view_count)).limit(limit)
    return list(db.scalars(stmt))


def newest(db: Session, *, limit: int = 20, genre: str | None = None) -> list[Story]:
    stmt = select(Story).where(Story.status == "published")
    if genre:
        stmt = stmt.where(Story.genre == genre)
    stmt = stmt.order_by(desc(Story.published_at)).limit(limit)
    return list(db.scalars(stmt))


def recommended(db: Session, *, user_genres: list[str] | None, limit: int = 20) -> list[Story]:
    stmt = select(Story).where(Story.status == "published")
    if user_genres:
        stmt = stmt.where(Story.genre.in_(user_genres))
    stmt = stmt.order_by(desc(Story.like_count), desc(Story.published_at)).limit(limit)
    return list(db.scalars(stmt))


def search(
    db: Session,
    *,
    q: str | None,
    genre: str | None,
    tags: list[str] | None,
    cursor: str | None,
    limit: int,
) -> tuple[list[Story], str | None]:
    cur = decode_cursor(cursor) or {}
    offset = int(cur.get("o", 0))

    stmt = select(Story).where(Story.status == "published")
    if q:
        ts = func.plainto_tsquery("english", q)
        stmt = stmt.where(Story.search_vector.op("@@")(ts)).order_by(desc(func.ts_rank(Story.search_vector, ts)))
    else:
        stmt = stmt.order_by(desc(Story.published_at))
    if genre:
        stmt = stmt.where(Story.genre == genre)
    if tags:
        stmt = stmt.where(Story.tags.op("&&")(tags))

    rows = list(db.scalars(stmt.offset(offset).limit(limit + 1)))
    next_cursor = None
    if len(rows) > limit:
        rows = rows[:limit]
        next_cursor = encode_cursor({"o": offset + limit})
    return rows, next_cursor


def chapters_for_story(db: Session, story_id: UUID) -> list[Chapter]:
    stmt = (
        select(Chapter)
        .where(Chapter.story_id == story_id, Chapter.status == "published")
        .order_by(Chapter.chapter_number)
    )
    return list(db.scalars(stmt))


def is_bookmarked(db: Session, user_id: UUID | None, story_id: UUID) -> bool:
    if not user_id:
        return False
    return db.scalar(
        select(func.count()).select_from(Bookmark).where(Bookmark.user_id == user_id, Bookmark.story_id == story_id)
    ) > 0


def is_liked(db: Session, user_id: UUID | None, story_id: UUID) -> bool:
    if not user_id:
        return False
    return db.scalar(
        select(func.count()).select_from(StoryLike).where(StoryLike.user_id == user_id, StoryLike.story_id == story_id)
    ) > 0


def increment_view(db: Session, story: Story) -> None:
    story.view_count = (story.view_count or 0) + 1
    db.flush()


def related(db: Session, story: Story, limit: int = 10) -> list[Story]:
    stmt = (
        select(Story)
        .where(
            Story.status == "published",
            Story.id != story.id,
            Story.genre == story.genre,
        )
        .order_by(desc(Story.like_count))
        .limit(limit)
    )
    return list(db.scalars(stmt))
