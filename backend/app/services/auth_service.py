import hashlib
import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional

from sqlalchemy import and_, or_, select
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import (
    ConflictError,
    ForbiddenError,
    UnauthorizedError,
    ValidationError,
)
from app.core.security import (
    create_access_token,
    generate_refresh_token,
    generate_verification_token,
    hash_password,
    needs_rehash,
    verify_password,
)
from app.models import EmailVerification, RefreshToken, User
from app.schemas.auth import AuthResponse, TokenPair


def _hash_refresh(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _username_taken(db: Session, username: str) -> bool:
    return db.scalar(select(User).where(User.username == username)) is not None


def _email_taken(db: Session, email: str) -> bool:
    return db.scalar(select(User).where(User.email == email)) is not None


def register_user(
    db: Session,
    *,
    email: str,
    username: str,
    password: str,
    birth_year: int | None,
    user_agent: str | None,
    ip: str | None,
    device_fingerprint: str | None,
) -> AuthResponse:
    if _email_taken(db, email):
        raise ConflictError("Email already registered", code="email_taken")
    if _username_taken(db, username):
        raise ConflictError("Username already taken", code="username_taken")

    user = User(
        email=email,
        username=username,
        password_hash=hash_password(password),
        is_guest=False,
        is_verified=False,
        birth_year=birth_year,
    )
    db.add(user)
    db.flush()
    return _issue_auth(db, user, user_agent=user_agent, ip=ip, device_fingerprint=device_fingerprint)


def login_user(
    db: Session,
    *,
    email: str,
    password: str,
    user_agent: str | None,
    ip: str | None,
    device_fingerprint: str | None,
) -> AuthResponse:
    user = db.scalar(select(User).where(User.email == email))
    now = datetime.now(timezone.utc)
    if not user or not user.password_hash or user.deleted_at is not None:
        raise UnauthorizedError("Invalid credentials", code="invalid_credentials")
    if user.locked_until and user.locked_until > now:
        raise ForbiddenError("Account temporarily locked", code="account_locked")
    if user.is_banned:
        raise ForbiddenError("Account banned", code="banned")

    if not verify_password(password, user.password_hash):
        user.failed_login_count = (user.failed_login_count or 0) + 1
        if user.failed_login_count >= 8:
            user.locked_until = now + timedelta(minutes=15)
        db.flush()
        raise UnauthorizedError("Invalid credentials", code="invalid_credentials")

    if needs_rehash(user.password_hash):
        user.password_hash = hash_password(password)

    user.failed_login_count = 0
    user.locked_until = None
    user.last_login_at = now
    db.flush()
    return _issue_auth(db, user, user_agent=user_agent, ip=ip, device_fingerprint=device_fingerprint)


def guest_register(
    db: Session,
    *,
    device_fingerprint: str,
    user_agent: str | None,
    ip: str | None,
) -> AuthResponse:
    suffix = secrets.token_hex(4)
    username = f"guest_{suffix}"
    while _username_taken(db, username):
        suffix = secrets.token_hex(4)
        username = f"guest_{suffix}"
    user = User(username=username, is_guest=True, is_verified=False)
    db.add(user)
    db.flush()
    return _issue_auth(db, user, user_agent=user_agent, ip=ip, device_fingerprint=device_fingerprint)


def _issue_auth(
    db: Session,
    user: User,
    *,
    user_agent: str | None,
    ip: str | None,
    device_fingerprint: str | None,
    parent_id=None,
) -> AuthResponse:
    _enforce_session_cap(db, user.id)

    refresh_plain = generate_refresh_token()
    refresh_expires = datetime.now(timezone.utc) + timedelta(
        days=settings.JWT_REFRESH_TOKEN_EXPIRE_DAYS
    )
    rt = RefreshToken(
        user_id=user.id,
        token_hash=_hash_refresh(refresh_plain),
        parent_id=parent_id,
        device_fingerprint=device_fingerprint,
        user_agent=user_agent,
        ip_address=ip,
        expires_at=refresh_expires,
    )
    db.add(rt)
    db.flush()

    access, access_exp = create_access_token(
        str(user.id),
        claims={"username": user.username, "tier": user.subscription_tier, "is_guest": user.is_guest},
    )

    return AuthResponse(
        user_id=user.id,
        username=user.username,
        is_guest=user.is_guest,
        coin_balance=user.coin_balance,
        subscription_tier=user.subscription_tier,
        tokens=TokenPair(access_token=access, refresh_token=refresh_plain, expires_at=access_exp),
    )


def _enforce_session_cap(db: Session, user_id) -> None:
    now = datetime.now(timezone.utc)
    active = list(
        db.scalars(
            select(RefreshToken)
            .where(
                and_(
                    RefreshToken.user_id == user_id,
                    RefreshToken.revoked_at.is_(None),
                    RefreshToken.expires_at > now,
                )
            )
            .order_by(RefreshToken.issued_at.desc())
        )
    )
    if len(active) >= settings.MAX_SESSIONS_PER_USER:
        for rt in active[settings.MAX_SESSIONS_PER_USER - 1 :]:
            rt.revoked_at = now
            rt.revoked_reason = "session_cap"


def refresh_tokens(
    db: Session,
    *,
    refresh_token: str,
    user_agent: str | None,
    ip: str | None,
    device_fingerprint: str | None,
) -> AuthResponse:
    token_hash = _hash_refresh(refresh_token)
    rt = db.scalar(select(RefreshToken).where(RefreshToken.token_hash == token_hash))
    now = datetime.now(timezone.utc)
    if not rt:
        raise UnauthorizedError("Invalid refresh token", code="invalid_refresh")
    if rt.revoked_at is not None:
        _revoke_descendants(db, rt.user_id, reason="reuse_detected")
        raise UnauthorizedError("Refresh token reuse detected", code="reuse_detected")
    if rt.expires_at <= now:
        raise UnauthorizedError("Refresh token expired", code="refresh_expired")

    user = db.get(User, rt.user_id)
    if not user or user.is_banned or user.deleted_at is not None:
        raise UnauthorizedError("User not active")

    rt.revoked_at = now
    rt.revoked_reason = "rotated"
    db.flush()
    return _issue_auth(
        db, user, user_agent=user_agent, ip=ip, device_fingerprint=device_fingerprint, parent_id=rt.id
    )


def create_password_reset(db: Session, *, email: str) -> str | None:
    user = db.scalar(select(User).where(User.email == email, User.deleted_at.is_(None)))
    if not user or user.is_guest:
        return None

    now = datetime.now(timezone.utc)
    db.execute(
        EmailVerification.__table__.update()
        .where(
            and_(
                EmailVerification.user_id == user.id,
                EmailVerification.purpose == "password_reset",
                EmailVerification.used_at.is_(None),
            )
        )
        .values(used_at=now)
    )
    token = generate_verification_token()
    db.add(
        EmailVerification(
            user_id=user.id,
            token_hash=_hash_token(token),
            purpose="password_reset",
            expires_at=now + timedelta(minutes=settings.PASSWORD_RESET_TTL_MINUTES),
        )
    )
    db.flush()
    return token


def reset_password(db: Session, *, token: str, new_password: str) -> None:
    token_hash = _hash_token(token)
    now = datetime.now(timezone.utc)
    record = db.scalar(
        select(EmailVerification).where(
            EmailVerification.token_hash == token_hash,
            EmailVerification.purpose == "password_reset",
        )
    )
    if not record or record.used_at is not None or record.expires_at <= now:
        raise UnauthorizedError("Invalid or expired reset token", code="invalid_reset_token")

    user = db.get(User, record.user_id)
    if not user or user.deleted_at is not None or user.is_banned:
        raise UnauthorizedError("User not active")

    user.password_hash = hash_password(new_password)
    user.is_guest = False
    user.failed_login_count = 0
    user.locked_until = None
    record.used_at = now
    _revoke_descendants(db, user.id, reason="password_reset")
    db.flush()


def _revoke_descendants(db: Session, user_id, *, reason: str) -> None:
    now = datetime.now(timezone.utc)
    db.execute(
        RefreshToken.__table__.update()
        .where(
            and_(
                RefreshToken.user_id == user_id,
                RefreshToken.revoked_at.is_(None),
            )
        )
        .values(revoked_at=now, revoked_reason=reason)
    )


def logout(db: Session, *, refresh_token: Optional[str], user: User) -> None:
    now = datetime.now(timezone.utc)
    if refresh_token:
        rt = db.scalar(
            select(RefreshToken).where(RefreshToken.token_hash == _hash_refresh(refresh_token))
        )
        if rt and rt.user_id == user.id and rt.revoked_at is None:
            rt.revoked_at = now
            rt.revoked_reason = "logout"
    db.flush()


def logout_all(db: Session, *, user: User) -> None:
    _revoke_descendants(db, user.id, reason="logout_all")


def google_sign_in(
    db: Session,
    *,
    google_sub: str,
    email: str | None,
    name: str | None,
    picture: str | None,
    user_agent: str | None,
    ip: str | None,
    device_fingerprint: str | None,
) -> AuthResponse:
    user = db.scalar(select(User).where(User.google_id == google_sub))
    if not user and email:
        user = db.scalar(select(User).where(User.email == email))
        if user:
            user.google_id = google_sub
    if not user:
        base = (name or (email.split("@")[0] if email else "user")).lower()
        base = "".join(ch if ch.isalnum() or ch in "._" else "_" for ch in base)[:40] or "user"
        username = base
        while _username_taken(db, username):
            username = f"{base}_{secrets.token_hex(2)}"
        user = User(
            email=email,
            username=username,
            google_id=google_sub,
            avatar_url=picture,
            is_verified=bool(email),
            is_guest=False,
        )
        db.add(user)
        db.flush()

    if user.is_banned or user.deleted_at is not None:
        raise ForbiddenError("Account not available")
    user.last_login_at = datetime.now(timezone.utc)
    db.flush()
    return _issue_auth(db, user, user_agent=user_agent, ip=ip, device_fingerprint=device_fingerprint)


def schedule_account_deletion(db: Session, *, user: User) -> None:
    now = datetime.now(timezone.utc)
    user.deleted_at = now
    user.email = f"deleted+{user.id}@deleted.local"
    user.phone = None
    user.password_hash = None
    user.google_id = None
    user.apple_id = None
    user.avatar_url = None
    _revoke_descendants(db, user.id, reason="account_deleted")
    db.flush()
