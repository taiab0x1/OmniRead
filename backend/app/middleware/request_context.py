import time
import uuid

import structlog
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

log = structlog.get_logger("http")


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex
        structlog.contextvars.bind_contextvars(request_id=request_id, path=request.url.path)
        start = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            duration_ms = (time.perf_counter() - start) * 1000
            log.exception("request_failed", duration_ms=round(duration_ms, 2))
            raise
        duration_ms = (time.perf_counter() - start) * 1000
        log.info(
            "request",
            method=request.method,
            status=response.status_code,
            duration_ms=round(duration_ms, 2),
        )
        response.headers["X-Request-Id"] = request_id
        structlog.contextvars.clear_contextvars()
        return response
