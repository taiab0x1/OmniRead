from datetime import datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import and_, select

from app.celery_app import celery
from app.db.session import session_scope
from app.models import AIGenerationJob, Chapter, RefreshToken, Story, User
from app.services import ai_service


@celery.task(name="app.workers.ai_tasks.generate_story", bind=True, max_retries=3)
def generate_story(self, job_id: str, params: dict, model: str | None = None, provider: str | None = None):
    with session_scope() as db:
        job = db.get(AIGenerationJob, UUID(job_id))
        if not job:
            return
        job.status = "running"
        job.started_at = datetime.now(timezone.utc)
        db.flush()
        try:
            outline = ai_service.generate_story_outline(db, job=job, params=params, model=model, provider_name=provider)
            story = ai_service.materialize_outline(db, outline=outline, params=params)
            job.story_id = story.id
            job.status = "succeeded"
            job.output = {"story_id": str(story.id), "outline": outline}
        except Exception as exc:
            job.status = "failed"
            job.error = str(exc)
            db.flush()
            raise self.retry(exc=exc, countdown=2 ** self.request.retries * 5)
        finally:
            job.completed_at = datetime.now(timezone.utc)


@celery.task(name="app.workers.ai_tasks.generate_chapter", bind=True, max_retries=3)
def generate_chapter(self, job_id: str, params: dict, model: str | None = None, provider: str | None = None):
    with session_scope() as db:
        job = db.get(AIGenerationJob, UUID(job_id))
        if not job:
            return
        job.status = "running"
        job.started_at = datetime.now(timezone.utc)
        db.flush()
        try:
            story = db.get(Story, job.story_id) if job.story_id else None
            if not story:
                raise RuntimeError("Story not found for chapter generation")
            chapter = db.scalar(
                select(Chapter).where(
                    Chapter.story_id == story.id,
                    Chapter.chapter_number == int(params["chapter_number"]),
                )
            )
            if not chapter:
                raise RuntimeError("Chapter row not found")
            previous = None
            if chapter.chapter_number > 1:
                prev = db.scalar(
                    select(Chapter).where(
                        Chapter.story_id == story.id,
                        Chapter.chapter_number == chapter.chapter_number - 1,
                    )
                )
                previous = (prev.cliffhanger_preview or prev.content[:600]) if prev else None
            res = ai_service.generate_chapter_text(
                title=story.title,
                summary=story.summary or "",
                characters=params.get("characters"),
                previous_summary=previous,
                this_synopsis=chapter.cliffhanger_preview or "",
                chapter_number=chapter.chapter_number,
                word_count=int(params.get("word_count_target", 750)),
                cliffhanger_type=params.get("cliffhanger_type", "emotional"),
                notes=params.get("notes"),
                genre=story.genre,
                model=model,
                provider_name=provider,
            )
            chapter.content = res.text.strip()
            chapter.word_count = len(chapter.content.split())
            job.chapter_id = chapter.id
            job.provider = res.provider
            job.model = res.model
            job.tokens_in = res.tokens_in
            job.tokens_out = res.tokens_out
            job.status = "succeeded"
            job.output = {"chapter_id": str(chapter.id)}
        except Exception as exc:
            job.status = "failed"
            job.error = str(exc)
            db.flush()
            raise self.retry(exc=exc, countdown=2 ** self.request.retries * 5)
        finally:
            job.completed_at = datetime.now(timezone.utc)
