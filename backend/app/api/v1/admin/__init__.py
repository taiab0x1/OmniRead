from fastapi import APIRouter

from app.api.v1.admin import (
    ai,
    analytics,
    auth as admin_auth,
    audit,
    chapters as admin_chapters,
    coins as admin_coins,
    config,
    growth as admin_growth,
    imports as admin_imports,
    moderation,
    notifications as admin_notifications,
    stories as admin_stories,
    users as admin_users,
)

router = APIRouter()
router.include_router(admin_auth.router, prefix="/auth", tags=["admin-auth"])
router.include_router(admin_stories.router, prefix="/stories", tags=["admin-stories"])
router.include_router(admin_chapters.router, prefix="/chapters", tags=["admin-chapters"])
router.include_router(admin_coins.router, prefix="/coins", tags=["admin-coins"])
router.include_router(ai.router, prefix="/ai", tags=["admin-ai"])
router.include_router(admin_users.router, prefix="/users", tags=["admin-users"])
router.include_router(analytics.router, prefix="/analytics", tags=["admin-analytics"])
router.include_router(config.router, prefix="/config", tags=["admin-config"])
router.include_router(moderation.router, prefix="/moderation", tags=["admin-moderation"])
router.include_router(audit.router, prefix="/audit", tags=["admin-audit"])
router.include_router(admin_imports.router, prefix="/content", tags=["admin-imports"])
router.include_router(admin_growth.router, prefix="/growth", tags=["admin-growth"])
router.include_router(admin_notifications.router, prefix="/notifications", tags=["admin-notifications"])
