from datetime import datetime, timedelta, timezone

from sqlalchemy import and_, delete, select, update

from app.celery_app import celery
from app.db.session import session_scope
from app.models import FcmToken, Notification, RefreshToken, Story, User
from app.services import push_service


@celery.task(name="app.workers.maintenance_tasks.expire_refresh_tokens")
def expire_refresh_tokens() -> int:
    now = datetime.now(timezone.utc)
    with session_scope() as db:
        result = db.execute(
            update(RefreshToken)
            .where(and_(RefreshToken.revoked_at.is_(None), RefreshToken.expires_at <= now))
            .values(revoked_at=now, revoked_reason="expired")
        )
        return result.rowcount


@celery.task(name="app.workers.maintenance_tasks.purge_deleted_users")
def purge_deleted_users() -> int:
    cutoff = datetime.now(timezone.utc) - timedelta(days=30)
    with session_scope() as db:
        result = db.execute(
            delete(User).where(and_(User.deleted_at.is_not(None), User.deleted_at <= cutoff))
        )
        return result.rowcount


@celery.task(name="app.workers.maintenance_tasks.publish_scheduled_stories")
def publish_scheduled_stories() -> int:
    now = datetime.now(timezone.utc)
    with session_scope() as db:
        stories = list(
            db.scalars(
                select(Story).where(
                    Story.status == "scheduled",
                    Story.scheduled_at.is_not(None),
                    Story.scheduled_at <= now,
                )
            )
        )
        for story in stories:
            story.status = "published"
            story.published_at = story.published_at or now
            story.scheduled_at = None
        return len(stories)


@celery.task(name="app.workers.maintenance_tasks.deliver_pending_notifications")
def deliver_pending_notifications(limit: int = 500) -> int:
    now = datetime.now(timezone.utc)
    delivered = 0
    with session_scope() as db:
        notifications = list(
            db.scalars(
                select(Notification)
                .where(Notification.sent_at.is_(None))
                .order_by(Notification.created_at)
                .limit(limit)
            )
        )
        for notification in notifications:
            tokens = list(
                db.scalars(
                    select(FcmToken.token).where(FcmToken.user_id == notification.user_id)
                )
            )
            success_count = push_service.send_to_many(
                tokens,
                notification.title or "OmniRead",
                notification.body or "",
                data=notification.data,
            )
            if success_count > 0 or not tokens:
                notification.sent_at = now
                delivered += 1
        return delivered
