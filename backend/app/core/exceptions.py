from typing import Any


class AppError(Exception):
    status_code: int = 500
    code: str = "internal_error"
    message: str = "Something went wrong"

    def __init__(self, message: str | None = None, *, code: str | None = None, extra: Any = None):
        super().__init__(message or self.message)
        if message:
            self.message = message
        if code:
            self.code = code
        self.extra = extra


class NotFoundError(AppError):
    status_code = 404
    code = "not_found"
    message = "Resource not found"


class UnauthorizedError(AppError):
    status_code = 401
    code = "unauthorized"
    message = "Authentication required"


class ForbiddenError(AppError):
    status_code = 403
    code = "forbidden"
    message = "Not allowed"


class ValidationError(AppError):
    status_code = 422
    code = "validation_error"
    message = "Invalid input"


class ConflictError(AppError):
    status_code = 409
    code = "conflict"
    message = "Conflict"


class RateLimitedError(AppError):
    status_code = 429
    code = "rate_limited"
    message = "Too many requests"


class PaymentRequiredError(AppError):
    status_code = 402
    code = "payment_required"
    message = "Insufficient coins"


class BadRequestError(AppError):
    status_code = 400
    code = "bad_request"
    message = "Bad request"
