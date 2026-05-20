from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import desc, func, select
from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, CoinTransaction, Subscription, User
from app.schemas.admin import BanUserRequest, CoinAdjustRequest
from app.schemas.common import Meta, ok
from app.services import audit_service, coin_service

router = APIRouter()


@router.get("")
def list_users(
    q: str | None = None,
    tier: str | None = None,
    banned: bool | None = None,
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin", "analytics")),
):
    stmt = select(User).where(User.deleted_at.is_(None))
    if q:
        like = f"%{q}%"
        stmt = stmt.where((User.email.ilike(like)) | (User.username.ilike(like)))
    if tier:
        stmt = stmt.where(User.subscription_tier == tier)
    if banned is not None:
        stmt = stmt.where(User.is_banned.is_(banned))
    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    rows = list(
        db.scalars(
            stmt.order_by(desc(User.created_at)).offset((page - 1) * per_page).limit(per_page)
        )
    )
    items = [
        {
            "id": str(u.id),
            "email": u.email,
            "username": u.username,
            "is_guest": u.is_guest,
            "is_banned": u.is_banned,
            "coin_balance": u.coin_balance,
            "subscription_tier": u.subscription_tier,
            "subscription_expires_at": u.subscription_expires_at,
            "created_at": u.created_at,
        }
        for u in rows
    ]
    return ok(items, meta=Meta(page=page, per_page=per_page, total=total))


@router.get("/{user_id}")
def user_detail(
    user_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin", "analytics")),
):
    u = db.get(User, user_id)
    if not u:
        raise NotFoundError()
    sub = db.scalar(
        select(Subscription).where(Subscription.user_id == u.id).order_by(desc(Subscription.expires_at)).limit(1)
    )
    txs = list(
        db.scalars(
            select(CoinTransaction)
            .where(CoinTransaction.user_id == u.id)
            .order_by(desc(CoinTransaction.created_at))
            .limit(50)
        )
    )
    return ok(
        {
            "id": str(u.id),
            "email": u.email,
            "username": u.username,
            "is_guest": u.is_guest,
            "is_verified": u.is_verified,
            "is_banned": u.is_banned,
            "ban_reason": u.ban_reason,
            "coin_balance": u.coin_balance,
            "subscription_tier": u.subscription_tier,
            "subscription_expires_at": u.subscription_expires_at,
            "reading_streak": u.reading_streak,
            "created_at": u.created_at,
            "subscription": (
                {
                    "sku": sub.sku,
                    "state": sub.state,
                    "expires_at": sub.expires_at,
                    "auto_renewing": sub.auto_renewing,
                }
                if sub
                else None
            ),
            "recent_transactions": [
                {
                    "id": str(t.id),
                    "amount": t.amount,
                    "type": t.type,
                    "balance_after": t.balance_after,
                    "created_at": t.created_at,
                }
                for t in txs
            ],
        }
    )


@router.put("/{user_id}/ban")
def ban_user(
    user_id: UUID,
    body: BanUserRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    u = db.get(User, user_id)
    if not u:
        raise NotFoundError()
    u.is_banned = True
    u.ban_reason = body.reason
    audit_service.log(
        db, admin=admin, action="user.ban", target_type="user", target_id=str(u.id),
        metadata={"reason": body.reason}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"banned": True})


@router.put("/{user_id}/unban")
def unban_user(
    user_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    u = db.get(User, user_id)
    if not u:
        raise NotFoundError()
    u.is_banned = False
    u.ban_reason = None
    audit_service.log(
        db, admin=admin, action="user.unban", target_type="user", target_id=str(u.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"banned": False})


@router.post("/{user_id}/coins/adjust")
def adjust_coins(
    user_id: UUID,
    body: CoinAdjustRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    u = db.get(User, user_id)
    if not u:
        raise NotFoundError()
    if body.delta > 0:
        coin_service.credit(
            db, user_id=u.id, amount=body.delta, type_="admin_adjust",
            description=f"Admin: {body.reason}", idempotency_key=None,
        )
    elif body.delta < 0:
        coin_service.debit(
            db, user_id=u.id, amount=-body.delta, type_="admin_adjust",
            description=f"Admin: {body.reason}", idempotency_key=None,
        )
    audit_service.log(
        db, admin=admin, action="user.coins_adjust", target_type="user", target_id=str(u.id),
        metadata={"delta": body.delta, "reason": body.reason}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"new_balance": coin_service.get_balance(db, u.id)})


@router.post("/{user_id}/force-logout")
def force_logout(
    user_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("moderator", "super_admin")),
):
    from app.services.auth_service import _revoke_descendants
    u = db.get(User, user_id)
    if not u:
        raise NotFoundError()
    _revoke_descendants(db, u.id, reason="admin_force_logout")
    audit_service.log(
        db, admin=admin, action="user.force_logout", target_type="user", target_id=str(u.id),
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"revoked": True})
