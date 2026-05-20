from typing import Any, Generic, Optional, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class Meta(BaseModel):
    page: int | None = None
    per_page: int | None = None
    total: int | None = None
    next_cursor: str | None = None


class APIError(BaseModel):
    code: str
    message: str
    extra: Any | None = None


class APIResponse(BaseModel, Generic[T]):
    success: bool = True
    data: Optional[T] = None
    meta: Meta | None = None
    error: APIError | None = None


def ok(data: Any = None, meta: Meta | None = None) -> dict:
    return {"success": True, "data": data, "meta": meta, "error": None}


def err(code: str, message: str, *, extra: Any = None, status: int = 400) -> dict:
    return {
        "success": False,
        "data": None,
        "meta": None,
        "error": {"code": code, "message": message, "extra": extra},
    }


class CursorPage(BaseModel, Generic[T]):
    items: list[T]
    next_cursor: str | None = None


class PaginationParams(BaseModel):
    cursor: str | None = None
    limit: int = Field(default=20, ge=1, le=50)
