from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, Query
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.dependencies import require_role
from app.models import AdminUser, AuditLog
from app.schemas.common import Meta, ok

router = APIRouter()


@router.get("")
def list_audit(
    actor_admin_id: UUID | None = None,
    action: str | None = None,
    target_type: str | None = None,
    page: int = Query(1, ge=1),
    per_page: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    stmt = select(AuditLog)
    if actor_admin_id:
        stmt = stmt.where(AuditLog.actor_admin_id == actor_admin_id)
    if action:
        stmt = stmt.where(AuditLog.action == action)
    if target_type:
        stmt = stmt.where(AuditLog.target_type == target_type)
    rows = list(
        db.scalars(
            stmt.order_by(desc(AuditLog.created_at)).offset((page - 1) * per_page).limit(per_page)
        )
    )
    items = [
        {
            "id": str(r.id),
            "actor_admin_id": str(r.actor_admin_id) if r.actor_admin_id else None,
            "action": r.action,
            "target_type": r.target_type,
            "target_id": r.target_id,
            "metadata": r.metadata_json,
            "ip_address": str(r.ip_address) if r.ip_address else None,
            "created_at": r.created_at,
        }
        for r in rows
    ]
    return ok(items, meta=Meta(page=page, per_page=per_page))
