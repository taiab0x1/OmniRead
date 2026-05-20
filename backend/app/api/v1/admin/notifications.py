"""Admin notification broadcast: pushes a Notification record per target user.

Target resolution priority:
1. `segment_id` — uses the segment's filter
2. `target_genre` — users whose preferred_genres contains the value
3. None — all non-banned, non-guest users
"""
from uuid import UUID

from fastapi import APIRouter, Depends, Request
from sqlalchemy import and_, select
from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, Notification, User, UserSegment
from app.schemas.admin import BroadcastRequest
from app.schemas.common import ok
from app.services import audit_service, segment_service

router = APIRouter()


@router.post("/broadcast")
def broadcast(
    body: BroadcastRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor")),
):
    target_users: list[UUID]

    if body.segment_id:
        segment = db.get(UserSegment, body.segment_id)
        if not segment:
            raise NotFoundError("Segment not found")
        target_users = segment_service.user_ids(db, segment.filter)
    elif body.target_genre:
        target_users = list(
            db.scalars(
                select(User.id).where(
                    and_(
                        User.is_banned.is_(False),
                        User.is_guest.is_(False),
                        User.preferred_genres.op("&&")([body.target_genre]),
                    )
                )
            )
        )
    else:
        target_users = list(
            db.scalars(
                select(User.id).where(User.is_banned.is_(False), User.is_guest.is_(False))
            )
        )

    if not target_users:
        return ok({"sent": 0, "target_count": 0})

    # Bulk-insert Notification rows; the FCM worker picks them up via a
    # separate Celery beat task. We don't push to FCM directly here so the
    # endpoint stays fast.
    db.bulk_insert_mappings(
        Notification,
        [
            {
                "user_id": uid,
                "type": body.type,
                "title": body.title,
                "body": body.body,
                "data": body.data,
            }
            for uid in target_users
        ],
    )
    audit_service.log(
        db, admin=admin, action="notification.broadcast", target_type="broadcast",
        target_id=str(body.segment_id or "all"),
        metadata={
            "title": body.title,
            "target_count": len(target_users),
            "segment_id": str(body.segment_id) if body.segment_id else None,
            "target_genre": body.target_genre,
        },
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"sent": len(target_users), "target_count": len(target_users)})
