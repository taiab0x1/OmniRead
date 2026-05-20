import os
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

os.environ.setdefault("DATABASE_URL", os.environ.get("TEST_DATABASE_URL", "postgresql+psycopg2://omniread:omniread@localhost:5432/omniread_test"))
os.environ.setdefault("JWT_SECRET_KEY", "test-secret-do-not-use-in-prod")
os.environ.setdefault("REDIS_URL", "redis://localhost:6379/15")
os.environ.setdefault("ENV", "test")

from app.config import settings  # noqa: E402
from app.db.session import Base, get_db  # noqa: E402
from app.db.redis import redis_client  # noqa: E402
from app.main import app  # noqa: E402

ENGINE = create_engine(settings.DATABASE_URL, future=True)
TestSessionLocal = sessionmaker(bind=ENGINE, autoflush=False, autocommit=False, future=True)


@pytest.fixture(scope="session", autouse=True)
def _bootstrap_schema():
    try:
        with ENGINE.connect() as conn:
            conn.exec_driver_sql("SELECT 1")
    except Exception:
        pytest.skip("Postgres test DB unavailable")
    import app.models  # noqa: F401
    Base.metadata.drop_all(ENGINE)
    Base.metadata.create_all(ENGINE)
    yield
    Base.metadata.drop_all(ENGINE)


@pytest.fixture
def db():
    session = TestSessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


@pytest.fixture
def client(db):
    redis_client.flushdb()

    def override():
        try:
            yield db
        finally:
            pass

    app.dependency_overrides[get_db] = override
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.pop(get_db, None)
    redis_client.flushdb()


@pytest.fixture
def fresh_email():
    return f"u_{uuid.uuid4().hex[:10]}@omniread.app"


@pytest.fixture
def fresh_username():
    return f"user_{uuid.uuid4().hex[:8]}"
