# OmniRead Backend

FastAPI service powering the OmniRead Android app and admin dashboard.

## Stack

- Python 3.11, FastAPI, SQLAlchemy 2, PostgreSQL 16, Redis 7, Celery 5
- Argon2id password hashing, JWT access tokens with rotating refresh tokens
- Google Play Billing verification + RTDN handler
- Pluggable AI provider (DeepSeek, Anthropic, OpenRouter) via Celery workers

## Quick start (Docker)

```bash
cp .env.example .env
docker compose up -d postgres redis
docker compose run --rm api alembic upgrade head
ADMIN_PASSWORD=changeme docker compose run --rm api python -m app.scripts.seed
docker compose up -d
```

API: http://localhost:8000 · OpenAPI: http://localhost:8000/docs (dev only)

## Local (no Docker)

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env  # point DATABASE_URL/REDIS_URL at local instances
alembic upgrade head
ADMIN_PASSWORD=changeme python -m app.scripts.seed
uvicorn app.main:app --reload
```

In a second shell:

```bash
celery -A app.celery_app.celery worker -Q high,default,low --loglevel=INFO
celery -A app.celery_app.celery beat --loglevel=INFO
```

## Tests

Tests need a Postgres test DB.

```bash
export TEST_DATABASE_URL=postgresql+psycopg2://omniread:omniread@localhost:5432/omniread_test
createdb omniread_test  # or via psql
pytest -q
```

Tests use `metadata.create_all` for speed. Migrations are validated separately via `alembic upgrade head` in CI.

## Project layout

```
app/
├── api/v1/              # public + admin route handlers
├── core/                # security, rate_limit, exceptions, logging
├── db/                  # SQLAlchemy session, Redis client
├── middleware/          # request context, security headers
├── models/              # ORM models
├── schemas/             # Pydantic request/response shapes
├── services/            # business logic (coin, story, payment, ai, ...)
├── workers/             # Celery tasks
├── scripts/             # seed / one-off jobs
├── utils/               # slug, cursor helpers
├── celery_app.py
├── config.py
├── dependencies.py
└── main.py
alembic/                 # migrations
tests/                   # pytest suite
```

## Auth model

- Access tokens: 15 min, JWT HS256 with `kid` header for rotation
- Refresh tokens: 30 days, rotated on every refresh, reuse triggers full revocation
- Max 5 active sessions per user (oldest revoked)
- Passwords hashed with Argon2id (`time_cost=3, memory_cost=64MiB, parallelism=4`)

## Coin / unlock concurrency

Debits use atomic `UPDATE … WHERE coin_balance >= :cost RETURNING coin_balance`. The application never reads-then-writes the balance, so concurrent unlocks can never go negative. The race is covered by `tests/test_unlock_race.py`.

Idempotency keys are stored on `coin_transactions.idempotency_key` (unique). Default key for chapter unlock is `unlock:{user_id}:{chapter_id}`, ensuring repeated calls don't double-debit.

## Payments

- `POST /v1/payments/coins/purchase`: verifies a Play Billing product purchase token via Android Publisher API, acknowledges it, credits coins idempotently
- `POST /v1/payments/subscription/subscribe`: verifies via `purchases.subscriptionsv2.get`, materializes a `subscriptions` row, updates `users.subscription_tier` + `subscription_expires_at`
- `POST /v1/payments/google-play/rtdn`: handler for Pub/Sub-pushed Real-time Developer Notifications (refunds, cancellations, holds, grace periods)
- `POST /v1/payments/ad-reward/validate`: accepts only server-verified AdMob SSV transactions; client-only reward callbacks are not trusted. Cooldown + daily cap enforced server-side

## AI generation

- Provider abstraction in `app/services/ai_provider.py` (DeepSeek / Anthropic / OpenRouter)
- Prompts in `app/services/ai_prompts.py`; production should move to versioned `prompt_templates` table
- Story generation = outline first (`generate_story` Celery task) → admin review → per-chapter generation
- Cost tracking per job, retried with exponential backoff

## Admin

- `POST /v1/admin/auth/login` — email + password + TOTP (mandatory after first setup)
- IP allowlist via `ADMIN_IP_ALLOWLIST` env var
- All write operations write to `audit_log`
- RBAC roles: `super_admin`, `editor`, `moderator`, `analytics`

## Notes

- Audio (TTS, audio mode, syncing) is intentionally out of v1. See `../BLUEPRINT_GAPS.md`.
- For production: terminate TLS at Cloudflare/Nginx, run gunicorn with 2-4 workers per CPU, point Sentry DSN, set `ENV=production` (disables `/docs`).
