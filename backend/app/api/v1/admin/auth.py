from datetime import datetime, timezone

import pyotp
from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import ForbiddenError, UnauthorizedError
from app.core.rate_limit import fixed_window
from app.core.security import create_access_token, hash_password, verify_password
from app.db.session import get_db
from app.dependencies import get_client_ip, get_current_admin
from app.models import AdminUser
from app.schemas.admin import AdminLoginRequest, AdminMe, AdminTokenResponse, AdminTotpSetupResponse
from app.schemas.common import ok
from app.services import audit_service

router = APIRouter()


@router.post("/login")
def admin_login(
    body: AdminLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    ip = get_client_ip(request)
    if settings.ADMIN_IP_ALLOWLIST and ip not in settings.ADMIN_IP_ALLOWLIST:
        raise ForbiddenError("Admin IP not allowed", code="ip_not_allowed")
    fixed_window(f"rl:admin-login:{ip}", limit=10, period_seconds=60)
    admin = db.scalar(select(AdminUser).where(AdminUser.email == body.email))
    if not admin or not admin.is_active:
        raise UnauthorizedError("Invalid credentials", code="invalid_credentials")
    if not verify_password(body.password, admin.password_hash):
        raise UnauthorizedError("Invalid credentials", code="invalid_credentials")

    if admin.totp_secret:
        if not body.totp_code:
            raise UnauthorizedError("TOTP code required", code="totp_required")
        if not pyotp.TOTP(admin.totp_secret).verify(body.totp_code, valid_window=1):
            raise UnauthorizedError("Invalid TOTP code", code="invalid_totp")
        requires_setup = False
    else:
        requires_setup = True

    admin.last_login_at = datetime.now(timezone.utc)
    audit_service.log(
        db,
        admin=admin,
        action="admin.login",
        ip=ip,
        user_agent=request.headers.get("user-agent"),
    )
    db.commit()
    token, exp = create_access_token(
        str(admin.id),
        claims={"scope": "admin", "role": admin.role},
    )
    return ok(
        AdminTokenResponse(access_token=token, expires_at=exp, requires_totp_setup=requires_setup).model_dump()
    )


@router.post("/totp/setup")
def setup_totp(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(get_current_admin),
):
    if admin.totp_secret:
        raise ForbiddenError("TOTP already configured")
    secret = pyotp.random_base32()
    admin.totp_secret = secret
    db.commit()
    otpauth_url = pyotp.totp.TOTP(secret).provisioning_uri(name=admin.email, issuer_name="OmniRead")
    return ok(AdminTotpSetupResponse(secret=secret, otpauth_url=otpauth_url).model_dump())


@router.post("/totp/confirm")
def confirm_totp(
    code: str,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(get_current_admin),
):
    if not admin.totp_secret:
        raise ForbiddenError("TOTP not initialized")
    if not pyotp.TOTP(admin.totp_secret).verify(code, valid_window=1):
        raise UnauthorizedError("Invalid TOTP code", code="invalid_totp")
    admin.totp_enabled_at = datetime.now(timezone.utc)
    db.commit()
    return ok({"enabled": True})


@router.get("/me")
def me(admin: AdminUser = Depends(get_current_admin)):
    return ok(AdminMe.model_validate(admin, from_attributes=True).model_dump())
