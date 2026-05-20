from fastapi import APIRouter, Depends, Header, Request
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import BadRequestError
from app.core.rate_limit import fixed_window
from app.db.session import get_db
from app.dependencies import get_client_ip, get_current_user
from app.models import User
from app.schemas.auth import (
    ForgotPasswordRequest,
    GoogleAuthRequest,
    GuestAuthRequest,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    ResetPasswordRequest,
)
from app.schemas.common import ok
from app.services import auth_service
from app.services.google_oauth import verify_google_id_token

router = APIRouter()


def _ip_key(name: str, ip: str) -> str:
    return f"rl:auth:{name}:{ip}"


def _ua(request: Request) -> str | None:
    return request.headers.get("user-agent")


@router.post("/register")
def register(
    body: RegisterRequest,
    request: Request,
    db: Session = Depends(get_db),
    x_device_fp: str | None = Header(default=None, alias="X-Device-Fingerprint"),
):
    ip = get_client_ip(request)
    fixed_window(_ip_key("register", ip), limit=5, period_seconds=3600)
    res = auth_service.register_user(
        db,
        email=str(body.email),
        username=body.username,
        password=body.password,
        birth_year=body.birth_year,
        user_agent=_ua(request),
        ip=ip,
        device_fingerprint=x_device_fp,
    )
    db.commit()
    return ok(res.model_dump())


@router.post("/login")
def login(
    body: LoginRequest,
    request: Request,
    db: Session = Depends(get_db),
    x_device_fp: str | None = Header(default=None, alias="X-Device-Fingerprint"),
):
    ip = get_client_ip(request)
    fixed_window(_ip_key("login", ip), limit=10, period_seconds=60)
    res = auth_service.login_user(
        db,
        email=str(body.email),
        password=body.password,
        user_agent=_ua(request),
        ip=ip,
        device_fingerprint=x_device_fp,
    )
    db.commit()
    return ok(res.model_dump())


@router.post("/google")
def google(
    body: GoogleAuthRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    ip = get_client_ip(request)
    fixed_window(_ip_key("google", ip), limit=10, period_seconds=60)
    info = verify_google_id_token(body.id_token)
    if not info:
        raise BadRequestError("Invalid Google id_token", code="invalid_id_token")
    res = auth_service.google_sign_in(
        db,
        google_sub=info["sub"],
        email=info.get("email"),
        name=info.get("name"),
        picture=info.get("picture"),
        user_agent=_ua(request),
        ip=ip,
        device_fingerprint=body.device_fingerprint,
    )
    db.commit()
    return ok(res.model_dump())


@router.post("/guest")
def guest(
    body: GuestAuthRequest,
    request: Request,
    db: Session = Depends(get_db),
):
    ip = get_client_ip(request)
    fixed_window(_ip_key("guest", ip), limit=20, period_seconds=3600)
    res = auth_service.guest_register(
        db,
        device_fingerprint=body.device_fingerprint,
        user_agent=_ua(request),
        ip=ip,
    )
    db.commit()
    return ok(res.model_dump())


@router.post("/refresh")
def refresh(
    body: RefreshRequest,
    request: Request,
    db: Session = Depends(get_db),
    x_device_fp: str | None = Header(default=None, alias="X-Device-Fingerprint"),
):
    ip = get_client_ip(request)
    fixed_window(_ip_key("refresh", ip), limit=30, period_seconds=60)
    res = auth_service.refresh_tokens(
        db,
        refresh_token=body.refresh_token,
        user_agent=_ua(request),
        ip=ip,
        device_fingerprint=x_device_fp,
    )
    db.commit()
    return ok(res.model_dump())


@router.post("/logout")
def logout(
    body: RefreshRequest | None = None,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    auth_service.logout(db, refresh_token=body.refresh_token if body else None, user=user)
    db.commit()
    return ok({"logged_out": True})


@router.post("/logout-all")
def logout_all(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    auth_service.logout_all(db, user=user)
    db.commit()
    return ok({"logged_out_all": True})


@router.delete("/account")
def delete_account(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    auth_service.schedule_account_deletion(db, user=user)
    db.commit()
    return ok({"scheduled": True, "purge_in_days": 30})


@router.post("/forgot-password")
def forgot_password(body: ForgotPasswordRequest, db: Session = Depends(get_db)):
    token = auth_service.create_password_reset(db, email=str(body.email))
    db.commit()
    payload = {"sent": True}
    if token and settings.ENV != "production":
        payload["reset_token"] = token
    return ok(payload)


@router.post("/reset-password")
def reset_password(body: ResetPasswordRequest, db: Session = Depends(get_db)):
    auth_service.reset_password(db, token=body.token, new_password=body.new_password)
    db.commit()
    return ok({"reset": True})
