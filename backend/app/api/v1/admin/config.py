from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.exceptions import NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, AppConfig
from app.schemas.admin import ConfigUpdate
from app.schemas.common import ok
from app.services import audit_service

router = APIRouter()


@router.get("")
def list_config(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "moderator", "analytics")),
):
    rows = list(db.scalars(select(AppConfig)))
    return ok({r.key: r.value for r in rows})


@router.get("/{key}")
def get_config(
    key: str,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "moderator", "analytics")),
):
    cfg = db.get(AppConfig, key)
    if not cfg:
        raise NotFoundError()
    return ok({"key": cfg.key, "value": cfg.value, "updated_at": cfg.updated_at})


@router.put("/{key}")
def upsert_config(
    key: str,
    body: ConfigUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    cfg = db.get(AppConfig, key)
    if cfg:
        cfg.value = body.value
        cfg.updated_by = admin.id
    else:
        cfg = AppConfig(key=key, value=body.value, updated_by=admin.id)
        db.add(cfg)
    audit_service.log(
        db, admin=admin, action="config.update", target_type="config", target_id=key,
        metadata={"value": body.value}, ip=get_client_ip(request),
    )
    db.commit()
    return ok({"key": key, "value": cfg.value})
