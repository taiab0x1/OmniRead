"""User segment compiler — turns a SegmentFilter dict into a SQLAlchemy
WHERE clause against the users table."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from sqlalchemy import and_, func, select
from sqlalchemy.orm import Session

from app.models import User


def filter_to_clause(filter_dict: dict):
    """Build a list of WHERE expressions from a serialized SegmentFilter."""
    clauses = []
    now = datetime.now(timezone.utc)

    if tiers := filter_dict.get("subscription_tier"):
        clauses.append(User.subscription_tier.in_(tiers))
    if (is_guest := filter_dict.get("is_guest")) is not None:
        clauses.append(User.is_guest.is_(bool(is_guest)))
    if genres := filter_dict.get("preferred_genres_any"):
        # preferred_genres is a Postgres ARRAY(String); overlap = "&&"
        clauses.append(User.preferred_genres.op("&&")(genres))
    if locales := filter_dict.get("locale"):
        clauses.append(User.locale.in_(locales))
    if regions := filter_dict.get("region"):
        clauses.append(User.region.in_(regions))
    if (s := filter_dict.get("min_reading_streak")) is not None:
        clauses.append(User.reading_streak >= s)
    if (c := filter_dict.get("min_coin_balance")) is not None:
        clauses.append(User.coin_balance >= c)
    if (c := filter_dict.get("max_coin_balance")) is not None:
        clauses.append(User.coin_balance <= c)
    if (d := filter_dict.get("days_since_signup_min")) is not None:
        clauses.append(User.created_at <= now - timedelta(days=d))
    if (d := filter_dict.get("days_since_signup_max")) is not None:
        clauses.append(User.created_at >= now - timedelta(days=d))
    if (d := filter_dict.get("days_since_last_login_max")) is not None:
        clauses.append(User.last_login_at >= now - timedelta(days=d))

    # Implicit: only non-banned, non-deleted users
    clauses.append(User.is_banned.is_(False))
    clauses.append(User.deleted_at.is_(None))
    return clauses


def count_users(db: Session, filter_dict: dict) -> int:
    clauses = filter_to_clause(filter_dict)
    stmt = select(func.count(User.id))
    if clauses:
        stmt = stmt.where(and_(*clauses))
    return db.scalar(stmt) or 0


def user_ids(db: Session, filter_dict: dict, limit: int | None = None):
    clauses = filter_to_clause(filter_dict)
    stmt = select(User.id)
    if clauses:
        stmt = stmt.where(and_(*clauses))
    if limit:
        stmt = stmt.limit(limit)
    return [row for row in db.scalars(stmt)]
