"""A/B experiment service — deterministic variant assignment + analytics.

Assignment uses a hash of (experiment.key + user.id) mapped to 0..99, then
walks the cumulative variant weights to pick the variant. This means a given
user always lands in the same bucket without persisting an assignment first,
but we DO persist on first exposure to lock in the bucket if variant weights
change later.
"""
from __future__ import annotations

import hashlib
from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import case, func, select
from sqlalchemy.orm import Session

from app.models import Experiment, ExperimentAssignment, User, UserSegment
from app.services import segment_service


def _bucket(experiment_key: str, user_id: str) -> int:
    """Deterministic 0..99 bucket for (experiment, user)."""
    h = hashlib.sha256(f"{experiment_key}:{user_id}".encode("utf-8")).digest()
    # Take first 4 bytes as unsigned int, mod 100
    n = int.from_bytes(h[:4], "big")
    return n % 100


def _pick_variant(experiment: Experiment, bucket: int) -> str | None:
    cumulative = 0
    for variant in experiment.variants:
        weight = int(variant.get("weight", 0))
        if weight <= 0:
            continue
        cumulative += weight
        if bucket < cumulative:
            return variant["key"]
    # If weights don't sum to 100, treat overflow as the last variant's bucket
    return experiment.variants[-1]["key"] if experiment.variants else None


def assign(db: Session, experiment: Experiment, user: User) -> str | None:
    """Return the user's variant for an experiment, creating an assignment
    row if this is the user's first exposure. Returns None if the user is
    outside the experiment's segment."""
    if experiment.status != "running":
        return None

    # Segment scope
    if experiment.segment_id:
        seg_row = db.get(UserSegment, experiment.segment_id)
        if seg_row:
            clauses = segment_service.filter_to_clause(seg_row.filter)
            matches = db.scalar(
                select(func.count(User.id)).where(User.id == user.id, *clauses)
            )
            if not matches:
                return None

    # Existing assignment? Stick to it.
    existing = db.scalar(
        select(ExperimentAssignment).where(
            ExperimentAssignment.experiment_id == experiment.id,
            ExperimentAssignment.user_id == user.id,
        )
    )
    if existing:
        if not existing.first_exposure_at:
            existing.first_exposure_at = datetime.now(timezone.utc)
            db.commit()
        return existing.variant

    bucket = _bucket(experiment.key, str(user.id))
    variant = _pick_variant(experiment, bucket)
    if variant is None:
        return None

    db.add(
        ExperimentAssignment(
            experiment_id=experiment.id,
            user_id=user.id,
            variant=variant,
            first_exposure_at=datetime.now(timezone.utc),
        )
    )
    db.commit()
    return variant


def mark_converted(db: Session, experiment_id: UUID, user_id: UUID) -> bool:
    row = db.scalar(
        select(ExperimentAssignment).where(
            ExperimentAssignment.experiment_id == experiment_id,
            ExperimentAssignment.user_id == user_id,
        )
    )
    if not row or row.converted_at is not None:
        return False
    row.converted_at = datetime.now(timezone.utc)
    db.commit()
    return True


def variant_breakdown(db: Session, experiment_id: UUID) -> list[dict]:
    """Per-variant counts of assignments + conversions for the analytics card."""
    rows = db.execute(
        select(
            ExperimentAssignment.variant,
            func.count(ExperimentAssignment.id),
            func.sum(case((ExperimentAssignment.converted_at.is_not(None), 1), else_=0)),
        )
        .where(ExperimentAssignment.experiment_id == experiment_id)
        .group_by(ExperimentAssignment.variant)
    ).all()
    out = []
    for variant, n, c in rows:
        n = int(n or 0)
        c = int(c or 0)
        out.append(
            {
                "variant": variant,
                "assigned": n,
                "converted": c,
                "conversion_rate": round(c / n, 4) if n else 0.0,
            }
        )
    return out
