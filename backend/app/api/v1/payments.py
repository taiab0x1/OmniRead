import base64
import json
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Body, Depends, Header, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, ForbiddenError
from app.core.logging import get_logger
from app.db.session import get_db
from app.dependencies import get_client_ip, get_current_user, get_idempotency_key
from app.models import CoinPackage, Subscription, User
from app.schemas.common import ok
from app.schemas.payment import (
    AdRewardResult,
    AdRewardValidateRequest,
    CoinPackageItem,
    PlayPurchaseVerifyRequest,
    SubscriptionStatus,
)
from app.services import ad_reward_service, admob_ssv, payment_service

router = APIRouter()
log = get_logger("payments")


@router.get("/coins/packages")
def list_packages(db: Session = Depends(get_db)):
    pkgs = payment_service.list_packages(db)
    return ok([CoinPackageItem.model_validate(p, from_attributes=True).model_dump() for p in pkgs])


@router.post("/coins/purchase")
def verify_coin_purchase(
    body: PlayPurchaseVerifyRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    idempotency_key: str | None = Depends(get_idempotency_key),
):
    if body.is_subscription:
        raise BadRequestError("Use /payments/subscription/subscribe", code="wrong_endpoint")
    purchase = payment_service.verify_coin_purchase(
        db, user=user, sku=body.sku, purchase_token=body.purchase_token
    )
    db.commit()
    return ok(
        {
            "purchase_id": str(purchase.id),
            "coins_credited": purchase.coins_credited,
            "balance": user.coin_balance,
        }
    )


@router.post("/subscription/subscribe")
def subscribe(
    body: PlayPurchaseVerifyRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    sub = payment_service.verify_subscription(
        db, user=user, sku=body.sku, purchase_token=body.purchase_token
    )
    db.commit()
    return ok(
        SubscriptionStatus(
            tier=user.subscription_tier,
            expires_at=user.subscription_expires_at,
            auto_renewing=sub.auto_renewing,
            state=sub.state,
        ).model_dump()
    )


@router.get("/subscription/status")
def subscription_status(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    sub = db.scalar(
        select(Subscription)
        .where(Subscription.user_id == user.id)
        .order_by(Subscription.expires_at.desc())
        .limit(1)
    )
    if not sub:
        return ok(
            SubscriptionStatus(
                tier=user.subscription_tier,
                expires_at=user.subscription_expires_at,
                auto_renewing=False,
                state="none",
            ).model_dump()
        )
    return ok(
        SubscriptionStatus(
            tier=user.subscription_tier,
            expires_at=sub.expires_at,
            auto_renewing=sub.auto_renewing,
            state=sub.state,
        ).model_dump()
    )


@router.post("/subscription/cancel")
def cancel_subscription(user: User = Depends(get_current_user)):
    return ok({"hint": "Cancellation must be performed via Google Play subscriptions UI."})


@router.post("/ad-reward/validate")
def validate_ad_reward(
    body: AdRewardValidateRequest,
    request: Request,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if not body.ssv_transaction_id:
        raise ForbiddenError("Reward must be verified by AdMob SSV", code="ssv_required")
    new_balance, next_available, unlocked_chapter_id = ad_reward_service.grant_ad_reward(
        db,
        user=user,
        placement=body.placement,
        chapter_id=body.chapter_id,
        device_fingerprint=body.device_fingerprint,
        ssv_transaction_id=body.ssv_transaction_id,
        ip=get_client_ip(request),
        user_agent=request.headers.get("user-agent"),
    )
    db.commit()
    coins = new_balance - user.coin_balance if unlocked_chapter_id is None else 0
    return ok(
        {
            **AdRewardResult(
                coins_credited=max(0, coins),
                new_balance=new_balance,
                next_available_at=next_available,
            ).model_dump(),
            "unlocked_chapter_id": str(unlocked_chapter_id) if unlocked_chapter_id else None,
        }
    )


@router.post("/google-play/rtdn")
def google_play_rtdn(
    payload: dict = Body(...),
    db: Session = Depends(get_db),
):
    """Real-time Developer Notifications. Pub/Sub push delivers a wrapped envelope."""
    message = payload.get("message") or {}
    data_b64 = message.get("data")
    if not data_b64:
        return ok({"ignored": True, "reason": "no_data"})
    try:
        decoded = json.loads(base64.b64decode(data_b64).decode())
    except Exception:
        raise BadRequestError("Invalid RTDN payload", code="invalid_rtdn")

    sub = payment_service.apply_rtdn(db, decoded)
    db.commit()
    return ok({"applied": sub is not None})


@router.get("/ad-reward/admob-callback")
def admob_ssv_callback(request: Request, db: Session = Depends(get_db)):
    """AdMob server-side verification target.

    Google issues a GET to this URL after a rewarded ad finishes. We verify
    the ECDSA signature against Google's published verifier keys, then credit
    the user. `user_id` is set on the client via `RewardedAd.setUserId(...)`
    to our internal UUID; `custom_data` optionally carries a chapter_id for
    unlock placements.

    Always returns 200 — failures are logged but not exposed so Google can't
    fingerprint our verification logic.
    """
    params = dict(request.query_params)
    query_bytes = request.url.query.encode("ascii") if isinstance(request.url.query, str) else b""

    try:
        admob_ssv.verify_callback(query_bytes, params)
    except admob_ssv.SSVError as e:
        log.warning("admob_ssv_rejected", reason=str(e), params={k: params.get(k) for k in ("ad_unit", "transaction_id", "key_id")})
        return ok({"received": False})

    # Required for credit
    user_id = params.get("user_id")
    transaction_id = params.get("transaction_id")
    if not user_id or not transaction_id:
        log.warning("admob_ssv_missing_fields", params=params)
        return ok({"received": True, "credited": False})

    try:
        user_uuid = UUID(user_id)
    except ValueError:
        log.warning("admob_ssv_bad_user_id", user_id=user_id)
        return ok({"received": True, "credited": False})

    user = db.get(User, user_uuid)
    if not user or user.is_banned or user.deleted_at is not None:
        log.warning("admob_ssv_user_missing", user_id=user_id)
        return ok({"received": True, "credited": False})

    custom_data = params.get("custom_data") or ""
    placement = "ad_reward"
    chapter_id: UUID | None = None
    if custom_data.startswith("chapter:"):
        try:
            chapter_id = UUID(custom_data.split(":", 1)[1])
            placement = "chapter_unlock"
        except ValueError:
            pass

    try:
        ad_reward_service.grant_ad_reward(
            db,
            user=user,
            placement=placement,
            chapter_id=chapter_id,
            device_fingerprint=None,
            ssv_transaction_id=transaction_id,
            ip=get_client_ip(request),
            user_agent=request.headers.get("user-agent"),
        )
        db.commit()
    except Exception as e:
        # Most likely: ConflictError (already credited), BadRequestError (caps).
        # Either way, ack to Google so it doesn't retry indefinitely.
        log.info("admob_ssv_credit_skipped", reason=str(e), tx=transaction_id)

    return ok({"received": True})
