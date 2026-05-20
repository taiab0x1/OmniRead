from functools import lru_cache
from typing import Annotated, List

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", case_sensitive=False, extra="ignore")

    ENV: str = "development"
    LOG_LEVEL: str = "INFO"
    APP_NAME: str = "OmniRead"
    API_V1_PREFIX: str = "/v1"

    DATABASE_URL: str
    DATABASE_URL_ASYNC: str | None = None
    DATABASE_POOL_SIZE: int = 10
    DATABASE_MAX_OVERFLOW: int = 20

    REDIS_URL: str = "redis://redis:6379/0"
    CELERY_BROKER_URL: str = "redis://redis:6379/1"
    CELERY_RESULT_BACKEND: str = "redis://redis:6379/2"

    JWT_SECRET_KEY: str
    JWT_ALGORITHM: str = "HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    JWT_REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    JWT_KEY_ID: str = "v1"
    # Set these during key rotation; old tokens signed with JWT_SECRET_KEY/JWT_KEY_ID
    # remain valid until they expire naturally. Rotate: promote secondary → primary,
    # generate new secondary, then clear secondary once no old tokens remain.
    JWT_SECONDARY_SECRET_KEY: str = ""
    JWT_SECONDARY_KEY_ID: str = ""

    FIREBASE_SERVICE_ACCOUNT_JSON: str = ""

    CORS_ORIGINS: Annotated[List[str], NoDecode] = Field(default_factory=list)

    GOOGLE_OAUTH_CLIENT_ID: str = ""
    GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: str = ""
    GOOGLE_PLAY_PACKAGE_NAME: str = ""

    ADMOB_APP_ID: str = ""
    ADMOB_SSV_VERIFY_KEY: str = ""

    AI_PROVIDER: str = "deepseek"
    DEEPSEEK_API_KEY: str = ""
    ANTHROPIC_API_KEY: str = ""
    OPENROUTER_API_KEY: str = ""
    OPENAI_API_KEY: str = ""

    R2_ACCOUNT_ID: str = ""
    R2_ACCESS_KEY_ID: str = ""
    R2_SECRET_ACCESS_KEY: str = ""
    R2_BUCKET: str = ""
    R2_PUBLIC_URL: str = ""

    SENTRY_DSN: str = ""
    ADMIN_IP_ALLOWLIST: Annotated[List[str], NoDecode] = Field(default_factory=list)

    RATE_LIMIT_DEFAULT_PER_MIN: int = 60
    RATE_LIMIT_AUTH_PER_MIN: int = 10
    RATE_LIMIT_AD_REWARD_PER_MIN: int = 5
    RATE_LIMIT_SEARCH_PER_MIN: int = 30

    PASSWORD_ARGON2_TIME_COST: int = 3
    PASSWORD_ARGON2_MEMORY_COST: int = 65536
    PASSWORD_ARGON2_PARALLELISM: int = 4

    MAX_SESSIONS_PER_USER: int = 5
    EMAIL_VERIFICATION_TTL_HOURS: int = 24
    PASSWORD_RESET_TTL_MINUTES: int = 10

    DEFAULT_FREE_CHAPTERS: int = 3
    DEFAULT_CHAPTER_COIN_COST: int = 5
    DAILY_LOGIN_COINS: int = 5
    REWARDED_AD_COINS: int = 10
    REWARDED_AD_COOLDOWN_MIN: int = 30
    REWARDED_AD_DAILY_CAP: int = 5

    @field_validator("CORS_ORIGINS", "ADMIN_IP_ALLOWLIST", mode="before")
    @classmethod
    def split_csv(cls, v):
        if isinstance(v, str):
            return [s.strip() for s in v.split(",") if s.strip()]
        return v or []


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
