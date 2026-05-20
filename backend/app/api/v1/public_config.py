from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models import AppConfig, Story
from app.schemas.common import ok
from sqlalchemy import func, desc

router = APIRouter()


@router.get("/app")
def get_app_config(db: Session = Depends(get_db)):
    """Public endpoint — returns all client-facing config in one call.
    No auth required. App calls this on launch to get genres, rewards, VIP plans, etc."""
    keys = ["genres", "rewards", "vip_plans", "home_sections", "ad_config", "coin_config", "feature_flags", "profile_menu"]
    rows = list(db.scalars(select(AppConfig).where(AppConfig.key.in_(keys))))
    result = {r.key: r.value for r in rows}
    return ok(result)


@router.get("/trending-tags")
def get_trending_tags(db: Session = Depends(get_db)):
    """Returns top genres/tags based on actual story data."""
    rows = db.execute(
        select(Story.genre, func.count(Story.id).label("cnt"))
        .where(Story.status == "published")
        .group_by(Story.genre)
        .order_by(desc("cnt"))
        .limit(10)
    ).all()
    tags = [r[0] for r in rows]
    return ok(tags)


@router.get("/trending-stories")
def get_trending_story_titles(db: Session = Depends(get_db)):
    """Returns top story titles for search trending section."""
    rows = list(
        db.scalars(
            select(Story.title)
            .where(Story.status == "published")
            .order_by(desc(Story.view_count))
            .limit(5)
        )
    )
    return ok(rows)
