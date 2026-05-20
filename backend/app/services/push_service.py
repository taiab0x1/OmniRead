import json
import logging
from typing import Any

logger = logging.getLogger("push_service")

_initialized: bool | None = None  # None = not yet attempted


def _init() -> bool:
    global _initialized
    if _initialized is not None:
        return _initialized
    from app.config import settings

    if not settings.FIREBASE_SERVICE_ACCOUNT_JSON:
        logger.warning("FIREBASE_SERVICE_ACCOUNT_JSON not set — push notifications disabled")
        _initialized = False
        return False
    try:
        import firebase_admin
        from firebase_admin import credentials

        if not firebase_admin._apps:
            cred = credentials.Certificate(json.loads(settings.FIREBASE_SERVICE_ACCOUNT_JSON))
            firebase_admin.initialize_app(cred)
        _initialized = True
        logger.info("Firebase Admin initialized")
        return True
    except Exception as exc:
        logger.error("Firebase Admin init failed: %s", exc)
        _initialized = False
        return False


def send_to_token(
    token: str,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    image_url: str | None = None,
) -> bool:
    if not _init():
        return False
    try:
        from firebase_admin import messaging

        msg = messaging.Message(
            notification=messaging.Notification(title=title, body=body, image=image_url),
            data={k: str(v) for k, v in (data or {}).items()},
            token=token,
            android=messaging.AndroidConfig(priority="high"),
        )
        messaging.send(msg)
        return True
    except Exception as exc:
        logger.error("Push send failed (token=%.20s): %s", token, exc)
        return False


def send_to_many(
    tokens: list[str],
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    image_url: str | None = None,
) -> int:
    """Multicast up to 500 tokens. Returns success count."""
    if not _init() or not tokens:
        return 0
    try:
        from firebase_admin import messaging

        batch_size = 500
        success = 0
        for i in range(0, len(tokens), batch_size):
            chunk = tokens[i : i + batch_size]
            msg = messaging.MulticastMessage(
                notification=messaging.Notification(title=title, body=body, image=image_url),
                data={k: str(v) for k, v in (data or {}).items()},
                tokens=chunk,
                android=messaging.AndroidConfig(priority="high"),
            )
            resp = messaging.send_each_for_multicast(msg)
            success += resp.success_count
        return success
    except Exception as exc:
        logger.error("Multicast push failed: %s", exc)
        return 0
