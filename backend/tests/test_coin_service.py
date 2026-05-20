import uuid

import pytest

from app.core.exceptions import PaymentRequiredError
from app.models import User
from app.services import coin_service


def test_credit_then_debit(db):
    u = User(
        username="coin_" + uuid.uuid4().hex[:8],
        email=f"coin{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
    )
    db.add(u)
    db.commit()

    coin_service.credit(db, user_id=u.id, amount=50, type_="test_credit")
    db.commit()
    assert coin_service.get_balance(db, u.id) == 50

    coin_service.debit(db, user_id=u.id, amount=20, type_="test_debit")
    db.commit()
    assert coin_service.get_balance(db, u.id) == 30


def test_debit_blocks_overdraft(db):
    u = User(
        username="coin_" + uuid.uuid4().hex[:8],
        email=f"coin2{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
        coin_balance=10,
    )
    db.add(u)
    db.commit()
    with pytest.raises(PaymentRequiredError):
        coin_service.debit(db, user_id=u.id, amount=100, type_="test_overdraft")


def test_idempotent_credit(db):
    u = User(
        username="coin_" + uuid.uuid4().hex[:8],
        email=f"coin3{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
    )
    db.add(u)
    db.commit()
    key = f"idem-{uuid.uuid4()}"
    coin_service.credit(db, user_id=u.id, amount=20, type_="t", idempotency_key=key)
    coin_service.credit(db, user_id=u.id, amount=20, type_="t", idempotency_key=key)
    db.commit()
    assert coin_service.get_balance(db, u.id) == 20


def test_daily_login_once_per_day(db):
    u = User(
        username="coin_" + uuid.uuid4().hex[:8],
        email=f"coin4{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
    )
    db.add(u)
    db.commit()
    a = coin_service.claim_daily_login(db, user_id=u.id)
    db.commit()
    b = coin_service.claim_daily_login(db, user_id=u.id)
    db.commit()
    assert a is not None
    assert b is None
