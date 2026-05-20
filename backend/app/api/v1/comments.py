from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, NotFoundError
from app.core.rate_limit import fixed_window
from app.db.session import get_db
from app.dependencies import get_current_user
from app.models import AdminUser, Chapter, Comment, User
from app.schemas.common import Meta, ok
from app.schemas.story import CommentCreate, CommentItem
from app.services import chapter_service

router = APIRouter()


@router.get("/{chapter_id}/comments")
def list_comments(
    chapter_id: UUID,
    cursor: str | None = None,
    limit: int = Query(20, ge=1, le=50),
    db: Session = Depends(get_db),
):
    chapter = db.get(Chapter, chapter_id)
    if not chapter or chapter.status != "published":
        raise NotFoundError("Chapter not found")

    stmt = (
        select(Comment)
        .where(
            Comment.chapter_id == chapter_id,
            Comment.is_hidden.is_(False),
            Comment.moderation_status == "approved",
        )
        .order_by(desc(Comment.created_at))
        .limit(limit + 1)
    )
    rows = list(db.scalars(stmt))
    next_cursor = None
    items = []

    user_ids = [c.user_id for c in rows[:limit] if c.user_id]
    admin_ids = [c.admin_id for c in rows[:limit] if c.admin_id]
    user_names = {}
    admin_names = {}
    if user_ids:
        for uid, uname in db.execute(
            select(User.id, User.username).where(User.id.in_(user_ids))
        ):
            user_names[uid] = uname
    if admin_ids:
        for aid, aname in db.execute(
            select(AdminUser.id, AdminUser.name).where(AdminUser.id.in_(admin_ids))
        ):
            admin_names[aid] = aname

    for comment in rows[:limit]:
        item = CommentItem.model_validate(comment, from_attributes=True).model_dump()
        if comment.admin_id:
            item["username"] = admin_names.get(comment.admin_id) or "Author"
            item["is_admin"] = True
        else:
            item["username"] = user_names.get(comment.user_id)
            item["is_admin"] = False
        items.append(item)
    return ok(items, meta=Meta(per_page=limit, next_cursor=next_cursor))


@router.post("/{chapter_id}/comments")
def create_comment(
    chapter_id: UUID,
    body: CommentCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if user.is_guest:
        raise BadRequestError("Guests cannot post comments", code="guest_forbidden")

    fixed_window(f"rl:comment:{user.id}", limit=10, period_seconds=60)
    fixed_window(f"rl:comment-day:{user.id}", limit=200, period_seconds=86400)

    chapter = db.get(Chapter, chapter_id)
    if not chapter or chapter.status != "published":
        raise NotFoundError("Chapter not found")
    if not chapter_service.is_unlocked(db, user, chapter):
        raise BadRequestError("Chapter not unlocked", code="chapter_locked")
    if body.parent_id:
        parent = db.get(Comment, body.parent_id)
        if not parent or parent.chapter_id != chapter_id:
            raise BadRequestError("Invalid parent comment")

    comment = Comment(
        user_id=user.id,
        chapter_id=chapter_id,
        parent_id=body.parent_id,
        content=body.content.strip(),
        is_spoiler=body.is_spoiler,
    )
    db.add(comment)
    db.commit()
    payload = CommentItem.model_validate(comment, from_attributes=True).model_dump()
    payload["username"] = user.username
    payload["is_admin"] = False
    return ok(payload)
