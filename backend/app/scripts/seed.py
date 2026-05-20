"""Bootstrap script: seed coin packages, default admin, default app config.

Usage (from backend/):
    python -m app.scripts.seed
"""
import os
import sys
from datetime import datetime, timezone

from sqlalchemy import select

from app.core.security import hash_password
from app.db.session import session_scope
from app.models import AdminUser, AppConfig, CoinPackage


COIN_PACKAGES = [
    {"sku": "coins_50", "name": "Starter", "coins": 50, "bonus": 0, "price": 0.99, "best": False, "order": 1},
    {"sku": "coins_150", "name": "Popular", "coins": 150, "bonus": 0, "price": 2.99, "best": False, "order": 2},
    {"sku": "coins_350", "name": "Value", "coins": 350, "bonus": 50, "price": 5.99, "best": False, "order": 3},
    {"sku": "coins_800", "name": "Best Value", "coins": 800, "bonus": 200, "price": 11.99, "best": True, "order": 4},
    {"sku": "coins_2000", "name": "Mega", "coins": 2000, "bonus": 500, "price": 24.99, "best": False, "order": 5},
]


DEFAULT_CONFIG = {
    "ad_config": {
        "rewarded_ads_enabled": True,
        "rewarded_cooldown_minutes": 30,
        "coins_per_rewarded_ad": 10,
        "interstitial_enabled": False,
        "interstitial_chapter_interval": 5,
        "banner_enabled": False,
    },
    "coin_config": {
        "chapter_base_cost": 5,
        "premium_chapter_cost": 10,
        "daily_login_coins": 5,
        "streak_bonus_multiplier": 1.5,
    },
    "content_config": {
        "free_chapters_default": 3,
        "feed_algorithm": "trending",
    },
    "feature_flags": {
        "comments_enabled": True,
        "audio_enabled": False,
        "offline_mode_enabled": False,
        "interactive_stories_enabled": False,
    },
}


def seed_packages(db) -> int:
    inserted = 0
    for p in COIN_PACKAGES:
        existing = db.scalar(select(CoinPackage).where(CoinPackage.sku == p["sku"]))
        if existing:
            continue
        db.add(
            CoinPackage(
                sku=p["sku"],
                name=p["name"],
                coins=p["coins"],
                bonus_coins=p["bonus"],
                price_usd=p["price"],
                is_best_value=p["best"],
                sort_order=p["order"],
            )
        )
        inserted += 1
    return inserted


def seed_admin(db) -> bool:
    email = os.environ.get("ADMIN_EMAIL", "admin@omniread.local")
    password = os.environ.get("ADMIN_PASSWORD")
    if not password:
        print("Skipping admin seed: ADMIN_PASSWORD not set", file=sys.stderr)
        return False
    existing = db.scalar(select(AdminUser).where(AdminUser.email == email))
    if existing:
        return False
    db.add(
        AdminUser(
            email=email,
            password_hash=hash_password(password),
            name=os.environ.get("ADMIN_NAME", "Admin"),
            role="super_admin",
        )
    )
    return True


def seed_config(db) -> int:
    inserted = 0
    for key, value in DEFAULT_CONFIG.items():
        existing = db.get(AppConfig, key)
        if existing:
            continue
        db.add(AppConfig(key=key, value=value))
        inserted += 1
    return inserted


def main() -> None:
    with session_scope() as db:
        pk = seed_packages(db)
        ad = seed_admin(db)
        cf = seed_config(db)
    print(f"Seeded: packages={pk}, admin={'yes' if ad else 'no'}, config_keys={cf}")


if __name__ == "__main__":
    main()
