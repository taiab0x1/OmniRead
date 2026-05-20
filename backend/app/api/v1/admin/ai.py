from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, AIGenerationJob, Chapter, Story
from app.schemas.admin import GenerateChapterRequest, GenerateStoryRequest
from app.schemas.common import ok
from app.services import audit_service
from app.workers import ai_tasks

router = APIRouter()


@router.post("/generate-story")
def generate_story(
    body: GenerateStoryRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    job = AIGenerationJob(
        job_type="story_outline",
        status="pending",
        input_params=body.model_dump(),
        requested_by=admin.id,
    )
    db.add(job)
    db.flush()
    job_id = job.id
    audit_service.log(
        db, admin=admin, action="ai.generate_story", target_type="ai_job", target_id=str(job_id),
        metadata={"genre": body.genre}, ip=get_client_ip(request),
    )
    db.commit()
    ai_tasks.generate_story.delay(str(job_id), body.model_dump(), body.model, None)
    return ok({"job_id": str(job_id), "status": "pending"})


@router.post("/generate-chapter")
def generate_chapter(
    body: GenerateChapterRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    story = db.get(Story, body.story_id)
    if not story:
        raise NotFoundError("Story not found")
    chapter = db.scalar(
        select(Chapter).where(
            Chapter.story_id == body.story_id, Chapter.chapter_number == body.chapter_number
        )
    )
    if not chapter:
        chapter = Chapter(
            story_id=body.story_id,
            chapter_number=body.chapter_number,
            content="[PENDING GENERATION]",
            status="draft",
            is_free=body.chapter_number <= story.free_chapters,
        )
        db.add(chapter)
        db.flush()

    job = AIGenerationJob(
        job_type="chapter",
        status="pending",
        story_id=story.id,
        chapter_id=chapter.id,
        input_params=body.model_dump(),
        requested_by=admin.id,
    )
    db.add(job)
    db.flush()
    job_id = job.id
    db.commit()
    ai_tasks.generate_chapter.delay(str(job_id), body.model_dump(), body.model, None)
    return ok({"job_id": str(job_id), "chapter_id": str(chapter.id)})


@router.get("/jobs")
def list_jobs(
    status: str | None = None,
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "analytics")),
):
    stmt = select(AIGenerationJob)
    if status:
        stmt = stmt.where(AIGenerationJob.status == status)
    rows = list(db.scalars(stmt.order_by(desc(AIGenerationJob.created_at)).limit(limit)))
    return ok(
        [
            {
                "id": str(j.id),
                "type": j.job_type,
                "status": j.status,
                "story_id": str(j.story_id) if j.story_id else None,
                "chapter_id": str(j.chapter_id) if j.chapter_id else None,
                "model": j.model,
                "tokens_in": j.tokens_in,
                "tokens_out": j.tokens_out,
                "cost_usd": float(j.cost_usd) if j.cost_usd else None,
                "error": j.error,
                "started_at": j.started_at,
                "completed_at": j.completed_at,
                "created_at": j.created_at,
            }
            for j in rows
        ]
    )


@router.post("/jobs/{job_id}/retry")
def retry_job(
    job_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    job = db.get(AIGenerationJob, job_id)
    if not job:
        raise NotFoundError()
    if job.status not in ("failed",):
        raise BadRequestError("Only failed jobs can be retried", code="not_retryable")
    job.status = "pending"
    job.error = None
    job.started_at = None
    job.completed_at = None
    db.commit()
    if job.job_type == "story_outline":
        ai_tasks.generate_story.delay(str(job.id), job.input_params or {}, job.model, None)
    elif job.job_type == "chapter":
        ai_tasks.generate_chapter.delay(str(job.id), job.input_params or {}, job.model, None)
    return ok({"job_id": str(job.id), "status": "pending"})
def get_job(
    job_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin", "analytics")),
):
    job = db.get(AIGenerationJob, job_id)
    if not job:
        raise NotFoundError()
    return ok(
        {
            "id": str(job.id),
            "type": job.job_type,
            "status": job.status,
            "input_params": job.input_params,
            "output": job.output,
            "error": job.error,
            "tokens_in": job.tokens_in,
            "tokens_out": job.tokens_out,
            "cost_usd": float(job.cost_usd) if job.cost_usd else None,
            "started_at": job.started_at,
            "completed_at": job.completed_at,
        }
    )
