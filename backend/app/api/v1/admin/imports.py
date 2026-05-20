"""Bulk content import: stories + chapters from JSON.

Supports a dry-run mode that validates rows without writing to the DB.
The admin can review the report, then re-submit with dry_run=false to commit.

CSV is accepted via a converter helper on the admin side (parses CSV to the
same JSON shape and POSTs it here).
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, Chapter, Story
from app.schemas.admin import ContentImportRequest
from app.schemas.common import ok
from app.services import audit_service
from app.utils.slug import slugify, unique_slug

router = APIRouter()


@router.post("/import")
def import_content(
    body: ContentImportRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("editor", "super_admin")),
):
    errors: list[dict] = []
    summary = {"stories_new": 0, "chapters_new": 0, "stories_existing": 0}

    # Map of slug → Story object (either to-be-created during this import or
    # already existing in DB). Lets chapters reference their story.
    new_stories: dict[str, Story] = {}

    def _slug_taken(slug: str) -> bool:
        if slug in new_stories:
            return True
        return db.scalar(select(Story).where(Story.slug == slug)) is not None

    for idx, row in enumerate(body.rows):
        try:
            if row.kind == "story":
                if not row.title or not row.genre:
                    raise ValueError("story rows require title and genre")
                base_slug = slugify(row.title)
                slug = unique_slug(base_slug, _slug_taken)
                story = Story(
                    title=row.title,
                    slug=slug,
                    genre=row.genre,
                    summary=row.summary,
                    hook_line=row.hook_line,
                    tags=row.tags or [],
                    is_premium=bool(row.is_premium),
                    free_chapters=row.free_chapters if row.free_chapters is not None else 3,
                    status="draft",
                    ai_generated=False,
                )
                new_stories[slug] = story
                if not body.dry_run:
                    db.add(story)
                    db.flush()
                summary["stories_new"] += 1

            elif row.kind == "chapter":
                if not row.story_slug or row.chapter_number is None or row.content is None:
                    raise ValueError(
                        "chapter rows require story_slug, chapter_number, content"
                    )
                # Resolve target story
                target = new_stories.get(row.story_slug)
                if target is None:
                    target = db.scalar(select(Story).where(Story.slug == row.story_slug))
                    if target is None:
                        raise ValueError(f"unknown story_slug: {row.story_slug}")
                    summary["stories_existing"] += 1

                # Check for chapter-number collision in DB (skip when dry-run
                # on a story we just created — story has no chapters yet)
                if target.id and not body.dry_run:
                    collision = db.scalar(
                        select(Chapter).where(
                            Chapter.story_id == target.id,
                            Chapter.chapter_number == row.chapter_number,
                        )
                    )
                    if collision:
                        raise ValueError(
                            f"chapter {row.chapter_number} already exists for {row.story_slug}"
                        )
                if not body.dry_run:
                    chapter = Chapter(
                        story_id=target.id,
                        chapter_number=row.chapter_number,
                        title=row.title,
                        content=row.content,
                        word_count=len(row.content.split()),
                        is_free=bool(row.is_free),
                        coin_cost=row.coin_cost if row.coin_cost is not None else 5,
                        status="draft",
                    )
                    db.add(chapter)
                summary["chapters_new"] += 1
        except ValueError as e:
            errors.append({"row": idx, "kind": row.kind, "error": str(e)})

    if body.dry_run:
        # Nothing to commit — rollback any flushed state
        db.rollback()
    else:
        if errors:
            db.rollback()
            raise BadRequestError(
                "Import has errors — fix and retry (dry_run recommended)",
                code="import_errors",
            )
        audit_service.log(
            db, admin=admin, action="content.import", target_type="bulk",
            target_id="import", metadata=summary, ip=get_client_ip(request),
        )
        db.commit()

    return ok({"dry_run": body.dry_run, "summary": summary, "errors": errors})
