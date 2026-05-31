# OmniRead

AI‑powered, vertical‑feed story platform.

This repository contains **three deliverables**:

| Path | What |
| --- | --- |
| `backend/` | FastAPI service (Python 3.11, PostgreSQL, Redis, Celery) |
| `admin/` | React + Vite admin console |
| `android/` | Kotlin + Jetpack Compose Android app |

Also included:

- `immersive_ai_story_app_blueprint_v2.md` — original product blueprint/spec
- `BLUEPRINT_GAPS.md` — living gap tracker / scope notes
- `PLAYSTORE_LAUNCH_CHECKLIST.md` — launch checklist

## Screenshots

> Screenshots live at the repo root:
>
> - `omniread_discover.png`
> - `omniread_live.png`, `omniread_live2.png`
> - `omniread_more.png`
> - `omniread_vip.png`
> - `omniread_after_launch.png`

## Quick start

### Backend (API + worker + Postgres + Redis)

```bash
cd backend
cp .env.example .env

# Start dependencies
docker compose up -d postgres redis

# Run migrations
docker compose run --rm api alembic upgrade head

# Seed admin user (change password!)
ADMIN_PASSWORD=changeme docker compose run --rm api python -m app.scripts.seed

# Start API + worker stack
docker compose up -d
```

### Admin console

```bash
cd admin
cp .env.example .env
npm install
npm run dev   # http://localhost:5173
```

By default, the dev server proxies `/v1` → `http://localhost:8000`.

### Android app

- Open `android/` in Android Studio.
- Default API base points to the hosted HTTPS API.
- To point an emulator at a local backend:

```bash
./gradlew :app:installDebug -POMNIREAD_API_BASE=http://10.0.2.2:8000
```

## Scope decisions (v1)

- Audio (TTS, audio mode, sync) is **out of v1** (see `BLUEPRINT_GAPS.md`).
- Subscriptions are wired server-side (Play Billing verify + RTDN webhook), but mobile purchase UI is deferred to v1.1.
- iOS is post-v2.

## Repo layout

```
OmniRead/
├── backend/                          FastAPI + Celery + Alembic
├── admin/                            Vite + React + Tailwind
├── android/                          Kotlin + Jetpack Compose
├── legal-site/                       Static legal site
├── immersive_ai_story_app_blueprint_v2.md
├── BLUEPRINT_GAPS.md                 Gap tracker, audio-removal scope, follow-ups
└── README.md                         (this file)
```

## Documentation

Each subdirectory has its own README with stack details, commands, and architecture notes:

- `backend/README.md`
- `admin/README.md`
- `android/README.md`
- `BLUEPRINT_GAPS.md`

## Contributing

1. Fork the repo.
2. Create a feature branch.
3. Keep changes scoped (backend/admin/android separated where possible).
4. Open a PR with screenshots / logs when UI or infra changes are involved.

## License

No license file is present yet. If you plan to make this repository public, you likely want to add a license (MIT/Apache‑2.0/GPL/etc.) before publishing.
