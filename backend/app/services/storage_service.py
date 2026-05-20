"""Cloudflare R2 / S3-compatible storage for admin uploads (covers, etc).

R2 exposes an S3 API; we use boto3 with a custom endpoint. If R2 is not
configured (local dev), the upload returns a data URI fallback so the admin
UI keeps working end-to-end.
"""
from __future__ import annotations

import base64
import secrets
from typing import BinaryIO

import boto3
from botocore.config import Config

from app.config import settings
from app.core.logging import get_logger

log = get_logger("storage")

_ALLOWED_IMAGE_MIME = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
}
MAX_COVER_BYTES = 5 * 1024 * 1024  # 5 MB


def _client():
    if not (
        settings.R2_ACCOUNT_ID
        and settings.R2_ACCESS_KEY_ID
        and settings.R2_SECRET_ACCESS_KEY
        and settings.R2_BUCKET
    ):
        return None
    endpoint = f"https://{settings.R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
    return boto3.client(
        "s3",
        endpoint_url=endpoint,
        aws_access_key_id=settings.R2_ACCESS_KEY_ID,
        aws_secret_access_key=settings.R2_SECRET_ACCESS_KEY,
        config=Config(signature_version="s3v4", retries={"max_attempts": 3}),
    )


def upload_cover(fileobj: BinaryIO, *, mime: str, size: int) -> str:
    """Upload a story cover image and return a public URL.

    Raises ValueError for invalid mime / oversize files. Falls back to a
    data URI when R2 is not configured.
    """
    if mime not in _ALLOWED_IMAGE_MIME:
        raise ValueError(f"unsupported mime type: {mime}")
    if size > MAX_COVER_BYTES:
        raise ValueError(f"file too large (max {MAX_COVER_BYTES} bytes)")

    ext = _ALLOWED_IMAGE_MIME[mime]
    key = f"covers/{secrets.token_urlsafe(16)}.{ext}"

    client = _client()
    if client is None:
        # Dev fallback: inline as a data URI (caller persists it on the story).
        body = fileobj.read()
        log.warning("storage_fallback_data_uri", reason="R2 not configured")
        return f"data:{mime};base64,{base64.b64encode(body).decode('ascii')}"

    client.upload_fileobj(
        fileobj,
        settings.R2_BUCKET,
        key,
        ExtraArgs={"ContentType": mime, "CacheControl": "public, max-age=31536000"},
    )
    base = settings.R2_PUBLIC_URL.rstrip("/") if settings.R2_PUBLIC_URL else (
        f"https://{settings.R2_BUCKET}.{settings.R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
    )
    return f"{base}/{key}"
