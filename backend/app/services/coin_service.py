from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import ConflictError, PaymentRequiredError
from app.models import CoinTransaction, User


def get_balance(db: Session, user_id: UUID) -> int:
    return int(db.execute(select(User.coin_balance).where(User.id == user_id)).scalar_one())


def credit(
    db: Session,
    *,
    user_id: UUID,
    amount: int,
    type_: str,
    description: str | None = None,
    reference_id: UUID | None = None,
    idempotency_key: str | None = None,
) -> CoinTransaction:
    if amount <= 0:
        raise ValueError("credit amount must be positive")
    if idempotency_key:
        existing = db.scalar(
            select(CoinTransaction).where(CoinTransaction.idempotency_key == idempotency_key)
        )
        if existing:
            return existing

    result = db.execute(
        update(User)
        .where(User.id == user_id)
        .values(coin_balance=User.coin_balance + amount)
        .returning(User.coin_balance)
    )
    row = result.first()
    if not row:
        raise ConflictError("User not found", code="user_not_found")
    new_balance = int(row[0])
    tx = CoinTransaction(
        user_id=user_id,
        amount=amount,
        type=type_,
        description=description,
        reference_id=reference_id,
        balance_after=new_balance,
        idempotency_key=idempotency_key,
    )
    db.add(tx)
    db.flush()
    return tx


def debit(
    db: Session,
    *,
    user_id: UUID,
    amount: int,
    type_: str,
    description: str | None = None,
    reference_id: UUID | None = None,
    idempotency_key: str | None = None,
) -> CoinTransaction:
    if amount <= 0:
        raise ValueError("debit amount must be positive")
    if idempotency_key:
        existing = db.scalar(
            select(CoinTransaction).where(CoinTransaction.idempotency_key == idempotency_key)
        )
        if existing:
            return existing

    result = db.execute(
        update(User)
        .where(User.id == user_id, User.coin_balance >= amount)
        .values(coin_balance=User.coin_balance - amount)
        .returning(User.coin_balance)
    )
    row = result.first()
    if not row:
        raise PaymentRequiredError("Insufficient coins", code="insufficient_coins")
    new_balance = int(row[0])
    tx = CoinTransaction(
        user_id=user_id,
        amount=-amount,
        type=type_,
        description=description,
        reference_id=reference_id,
        balance_after=new_balance,
        idempotency_key=idempotency_key,
    )
    db.add(tx)
    db.flush()
    return tx


def claim_daily_login(db: Session, *, user_id: UUID) -> CoinTransaction | None:
    today_key = datetime.now(timezone.utc).strftime("%Y%m%d")
    idem = f"daily-login:{user_id}:{today_key}"
    existing = db.scalar(select(CoinTransaction).where(CoinTransaction.idempotency_key == idem))
    if existing:
        return None
    return credit(
        db,
        user_id=user_id,
        amount=settings.DAILY_LOGIN_COINS,
        type_="daily_login",
        description="Daily login bonus",
        idempotency_key=idem,
    )
