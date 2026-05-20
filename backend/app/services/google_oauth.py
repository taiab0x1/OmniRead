from typing import Optional

from google.auth.transport import requests as google_requests
from google.oauth2 import id_token

from app.config import settings


def verify_google_id_token(token: str) -> Optional[dict]:
    if not settings.GOOGLE_OAUTH_CLIENT_ID:
        return None
    try:
        info = id_token.verify_oauth2_token(
            token,
            google_requests.Request(),
            settings.GOOGLE_OAUTH_CLIENT_ID,
        )
        if info.get("iss") not in ("accounts.google.com", "https://accounts.google.com"):
            return None
        return info
    except Exception:
        return None
