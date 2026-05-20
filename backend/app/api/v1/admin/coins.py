from uuid import UUID

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import get_db
from app.dependencies import get_client_ip, require_role
from app.models import AdminUser, CoinPackage
from app.schemas.admin import CoinPackageCreate, CoinPackageUpdate
from app.schemas.common import ok
from app.schemas.payment import CoinPackageItem
from app.services import audit_service

router = APIRouter()


def _payload(pkg: CoinPackage) -> dict:
    return CoinPackageItem.model_validate(pkg, from_attributes=True).model_dump()


@router.get("/packages")
def list_coin_packages(
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin", "editor", "analytics")),
):
    rows = list(db.scalars(select(CoinPackage).order_by(CoinPackage.sort_order, CoinPackage.name)))
    return ok([_payload(r) for r in rows])


@router.post("/packages")
def create_coin_package(
    body: CoinPackageCreate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    if db.scalar(select(CoinPackage).where(CoinPackage.sku == body.sku)):
        raise ConflictError("SKU already exists", code="sku_taken")
    pkg = CoinPackage(**body.model_dump())
    db.add(pkg)
    db.flush()
    audit_service.log(
        db,
        admin=admin,
        action="coin_package.create",
        target_type="coin_package",
        target_id=str(pkg.id),
        metadata={"sku": pkg.sku},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok(_payload(pkg))


@router.put("/packages/{package_id}")
def update_coin_package(
    package_id: UUID,
    body: CoinPackageUpdate,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    pkg = db.get(CoinPackage, package_id)
    if not pkg:
        raise NotFoundError("Package not found")
    changes = body.model_dump(exclude_none=True)
    if "sku" in changes and changes["sku"] != pkg.sku:
        if db.scalar(select(CoinPackage).where(CoinPackage.sku == changes["sku"])):
            raise ConflictError("SKU already exists", code="sku_taken")
    for key, value in changes.items():
        setattr(pkg, key, value)
    audit_service.log(
        db,
        admin=admin,
        action="coin_package.update",
        target_type="coin_package",
        target_id=str(pkg.id),
        metadata=changes,
        ip=get_client_ip(request),
    )
    db.commit()
    return ok(_payload(pkg))


@router.delete("/packages/{package_id}")
def deactivate_coin_package(
    package_id: UUID,
    request: Request,
    db: Session = Depends(get_db),
    admin: AdminUser = Depends(require_role("super_admin")),
):
    pkg = db.get(CoinPackage, package_id)
    if not pkg:
        raise NotFoundError("Package not found")
    pkg.is_active = False
    audit_service.log(
        db,
        admin=admin,
        action="coin_package.deactivate",
        target_type="coin_package",
        target_id=str(pkg.id),
        metadata={"sku": pkg.sku},
        ip=get_client_ip(request),
    )
    db.commit()
    return ok({"deactivated": True})
