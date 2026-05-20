from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, Chapter, Comment, ContentReport
from app.schemas.admin import AdminReplyRequest, ReportResolveRequest
from app.schemas.common import ok
from app.services import audit_service

router = APIRouter()


@router.get("/comments")
def comment_queue(
    moderation_status: str = "approved",
    is_hidden: bool | None = None,
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    stmt = select(Comment).where(Comment.moderation_status == moderation_status)
    if is_hidden is not None:
        stmt = stmt.where(Comment.is_hidden.is_(is_hidden))
    rows = list(db.scalars(stmt.order_by(desc(Comment.created_at)).limit(limit)))
    return ok(
        [
            {
                "id": str(c.id),
                "user_id": str(c.user_id) if c.user_id else None,
                "admin_id": str(c.admin_id) if c.admin_id else None,
                "chapter_id": str(c.chapter_id),
                "content": c.content,
                "is_hidden": c.is_hidden,
                "is_spoiler": c.is_spoiler,
                "moderation_status": c.moderation_status,
                "created_at": c.created_at,
            }
            for c in rows
        ]
    )


@router.put("/comments/{comment_id}/hide")
def hide_comment(
    comment_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    c = db.get(Comment, comment_id)
    if not c:
        raise NotFoundError()
    c.is_hidden = True
    c.moderation_status = "rejected"
    audit_service.log(
        db, admin=admin, action="comment.hide", target_type="comment", target_id=str(c.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"hidden": True})


@router.put("/comments/{comment_id}/restore")
def restore_comment(
    comment_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    c = db.get(Comment, comment_id)
    if not c:
        raise NotFoundError()
    c.is_hidden = False
    c.moderation_status = "approved"
    audit_service.log(
        db, admin=admin, action="comment.restore", target_type="comment", target_id=str(c.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"hidden": False})


@router.get("/reports")
def list_reports(
    status: str = "pending",
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    rows = list(
        db.scalars(
            select(ContentReport)
            .where(ContentReport.status == status)
            .order_by(desc(ContentReport.created_at))
            .limit(limit)
        )
    )
    return ok(
        [
            {
                "id": str(r.id),
                "reporter_id": str(r.reporter_id) if r.reporter_id else None,
                "target_type": r.target_type,
                "target_id": str(r.target_id),
                "reason": r.reason,
                "notes": r.notes,
                "status": r.status,
                "created_at": r.created_at,
            }
            for r in rows
        ]
    )


@router.put("/reports/{report_id}/resolve")
def resolve_report(
    report_id: UUID,
    body: ReportResolveRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    rep = db.get(ContentReport, report_id)
    if not rep:
        raise NotFoundError()
    rep.status = body.action
    rep.resolved_by = admin.id
    rep.resolved_at = datetime.now(timezone.utc)
    if body.notes:
        rep.notes = (rep.notes or "") + "\n[admin] " + body.notes
    audit_service.log(
        db, admin=admin, action="report.resolve", target_type="report", target_id=str(rep.id),
        metadata={"action": body.action}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"status": rep.status})


@router.post("/comments/reply")
def admin_reply(
    body: AdminReplyRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "editor", "super_admin")),
):
    """Post a reply on a chapter comment thread as the admin/author.

    The reply is shown to readers with an "Author" badge. Pass `parent_id`
    to thread under an existing comment; omit it to post a top-level
    admin comment.
    """
    chapter = db.get(Chapter, body.chapter_id)
    if not chapter:
        raise NotFoundError("Chapter not found")
    if body.parent_id:
        parent = db.get(Comment, body.parent_id)
        if not parent or parent.chapter_id != body.chapter_id:
            raise BadRequestError("Invalid parent comment")
    comment = Comment(
        admin_id=admin.id,
        user_id=None,
        chapter_id=body.chapter_id,
        parent_id=body.parent_id,
        content=body.content.strip(),
        moderation_status="approved",
    )
    db.add(comment)
    audit_service.log(
        db, admin=admin, action="comment.admin_reply", target_type="comment",
        target_id=str(body.chapter_id), metadata={"parent_id": str(body.parent_id) if body.parent_id else None},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"id": str(comment.id), "is_admin": True, "username": admin.name or "Author"})
