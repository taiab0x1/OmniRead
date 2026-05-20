# Blueprint Gaps & Open Decisions

Companion note to `immersive_ai_story_app_blueprint_v2.md`. Captures issues found in v2.0 review plus scope changes agreed during build.

---

## Scope change: audio dropped from v1

Audio (TTS narration, audio mode UI, ambient sounds, premium voices, synced text) is **out of scope for v1**. Reasons: avoids the unsolved sync-alignment problem, removes the ElevenLabs cost trap, and lets us validate the read-only retention loop first.

Changes to apply across the codebase:

- §3.4 TTS Worker — not built
- §4 `chapters` — drop `audio_url`, `audio_duration`, `audio_status`, `audio_voice`
- §4 `tts_jobs` — table not created
- §4 `reading_progress` — drop `audio_position`, drop `mode` column (read-only)
- §4 `users` — drop `preferred_voice`
- §5 `/chapters/{id}/audio`, `/admin/tts/*` — endpoints not built
- §7 entire section — deferred
- §8/§9 Audio Player UI (mini + full), waveform, voice preview, ambient sounds — deferred
- §11 Premium voice tier, voice unlocks (20 coins) — deferred. Subscription value prop becomes: unlimited unlocks + no ads + early access + offline + monthly coin bonus
- §15 Audio listen events — deferred
- §1 differentiator table — "Synchronized audio+text" claim is removed for v1 positioning
- §22 ElevenLabs cost line — removed; AI cost only

Re-add as v1.1 once retention thresholds hit.

---

## Critical gaps to address before public launch

These were flagged in the v2.0 review and remain unresolved.

### 1. Synchronized text highlighting (when audio returns)
Piper / Coqui don't emit word-level timestamps. ElevenLabs does. Forced alignment (aeneas / Whisper / MFA) is required for non-ElevenLabs voices. Plan this subsystem before reintroducing audio.

### 2. Localization / i18n
Targets are SEA / South Asia / MENA / LATAM but stack is English-only. Either narrow launch markets or add: locale column on users, per-locale story tables, UI string catalog, RTL support (Arabic/Urdu).

### 3. AI content copyright
US Copyright Office and others rule pure AI output is not copyrightable. Define a content rights policy: which works are AI-only vs AI-assisted-with-human-edits, what's defensible against scraping, what license users get.

### 4. Google Play Real-time Developer Notifications (RTDN)
Without an RTDN webhook handler, the backend can't react to refunds, cancellations, holds, grace periods. Already added to backend scope.

### 5. Content rating / age gating
Dark Romance, Mafia, Horror need a rating taxonomy (e.g., 13+/16+/18+) and a signup age gate. IARC questionnaire on Play Console will require an answer.

### 6. Content moderation pipeline
Beyond OpenAI moderation API call, define: pre-publish review checklist, comment moderation queue SLA, report → action workflow, appeals process.

---

## Database schema additions vs v2.0

Adding to original schema:

- `refresh_tokens` — for rotating refresh tokens, max-5-sessions enforcement, device fingerprint
- `audit_log` — admin action trail required by §12
- `story_ratings` — backs `stories.avg_rating` / `total_ratings`
- `content_reports` — backs §18 report flow
- `fcm_tokens` — per-device push tokens
- `prompt_templates` — versioned AI prompts for reproducibility
- `users.deleted_at` — soft delete window (GDPR 30 days)
- `users.region`, `users.locale` — for regional pricing and i18n
- `users.age_verified_at`, `users.birth_year` — for age gate
- `users.failed_login_count`, `users.locked_until` — brute force defense
- Indexes: `stories(genre, published_at)`, `stories(is_featured, is_trending)`, `chapters(story_id, chapter_number)`, `reading_progress(user_id, last_read_at DESC)`, `coin_transactions(user_id, created_at DESC)`, `notifications(user_id, is_read, created_at DESC)`

Concurrency:

- `coin_transactions` insertions and `users.coin_balance` updates run inside a single transaction with `SELECT ... FOR UPDATE` on the user row, or atomic `UPDATE users SET coin_balance = coin_balance - :cost WHERE id = :uid AND coin_balance >= :cost RETURNING coin_balance`. Never compute new balance in app code.

---

## Security upgrades vs v2.0

- Argon2id for password hashing (not bcrypt)
- Argon2 params: `time_cost=3, memory_cost=64MiB, parallelism=4` baseline
- JWT signing key rotation: kid header + key set, rotate quarterly
- Per-user rate limits in addition to per-IP (mobile NAT bypass)
- Idempotency keys on all payment endpoints (`Idempotency-Key` header)
- Cursor pagination for feed endpoints (offset breaks under churn)
- Comment length cap (2000 chars) and per-user post rate (10/min)
- Registration rate limit: 5 accounts per IP per hour, plus optional CAPTCHA hook
- Admin: 2FA mandatory from day 1; IP allowlist as defense-in-depth, not sole gate

---

## Compliance scope expansion

Beyond GDPR:

- DPDP Act (India) — required for Indian launch market
- CCPA / CPRA (US) — for any US users
- LGPD (Brazil) — when LATAM expansion proceeds
- COPPA — backed by signup age gate
- DSAR data export endpoint, not just deletion
- Public sub-processor list + DPAs with: Anthropic / OpenAI / DeepSeek / Hetzner / Cloudflare / Firebase / AdMob

---

## Operations gaps to address before launch

- Sentry for error tracking from day 1
- Prometheus + Grafana, or hosted alternative (Better Stack, Grafana Cloud free)
- Structured logging (JSON), log aggregation (Loki or hosted)
- Alerting rules for: AI job failure rate, payment verify failure rate, ad reward fraud spike, DB connection pool saturation, p99 latency
- Staging environment (separate VPS, separate DB, real Play sandbox)
- Load test plan: k6 or Locust, target 5k concurrent feed reads + 500 unlocks/min before soft launch
- Restore runbook for PostgreSQL + Redis
- Single-VPS migration trigger documented (e.g., DB CPU > 60% sustained 1h or DAU > 2k)

---

## UX/design fixes

- `text-muted #666680` on `bg-primary #0B0B0F` is ~2.66:1 contrast — fails WCAG AA for body. Lighten to ~`#9999B3` or restrict to non-text elements.
- Define empty / loading / error / offline states for every screen
- Accessibility: TalkBack content descriptions, dynamic font scaling beyond reader-mode slider, reduced-motion respect
- Tablet / foldable layouts
- Haptic feedback patterns spec

---

## Sequencing fixes

- Subscription product creation in Play Console must start week 8, not week 12 (review delay)
- Editorial cost line in §22 (30 stories × 30–60 min review = real launch cost not budgeted)
- Admin 2FA must ship in week 1–2, not bolted on later

---

## Items deferred but tracked

- Synchronized text highlighting (returns with audio)
- Voice cloning, story trailers, interactive stories, creator accounts (v2.0+)
- Elasticsearch/Meilisearch migration (v1.1 — PG full-text for MVP)
- ML-based recommendations (v2.0+)

---

*Living doc. Update as decisions land or new gaps surface.*
