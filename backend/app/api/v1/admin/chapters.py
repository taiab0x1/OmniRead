from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import desc, select, update
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, Chapter, Story
from app.schemas.admin import BulkChapterPublishRequest, ChapterCreate, ChapterUpdate
from app.schemas.common import ok
from app.services import audit_service

router = APIRouter()


@router.get("")
def list_chapters(
    story_id: UUID | None = None,
    status: str | None = None,
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "moderator")),
):
    stmt = select(Chapter)
    if story_id:
        stmt = stmt.where(Chapter.story_id == story_id)
    if status:
        stmt = stmt.where(Chapter.status == status)
    stmt = stmt.order_by(Chapter.story_id, Chapter.chapter_number).limit(limit)
    rows = list(db.scalars(stmt))
    return ok(
        [
            {
                "id": str(c.id),
                "story_id": str(c.story_id),
                "chapter_number": c.chapter_number,
                "title": c.title,
                "is_free": c.is_free,
                "coin_cost": c.coin_cost,
                "status": c.status,
                "word_count": c.word_count,
                "has_cliffhanger": c.has_cliffhanger,
                "published_at": c.published_at,
            }
            for c in rows
        ]
    )


@router.post("")
def create_chapter(
    body: ChapterCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, body.story_id)
    if not story:
        raise NotFoundError("Story not found")
    existing = db.scalar(
        select(Chapter).where(
            Chapter.story_id == body.story_id, Chapter.chapter_number == body.chapter_number
        )
    )
    if existing:
        raise ConflictError("Chapter number already exists", code="chapter_exists")
    chapter = Chapter(
        story_id=body.story_id,
        chapter_number=body.chapter_number,
        title=body.title,
        content=body.content,
        word_count=len(body.content.split()),
        is_free=body.is_free,
        coin_cost=body.coin_cost,
        has_cliffhanger=body.has_cliffhanger,
        cliffhanger_preview=body.cliffhanger_preview,
        status="draft",
    )
    db.add(chapter)
    audit_service.log(
        db, admin=admin, action="chapter.create", target_type="chapter", target_id=str(chapter.id),
        metadata={"story_id": str(body.story_id), "n": body.chapter_number}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"id": str(chapter.id)})


@router.get("/{chapter_id}")
def get_chapter(
    chapter_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "moderator")),
):
    ch = db.get(Chapter, chapter_id)
    if not ch:
        raise NotFoundError()
    return ok(
        {
            "id": str(ch.id),
            "story_id": str(ch.story_id),
            "chapter_number": ch.chapter_number,
            "title": ch.title,
            "content": ch.content,
            "word_count": ch.word_count,
            "is_free": ch.is_free,
            "coin_cost": ch.coin_cost,
            "has_cliffhanger": ch.has_cliffhanger,
            "cliffhanger_preview": ch.cliffhanger_preview,
            "status": ch.status,
            "published_at": ch.published_at,
        }
    )


@router.put("/{chapter_id}")
def update_chapter(
    chapter_id: UUID,
    body: ChapterUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    ch = db.get(Chapter, chapter_id)
    if not ch:
        raise NotFoundError()
    changes = body.model_dump(exclude_none=True)
    if "content" in changes:
        ch.word_count = len(changes["content"].split())
    for k, v in changes.items():
        setattr(ch, k, v)
    audit_service.log(
        db, admin=admin, action="chapter.update", target_type="chapter", target_id=str(ch.id),
        metadata={k: v for k, v in changes.items() if k != "content"}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"updated": True})


@router.post("/{chapter_id}/publish")
def publish_chapter(
    chapter_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    ch = db.get(Chapter, chapter_id)
    if not ch:
        raise NotFoundError()
    ch.status = "published"
    ch.published_at = ch.published_at or datetime.now(timezone.utc)
    audit_service.log(
        db, admin=admin, action="chapter.publish", target_type="chapter", target_id=str(ch.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"status": "published"})


@router.delete("/{chapter_id}")
def delete_chapter(
    chapter_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    ch = db.get(Chapter, chapter_id)
    if not ch:
        raise NotFoundError()
    db.delete(ch)
    audit_service.log(
        db, admin=admin, action="chapter.delete", target_type="chapter", target_id=str(chapter_id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"deleted": True})


@router.post("/bulk-publish")
def bulk_publish(
    body: BulkChapterPublishRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    """Publish all draft chapters for a story (or a specific subset).

    Returns the number of chapters affected. Chapters already published are
    not re-touched.
    """
    story = db.get(Story, body.story_id)
    if not story:
        raise NotFoundError("Story not found")

    stmt = select(Chapter).where(
        Chapter.story_id == body.story_id, Chapter.status == "draft"
    )
    if body.chapter_ids:
        stmt = stmt.where(Chapter.id.in_(body.chapter_ids))
    drafts = list(db.scalars(stmt))
    now = datetime.now(timezone.utc)
    for ch in drafts:
        ch.status = "published"
        ch.published_at = ch.published_at or now

    # Refresh story's total_chapters
    if drafts:
        story.total_chapters = db.scalar(
            select(__import__("sqlalchemy").func.count(Chapter.id)).where(
                Chapter.story_id == body.story_id, Chapter.status == "published"
            )
        ) or 0

    audit_service.log(
        db, admin=admin, action="chapter.bulk_publish", target_type="story",
        target_id=str(body.story_id), metadata={"count": len(drafts)},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"published": len(drafts)})
