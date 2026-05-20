# OmniRead Admin

React + Vite + Tailwind admin console for the OmniRead backend.

## Stack

- React 18 + TypeScript
- Vite 6
- Tailwind 3 with custom dark cinematic theme
- React Query (server state) + Zustand (auth)
- React Router 6, react-hook-form, zod, recharts, axios
- Lucide icons

## Run

```bash
cp .env.example .env
npm install
npm run dev
```

Dev server: http://localhost:5173 (proxies `/v1` → `http://localhost:8000`).

## Pages

| Route | Roles | What it does |
| --- | --- | --- |
| `/login` | – | Admin login (TOTP gated after first setup) |
| `/` | all | Dashboard with live metrics |
| `/analytics` | super_admin, analytics | Revenue, retention, top content |
| `/stories` | super_admin, editor, moderator | Story list + create draft |
| `/stories/:id` | super_admin, editor | Story editor + chapter list |
| `/chapters/:id` | super_admin, editor | Chapter editor (long-form, autosave on submit) |
| `/ai` | super_admin, editor | AI Studio: queue story outline / chapter generation jobs |
| `/users` | super_admin, moderator, analytics | Search and triage users |
| `/users/:id` | super_admin, moderator | Ban / unban / coin adjust / force logout |
| `/moderation` | super_admin, moderator | Reports queue + comment hide/restore |
| `/config` | super_admin | Edit live remote config keys (JSON editor) |
| `/audit` | super_admin | Read-only audit log |

## Auth

- Calls `POST /v1/admin/auth/login` (email + password + optional TOTP)
- Access token (15 min) stored in `localStorage` via Zustand `persist`
- Token expiry checked client-side before each render; expired tokens force logout
- 401 from any API response auto-redirects to `/login`

## Layout / theming

Dark cinematic theme matching the mobile spec: `bg-bg-0` (#0B0B0F) base, accent #8B5CF6.
Display font: Playfair Display. Body: Inter.

## Notes

- Audio/TTS pages are intentionally absent (out of v1 scope). When audio returns, add a TTS panel under `/ai`.
- The Story editor uses a plain textarea for content. Swap to TipTap or Quill if rich formatting becomes a requirement.
- Config editor accepts arbitrary JSON. Validate shape server-side before publishing.
