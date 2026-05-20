import threading
import uuid

import pytest

from app.db.session import SessionLocal
from app.models import Chapter, Story, User, UserChapterUnlock
from app.services import chapter_service, coin_service


def _make_story(db) -> Story:
    s = Story(
        title="Test Story " + uuid.uuid4().hex[:6],
        slug="test-" + uuid.uuid4().hex[:8],
        genre="drama",
        status="published",
    )
    db.add(s)
    db.flush()
    return s


def _make_chapter(db, story, *, n=1, free=False, cost=10) -> Chapter:
    c = Chapter(
        story_id=story.id,
        chapter_number=n,
        title=f"Ch {n}",
        content="word " * 100,
        word_count=100,
        is_free=free,
        coin_cost=cost,
        status="published",
    )
    db.add(c)
    db.flush()
    return c


def _make_user(db, balance=0) -> User:
    u = User(
        username="u_" + uuid.uuid4().hex[:8],
        email=f"u{uuid.uuid4().hex[:6]}@test.local",
        password_hash="x",
        coin_balance=balance,
    )
    db.add(u)
    db.flush()
    return u


def test_unlock_chapter_with_coins(db):
    story = _make_story(db)
    chapter = _make_chapter(db, story, cost=10)
    user = _make_user(db, balance=50)
    db.commit()

    chapter, balance = chapter_service.unlock_with_coins(
        db, user=user, chapter_id=chapter.id, idempotency_key=None
    )
    assert balance == 40
    db.commit()

    chapter2, balance2 = chapter_service.unlock_with_coins(
        db, user=user, chapter_id=chapter.id, idempotency_key=None
    )
    assert balance2 == 40


def test_unlock_insufficient_coins_raises(db):
    story = _make_story(db)
    chapter = _make_chapter(db, story, cost=100)
    user = _make_user(db, balance=10)
    db.commit()
    from app.core.exceptions import PaymentRequiredError

    with pytest.raises(PaymentRequiredError):
        chapter_service.unlock_with_coins(
            db, user=user, chapter_id=chapter.id, idempotency_key=None
        )


def test_concurrent_unlocks_do_not_oversell(db):
    story = _make_story(db)
    chapter = _make_chapter(db, story, cost=30)
    user = _make_user(db, balance=50)
    db.commit()
    user_id = user.id
    chapter_id = chapter.id

    errors: list[Exception] = []
    successes: list[int] = []

    def attempt():
        s = SessionLocal()
        try:
            u = s.get(User, user_id)
            try:
                _, bal = chapter_service.unlock_with_coins(
                    s, user=u, chapter_id=chapter_id,
                    idempotency_key=f"c-{threading.get_ident()}",
                )
                s.commit()
                successes.append(bal)
            except Exception as e:
                errors.append(e)
                s.rollback()
        finally:
            s.close()

    threads = [threading.Thread(target=attempt) for _ in range(5)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    s = SessionLocal()
    try:
        u = s.get(User, user_id)
        assert u.coin_balance >= 0
        assert u.coin_balance <= 50
    finally:
        s.close()
