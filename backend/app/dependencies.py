import hashlib

import jwt
from fastapi import Depends, Header, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from app.config import settings
from app.db.session import get_db
from app.models import AdminUser, User
from app.core.exceptions import ForbiddenError, UnauthorizedError

bearer = HTTPBearer(auto_error=False)


def _decode(token: str) -> dict:
    try:
        return jwt.decode(token, settings.JWT_SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise UnauthorizedError("Token expired", code="token_expired")
    except jwt.InvalidTokenError:
        raise UnauthorizedError("Invalid token", code="invalid_token")


def get_current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if not creds or creds.scheme.lower() != "bearer":
        raise UnauthorizedError()
    payload = _decode(creds.credentials)
    if payload.get("type") != "access" or payload.get("scope") == "admin":
        raise UnauthorizedError("Wrong token type")
    user = db.get(User, payload["sub"])
    if not user or user.is_banned or user.deleted_at is not None:
        raise UnauthorizedError("User not active")
    return user


def get_optional_user(
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User | None:
    if not creds:
        return None
    try:
        return get_current_user(creds, db)
    except Exception:
        return None


def get_current_admin(
    request: Request,
    creds: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> AdminUser:
    if settings.ADMIN_IP_ALLOWLIST:
        client_ip = request.client.host if request.client else None
        if client_ip not in settings.ADMIN_IP_ALLOWLIST:
            raise ForbiddenError("Admin IP not allowed", code="ip_not_allowed")
    if not creds:
        raise UnauthorizedError()
    payload = _decode(creds.credentials)
    if payload.get("scope") != "admin":
        raise UnauthorizedError("Not an admin token")
    admin = db.get(AdminUser, payload["sub"])
    if not admin or not admin.is_active:
        raise UnauthorizedError("Admin not active")
    return admin


def require_role(*allowed_roles: str):
    def dep(admin: AdminUser = Depends(get_current_admin)) -> AdminUser:
        if "super_admin" in allowed_roles or admin.role == "super_admin":
            return admin
        if admin.role not in allowed_roles:
            raise ForbiddenError(f"Requires one of: {','.join(allowed_roles)}")
        return admin
    return dep


def get_idempotency_key(idempotency_key: str | None = Header(None)) -> str | None:
    return idempotency_key


def get_client_ip(request: Request) -> str:
    fwd = request.headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.client.host if request.client else "0.0.0.0"


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()
