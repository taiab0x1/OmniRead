from uuid import UUID

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError
from app.db.session import get_db
from app.dependencies import get_current_user, get_idempotency_key, get_optional_user
from app.models import User
from app.schemas.common import ok
from app.schemas.payment import UnlockResponse
from app.schemas.story import ChapterContent, ChapterPreview, ProgressUpdate
from app.services import chapter_service

router = APIRouter()


@router.get("/{chapter_id}")
def get_chapter(
    chapter_id: UUID,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
):
    chapter, unlocked, next_id, prev_id = chapter_service.get_chapter_for_user(
        db, chapter_id=chapter_id, user=user
    )
    if not unlocked:
        return ok(
            ChapterPreview(
                id=chapter.id,
                story_id=chapter.story_id,
                chapter_number=chapter.chapter_number,
                title=chapter.title,
                preview=chapter_service.preview_text(chapter.content, words=100),
                is_free=chapter.is_free,
                coin_cost=chapter.coin_cost,
                is_unlocked=False,
            ).model_dump()
        )
    payload = ChapterContent.model_validate(chapter, from_attributes=True).model_dump()
    payload["is_unlocked"] = True
    payload["next_chapter_id"] = next_id
    payload["prev_chapter_id"] = prev_id
    return ok(payload)


@router.post("/{chapter_id}/unlock")
def unlock_with_coins(
    chapter_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    idempotency_key: str | None = Depends(get_idempotency_key),
):
    chapter, new_balance = chapter_service.unlock_with_coins(
        db, user=user, chapter_id=chapter_id, idempotency_key=idempotency_key
    )
    db.commit()
    return ok(
        UnlockResponse(
            chapter_id=chapter.id,
            unlock_method="coins",
            coins_spent=chapter.coin_cost,
            new_balance=new_balance,
        ).model_dump()
    )


@router.post("/{chapter_id}/progress")
def save_progress(
    chapter_id: UUID,
    body: ProgressUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if chapter_id != body.chapter_id:
        raise BadRequestError("Path and body chapter_id must match")
    chapter, unlocked, _, _ = chapter_service.get_chapter_for_user(
        db, chapter_id=chapter_id, user=user
    )
    if not unlocked:
        raise BadRequestError("Chapter not unlocked", code="chapter_locked")
    prog = chapter_service.upsert_progress(
        db,
        user=user,
        story_id=chapter.story_id,
        chapter_id=chapter.id,
        scroll_position=body.scroll_position,
        completed=body.completed,
    )
    db.commit()
    return ok({"saved_at": prog.last_read_at, "completed": prog.completed})
