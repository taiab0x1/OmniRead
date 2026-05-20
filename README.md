# OmniRead

AI-powered, vertical-feed story platform. Three deliverables in this repo:

| Path | What |
| --- | --- |
| `backend/` | FastAPI service (Python 3.11, PostgreSQL, Redis, Celery) |
| `admin/` | React + Vite admin console |
| `android/` | Kotlin + Jetpack Compose Android app |

Plus the original product spec at `immersive_ai_story_app_blueprint_v2.md` and a living gap-tracker at `BLUEPRINT_GAPS.md`.

## Quick start

```bash
# Backend (Postgres + Redis + API + worker)
cd backend
cp .env.example .env
docker compose up -d postgres redis
docker compose run --rm api alembic upgrade head
ADMIN_PASSWORD=changeme docker compose run --rm api python -m app.scripts.seed
docker compose up -d
```

```bash
# Admin (in another shell)
cd admin
cp .env.example .env
npm install
npm run dev   # http://localhost:5173
```

```bash
# Android
# Open android/ in Android Studio.
# The default API target is the VPS over HTTPS.
# For a local emulator backend, run with:
# ./gradlew :app:installDebug -POMNIREAD_API_BASE=http://10.0.2.2:8000
```

## Scope decisions

- Audio (TTS, audio mode, sync) is **out of v1**. The blueprint's audio surface area introduces a forced-alignment subsystem and ElevenLabs cost trap that don't earn their keep until retention is proven. Plan to reintroduce in v1.1 — see `BLUEPRINT_GAPS.md`.
- Subscriptions are wired server-side (Play Billing verify + RTDN webhook), but mobile UI is deferred to v1.1.
- iOS is post-v2.

## Repo layout

```
OmniRead/
├── backend/                          FastAPI + Celery + Alembic
├── admin/                            Vite + React + Tailwind
├── android/                          Kotlin + Jetpack Compose
├── immersive_ai_story_app_blueprint_v2.md
├── BLUEPRINT_GAPS.md                 Gap tracker, audio-removal scope, follow-ups
└── README.md                         (this file)
```

## Documentation

Each subdirectory has its own README with stack details, commands, and architecture notes:

- [backend/README.md](backend/README.md)
- [admin/README.md](admin/README.md)
- [android/README.md](android/README.md)
- [BLUEPRINT_GAPS.md](BLUEPRINT_GAPS.md)
