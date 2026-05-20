from datetime import datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import and_, func, select
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import BadRequestError, ConflictError
from app.core.rate_limit import fixed_window
from app.models import RewardedAdEvent, User, UserChapterUnlock
from app.services import chapter_service, coin_service


def _today_start(now: datetime) -> datetime:
    return now.replace(hour=0, minute=0, second=0, microsecond=0)


def _check_caps(db: Session, user: User, device_fingerprint: str | None) -> None:
    now = datetime.now(timezone.utc)
    fixed_window(f"rl:adreward:{user.id}", limit=settings.RATE_LIMIT_AD_REWARD_PER_MIN, period_seconds=60)

    today = _today_start(now)
    user_today = db.scalar(
        select(func.count(RewardedAdEvent.id)).where(
            RewardedAdEvent.user_id == user.id,
            RewardedAdEvent.created_at >= today,
            RewardedAdEvent.validated.is_(True),
        )
    )
    if (user_today or 0) >= settings.REWARDED_AD_DAILY_CAP:
        raise BadRequestError("Daily ad cap reached", code="ad_daily_cap")

    if device_fingerprint:
        device_today = db.scalar(
            select(func.count(RewardedAdEvent.id)).where(
                RewardedAdEvent.device_fingerprint == device_fingerprint,
                RewardedAdEvent.created_at >= today,
                RewardedAdEvent.validated.is_(True),
            )
        )
        if (device_today or 0) >= settings.REWARDED_AD_DAILY_CAP:
            raise BadRequestError("Device ad cap reached", code="device_daily_cap")

    cooldown = now - timedelta(minutes=settings.REWARDED_AD_COOLDOWN_MIN)
    last = db.scalar(
        select(RewardedAdEvent.created_at)
        .where(RewardedAdEvent.user_id == user.id, RewardedAdEvent.validated.is_(True))
        .order_by(RewardedAdEvent.created_at.desc())
        .limit(1)
    )
    if last and last > cooldown:
        raise BadRequestError("Ad cooldown active", code="ad_cooldown")


def grant_ad_reward(
    db: Session,
    *,
    user: User,
    placement: str,
    chapter_id: UUID | None,
    device_fingerprint: str | None,
    ssv_transaction_id: str | None,
    ip: str | None,
    user_agent: str | None,
) -> tuple[int, datetime, UUID | None]:
    if ssv_transaction_id:
        existing = db.scalar(
            select(RewardedAdEvent).where(RewardedAdEvent.ssv_transaction_id == ssv_transaction_id)
        )
        if existing:
            raise ConflictError("Already credited", code="already_credited")
    _check_caps(db, user, device_fingerprint)

    coins = settings.REWARDED_AD_COINS
    event = RewardedAdEvent(
        user_id=user.id,
        device_fingerprint=device_fingerprint,
        ad_network="admob",
        ssv_transaction_id=ssv_transaction_id,
        reward_type="coins" if placement != "chapter_unlock" else "chapter_unlock",
        reward_amount=coins,
        chapter_id=chapter_id,
        validated=True,
        ip_address=ip,
        user_agent=user_agent,
    )
    db.add(event)
    db.flush()

    unlocked_chapter_id = None
    if placement == "chapter_unlock" and chapter_id:
        chapter = chapter_service.unlock_via_ad(db, user=user, chapter_id=chapter_id)
        unlocked_chapter_id = chapter.id
    else:
        coin_service.credit(
            db,
            user_id=user.id,
            amount=coins,
            type_="ad_reward",
            description=f"Rewarded ad ({placement})",
            reference_id=event.id,
            idempotency_key=f"ad-reward:{event.id}",
        )

    next_available = datetime.now(timezone.utc) + timedelta(minutes=settings.REWARDED_AD_COOLDOWN_MIN)
    new_balance = coin_service.get_balance(db, user.id)
    return new_balance, next_available, unlocked_chapter_id
