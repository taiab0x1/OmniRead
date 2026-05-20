from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import desc, select
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_current_user
from app.models import (
    Bookmark,
    Chapter,
    CoinTransaction,
    ContentReport,
    Experiment,
    FcmToken,
    Notification,
    ReadingProgress,
    Story,
    User,
    UserChapterUnlock,
)
from app.schemas.common import ok
from app.schemas.user import (
    CoinBalance,
    CoinTransactionItem,
    FcmRegisterRequest,
    NotificationItem,
    StreakInfo,
    UserPublic,
    UserUpdate,
)
from app.services import auth_service, coin_service, experiment_service

router = APIRouter()


@router.get("/profile")
def me(user: User = Depends(get_current_user)):
    return ok(UserPublic.model_validate(user, from_attributes=True).model_dump())


@router.put("/profile")
def update_profile(
    body: UserUpdate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if body.username and body.username != user.username:
        existing = db.scalar(select(User).where(User.username == body.username, User.id != user.id))
        if existing:
            raise ConflictError("Username taken", code="username_taken")
        user.username = body.username
    if body.avatar_url is not None:
        user.avatar_url = body.avatar_url
    if body.preferred_genres is not None:
        user.preferred_genres = body.preferred_genres
    if body.locale:
        user.locale = body.locale
    if body.region:
        user.region = body.region
    db.commit()
    return ok(UserPublic.model_validate(user, from_attributes=True).model_dump())


@router.get("/coins")
def coins(user: User = Depends(get_current_user)):
    return ok(
        CoinBalance(
            balance=user.coin_balance,
            subscription_tier=user.subscription_tier,
            subscription_expires_at=user.subscription_expires_at,
        ).model_dump()
    )


@router.get("/coins/transactions")
def coin_history(
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    rows = list(
        db.scalars(
            select(CoinTransaction)
            .where(CoinTransaction.user_id == user.id)
            .order_by(desc(CoinTransaction.created_at))
            .limit(limit)
        )
    )
    return ok([CoinTransactionItem.model_validate(r, from_attributes=True).model_dump() for r in rows])


@router.post("/coins/claim-daily")
def claim_daily(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    tx = coin_service.claim_daily_login(db, user_id=user.id)
    db.commit()
    if tx is None:
        return ok({"claimed": False, "message": "Already claimed today"})
    return ok({"claimed": True, "amount": tx.amount, "balance": tx.balance_after})


@router.get("/bookmarks")
def list_bookmarks(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=100),
):
    rows = list(
        db.execute(
            select(Story)
            .join(Bookmark, Bookmark.story_id == Story.id)
            .where(Bookmark.user_id == user.id, Story.status == "published")
            .order_by(desc(Bookmark.created_at))
            .limit(limit)
        ).scalars()
    )
    return ok([{"id": str(s.id), "title": s.title, "cover_url": s.cover_url, "genre": s.genre} for s in rows])


@router.get("/history")
def history(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=100),
):
    rows = list(
        db.execute(
            select(ReadingProgress, Story)
            .join(Story, Story.id == ReadingProgress.story_id)
            .where(ReadingProgress.user_id == user.id)
            .order_by(desc(ReadingProgress.last_read_at))
            .limit(limit)
        )
    )
    items = []
    for prog, story in rows:
        items.append(
            {
                "story_id": str(story.id),
                "title": story.title,
                "cover_url": story.cover_url,
                "chapter_id": str(prog.chapter_id),
                "scroll_position": prog.scroll_position,
                "completed": prog.completed,
                "last_read_at": prog.last_read_at,
            }
        )
    return ok(items)


@router.get("/unlocks")
def unlocks(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    limit: int = Query(100, ge=1, le=500),
):
    rows = list(
        db.execute(
            select(UserChapterUnlock, Chapter)
            .join(Chapter, Chapter.id == UserChapterUnlock.chapter_id)
            .where(UserChapterUnlock.user_id == user.id)
            .order_by(desc(UserChapterUnlock.unlocked_at))
            .limit(limit)
        )
    )
    return ok(
        [
            {
                "chapter_id": str(unlock.chapter_id),
                "story_id": str(chap.story_id),
                "method": unlock.unlock_method,
                "coins_spent": unlock.coins_spent,
                "unlocked_at": unlock.unlocked_at,
            }
            for unlock, chap in rows
        ]
    )


@router.get("/streak")
def streak(user: User = Depends(get_current_user)):
    next_reward = max(0, 7 - (user.reading_streak % 7))
    return ok(
        StreakInfo(
            current_streak=user.reading_streak,
            last_read_at=user.last_read_at,
            next_reward_in_days=next_reward,
        ).model_dump()
    )


@router.get("/notifications")
def notifications(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=200),
):
    rows = list(
        db.scalars(
            select(Notification)
            .where(Notification.user_id == user.id)
            .order_by(desc(Notification.created_at))
            .limit(limit)
        )
    )
    return ok([NotificationItem.model_validate(r, from_attributes=True).model_dump() for r in rows])


