import re
import secrets
import unicodedata


def slugify(text: str, max_len: int = 80) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"[^a-zA-Z0-9\s-]", "", text).strip().lower()
    text = re.sub(r"[\s-]+", "-", text)
    return text[:max_len].strip("-") or "untitled"


def unique_slug(base: str, exists: callable) -> str:
    candidate = slugify(base)
    if not exists(candidate):
        return candidate
    for _ in range(5):
        suffix = secrets.token_hex(3)
        next_try = f"{candidate}-{suffix}"
        if not exists(next_try):
            return next_try
    return f"{candidate}-{secrets.token_hex(6)}"
