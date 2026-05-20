from typing import Any
from uuid import UUID

from sqlalchemy.orm import Session

from app.models import AdminUser, AuditLog


def log(
    db: Session,
    *,
    admin: AdminUser | None,
    action: str,
    target_type: str | None = None,
    target_id: str | None = None,
    metadata: dict[str, Any] | None = None,
    ip: str | None = None,
    user_agent: str | None = None,
) -> AuditLog:
    entry = AuditLog(
        actor_admin_id=admin.id if admin else None,
        action=action,
        target_type=target_type,
        target_id=str(target_id) if target_id is not None else None,
        metadata_json=metadata,
        ip_address=ip,
        user_agent=user_agent,
    )
    db.add(entry)
    db.flush()
    return entry