@router.put("/notifications/read")
def mark_read(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    db.execute(
        Notification.__table__.update()
        .where(Notification.user_id == user.id, Notification.is_read.is_(False))
        .values(is_read=True)
    )
    db.commit()
    return ok({"marked_read": True})


@router.post("/fcm-token")
def register_fcm(
    body: FcmRegisterRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    existing = db.scalar(select(FcmToken).where(FcmToken.token == body.token))
    if existing:
        existing.user_id = user.id
        existing.app_version = body.app_version
        existing.last_seen_at = datetime.now(timezone.utc)
    else:
        db.add(
            FcmToken(
                user_id=user.id,
                token=body.token,
                platform=body.platform,
                app_version=body.app_version,
            )
        )
    db.commit()
    return ok({"registered": True})


@router.post("/report")
def report_content(
    target_type: str,
    target_id: str,
    reason: str,
    notes: str | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    if target_type not in {"story", "chapter", "comment", "user"}:
        raise NotFoundError("Invalid target_type")
    db.add(
        ContentReport(
            reporter_id=user.id,
            target_type=target_type,
            target_id=target_id,
            reason=reason,
            notes=notes,
        )
    )
    db.commit()
    return ok({"submitted": True})


@router.get("/experiments")
def my_experiments(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Returns the user's current variant for every running experiment.

    The client uses this to gate UI/UX choices. Assignments are sticky once
    written — re-querying returns the same variant.
    """
    running = list(db.scalars(select(Experiment).where(Experiment.status == "running")))
    out: dict[str, dict] = {}
    for exp in running:
        variant = experiment_service.assign(db, exp, user)
        if variant is None:
            continue
        # Find the variant's config (if any) for the client to read
        cfg = None
        for v in exp.variants:
            if v.get("key") == variant:
                cfg = v.get("config")
                break
        out[exp.key] = {"variant": variant, "config": cfg}
    return ok(out)


@router.get("/export")
def export_my_data(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """GDPR / DPDP / CCPA DSAR — returns all data held for this user."""
    import json as _json

    from fastapi.responses import Response

    transactions = list(
        db.scalars(select(CoinTransaction).where(CoinTransaction.user_id == user.id).order_by(CoinTransaction.created_at))
    )
    bms = list(
        db.execute(
            select(Story.id, Story.title)
            .join(Bookmark, Bookmark.story_id == Story.id)
            .where(Bookmark.user_id == user.id)
        ).all()
    )
    hist = list(
        db.scalars(
            select(ReadingProgress).where(ReadingProgress.user_id == user.id).order_by(ReadingProgress.last_read_at)
        )
    )
    unlocks_list = list(
        db.execute(
            select(UserChapterUnlock, Chapter)
            .join(Chapter, Chapter.id == UserChapterUnlock.chapter_id)
            .where(UserChapterUnlock.user_id == user.id)
        )
    )
    notifs = list(
        db.scalars(select(Notification).where(Notification.user_id == user.id).order_by(Notification.created_at))
    )
    payload = {
        "export_generated_at": datetime.now(timezone.utc).isoformat(),
        "user": UserPublic.model_validate(user, from_attributes=True).model_dump(mode="json"),
        "coin_transactions": [
            {"amount": t.amount, "type": t.type, "description": t.description, "balance_after": t.balance_after, "created_at": t.created_at.isoformat()}
            for t in transactions
        ],
        "bookmarks": [{"story_id": str(sid), "title": stitle} for sid, stitle in bms],
        "reading_history": [
            {"story_id": str(h.story_id), "chapter_id": str(h.chapter_id), "scroll_position": h.scroll_position, "completed": h.completed, "last_read_at": h.last_read_at.isoformat() if h.last_read_at else None}
            for h in hist
        ],
        "unlocked_chapters": [
            {"chapter_id": str(u.chapter_id), "story_id": str(c.story_id), "method": u.unlock_method, "coins_spent": u.coins_spent, "unlocked_at": u.unlocked_at.isoformat()}
            for u, c in unlocks_list
        ],
        "notifications": [
            {"type": n.type, "title": n.title, "body": n.body, "is_read": n.is_read, "created_at": n.created_at.isoformat()}
            for n in notifs
        ],
    }
    return Response(
        content=_json.dumps(payload, indent=2),
        media_type="application/json",
        headers={"Content-Disposition": f'attachment; filename="omniread-export-{user.id}.json"'},
    )
def experiment_converted(
    experiment_key: str,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Mark the user's assignment for `experiment_key` as converted (one-shot)."""
    exp = db.scalar(select(Experiment).where(Experiment.key == experiment_key))
    if not exp:
        raise NotFoundError("Experiment not found")
    changed = experiment_service.mark_converted(db, exp.id, user.id)
    return ok({"converted": changed})
