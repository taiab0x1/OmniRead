from celery import Celery

from app.config import settings

celery = Celery(
    "omniread",
    broker=settings.CELERY_BROKER_URL,
    backend=settings.CELERY_RESULT_BACKEND,
    include=["app.workers.ai_tasks", "app.workers.maintenance_tasks"],
)

celery.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="UTC",
    enable_utc=True,
    task_default_queue="default",
    task_acks_late=True,
    task_reject_on_worker_lost=True,
    worker_prefetch_multiplier=1,
    task_routes={
        "app.workers.ai_tasks.generate_story": {"queue": "high"},
        "app.workers.ai_tasks.generate_chapter": {"queue": "high"},
        "app.workers.ai_tasks.generate_summary": {"queue": "low"},
    },
    beat_schedule={
        "purge-deleted-users": {
            "task": "app.workers.maintenance_tasks.purge_deleted_users",
            "schedule": 3600.0,
        },
        "expire-refresh-tokens": {
            "task": "app.workers.maintenance_tasks.expire_refresh_tokens",
            "schedule": 600.0,
        },
        "publish-scheduled-stories": {
            "task": "app.workers.maintenance_tasks.publish_scheduled_stories",
            "schedule": 300.0,
        },
        "deliver-pending-notifications": {
            "task": "app.workers.maintenance_tasks.deliver_pending_notifications",
            "schedule": 60.0,
        },
    },
)
