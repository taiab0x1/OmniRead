"""AdMob server-side verification (SSV) for rewarded ads.

Flow:
  1. Google issues a GET to your callback URL with query params:
        ad_network, ad_unit, reward_amount, reward_item, timestamp,
        transaction_id, user_id, custom_data, signature, key_id
     Where `signature` and `key_id` are *always last*.
  2. We verify the ECDSA signature over the raw query string up to (but not
     including) `&signature=...`.
  3. Public keys live at:
        https://www.gstatic.com/admob/reward/verifier-keys.json
     Keys rotate; we cache and refresh on unknown key_id.
  4. On valid signature, look up the user via `user_id` (we pass our internal
     user UUID via RewardedAd.setUserId on the client) and credit through the
     existing ad_reward_service.

Reference:
  https://developers.google.com/admob/android/ssv
"""
from __future__ import annotations

import base64
import time
import urllib.request
from threading import Lock

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from app.core.logging import get_logger

log = get_logger("admob_ssv")

_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json"
_KEYS_TTL_SECONDS = 3600  # 1h

_cache: dict[str, ec.EllipticCurvePublicKey] = {}
_cache_loaded_at: float = 0.0
_lock = Lock()


class SSVError(Exception):
    """SSV verification failed."""


def _load_keys(force: bool = False) -> None:
    global _cache_loaded_at
    now = time.time()
    if not force and _cache and (now - _cache_loaded_at) < _KEYS_TTL_SECONDS:
        return
    with _lock:
        if not force and _cache and (time.time() - _cache_loaded_at) < _KEYS_TTL_SECONDS:
            return
        try:
            with urllib.request.urlopen(_KEYS_URL, timeout=5) as resp:
                payload = resp.read()
            import json
            doc = json.loads(payload)
        except Exception as e:
            log.error("admob_ssv_keys_fetch_failed", error=str(e))
            raise SSVError(f"Could not fetch verifier keys: {e}")
        new_cache: dict[str, ec.EllipticCurvePublicKey] = {}
        for entry in doc.get("keys", []):
            key_id = str(entry["keyId"])
            pem = entry["pem"].encode("ascii")
            try:
                key = serialization.load_pem_public_key(pem)
                if isinstance(key, ec.EllipticCurvePublicKey):
                    new_cache[key_id] = key
            except Exception as e:
                log.warning("admob_ssv_key_parse_failed", key_id=key_id, error=str(e))
        if not new_cache:
            raise SSVError("No verifier keys parsed")
        _cache.clear()
        _cache.update(new_cache)
        _cache_loaded_at = time.time()
        log.info("admob_ssv_keys_loaded", count=len(_cache))


def verify_callback(query_string: bytes, params: dict[str, str]) -> None:
    """Verify an AdMob SSV callback.

    `query_string` is the raw query string bytes from the request (the part
    after `?`). `params` is the parsed dict (used to read key_id/signature).

    Raises SSVError on any failure.
    """
    sig_b64 = params.get("signature")
    key_id = params.get("key_id")
    if not sig_b64 or not key_id:
        raise SSVError("missing signature or key_id")

    # Find the boundary: everything up to "&signature=" is signed
    qs = query_string.decode("ascii", errors="replace") if isinstance(query_string, bytes) else query_string
    marker = "&signature="
    idx = qs.rfind(marker)
    if idx <= 0:
        # signature= might be the first param (unusual but handle it)
        if qs.startswith("signature="):
            raise SSVError("signature param has nothing to sign")
        raise SSVError("could not locate signature boundary in query string")
    signed_payload = qs[:idx].encode("ascii")

    # AdMob uses URL-safe base64 without padding for the signature.
    pad = "=" * ((4 - len(sig_b64) % 4) % 4)
    try:
        signature = base64.urlsafe_b64decode(sig_b64 + pad)
    except Exception as e:
        raise SSVError(f"invalid base64 signature: {e}")

    _load_keys()
    key = _cache.get(key_id)
    if key is None:
        # Unknown key_id — force-refresh once in case of rotation
        _load_keys(force=True)
        key = _cache.get(key_id)
    if key is None:
        raise SSVError(f"unknown key_id: {key_id}")

    try:
        key.verify(signature, signed_payload, ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        raise SSVError("invalid signature")
    except Exception as e:
        raise SSVError(f"signature verification error: {e}")
