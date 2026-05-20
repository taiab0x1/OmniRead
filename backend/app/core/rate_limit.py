import time
from typing import Iterable

from app.db.redis import redis_client
from app.core.exceptions import RateLimitedError


def fixed_window(key: str, limit: int, period_seconds: int = 60) -> int:
    pipe = redis_client.pipeline()
    pipe.incr(key)
    pipe.expire(key, period_seconds, nx=True)
    count, _ = pipe.execute()
    if int(count) > limit:
        raise RateLimitedError(f"Rate limit exceeded ({limit}/{period_seconds}s)")
    return int(count)


def enforce(keys: Iterable[tuple[str, int, int]]) -> None:
    for key, limit, period in keys:
        fixed_window(key, limit, period)


def daily_cap(key: str, limit: int) -> int:
    now = int(time.time())
    bucket = now - (now % 86400)
    return fixed_window(f"{key}:{bucket}", limit, period_seconds=86400)
