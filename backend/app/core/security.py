import secrets
from datetime import datetime, timedelta, timezone
from typing import Any

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

from app.config import settings

_ph = PasswordHasher(
    time_cost=settings.PASSWORD_ARGON2_TIME_COST,
    memory_cost=settings.PASSWORD_ARGON2_MEMORY_COST,
    parallelism=settings.PASSWORD_ARGON2_PARALLELISM,
)


def hash_password(plain: str) -> str:
    return _ph.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return _ph.verify(hashed, plain)
    except VerifyMismatchError:
        return False
    except Exception:
        return False


def needs_rehash(hashed: str) -> bool:
    try:
        return _ph.check_needs_rehash(hashed)
    except Exception:
        return False


def create_access_token(subject: str, *, claims: dict[str, Any] | None = None) -> tuple[str, datetime]:
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES)
    payload: dict[str, Any] = {
        "sub": subject,
        "type": "access",
        "iat": int(datetime.now(timezone.utc).timestamp()),
        "exp": int(expire.timestamp()),
    }
    if claims:
        payload.update(claims)
    token = jwt.encode(
        payload,
        settings.JWT_SECRET_KEY,
        algorithm=settings.JWT_ALGORITHM,
        headers={"kid": settings.JWT_KEY_ID},
    )
    return token, expire


def _build_key_set() -> dict[str, str]:
    """Returns all active signing keys keyed by kid for rotation support."""
    keys = {settings.JWT_KEY_ID: settings.JWT_SECRET_KEY}
    if settings.JWT_SECONDARY_SECRET_KEY and settings.JWT_SECONDARY_KEY_ID:
        keys[settings.JWT_SECONDARY_KEY_ID] = settings.JWT_SECONDARY_SECRET_KEY
    return keys


def decode_access_token(token: str) -> dict[str, Any]:
    key_set = _build_key_set()
    try:
        header = jwt.get_unverified_header(token)
        kid = header.get("kid", settings.JWT_KEY_ID)
    except jwt.exceptions.DecodeError:
        raise
    secret = key_set.get(kid)
    if not secret:
        raise jwt.exceptions.InvalidKeyError(f"Unknown key id: {kid}")
    return jwt.decode(token, secret, algorithms=[settings.JWT_ALGORITHM])


def generate_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def generate_verification_token() -> str:
    return secrets.token_urlsafe(32)


def generate_short_code(n: int = 6) -> str:
    return "".join(secrets.choice("0123456789") for _ in range(n))
