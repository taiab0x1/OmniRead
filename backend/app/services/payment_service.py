from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.exceptions import BadRequestError, ConflictError, NotFoundError
from app.models import CoinPackage, Purchase, Subscription, User
from app.services import coin_service, play_billing


def list_packages(db: Session) -> list[CoinPackage]:
    return list(
        db.scalars(
            select(CoinPackage).where(CoinPackage.is_active.is_(True)).order_by(CoinPackage.sort_order)
        )
    )


def verify_coin_purchase(
    db: Session,
    *,
    user: User,
    sku: str,
    purchase_token: str,
) -> Purchase:
    existing = db.scalar(select(Purchase).where(Purchase.purchase_token == purchase_token))
    if existing and existing.coins_credited:
        return existing

    pkg = db.scalar(select(CoinPackage).where(CoinPackage.sku == sku, CoinPackage.is_active.is_(True)))
    if not pkg:
        raise NotFoundError("Unknown SKU", code="unknown_sku")

    raw = play_billing.verify_product_purchase(sku, purchase_token)
    if not raw:
        raise BadRequestError("Purchase not verifiable", code="play_verify_failed")

    if int(raw.get("purchaseState", 1)) != 0:
        raise BadRequestError("Purchase not in PURCHASED state", code="purchase_not_complete")
    if int(raw.get("acknowledgementState", 0)) == 0:
        play_billing.acknowledge_product(sku, purchase_token)

    total = pkg.coins + (pkg.bonus_coins or 0)
    if not existing:
        purchase = Purchase(
            user_id=user.id,
            sku=sku,
            purchase_token=purchase_token,
            order_id=raw.get("orderId"),
            purchase_state="purchased",
            price_amount_micros=raw.get("priceAmountMicros"),
            price_currency=raw.get("priceCurrencyCode"),
            raw_response=raw,
            verified_at=datetime.now(timezone.utc),
        )
        db.add(purchase)
        db.flush()
    else:
        purchase = existing
        purchase.raw_response = raw
        purchase.verified_at = datetime.now(timezone.utc)

    coin_service.credit(
        db,
        user_id=user.id,
        amount=total,
        type_="purchase",
        description=f"Coin pack {pkg.name}",
        reference_id=purchase.id,
        idempotency_key=f"purchase:{purchase_token}",
    )
    purchase.coins_credited = total
    db.flush()
    return purchase


def verify_subscription(
    db: Session,
    *,
    user: User,
    sku: str,
    purchase_token: str,
) -> Subscription:
    raw = play_billing.verify_subscription(sku, purchase_token)
    if not raw:
        raise BadRequestError("Subscription not verifiable", code="play_verify_failed")
    line_items = raw.get("lineItems") or []
    expiry_iso = (line_items[0].get("expiryTime") if line_items else None) or raw.get("expiryTime")
    starts_iso = raw.get("startTime")
    state = raw.get("subscriptionState", "SUBSCRIPTION_STATE_ACTIVE")
    auto_renew = bool(raw.get("autoRenewing", True))

    expires_at = _parse_iso(expiry_iso) or datetime.now(timezone.utc)
    starts_at = _parse_iso(starts_iso) or datetime.now(timezone.utc)

    sub = db.scalar(select(Subscription).where(Subscription.purchase_token == purchase_token))
    if sub and sub.user_id != user.id:
        raise ConflictError("Token belongs to another user", code="token_user_mismatch")

    if not sub:
        sub = Subscription(
            user_id=user.id,
            sku=sku,
            purchase_token=purchase_token,
            state=_normalize_state(state),
            auto_renewing=auto_renew,
            starts_at=starts_at,
            expires_at=expires_at,
            raw_notification=raw,
        )
        db.add(sub)
    else:
        sub.state = _normalize_state(state)
        sub.auto_renewing = auto_renew
        sub.starts_at = starts_at
        sub.expires_at = expires_at
        sub.raw_notification = raw

    if sub.state in {"active", "in_grace_period"}:
        user.subscription_tier = "premium"
        user.subscription_expires_at = max(
            user.subscription_expires_at or expires_at, expires_at
        )

    db.flush()
    return sub


def _parse_iso(s: str | None) -> datetime | None:
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except Exception:
        return None


def _normalize_state(value: str | None) -> str:
    v = (value or "").lower()
    mapping = {
        "subscription_state_active": "active",
        "subscription_state_in_grace_period": "in_grace_period",
        "subscription_state_on_hold": "on_hold",
        "subscription_state_paused": "paused",
        "subscription_state_canceled": "cancelled",
        "subscription_state_expired": "expired",
    }
    return mapping.get(v, v or "active")


def apply_rtdn(db: Session, payload: dict) -> Subscription | None:
    """Process Real-time Developer Notification message body."""
    sub_notif = payload.get("subscriptionNotification")
    if not sub_notif:
        return None
    sku = sub_notif.get("subscriptionId")
    token = sub_notif.get("purchaseToken")
    if not sku or not token:
        return None
    sub = db.scalar(select(Subscription).where(Subscription.purchase_token == token))
    if not sub:
        return None
    user = db.get(User, sub.user_id)
    if not user:
        return None
    return verify_subscription(db, user=user, sku=sku, purchase_token=token)
