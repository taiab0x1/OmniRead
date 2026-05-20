from fastapi import APIRouter

from app.api.v1 import auth, chapters, comments, payments, public_config, stories, users
from app.api.v1.admin import router as admin_router

router = APIRouter()
router.include_router(auth.router, prefix="/auth", tags=["auth"])
router.include_router(stories.router, prefix="/stories", tags=["stories"])
router.include_router(chapters.router, prefix="/chapters", tags=["chapters"])
router.include_router(comments.router, prefix="/chapters", tags=["comments"])
router.include_router(users.router, prefix="/user", tags=["user"])
router.include_router(payments.router, prefix="/payments", tags=["payments"])
router.include_router(public_config.router, prefix="/config", tags=["config"])
router.include_router(admin_router, prefix="/admin", tags=["admin"])
