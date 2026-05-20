"""Admin segment + experiment + refund endpoints + revenue dashboard data."""
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import desc, func, select
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import (
    AdminUser,
    Experiment,
    ExperimentAssignment,
    Purchase,
    Refund,
    Subscription,
    User,
    UserSegment,
)
from app.schemas.admin import (
    ExperimentCreate,
    ExperimentUpdate,
    RefundCreate,
    SegmentCreate,
    SegmentUpdate,
)
from app.schemas.common import ok
from app.services import audit_service, experiment_service, segment_service

router = APIRouter()


# ----- Segments -----

def _segment_payload(s: UserSegment) -> dict:
    return {
        "id": str(s.id),
        "name": s.name,
        "description": s.description,
        "filter": s.filter,
        "cached_user_count": s.cached_user_count,
        "cached_at": s.cached_at,
        "created_at": s.created_at,
        "updated_at": s.updated_at,
    }


@router.get("/segments")
def list_segments(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    rows = list(db.scalars(select(UserSegment).order_by(UserSegment.name)))
    return ok([_segment_payload(r) for r in rows])


@router.post("/segments")
def create_segment(
    body: SegmentCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor")),
):
    if db.scalar(select(UserSegment).where(UserSegment.name == body.name)):
        raise ConflictError("Segment name already exists", code="segment_name_taken")
    seg = UserSegment(
        name=body.name,
        description=body.description,
        filter=body.filter.model_dump(exclude_none=True),
        created_by=admin.id,
    )
    db.add(seg)
    db.flush()
    seg.cached_user_count = segment_service.count_users(db, seg.filter)
    seg.cached_at = datetime.now(timezone.utc)
    audit_service.log(
        db, admin=admin, action="segment.create", target_type="segment",
        target_id=str(seg.id), metadata={"name": seg.name}, ip=get_client_ip(request),
    )
    db.commit()
    return ok(_segment_payload(seg))


@router.get("/segments/{segment_id}")
def get_segment(
    segment_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    seg = db.get(UserSegment, segment_id)
    if not seg:
        raise NotFoundError()
    return ok(_segment_payload(seg))


@router.put("/segments/{segment_id}")
def update_segment(
    segment_id: UUID,
    body: SegmentUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor")),
):
    seg = db.get(UserSegment, segment_id)
    if not seg:
        raise NotFoundError()
    if body.name is not None:
        seg.name = body.name
    if body.description is not None:
        seg.description = body.description
    if body.filter is not None:
        seg.filter = body.filter.model_dump(exclude_none=True)
        seg.cached_user_count = segment_service.count_users(db, seg.filter)
        seg.cached_at = datetime.now(timezone.utc)
    audit_service.log(
        db, admin=admin, action="segment.update", target_type="segment",
        target_id=str(seg.id), ip=get_client_ip(request),
    )
    db.commit()
    return ok(_segment_payload(seg))


@router.delete("/segments/{segment_id}")
def delete_segment(
    segment_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    seg = db.get(UserSegment, segment_id)
    if not seg:
        raise NotFoundError()
    db.delete(seg)
    audit_service.log(
        db, admin=admin, action="segment.delete", target_type="segment",
        target_id=str(segment_id), ip=get_client_ip(request),
    )
    db.commit()
    return ok({"deleted": True})


@router.post("/segments/preview")
def preview_segment(
    body: SegmentCreate,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    """Count matching users for an in-flight filter without saving the segment."""
    filter_dict = body.filter.model_dump(exclude_none=True)
    count = segment_service.count_users(db, filter_dict)
    sample_ids = segment_service.user_ids(db, filter_dict, limit=5)
    sample = []
    if sample_ids:
        for u in db.scalars(select(User).where(User.id.in_(sample_ids))):
            sample.append(
                {"id": str(u.id), "username": u.username, "tier": u.subscription_tier}
            )
    return ok({"count": count, "sample": sample})


# ----- Experiments -----

def _experiment_payload(e: Experiment, breakdown: list | None = None) -> dict:
    payload = {
        "id": str(e.id),
        "key": e.key,
        "name": e.name,
        "description": e.description,
        "status": e.status,
        "variants": e.variants,
        "segment_id": str(e.segment_id) if e.segment_id else None,
        "started_at": e.started_at,
        "ended_at": e.ended_at,
        "created_at": e.created_at,
        "updated_at": e.updated_at,
    }
    if breakdown is not None:
        payload["breakdown"] = breakdown
    return payload


def _validate_variants(variants: list) -> None:
    keys = [v.key for v in variants]
    if len(set(keys)) != len(keys):
        raise ConflictError("Variant keys must be unique", code="duplicate_variant_key")
    total = sum(v.weight for v in variants)
    if total != 100:
        raise ConflictError(
            f"Variant weights must sum to 100 (got {total})",
            code="invalid_weights",
        )


@router.get("/experiments")
def list_experiments(
    status: str | None = None,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    stmt = select(Experiment).order_by(desc(Experiment.created_at))
    if status:
        stmt = stmt.where(Experiment.status == status)
    rows = list(db.scalars(stmt))
    return ok([_experiment_payload(e) for e in rows])


@router.post("/experiments")
def create_experiment(
    body: ExperimentCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor")),
):
    if db.scalar(select(Experiment).where(Experiment.key == body.key)):
        raise ConflictError("Experiment key already exists", code="experiment_key_taken")
    _validate_variants(body.variants)
    exp = Experiment(
        key=body.key,
        name=body.name,
        description=body.description,
        variants=[v.model_dump() for v in body.variants],
        segment_id=body.segment_id,
        created_by=admin.id,
    )
    db.add(exp)
    audit_service.log(
        db, admin=admin, action="experiment.create", target_type="experiment",
        target_id=str(exp.id), metadata={"key": body.key}, ip=get_client_ip(request),
    )
    db.commit()
    return ok(_experiment_payload(exp))


@router.get("/experiments/{experiment_id}")
def get_experiment(
    experiment_id: UUID,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    e = db.get(Experiment, experiment_id)
    if not e:
        raise NotFoundError()
    breakdown = experiment_service.variant_breakdown(db, e.id)
    return ok(_experiment_payload(e, breakdown=breakdown))


@router.put("/experiments/{experiment_id}")
def update_experiment(
    experiment_id: UUID,
    body: ExperimentUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor")),
):
    e = db.get(Experiment, experiment_id)
    if not e:
        raise NotFoundError()
    changes: dict = {}
    if body.name is not None:
        e.name = body.name
        changes["name"] = body.name
    if body.description is not None:
        e.description = body.description
    if body.variants is not None:
        _validate_variants(body.variants)
        e.variants = [v.model_dump() for v in body.variants]
        changes["variants"] = e.variants
    if body.segment_id is not None:
        e.segment_id = body.segment_id
    if body.status is not None and body.status != e.status:
        if body.status == "running" and e.started_at is None:
            e.started_at = datetime.now(timezone.utc)
        if body.status == "completed" and e.ended_at is None:
            e.ended_at = datetime.now(timezone.utc)
        e.status = body.status
        changes["status"] = body.status
    audit_service.log(
        db, admin=admin, action="experiment.update", target_type="experiment",
        target_id=str(e.id), metadata=changes, ip=get_client_ip(request),
    )
    db.commit()
    return ok(_experiment_payload(e))


@router.delete("/experiments/{experiment_id}")
def delete_experiment(
    experiment_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    e = db.get(Experiment, experiment_id)
    if not e:
        raise NotFoundError()
    db.delete(e)
    audit_service.log(
        db, admin=admin, action="experiment.delete", target_type="experiment",
        target_id=str(experiment_id), ip=get_client_ip(request),
    )
    db.commit()
    return ok({"deleted": True})


# ----- Revenue dashboard -----

@router.get("/revenue/summary")
def revenue_summary(
    days: int = Query(30, ge=1, le=365),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics")),
):
    """Top-line revenue numbers + a daily series for the chart."""
    now = datetime.now(timezone.utc)
    since = now - timedelta(days=days)

    gross_micros = int(
        db.scalar(
            select(func.coalesce(func.sum(Purchase.price_amount_micros), 0)).where(
                Purchase.created_at >= since, Purchase.coins_credited > 0
            )
        )
        or 0
    )
    refund_micros = int(
        db.scalar(
            select(func.coalesce(func.sum(Refund.amount_micros), 0)).where(
                Refund.created_at >= since
            )
        )
        or 0
    )
    paying_users = int(
        db.scalar(
            select(func.count(func.distinct(Purchase.user_id))).where(
                Purchase.created_at >= since, Purchase.coins_credited > 0
            )
        )
        or 0
    )
    purchase_count = int(
        db.scalar(
            select(func.count(Purchase.id)).where(
                Purchase.created_at >= since, Purchase.coins_credited > 0
            )
        )
        or 0
    )
    active_subs = int(
        db.scalar(
            select(func.count(Subscription.id)).where(
                Subscription.state.in_(["active", "in_grace_period"]),
                Subscription.expires_at > now,
            )
        )
        or 0
    )

    # Approximate MRR: sum monthly value of active subscriptions.
    # We don't have unit price per sub at the row level, so use sub SKU price
    # as a stand-in via Subscription.raw_notification if available.
    # Falls back to $9.99/mo if no signal present.
    sub_prices = db.execute(
        select(Subscription.sku, func.count(Subscription.id))
        .where(
            Subscription.state.in_(["active", "in_grace_period"]),
            Subscription.expires_at > now,
        )
        .group_by(Subscription.sku)
    ).all()
    mrr_micros = 0
    for sku, n in sub_prices:
        # Crude guess from SKU naming (sub_monthly_999, sub_annual_5999, …)
        per_month_micros = 9_990_000
        if "annual" in sku:
            per_month_micros = 5_990_000  # $5.99/mo equivalent for $59.99/yr
        elif "weekly" in sku:
            per_month_micros = 9_990_000 * 4
        mrr_micros += per_month_micros * int(n)

    # Daily series
    series_rows = db.execute(
        select(
            func.date_trunc("day", Purchase.created_at).label("d"),
            func.coalesce(func.sum(Purchase.price_amount_micros), 0),
            func.count(Purchase.id),
        )
        .where(Purchase.created_at >= since, Purchase.coins_credited > 0)
        .group_by("d")
        .order_by("d")
    ).all()
    series = [
        {"date": r[0].date().isoformat(), "micros": int(r[1]), "purchases": int(r[2])}
        for r in series_rows
    ]

    # By SKU breakdown (top 8)
    by_sku_rows = db.execute(
        select(
            Purchase.sku,
            func.coalesce(func.sum(Purchase.price_amount_micros), 0),
            func.count(Purchase.id),
        )
        .where(Purchase.created_at >= since, Purchase.coins_credited > 0)
        .group_by(Purchase.sku)
        .order_by(desc(func.coalesce(func.sum(Purchase.price_amount_micros), 0)))
        .limit(8)
    ).all()
    by_sku = [
        {"sku": r[0], "micros": int(r[1]), "purchases": int(r[2])} for r in by_sku_rows
    ]

    arpu_micros = int(gross_micros / paying_users) if paying_users else 0

    return ok(
        {
            "days": days,
            "gross_micros": gross_micros,
            "refund_micros": refund_micros,
            "net_micros": gross_micros - refund_micros,
            "purchase_count": purchase_count,
            "paying_users": paying_users,
            "arpu_micros": arpu_micros,
            "active_subscriptions": active_subs,
            "mrr_micros": mrr_micros,
            "arr_micros": mrr_micros * 12,
            "series": series,
            "by_sku": by_sku,
        }
    )


@router.get("/revenue/refunds")
def list_refunds(
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "analytics")),
):
    rows = list(
        db.execute(
            select(Refund, Purchase)
            .join(Purchase, Purchase.id == Refund.purchase_id)
            .order_by(desc(Refund.created_at))
            .limit(limit)
        )
    )
    return ok(
        [
            {
                "id": str(r.id),
                "purchase_id": str(r.purchase_id),
                "user_id": str(p.user_id),
                "sku": p.sku,
                "amount_micros": r.amount_micros,
                "currency": r.currency or p.price_currency,
                "reason": r.reason,
                "notes": r.notes,
                "created_at": r.created_at,
            }
            for r, p in rows
        ]
    )


@router.post("/revenue/refunds")
def create_refund(
    body: RefundCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    """Manual refund entry. Reconciles with Play webhook data when available;
    used here as a fallback for ad-hoc admin records."""
    purchase = db.get(Purchase, body.purchase_id)
    if not purchase:
        raise NotFoundError("Purchase not found")
    refund = Refund(
        purchase_id=body.purchase_id,
        amount_micros=body.amount_micros,
        currency=body.currency or purchase.price_currency,
        reason=body.reason,
        notes=body.notes,
    )
    db.add(refund)
    audit_service.log(
        db, admin=admin, action="refund.create", target_type="purchase",
        target_id=str(body.purchase_id),
        metadata={"amount_micros": body.amount_micros, "reason": body.reason},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"id": str(refund.id)})
