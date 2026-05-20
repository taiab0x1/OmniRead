from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, File, Query, Request, UploadFile
from sqlalchemy import desc, func, select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, ConflictError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, Chapter, Story
from app.schemas.admin import (
    AdminStoryItem,
    ScheduleStoryRequest,
    StoryCreate,
    StoryReorderRequest,
    StoryUpdate,
)
from app.schemas.common import Meta, ok
from app.services import audit_service, storage_service
from app.utils.slug import slugify, unique_slug

router = APIRouter()


def _slug_taken(db: Session, slug: str) -> bool:
    return db.scalar(select(Story).where(Story.slug == slug)) is not None


@router.get("")
def list_stories(
    status: str | None = None,
    genre: str | None = None,
    is_featured: bool | None = None,
    q: str | None = None,
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "moderator")),
):
    stmt = select(Story)
    if status:
        statuses = [s.strip() for s in status.split(",") if s.strip()]
        if len(statuses) > 1:
            stmt = stmt.where(Story.status.in_(statuses))
        else:
            stmt = stmt.where(Story.status == status)
    if genre:
        stmt = stmt.where(Story.genre == genre)
    if is_featured is not None:
        stmt = stmt.where(Story.is_featured.is_(is_featured))
    if q:
        stmt = stmt.where(Story.title.ilike(f"%{q}%"))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = list(
        db.scalars(
            stmt.order_by(desc(Story.created_at)).offset((page - 1) * per_page).limit(per_page)
        )
    )
    items = [AdminStoryItem.model_validate(r, from_attributes=True).model_dump() for r in rows]
    return ok(items, meta=Meta(page=page, per_page=per_page, total=total))


@router.post("")
def create_story(
    body: StoryCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    slug = unique_slug(slugify(body.title), lambda s: _slug_taken(db, s))
    story = Story(
        title=body.title,
        slug=slug,
        author_name=body.author_name,
        cover_url=body.cover_url,
        summary=body.summary,
        hook_line=body.hook_line,
        genre=body.genre,
        tags=body.tags or [],
        tone=body.tone,
        age_rating=body.age_rating,
        free_chapters=body.free_chapters,
        is_premium=body.is_premium,
        language=body.language,
        ai_generated=False,
        status="draft",
    )
    db.add(story)
    db.flush()
    audit_service.log(
        db,
        admin=admin,
        action="story.create",
        target_type="story",
        target_id=str(story.id),
        metadata={"title": body.title},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok(AdminStoryItem.model_validate(story, from_attributes=True).model_dump())


@router.get("/{story_id}")
def get_story(
    story_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "moderator", "analytics")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    chapter_count = db.scalar(select(func.count(Chapter.id)).where(Chapter.story_id == story.id))
    payload = AdminStoryItem.model_validate(story, from_attributes=True).model_dump()
    payload["summary"] = story.summary
    payload["hook_line"] = story.hook_line
    payload["tags"] = story.tags
    payload["chapter_count_total"] = chapter_count
    return ok(payload)


@router.put("/{story_id}")
def update_story(
    story_id: UUID,
    body: StoryUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    changes = body.model_dump(exclude_none=True)
    for k, v in changes.items():
        setattr(story, k, v)
    audit_service.log(
        db,
        admin=admin,
        action="story.update",
        target_type="story",
        target_id=str(story.id),
        metadata=changes,
        ip=get_client_ip(request),
    )
    db.commit()
    return ok(AdminStoryItem.model_validate(story, from_attributes=True).model_dump())


@router.post("/{story_id}/publish")
def publish_story(
    story_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    chapter_count = db.scalar(
        select(func.count(Chapter.id)).where(
            Chapter.story_id == story.id, Chapter.status == "published"
        )
    )
    if chapter_count == 0:
        raise ConflictError("Cannot publish: no published chapters", code="no_published_chapters")
    story.status = "published"
    if not story.published_at:
        story.published_at = datetime.now(timezone.utc)
    story.scheduled_at = None
    story.total_chapters = chapter_count
    audit_service.log(
        db,
        admin=admin,
        action="story.publish",
        target_type="story",
        target_id=str(story.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"status": "published", "published_at": story.published_at})


@router.post("/{story_id}/schedule")
def schedule_story(
    story_id: UUID,
    body: ScheduleStoryRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    chapter_count = db.scalar(
        select(func.count(Chapter.id)).where(
            Chapter.story_id == story.id, Chapter.status == "published"
        )
    )
    if chapter_count == 0:
        raise ConflictError("Cannot schedule: no published chapters", code="no_published_chapters")
    if story.status == "published":
        raise ConflictError("Story is already published", code="already_published")

    story.status = "scheduled"
    story.scheduled_at = body.scheduled_at
    story.published_at = None
    story.total_chapters = chapter_count
    audit_service.log(
        db,
        admin=admin,
        action="story.schedule",
        target_type="story",
        target_id=str(story.id),
        metadata={"scheduled_at": body.scheduled_at.isoformat()},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"status": "scheduled", "scheduled_at": story.scheduled_at})


@router.post("/{story_id}/unpublish")
def unpublish_story(
    story_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    story.status = "draft"
    story.scheduled_at = None
    story.published_at = None
    audit_service.log(
        db, admin=admin, action="story.unpublish", target_type="story", target_id=str(story.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"status": "draft"})


@router.delete("/{story_id}")
def delete_story(
    story_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    story = db.get(Story, story_id)
    if not story:
        raise NotFoundError()
    db.delete(story)
    audit_service.log(
        db, admin=admin, action="story.delete", target_type="story", target_id=str(story_id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"deleted": True})


@router.post("/upload-cover")
async def upload_cover(
    request: Request,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    """Upload a cover image. Returns the public URL — the caller then PUTs
    this URL into a story's `cover_url` via the update endpoint."""
    if file.content_type is None:
        raise BadRequestError("Missing content type")
    body = await file.read()
    try:
        url = storage_service.upload_cover(
            __import__("io").BytesIO(body),
            mime=file.content_type,
            size=len(body),
        )
    except ValueError as e:
        raise BadRequestError(str(e))
    audit_service.log(
        db, admin=admin, action="storage.upload_cover", target_type="cover",
        target_id=url[:64], metadata={"mime": file.content_type, "size": len(body)},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"url": url})


@router.post("/reorder")
def reorder_stories(
    body: StoryReorderRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    """Set display_order for a list of stories. Index in the list becomes
    the new display_order (0 = highest priority)."""
    stories = list(db.scalars(select(Story).where(Story.id.in_(body.order))))
    by_id = {s.id: s for s in stories}
    missing = [sid for sid in body.order if sid not in by_id]
    if missing:
        raise NotFoundError(f"Stories not found: {missing[:3]}")
    for idx, sid in enumerate(body.order):
        by_id[sid].display_order = idx
    audit_service.log(
        db, admin=admin, action="story.reorder", target_type="story",
        target_id=str(body.order[0]), metadata={"count": len(body.order)},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"reordered": len(body.order)})
