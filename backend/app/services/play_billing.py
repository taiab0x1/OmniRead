import json
from datetime import datetime, timezone
from typing import Any

import httpx
from google.oauth2 import service_account
from googleapiclient.discovery import build

from app.config import settings


_PUB_SCOPE = ["https://www.googleapis.com/auth/androidpublisher"]


def _service():
    if not settings.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:
        return None
    try:
        info = json.loads(settings.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)
    except Exception:
        return None
    creds = service_account.Credentials.from_service_account_info(info, scopes=_PUB_SCOPE)
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def verify_product_purchase(sku: str, token: str) -> dict[str, Any] | None:
    svc = _service()
    if not svc or not settings.GOOGLE_PLAY_PACKAGE_NAME:
        return None
    try:
        return (
            svc.purchases()
            .products()
            .get(packageName=settings.GOOGLE_PLAY_PACKAGE_NAME, productId=sku, token=token)
            .execute()
        )
    except Exception:
        return None


def acknowledge_product(sku: str, token: str) -> bool:
    svc = _service()
    if not svc:
        return False
    try:
        svc.purchases().products().acknowledge(
            packageName=settings.GOOGLE_PLAY_PACKAGE_NAME, productId=sku, token=token, body={}
        ).execute()
        return True
    except Exception:
        return False


def verify_subscription(sku: str, token: str) -> dict[str, Any] | None:
    svc = _service()
    if not svc or not settings.GOOGLE_PLAY_PACKAGE_NAME:
        return None
    try:
        return (
            svc.purchases()
            .subscriptionsv2()
            .get(packageName=settings.GOOGLE_PLAY_PACKAGE_NAME, token=token)
            .execute()
        )
    except Exception:
        return None


def acknowledge_subscription(sku: str, token: str) -> bool:
    svc = _service()
    if not svc:
        return False
    try:
        svc.purchases().subscriptions().acknowledge(
            packageName=settings.GOOGLE_PLAY_PACKAGE_NAME, subscriptionId=sku, token=token, body={}
        ).execute()
        return True
    except Exception:
        return False


def parse_expiry_ms(value: str | int | None) -> datetime | None:
    if value is None:
        return None
    try:
        ms = int(value)
        return datetime.fromtimestamp(ms / 1000, tz=timezone.utc)
    except Exception:
        return None
