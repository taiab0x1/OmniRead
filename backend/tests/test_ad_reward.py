import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.config import settings
from app.core.exceptions import BadRequestError, ConflictError
from app.models import RewardedAdEvent, User
from app.services import ad_reward_service


def _user(db) -> User:
    u = User(
        username="ad_" + uuid.uuid4().hex[:8],
        email=f"ad{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
    )
    db.add(u)
    db.flush()
    return u


def test_ad_reward_credits_coins(db):
    u = _user(db)
    db.commit()
    new_balance, _, _ = ad_reward_service.grant_ad_reward(
        db,
        user=u,
        placement="coin_pack",
        chapter_id=None,
        device_fingerprint="dev-1234",
        ssv_transaction_id=str(uuid.uuid4()),
        ip="127.0.0.1",
        user_agent="ua",
    )
    db.commit()
    assert new_balance == settings.REWARDED_AD_COINS


def test_ad_reward_idempotent_via_ssv(db):
    u = _user(db)
    db.commit()
    txid = str(uuid.uuid4())
    ad_reward_service.grant_ad_reward(
        db, user=u, placement="coin_pack", chapter_id=None,
        device_fingerprint="dev-1", ssv_transaction_id=txid, ip=None, user_agent=None,
    )
    db.commit()

    with pytest.raises(ConflictError):
        ad_reward_service.grant_ad_reward(
            db, user=u, placement="coin_pack", chapter_id=None,
            device_fingerprint="dev-1", ssv_transaction_id=txid, ip=None, user_agent=None,
        )


def test_ad_reward_cooldown_enforced(db, monkeypatch):
    monkeypatch.setattr(settings, "REWARDED_AD_COOLDOWN_MIN", 30)
    monkeypatch.setattr(settings, "REWARDED_AD_DAILY_CAP", 99)
    u = _user(db)
    db.commit()
    ad_reward_service.grant_ad_reward(
        db, user=u, placement="coin_pack", chapter_id=None,
        device_fingerprint="dev-2", ssv_transaction_id=str(uuid.uuid4()),
        ip=None, user_agent=None,
    )
    db.commit()
    with pytest.raises(BadRequestError) as ei:
        ad_reward_service.grant_ad_reward(
            db, user=u, placement="coin_pack", chapter_id=None,
            device_fingerprint="dev-2", ssv_transaction_id=str(uuid.uuid4()),
            ip=None, user_agent=None,
        )
    assert ei.value.code == "ad_cooldown"


def test_ad_reward_daily_cap_enforced(db, monkeypatch):
    monkeypatch.setattr(settings, "REWARDED_AD_COOLDOWN_MIN", 0)
    monkeypatch.setattr(settings, "REWARDED_AD_DAILY_CAP", 2)
    u = _user(db)
    db.commit()
    for _ in range(2):
        ad_reward_service.grant_ad_reward(
            db, user=u, placement="coin_pack", chapter_id=None,
            device_fingerprint="dev-cap", ssv_transaction_id=str(uuid.uuid4()),
            ip=None, user_agent=None,
        )
        db.commit()
    with pytest.raises(BadRequestError) as ei:
        ad_reward_service.grant_ad_reward(
            db, user=u, placement="coin_pack", chapter_id=None,
            device_fingerprint="dev-cap", ssv_transaction_id=str(uuid.uuid4()),
            ip=None, user_agent=None,
        )
    assert ei.value.code in {"ad_daily_cap", "device_daily_cap"}
