# Immersive AI Story App — Full Product Blueprint v2.0

> **Updated with detailed architecture, corrected risks, sequenced MVP, and production-grade specifications**

---

# Table of Contents

1. [Project Vision & Positioning](#1-project-vision--positioning)
2. [Market & Competitive Analysis](#2-market--competitive-analysis)
3. [Core Architecture](#3-core-architecture)
4. [Database Schema](#4-database-schema)
5. [Backend API — Full Specification](#5-backend-api--full-specification)
6. [AI Worker Service — Full Specification](#6-ai-worker-service--full-specification)
7. [TTS Worker Service — Full Specification](#7-tts-worker-service--full-specification)
8. [Mobile App — UI/UX Design System](#8-mobile-app--uiux-design-system)
9. [Mobile App — All Screens & Flows](#9-mobile-app--all-screens--flows)
10. [Admin Dashboard — Full Specification](#10-admin-dashboard--full-specification)
11. [Monetization System — Full Detail](#11-monetization-system--full-detail)
12. [Security Architecture](#12-security-architecture)
13. [Content Strategy & Cold Start Plan](#13-content-strategy--cold-start-plan)
14. [Search & Discovery Architecture](#14-search--discovery-architecture)
15. [Analytics & Tracking System](#15-analytics--tracking-system)
16. [Push Notification System](#16-push-notification-system)
17. [Offline & Caching Architecture](#17-offline--caching-architecture)
18. [Play Store Compliance](#18-play-store-compliance)
19. [DevOps & Deployment](#19-devops--deployment)
20. [MVP Execution Plan](#20-mvp-execution-plan)
21. [Post-MVP Roadmap](#21-post-mvp-roadmap)
22. [Infrastructure Cost Estimates](#22-infrastructure-cost-estimates)

---

# 1. Project Vision & Positioning

## Core Concept

An Android-first immersive AI-powered story platform combining:

- WebNovel-style addictive storytelling with cliffhanger loops
- AI-generated and admin-curated content
- AI voice narration with synchronized text highlighting
- TikTok-style vertical discovery feed
- Coin/subscription monetization
- Cinematic dark UI with emotional immersion

## Positioning Statement

> "TikTok meets Netflix meets Audible — but for AI-generated stories, on mobile, with WebNovel monetization."

## Differentiation vs. Competitors

| Feature | Our App | Wattpad | Pocket FM | Radish |
|---|---|---|---|---|
| AI-generated content | ✅ | ❌ | Partial | ❌ |
| TikTok-style feed | ✅ | ❌ | ❌ | ❌ |
| Synchronized audio+text | ✅ | ❌ | ✅ | ❌ |
| Admin-controlled content | ✅ | ❌ | ✅ | ✅ |
| Dynamic coin + sub model | ✅ | Partial | ✅ | ✅ |
| Cinematic dark UI | ✅ | ❌ | ❌ | ❌ |

---

# 2. Market & Competitive Analysis

## Target Audience

**Primary:** Women 18–34, South/Southeast Asia, US diaspora
**Secondary:** Men 18–28 who consume fantasy/thriller/sci-fi
**Tertiary:** Audiobook listeners who want shorter-form content

## Top Genres by Revenue (Mobile Story Apps)

1. Dark Romance
2. Mafia / Billionaire Romance
3. Werewolf / Vampire Paranormal
4. Revenge / Second Chance
5. Fantasy / Cultivation (xianxia style)
6. Horror / Psychological Thriller
7. Sci-Fi / Dystopia
8. LGBTQ+ Romance
9. Office / CEO Romance
10. War / Military

## Content Volume at Launch

Minimum viable content library before soft launch:
- 30 complete stories (at least 15 chapters each)
- 5 genres minimum
- At least 10 stories with full audio narration
- All covers AI-generated (Midjourney or Stable Diffusion)

---

# 3. Core Architecture

## System Overview

```
[Android App] ←──→ [Cloudflare CDN]
                          │
                    [Nginx Gateway]
                          │
              ┌───────────┼───────────┐
              │           │           │
       [FastAPI Main]  [AI Worker]  [TTS Worker]
              │           │           │
        [PostgreSQL]  [Redis Queue] [Object Storage]
                               │
                        [Cloudflare R2]
```

## Service Breakdown

### 1. Mobile App (Flutter)
- Android-first
- Dart + Flutter 3.x
- BLoC state management
- Hive for local storage/cache
- dio for HTTP
- just_audio for playback
- flutter_background_service for lock screen audio

### 2. Main Backend API (FastAPI)
- Python 3.11+
- FastAPI + Uvicorn
- SQLAlchemy ORM
- Alembic migrations
- Redis for sessions + caching
- Celery for background tasks

### 3. AI Worker (FastAPI microservice)
- OpenRouter or direct Claude/DeepSeek API
- Celery task queue
- Redis broker
- Story/chapter generation pipeline

### 4. TTS Worker (FastAPI microservice)
- Piper TTS (MVP) — free, fast, runs on CPU
- Coqui XTTS-v2 (v1.1) — higher quality, needs GPU
- ElevenLabs API fallback (for premium voice tiers)
- FFmpeg for audio post-processing
- MP3 output stored in Cloudflare R2

### 5. Admin Dashboard (React + Vite)
- React 18
- Tailwind CSS
- Shadcn/ui components
- React Query for data fetching
- Chart.js for analytics

### 6. Object Storage
- Cloudflare R2 (primary)
- Stores: audio files, cover images, user avatars, thumbnails
- Signed URLs with 1-hour expiry for premium content

### 7. Database
- PostgreSQL 16
- Read replica for analytics queries
- Redis for: sessions, cache, rate limits, queues

### 8. CDN & Proxy
- Cloudflare (free tier initially)
- HTTPS termination
- DDoS protection
- Rate limiting at edge

---

# 4. Database Schema

## Core Tables

### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    google_id VARCHAR(100) UNIQUE,
    apple_id VARCHAR(100) UNIQUE,
    avatar_url TEXT,
    is_guest BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    is_banned BOOLEAN DEFAULT FALSE,
    ban_reason TEXT,
    coin_balance INTEGER DEFAULT 0,
    subscription_tier VARCHAR(20) DEFAULT 'free',
    subscription_expires_at TIMESTAMPTZ,
    reading_streak INTEGER DEFAULT 0,
    last_read_at TIMESTAMPTZ,
    preferred_genres TEXT[],
    preferred_voice VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### stories
```sql
CREATE TABLE stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    author_name VARCHAR(100) DEFAULT 'AI Author',
    cover_url TEXT,
    summary TEXT,
    hook_line VARCHAR(500),
    genre VARCHAR(50) NOT NULL,
    tags TEXT[],
    tone VARCHAR(50),
    status VARCHAR(20) DEFAULT 'draft',
    is_featured BOOLEAN DEFAULT FALSE,
    is_trending BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    total_chapters INTEGER DEFAULT 0,
    free_chapters INTEGER DEFAULT 3,
    view_count BIGINT DEFAULT 0,
    like_count INTEGER DEFAULT 0,
    bookmark_count INTEGER DEFAULT 0,
    avg_rating DECIMAL(3,2) DEFAULT 0,
    total_ratings INTEGER DEFAULT 0,
    estimated_read_time INTEGER,
    estimated_listen_time INTEGER,
    language VARCHAR(10) DEFAULT 'en',
    ai_generated BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    scheduled_at TIMESTAMPTZ
);
```

### chapters
```sql
CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID REFERENCES stories(id) ON DELETE CASCADE,
    chapter_number INTEGER NOT NULL,
    title VARCHAR(255),
    content TEXT NOT NULL,
    word_count INTEGER,
    is_free BOOLEAN DEFAULT FALSE,
    coin_cost INTEGER DEFAULT 5,
    audio_url TEXT,
    audio_duration INTEGER,
    audio_status VARCHAR(20) DEFAULT 'pending',
    audio_voice VARCHAR(50),
    has_cliffhanger BOOLEAN DEFAULT FALSE,
    cliffhanger_preview TEXT,
    status VARCHAR(20) DEFAULT 'draft',
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(story_id, chapter_number)
);
```

### user_chapter_unlocks
```sql
CREATE TABLE user_chapter_unlocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    chapter_id UUID REFERENCES chapters(id),
    unlock_method VARCHAR(20),
    coins_spent INTEGER DEFAULT 0,
    unlocked_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, chapter_id)
);
```

### reading_progress
```sql
CREATE TABLE reading_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    story_id UUID REFERENCES stories(id),
    chapter_id UUID REFERENCES chapters(id),
    scroll_position INTEGER DEFAULT 0,
    audio_position INTEGER DEFAULT 0,
    mode VARCHAR(10) DEFAULT 'read',
    last_read_at TIMESTAMPTZ DEFAULT NOW(),
    completed BOOLEAN DEFAULT FALSE,
    UNIQUE(user_id, story_id)
);
```

### coin_transactions
```sql
CREATE TABLE coin_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    amount INTEGER NOT NULL,
    type VARCHAR(30) NOT NULL,
    description TEXT,
    reference_id UUID,
    balance_after INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### bookmarks
```sql
CREATE TABLE bookmarks (
    user_id UUID REFERENCES users(id),
    story_id UUID REFERENCES stories(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
);
```

### comments
```sql
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    chapter_id UUID REFERENCES chapters(id),
    parent_id UUID REFERENCES comments(id),
    content TEXT NOT NULL,
    like_count INTEGER DEFAULT 0,
    is_spoiler BOOLEAN DEFAULT FALSE,
    is_hidden BOOLEAN DEFAULT FALSE,
    moderation_status VARCHAR(20) DEFAULT 'approved',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### story_likes
```sql
CREATE TABLE story_likes (
    user_id UUID REFERENCES users(id),
    story_id UUID REFERENCES stories(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
);
```

### notifications
```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    body TEXT,
    data JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### admin_users
```sql
CREATE TABLE admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'editor',
    permissions JSONB DEFAULT '{}',
    totp_secret VARCHAR(100),
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### app_config
```sql
CREATE TABLE app_config (
    key VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    updated_by UUID REFERENCES admin_users(id),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

### ai_generation_jobs
```sql
CREATE TABLE ai_generation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(50) NOT NULL,
    story_id UUID REFERENCES stories(id),
    chapter_id UUID REFERENCES chapters(id),
    status VARCHAR(20) DEFAULT 'pending',
    input_params JSONB,
    output JSONB,
    error TEXT,
    tokens_used INTEGER,
    cost_usd DECIMAL(8,4),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### tts_jobs
```sql
CREATE TABLE tts_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID REFERENCES chapters(id),
    voice_id VARCHAR(50),
    status VARCHAR(20) DEFAULT 'pending',
    audio_url TEXT,
    duration_seconds INTEGER,
    file_size_bytes INTEGER,
    engine VARCHAR(30),
    error TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
```

### rewarded_ad_events
```sql
CREATE TABLE rewarded_ad_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    device_fingerprint VARCHAR(255),
    ad_network VARCHAR(50),
    reward_type VARCHAR(30),
    reward_amount INTEGER,
    chapter_id UUID,
    validated BOOLEAN DEFAULT FALSE,
    ip_address INET,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

# 5. Backend API — Full Specification

## Base URL Structure
```
https://api.yourdomain.com/v1/
```

## Authentication Endpoints

```
POST   /auth/register
POST   /auth/login
POST   /auth/google
POST   /auth/guest
POST   /auth/refresh
POST   /auth/logout
POST   /auth/verify-email
POST   /auth/forgot-password
POST   /auth/reset-password
DELETE /auth/account          (GDPR data deletion)
```

## Story Endpoints

```
GET    /stories                    (paginated feed)
GET    /stories/trending
GET    /stories/new
GET    /stories/recommended        (personalized)
GET    /stories/{id}
GET    /stories/{id}/chapters
GET    /stories/{id}/chapters/{num}
GET    /stories/{id}/comments
POST   /stories/{id}/like
POST   /stories/{id}/bookmark
DELETE /stories/{id}/bookmark
GET    /stories/search?q=&genre=&tags=
GET    /stories/genres
GET    /stories/{id}/related
```

## Chapter Endpoints

```
GET    /chapters/{id}              (checks unlock status)
POST   /chapters/{id}/unlock       (spend coins)
POST   /chapters/{id}/unlock/ad    (rewarded ad)
GET    /chapters/{id}/audio        (returns signed URL)
GET    /chapters/{id}/comments
POST   /chapters/{id}/comments
POST   /chapters/{id}/progress     (save reading progress)
```

## User Endpoints

```
GET    /user/profile
PUT    /user/profile
GET    /user/bookmarks
GET    /user/history
GET    /user/coins
GET    /user/coins/transactions
GET    /user/unlocks
GET    /user/achievements
GET    /user/streak
GET    /user/notifications
PUT    /user/notifications/read
PUT    /user/preferences
DELETE /user/account
```

## Payment Endpoints

```
POST   /payments/google-play/verify       (Play Billing)
POST   /payments/coins/packages           (list packages)
POST   /payments/coins/purchase           (one-time coins)
POST   /payments/subscription/subscribe
POST   /payments/subscription/cancel
GET    /payments/subscription/status
POST   /payments/ad-reward/validate       (server-side)
```

## Admin Endpoints

```
GET    /admin/stories
POST   /admin/stories
PUT    /admin/stories/{id}
DELETE /admin/stories/{id}
POST   /admin/stories/{id}/publish
POST   /admin/stories/{id}/schedule
POST   /admin/stories/{id}/cover
POST   /admin/chapters
PUT    /admin/chapters/{id}
POST   /admin/ai/generate-story
POST   /admin/ai/generate-chapter
POST   /admin/ai/regenerate
POST   /admin/tts/generate/{chapter_id}
POST   /admin/tts/regenerate/{chapter_id}
GET    /admin/tts/queue
GET    /admin/users
PUT    /admin/users/{id}/ban
PUT    /admin/users/{id}/subscription
GET    /admin/analytics/dashboard
GET    /admin/analytics/revenue
GET    /admin/analytics/retention
GET    /admin/config
PUT    /admin/config
GET    /admin/comments/moderation-queue
PUT    /admin/comments/{id}/hide
GET    /admin/ai/jobs
GET    /admin/tts/jobs
POST   /admin/notifications/broadcast
GET    /admin/ads/config
PUT    /admin/ads/config
```

## API Response Format

All endpoints return:
```json
{
  "success": true,
  "data": {},
  "meta": {
    "page": 1,
    "per_page": 20,
    "total": 1000
  },
  "error": null
}
```

## Rate Limits

| Endpoint Group | Limit |
|---|---|
| Auth endpoints | 10/min per IP |
| Story feed | 60/min per user |
| Chapter unlock | 20/min per user |
| AI generation (admin) | 10/min per admin |
| Ad reward validation | 5/min per device |
| Search | 30/min per user |

---

# 6. AI Worker Service — Full Specification

## Responsibilities
- Full story generation
- Chapter generation
- Story continuation
- Cliffhanger improvement
- Summary generation
- Character creation
- Content moderation check

## Story Generation Prompt Architecture

### System Prompt Template
```
You are a professional story writer specializing in [GENRE] fiction for mobile reading apps.
Write for an audience that enjoys [TONE] stories with strong emotional hooks.
Your writing style: cinematic, paced for mobile reading, emotionally engaging.
Each chapter should be 600–900 words.
Always end chapters with a compelling hook or cliffhanger.
Never use explicit sexual content.
```

### Story Generation Input Schema
```json
{
  "genre": "dark_romance",
  "tone": "emotional, intense, slow-burn",
  "setting": "contemporary urban",
  "characters": [
    {"name": "Elena", "role": "protagonist", "trait": "fierce, broken inside"},
    {"name": "Marcus", "role": "love_interest", "trait": "cold billionaire with secrets"}
  ],
  "plot_style": "enemies_to_lovers",
  "chapter_count": 20,
  "free_chapters": 3,
  "cliffhanger_frequency": "every_chapter"
}
```

### Chapter Continuation Context
Always pass to the AI:
- Story title + summary
- Character list
- Previous chapter summary (not full text — saves tokens)
- Chapter number
- Target chapter word count
- Cliffhanger instruction for this chapter

## Quality Control Pipeline

```
[AI generates chapter]
        ↓
[Word count check: 600–900 words]
        ↓
[Profanity/content filter]
        ↓
[Cliffhanger detection check]
        ↓
[Admin preview before publish]
        ↓
[Approved → TTS queue]
```

## Recommended AI Models

| Use Case | Model | Reason |
|---|---|---|
| Full story generation | Claude Sonnet | Best narrative quality |
| Chapter generation | DeepSeek V3 | Cheap + good quality |
| Summaries | DeepSeek V2.5 | Very cheap, fast |
| Cliffhanger rewrite | Claude Sonnet | Nuanced creative writing |
| Content moderation | OpenAI Moderation API | Purpose-built |

## Token & Cost Estimates

| Job | Avg Tokens | Cost (DeepSeek) | Cost (Claude) |
|---|---|---|---|
| Full story (20 chapters) | ~25,000 | ~$0.07 | ~$0.75 |
| Single chapter | ~1,200 | ~$0.003 | ~$0.04 |
| Summary | ~400 | ~$0.001 | ~$0.012 |
| Character set | ~800 | ~$0.002 | ~$0.024 |

**Daily budget for 10 new stories:** ~$5–$10 with mixed model strategy

## Job Queue Architecture

- Celery + Redis broker
- Priority queues:
  - HIGH: admin-triggered generation
  - NORMAL: scheduled generation
  - LOW: continuation/summary jobs
- Retry on failure: 3 attempts with exponential backoff
- Job status tracked in `ai_generation_jobs` table

---

# 7. TTS Worker Service — Full Specification

## TTS Engine Selection (Critical Decision)

### Option A: Piper TTS (MVP)
- **Cost:** Free, CPU-only
- **Quality:** Acceptable (robotic but usable)
- **Speed:** Fast (~30s per chapter on 4 vCPU)
- **Risk:** May hurt retention if audio feels low quality
- **Use for:** MVP to validate feature, not quality

### Option B: Coqui XTTS-v2 (v1.1)
- **Cost:** Free, GPU needed (~$0.50–$1/hr on Hetzner GPU)
- **Quality:** Near-human, very immersive
- **Speed:** ~2–3 min per chapter on GPU
- **Recommended for:** Post-MVP, premium tier

### Option C: ElevenLabs API (Recommended Hybrid)
- **Cost:** ~$0.30 per 1,000 characters (~$0.90 per chapter)
- **Quality:** Best available
- **Speed:** Seconds
- **Strategy:** Use for premium voice tier only. Charge users 20+ coins per audio chapter. At 5 coins = $0.05, 20 coins = $0.20, still below cost → offset with subscription revenue

### Recommended MVP Strategy
- Use Piper TTS for all voices (free, fast)
- Mark audio as "Basic Narration" in UI
- Add "Premium Voice" lock (ElevenLabs) for paying users
- This creates a natural monetization layer for audio

## Voice Library (MVP)

| Voice ID | Name | Style | Engine |
|---|---|---|---|
| v_neutral_f | Aria | Neutral female | Piper |
| v_warm_f | Sophia | Warm, romantic | Piper |
| v_dark_m | Marcus | Dark, deep male | Piper |
| v_horror | Shadow | Whispery horror | Piper |
| v_epic_m | Titan | Epic fantasy narrator | Piper |
| v_premium_f | Nova | Ultra-realistic female | ElevenLabs |
| v_premium_m | Atlas | Ultra-realistic male | ElevenLabs |

## Audio Generation Pipeline

```
[Chapter published]
       ↓
[TTS job created in queue]
       ↓
[Worker picks up job]
       ↓
[Text cleaned: remove markdown, fix punctuation]
       ↓
[TTS engine generates WAV]
       ↓
[FFmpeg converts: WAV → MP3 128kbps]
       ↓
[Uploaded to Cloudflare R2]
       ↓
[chapter.audio_url + audio_duration updated]
       ↓
[chapter.audio_status = 'ready']
```

## Audio Text Preprocessing

Before sending to TTS, clean the text:
- Strip chapter titles (narrated separately)
- Remove markdown formatting (**, *, _, etc.)
- Expand abbreviations (Mr. → Mister, etc.)
- Add natural pauses via SSML where supported
- Replace em dashes with comma+pause
- Convert quotes to spoken dialogue markers

## Storage & Bandwidth Estimates

| Stat | Estimate |
|---|---|
| Avg MP3 size per chapter | 3–5 MB |
| 1,000 chapters library | 3–5 GB storage |
| 10,000 daily listens | ~40–50 GB/day bandwidth |
| Cloudflare R2 cost | ~$0.015/GB storage + free egress via CDN |

## Signed URL Security

Audio URLs expire after 2 hours. Generated server-side per request. Only authenticated users with valid unlocks receive signed URLs. Never expose direct R2 bucket URLs.

---

# 8. Mobile App — UI/UX Design System

## Design Philosophy

The app must feel:
- Dark cinematic (like a moody Netflix/Spotify hybrid)
- Smooth 60fps animations throughout
- Emotionally immersive — UI should not distract from story
- Premium, not cheap
- Minimal chrome, maximum content

## Color System

### Primary Palette

| Token | Value | Usage |
|---|---|---|
| `bg-primary` | #0B0B0F | Main backgrounds |
| `bg-secondary` | #14141A | Cards, sheets |
| `bg-tertiary` | #1E1E28 | Inputs, elevated surfaces |
| `accent` | #8B5CF6 | CTAs, progress, highlights |
| `accent-secondary` | #EC4899 | Hearts, romance elements |
| `text-primary` | #FFFFFF | Headlines |
| `text-secondary` | #B3B3B3 | Body text |
| `text-muted` | #666680 | Placeholders, hints |
| `success` | #22C55E | Rewards, coins |
| `warning` | #F59E0B | Streak alerts |
| `danger` | #EF4444 | Bans, errors |
| `gold` | #F5C518 | Coins, premium |

### Reading Theme Palette

| Theme | Background | Text | Paper Texture |
|---|---|---|---|
| Dark (default) | #0B0B0F | #E8E8E8 | None |
| Warm Dark | #1A1208 | #F0DDB0 | Sepia overlay |
| Paper | #F5F0E8 | #2D2D2D | Subtle grain |
| Forest | #0A1A0A | #B8E6B8 | None |
| Ocean | #0A0F1A | #B0C8F0 | None |

## Typography

| Role | Font | Size | Weight |
|---|---|---|---|
| Display | Playfair Display | 28–36sp | Bold |
| Headline | Inter | 20–24sp | SemiBold |
| Body | Lora or Georgia | 16–20sp | Regular |
| Caption | Inter | 12–14sp | Regular |
| Button | Inter | 14–16sp | SemiBold |

**Reader mode:** User-adjustable 14sp–24sp, line height 1.6–2.0

## Iconography
- Use Lucide Icons for UI chrome
- Custom icons for: coins (gold), genres, voice types
- All icons outlined style (no filled in dark UI)

## Animation Guidelines

| Interaction | Animation | Duration |
|---|---|---|
| Page transitions | Fade + slide up | 300ms |
| Card tap | Scale 0.98 + fade | 150ms |
| Feed swipe | Momentum scroll | Native |
| Modal open | Slide from bottom | 350ms ease-out |
| Coin earn | Bounce + sparkle particle | 600ms |
| Audio waveform | Animated bars | Loop |
| Text highlight (audio sync) | Smooth gradient sweep | Per word |

## Component Library

### Story Card (Feed)
- Full-bleed cover image with gradient overlay
- Genre tag pill (top-left)
- Title (bottom, Playfair Display)
- Hook line (1 line, text-secondary)
- Stats row: 👁 views · ❤ likes · 📖 chapters
- Voice preview button (floating, bottom-right)
- Bookmark icon (top-right)

### Chapter Unlock Sheet
- Story title
- Chapter preview (first 100 words, blurred)
- Three unlock options:
  - Coins (cost badge)
  - Watch Ad (timer countdown if cooldown)
  - Go Premium (accent CTA)

### Audio Player Bar (Mini)
- Floating bottom bar above nav
- Cover thumbnail
- Title + chapter number
- Progress bar (thin accent line)
- Play/pause
- 15s forward

### Audio Player (Fullscreen)
- Blurred cover background
- Waveform visualization
- Synced text scroll (optional)
- Speed: 0.75x / 1x / 1.25x / 1.5x / 2x
- Sleep timer
- Ambient sound toggle
- Voice switch (if premium)

---

# 9. Mobile App — All Screens & Flows

## App Navigation Structure

```
├── Splash Screen
├── Onboarding (first launch only)
├── Auth Flow
│   ├── Welcome
│   ├── Login
│   ├── Register
│   └── Guest Mode
└── Main App (Bottom Nav)
    ├── Tab 1: Home Feed
    ├── Tab 2: Search & Discover
    ├── Tab 3: Library (bookmarks + history)
    └── Tab 4: Profile
```

## Screen Specifications

### Splash Screen
- Animated logo (logo mark draws in)
- Ambient particle background (purple mist)
- Duration: 1.5s
- Then: check auth state → route to Home or Onboarding

### Onboarding (4 screens)
1. **Discover Stories** — TikTok-style feed preview animation
2. **Listen Anywhere** — Audio wave animation
3. **AI-Powered** — AI writing animation with typewriter effect
4. **Earn & Unlock** — Coin animation
- Skip button always visible
- Progress dots
- Final CTA: "Start Reading" → Auth

### Home Feed
- Full-screen vertical card stack (PageView)
- Pull-to-refresh at top
- Auto-advance voice preview (3s after landing on card)
- Each card: swipe up = next story, tap = story details
- Sticky category filter pills (Dark Romance, Fantasy, etc.)
- Bottom 10%: related/trending strip

### Story Details Page
- Parallax scrolling cover header
- Back button (top-left) + share (top-right)
- Title (Playfair Display, 28sp)
- Genre + tags (pill chips)
- Author + AI badge
- Rating stars + review count
- Summary text (expandable)
- Stats: 👁 total views · 📖 chapters · 🎧 listen time
- TWO main CTAs:
  - PRIMARY: "Read Now" (accent purple)
  - SECONDARY: "Listen Now" (outline)
- Chapter list (shows lock icon on premium chapters)
- Comments section
- Related stories strip (horizontal scroll)

### Reader Mode
- Clean full-screen reading surface
- System bar hidden (immersive mode)
- Tap center = show/hide controls
- Controls (semi-transparent overlay):
  - Top: chapter title + progress (x/total)
  - Bottom: font size, theme, auto-scroll, audio mode
- Chapter end: show unlock sheet OR "Next Chapter" button
- Long press word = dictionary (future)
- Reading position auto-saved every 10 seconds

### Audio Mode (Fullscreen)
- Blurred/darkened cover as background
- Centered waveform animation (animated bars in accent color)
- Chapter title + story name
- Synced text (current sentence highlighted in accent)
- Timeline scrubber (thin accent line)
- Controls:
  - ⏮ Previous Chapter | ⏭ Next Chapter
  - ⏪ 15s back | ▶ Play/Pause | ⏩ 15s forward
  - Speed selector (0.75x – 2x)
  - Sleep timer (15/30/45/60 min)
  - Ambient sound toggle (rain, fire, forest)
- Swipe down = minimize to mini player

### Search & Discover
- Search bar (top, always focused on enter)
- Trending search tags
- Genre grid (cover images, 2-col)
- "New This Week" horizontal scroll
- "Most Listened" horizontal scroll
- "Hidden Gems" (low view count, high rating)
- Search results: story cards in list format

### Library
- Tabs: Bookmarks | History | Downloads
- Sort: Recently Added | Last Read | A-Z
- Continue Reading section (shows last opened story)
- Downloaded for offline section (premium users)

### Profile
- Avatar + username
- Streak flame (current streak days)
- Stats: Stories Read · Hours Listened · Chapters Unlocked
- Achievement badges row
- Coin balance (tappable → coin store)
- Subscription status badge
- Settings icon (top-right)

### Coin Store
- Current balance (large, centered)
- Earn Coins section:
  - Watch ad (+10 coins)
  - Daily login (+5 coins)
  - Reading milestone (+coins)
- Buy Coins section:
  - Packages: 50, 150, 350, 800, 2000 coins
  - Best value badge on most popular
- Subscription upsell banner (unlimited unlock)

### Settings
- Account section: Edit profile, Change password, Linked accounts
- Reading section: Font, Theme, Auto-scroll speed
- Audio section: Default voice, Playback speed, Ambient sounds
- Notifications: Toggle each notification type
- Privacy: Data deletion request
- Legal: Privacy Policy, Terms, Content Policy
- App version + feedback

---

# 10. Admin Dashboard — Full Specification

## Tech Stack
- React 18 + Vite
- Tailwind CSS + Shadcn/ui
- React Query (server state)
- Zustand (local state)
- Recharts (analytics)
- React Hook Form (forms)
- Hosted at: admin.yourdomain.com (Nginx, admin-only access)

## Dashboard Overview

### Metrics Widgets (Real-time, 5-min refresh)
- Daily Active Users (DAU) with 7-day sparkline
- Revenue Today ($) with yesterday comparison
- Stories Published (total + today)
- Total Chapters with Audio
- AI Jobs (pending / running / failed)
- TTS Jobs (pending / running / failed)
- Storage Used (GB)
- Active Subscriptions

## Story Management

### Story List View
- Table with: title, genre, status, chapters, views, created
- Filters: status, genre, AI-generated, featured
- Bulk actions: publish, unpublish, delete, schedule
- Quick actions per row: edit, preview, duplicate

### Story Editor
- Rich text editor (Quill or TipTap)
- Fields: title, genre, tags, tone, summary, hook line, cover image
- Chapter list with drag-to-reorder
- Chapter editor:
  - Content (rich text)
  - Free/paid toggle
  - Coin cost (if paid)
  - Cliffhanger preview text
  - Schedule publish date
  - Publish/Draft toggle
  - TTS status + regenerate button

### Cover Image Upload
- Upload from device
- AI image prompt (calls Stability AI / Midjourney API)
- Crop/resize tool
- Auto-thumbnail generation

## AI Generation Panel

### Generate Full Story
```
Inputs:
- Genre (dropdown)
- Tone/mood (multi-select tags)
- Setting (text)
- Main character names + traits
- Love interest / antagonist names + traits
- Plot type (enemies to lovers, revenge, etc.)
- Chapter count (5 / 10 / 15 / 20 / custom)
- Writing style notes (optional)

Outputs (shown in preview):
- Title (editable)
- Summary (editable)
- Hook line (editable)
- Chapter outlines (editable before full generation)
- [Approve → Generate full chapters]
```

### Generate Single Chapter
```
Inputs:
- Story selection
- Chapter number
- Previous chapter summary (auto-filled)
- Special instructions (optional)
- Cliffhanger type (action, emotional, revelation, danger)

Output:
- Generated chapter (editable in rich text)
- [Save as Draft] [Publish] [Regenerate]
```

### Improve Content
```
Options:
- Improve cliffhanger
- Rewrite opening hook
- Improve dialogue
- Add more emotion
- Shorten chapter
- Expand chapter
```

## Audio Management

### TTS Queue Monitor
- Live queue status (pending / processing / done / failed)
- Priority: Manual trigger > Scheduled > Auto-queue
- Estimated completion time per job
- Cost tracking (for ElevenLabs jobs)

### Voice Assignment
- Per-story voice assignment (all chapters use same voice)
- Override per chapter if needed
- Preview button (plays first 30s)
- Batch regenerate (if voice changed story-wide)

## User Management

### User Table
- Columns: username, email, created, subscription, coins, ban status
- Filters: subscription tier, banned, join date
- Search by email or username

### User Detail View
- Full profile
- Coin transaction history
- Reading history
- Subscription history
- Ban/unban with reason
- Manual coin adjustment (add/remove)
- Force logout (invalidate all sessions)
- Delete account (GDPR)

## Analytics Panel

### Retention Chart
- Day 1, 3, 7, 14, 30 retention %
- Cohort view by join week

### Revenue Chart
- Daily/weekly/monthly revenue
- Breakdown: subscriptions vs coins vs ads
- Revenue per user (ARPU)

### Content Performance
- Top stories by views, likes, completion rate
- Audio vs read split per story
- Chapter drop-off analysis (which chapter loses readers)
- Genre performance comparison

### Ad Analytics
- Impressions, completions, eCPM
- Rewarded ad completion rate
- Revenue by ad network

## App Config (Remote Config)

Editable from dashboard, applied instantly without app update:

```json
{
  "ad_config": {
    "rewarded_ads_enabled": true,
    "rewarded_cooldown_minutes": 30,
    "coins_per_rewarded_ad": 10,
    "interstitial_enabled": true,
    "interstitial_chapter_interval": 5,
    "banner_enabled": false
  },
  "coin_config": {
    "chapter_base_cost": 5,
    "premium_chapter_cost": 10,
    "daily_login_coins": 5,
    "streak_bonus_multiplier": 1.5
  },
  "content_config": {
    "free_chapters_default": 3,
    "feed_algorithm": "trending",
    "voice_preview_seconds": 30
  },
  "theme_config": {
    "accent_color": "#8B5CF6",
    "seasonal_theme": null,
    "event_banner": null
  },
  "feature_flags": {
    "comments_enabled": true,
    "offline_mode_enabled": true,
    "interactive_stories_enabled": false
  }
}
```

---

# 11. Monetization System — Full Detail

## Revenue Model

| Stream | Target % | Notes |
|---|---|---|
| Subscriptions | 40% | Highest LTV, most stable |
| Rewarded Ads | 25% | High volume in price-sensitive markets |
| Coin Purchases | 25% | Impulse driven by cliffhangers |
| Premium Features | 10% | Premium voices, themes, offline |

## Coin System

### Earning Coins
| Method | Coins | Conditions |
|---|---|---|
| Daily login | +5 | Once per day |
| 3-day streak | +15 | Bonus |
| 7-day streak | +50 | Bonus |
| Watch rewarded ad | +10 | Max 5x/day |
| Complete chapter | +2 | First time only |
| Rate a story | +3 | Once per story |
| Refer a friend | +50 | Friend must sign up |
| Reading milestone | +20 | Every 10 chapters total |

### Spending Coins
| Item | Cost | Notes |
|---|---|---|
| Standard chapter | 5 coins | Default |
| Premium chapter | 10 coins | Admin-set |
| Premium voice audio | 20 coins | Per chapter |
| Reading theme unlock | 50 coins | Permanent |
| Exclusive story | 100–200 coins | Admin-set |

### Coin Packages
| Package | Coins | Price USD | Bonus |
|---|---|---|---|
| Starter | 50 | $0.99 | – |
| Popular | 150 | $2.99 | – |
| Value | 350 | $5.99 | +50 bonus |
| Best Value | 800 | $11.99 | +200 bonus |
| Mega | 2000 | $24.99 | +500 bonus |

## Subscription Tiers

### Free Tier
- 3 free chapters per story
- Basic narration (Piper TTS)
- Ads between sessions
- Limited to 1 offline download (future)

### Premium ($5.99/month or $49.99/year)
- Unlimited chapter access
- No ads
- Premium voices (ElevenLabs)
- Unlimited offline downloads
- Early access to new stories
- Exclusive premium story library
- 100 coins/month bonus

## Rewarded Ad Psychology Flow

```
[User reads chapter 3 of story]
         ↓
[Cliffhanger scene at end]
         ↓
[Chapter 4 lock screen appears]
         ↓
[Three options:]
  ┌──────────────────────────────┐
  │  🪙 Unlock with 5 coins      │
  │  📺 Watch ad (free!)         │  ← most taps
  │  ⭐ Go Premium               │
  └──────────────────────────────┘
         ↓
[User watches 30s rewarded ad]
         ↓
[Chapter unlocks + coins earned]
         ↓
[Next chapter — same loop]
```

**Key insight:** Users watching 5+ rewarded ads/day are prime subscription upsell targets. Show subscription offer after 3rd rewarded ad in a session.

## Anti-Abuse for Rewarded Ads

Server-side validation required:
1. Verify ad completion via AdMob server callbacks
2. Check device fingerprint (max 5 rewards/day/device)
3. Check IP rate limiting
4. Detect emulator/VPN (reduce reward or block)
5. Cooldown: 30 minutes between rewarded ads per user
6. All rewards credited server-side, never client-side

## Regional Pricing Strategy

| Region | Coin Price Adjustment | Ad Frequency | Rewarded Value |
|---|---|---|---|
| US/UK/AU | Standard | Normal | +10 coins |
| Southeast Asia | –40% | Higher | +8 coins |
| South Asia | –60% | Highest | +6 coins |
| Latin America | –40% | Higher | +8 coins |
| Middle East | Standard | Normal | +10 coins |

Use Firebase Remote Config or backend config for this.

---

# 12. Security Architecture

## Authentication & Session Management

- JWT access tokens: 15-minute expiry
- Refresh tokens: 30-day expiry, rotating
- Refresh tokens stored: httpOnly cookie (web) or secure storage (mobile)
- Maximum 5 concurrent sessions per user
- Session invalidation on password change
- Device fingerprinting stored per session

## API Security

```python
# Rate limiting (Redis-backed)
# Example: FastAPI dependency
@app.middleware("http")
async def rate_limit(request, call_next):
    key = f"rl:{request.client.host}:{request.url.path}"
    count = redis.incr(key)
    if count == 1:
        redis.expire(key, 60)
    if count > RATE_LIMIT:
        raise HTTPException(429)
    return await call_next(request)
```

- HTTPS-only (HSTS headers)
- CORS: whitelist mobile app domain only
- SQL injection: SQLAlchemy parameterized queries only
- XSS: content security policy headers
- Input validation: Pydantic models on all inputs
- Request signing for sensitive operations (payments)

## Content Security

- Premium chapter content served only via authenticated endpoints
- Audio URLs signed with 2-hour expiry
- Image URLs: public CDN (cover images are not sensitive)
- Anti-hotlinking: Cloudflare rules
- Watermarking: planned for premium audio (v2)

## Password Security

- bcrypt with cost factor 12
- Minimum 8 characters, complexity enforced
- Breach detection: optional HaveIBeenPwned check
- OTP for password reset (6-digit, 10-minute expiry)

## Admin Security

- 2FA mandatory (TOTP, Google Authenticator)
- IP whitelisting for admin panel
- All admin actions logged to audit trail
- Role-based permissions:
  - Super Admin: all access
  - Editor: story/chapter management only
  - Moderator: user management + comments only
  - Analytics: read-only analytics

## Suspicious Activity Detection

Triggers auto-review:
- 10+ coin purchases in 1 hour
- 5+ rewarded ads from same device in 1 hour
- Login from new country + new device
- Password reset + immediate purchase
- Account creation + immediate large purchase

---

# 13. Content Strategy & Cold Start Plan

## The Cold Start Problem

A TikTok-style feed needs content BEFORE the first user arrives. Empty or sparse feed = immediate churn. This must be solved pre-launch.

## Pre-Launch Content Plan (8 Weeks Before Launch)

### Week 1–2: Story Bible
- Define 10 story templates per genre
- Write character archetypes for each genre
- Create AI prompt templates for consistent quality

### Week 3–4: AI Generation Sprint
- Generate 50 stories (10 genres × 5 stories each)
- Review and approve 30 (60% pass rate expected)
- Admin review each story for quality, plot holes

### Week 5–6: Audio Production
- Generate TTS audio for all approved chapter 1–5s
- Priority: complete 15 stories with full audio first
- Cover image generation (Midjourney or SDXL)

### Week 7–8: QA + Polish
- Read every approved story (human review)
- Edit for quality
- Set featured stories, trending flags
- Configure feed ranking weights

## Content Quality Standards

Each published story must have:
- ✅ Cover image (1080×1620 portrait)
- ✅ At least 10 chapters published at launch
- ✅ At least 3 free chapters
- ✅ Audio on chapters 1–3 minimum
- ✅ Cliffhanger on every paid chapter
- ✅ Compelling hook line (< 100 chars)
- ✅ Human-reviewed (not raw AI output)

## Ongoing Content Cadence

Post-launch rhythm:
- 2 new stories published per week
- 5 new chapters added per existing story per week
- 1 featured story rotation per week
- Seasonal content (holidays, Valentine's, etc.) planned 4 weeks ahead

## Human Editorial Layer

Raw AI output should NEVER be published directly. Required pipeline:
1. AI generates story
2. Editor reviews + edits (30–60 min per story)
3. Admin approves
4. Scheduled for publish

Even minor improvements dramatically increase quality and retention.

---

# 14. Search & Discovery Architecture

## Search Implementation

For MVP: PostgreSQL full-text search (`tsvector` + `tsquery`)

```sql
ALTER TABLE stories ADD COLUMN search_vector tsvector;
CREATE INDEX ON stories USING GIN(search_vector);

-- Update trigger
CREATE TRIGGER update_search_vector
BEFORE INSERT OR UPDATE ON stories
FOR EACH ROW EXECUTE FUNCTION
tsvector_update_trigger(search_vector, 'pg_catalog.english', 
    'title', 'summary', 'tags', 'genre', 'author_name');
```

For v1.1: Migrate to Elasticsearch or Meilisearch for:
- Typo tolerance
- Faceted search
- Instant search-as-you-type
- Better relevance ranking

## Feed Ranking Algorithm

The home feed is ranked by a weighted score:

```python
def calculate_feed_score(story, user):
    recency_score = decay(story.published_at, half_life_hours=48)
    popularity_score = log(story.view_count + 1) * 0.3 + log(story.like_count + 1) * 0.4
    genre_match = 1.5 if story.genre in user.preferred_genres else 1.0
    completion_rate = story.avg_completion_rate * 0.3
    audio_bonus = 1.2 if story.has_audio else 1.0
    
    return recency_score * popularity_score * genre_match * completion_rate * audio_bonus
```

## Recommendation System

MVP: Rule-based (same genre + similar tags)
v1.1: Collaborative filtering (users who read X also read Y)
v2.0: Full ML model (user embedding × story embedding)

---

# 15. Analytics & Tracking System

## Events to Track

### User Events
```
app_open, app_background
screen_view {screen_name}
story_view {story_id, source}
story_read_start {story_id, chapter_id}
story_read_complete {story_id, chapter_id, time_spent}
story_listen_start {story_id, chapter_id, voice_id}
story_listen_complete {story_id, chapter_id}
chapter_unlock {method: coins|ad|subscription, coins_spent}
ad_rewarded_start, ad_rewarded_complete, ad_rewarded_skip
bookmark_add, bookmark_remove
story_share {platform}
search_query {query, results_count}
voice_preview_play {story_id}
feed_swipe {direction, story_id}
```

### Revenue Events
```
coin_purchase {package_id, coins, price_usd}
subscription_start {tier, price, duration}
subscription_cancel {reason}
subscription_renew {tier}
```

## Analytics Stack

| Tool | Purpose |
|---|---|
| Firebase Analytics | User events, funnel analysis |
| PostHog (self-hosted) | Product analytics, session replay |
| Firebase Crashlytics | Crash reporting |
| Custom PostgreSQL | Revenue, content analytics |
| Metabase | Internal BI dashboards |

## Key Metrics to Monitor Daily

| Metric | Target (Month 3) | Action if Below |
|---|---|---|
| Day 1 retention | >40% | Fix onboarding / content |
| Day 7 retention | >20% | Improve notification system |
| Rewarded ad completion | >70% | Reduce ad length |
| Chapter unlock rate | >30% per story | Improve cliffhangers |
| Audio listen rate | >25% of readers | Improve voice quality |
| Subscription conversion | >2% of MAU | Improve premium value |

---

# 16. Push Notification System

## Implementation
- Firebase Cloud Messaging (FCM)
- Backend sends via Firebase Admin SDK
- Notification preferences stored per user

## Notification Types

| Type | Trigger | Frequency Cap |
|---|---|---|
| New Chapter | Story user bookmarked gets new chapter | Immediate |
| Daily Streak | User hasn't opened app by 7pm | Once/day |
| Coin Offer | Special coin bonus event | Max 2x/week |
| New Story | Story in user's preferred genre | Max 3x/week |
| Reading Milestone | User reached X chapters total | Event-based |
| Streak Lost | User broke their streak | Once |
| Personalized Rec | AI-chosen story for user | Max 1x/day |

## Notification Best Practices

- Never send more than 2 notifications per day per user
- Respect quiet hours: no notifications 10pm–8am local time
- Deep link directly to the relevant screen
- A/B test notification copy via Remote Config
- Track open rate per notification type (target: >15%)

---

# 17. Offline & Caching Architecture

## What Gets Cached (Mobile)

### Hive (Local Storage)
- User profile + preferences
- Reading progress (all stories)
- Bookmarks list
- Recent search queries
- App config (remote config values)

### File Cache (flutter_cache_manager)
- Story covers: cached for 7 days
- Chapter content (text): cached for 30 days
- Audio files (offline downloaded): permanent until deleted

## Offline Download (Premium Feature)

When user downloads a story:
1. App requests signed URLs for all chapter audio files
2. Audio files downloaded in background (WorkManager)
3. Stored in app private directory
4. Chapter text content downloaded and stored in Hive
5. Available offline indefinitely (until premium expires)

## CDN Cache Strategy (Cloudflare)

| Content Type | Cache Duration | Cache-Control |
|---|---|---|
| Cover images | 30 days | public, max-age=2592000 |
| Audio files | 24 hours | public, max-age=86400 |
| Story feed API | 60 seconds | public, max-age=60 |
| Chapter content | 5 minutes | private, max-age=300 |
| Auth endpoints | No cache | no-store |

---

# 18. Play Store Compliance

## Required Policy Documents

Host at yourdomain.com/legal/:
- Privacy Policy (data collection, retention, deletion)
- Terms of Service
- Content Policy (acceptable/prohibited content)
- Cookie Policy (for web admin)
- DMCA / Copyright Policy

## Data Deletion Flow

Users must be able to delete their account and all associated data:
- In-app: Settings → Delete Account → Confirm
- Email request: privacy@yourdomain.com
- 30-day deletion window (legal requirement in some regions)
- Database: set `deleted_at`, schedule full purge in 30 days

## Content Moderation Requirements

- Report button on every comment and story
- Report categories: Spam, Harassment, Explicit Content, Copyright, Other
- Admin receives report → review → action within 48 hours
- AI-generated stories: auto-checked via OpenAI Moderation API
- Zero tolerance for: child endangerment, CSAM, extreme violence
- Content ratings clearly shown per story

## Subscription Billing Compliance

- Must use Google Play Billing API (not Stripe) for subscription in-app
- Price clearly shown before confirmation
- Cancellation must be accessible (not hidden)
- No free trials without clear disclosure
- Refund policy clearly stated

## Ad Compliance

- No ads that mimic UI buttons
- No ads in reader mode during active reading
- Rewarded ads must be clearly labeled "Watch an ad"
- No ads targeted at children under 13
- Frequency caps enforced server-side

---

# 19. DevOps & Deployment

## Server Architecture

### Initial VPS Setup (Single Server MVP)

```
VPS: 8GB RAM, 4 vCPU, 80GB SSD
OS: Ubuntu 24.04 LTS
Provider: Hetzner (best price/performance) or DigitalOcean
```

### Docker Compose Services (MVP)

```yaml
services:
  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]

  api:
    build: ./backend
    environment:
      - DATABASE_URL
      - REDIS_URL
      - JWT_SECRET
    depends_on: [postgres, redis]

  ai_worker:
    build: ./ai-worker
    depends_on: [redis]
    replicas: 2

  tts_worker:
    build: ./tts-worker
    depends_on: [redis]
    replicas: 1

  admin:
    build: ./frontend-admin
    
  postgres:
    image: postgres:16
    volumes: [postgres_data:/var/lib/postgresql/data]
    
  redis:
    image: redis:7-alpine
    
  celery_worker:
    build: ./backend
    command: celery -A app.celery worker
    depends_on: [redis, postgres]
```

### Folder Structure

```
/project-root
├── /backend              # FastAPI main API
│   ├── /app
│   │   ├── /api          # Route handlers
│   │   ├── /models       # SQLAlchemy models
│   │   ├── /schemas      # Pydantic schemas
│   │   ├── /services     # Business logic
│   │   ├── /workers      # Celery tasks
│   │   └── /utils        # Helpers
│   ├── /alembic          # DB migrations
│   ├── requirements.txt
│   └── Dockerfile
├── /ai-worker            # AI generation microservice
│   ├── /app
│   │   ├── /generators   # Story/chapter generation
│   │   ├── /prompts      # Prompt templates
│   │   └── /queue        # Celery tasks
│   └── Dockerfile
├── /tts-worker           # TTS microservice
│   ├── /app
│   │   ├── /engines      # Piper, Coqui, ElevenLabs
│   │   ├── /processor    # Text preprocessing
│   │   └── /storage      # R2 upload
│   └── Dockerfile
├── /mobile-app           # Flutter Android app
│   ├── /lib
│   │   ├── /features     # Feature-based structure
│   │   ├── /core         # Auth, API, storage
│   │   └── /shared       # Widgets, themes
│   └── pubspec.yaml
├── /frontend-admin       # React admin dashboard
│   ├── /src
│   │   ├── /pages
│   │   ├── /components
│   │   └── /hooks
│   └── package.json
├── /nginx                # Nginx configs
├── /docker-compose.yml
└── /docker-compose.prod.yml
```

## CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/deploy.yml
on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - run: pytest backend/tests/
      - run: flutter test mobile-app/

  deploy:
    needs: test
    steps:
      - name: Build & push Docker images
      - name: SSH to VPS
      - name: docker-compose pull && docker-compose up -d
      - name: Run DB migrations
      - name: Health check
```

## Nginx Configuration

```nginx
server {
    listen 443 ssl;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://api:8000;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 50M;
    }
}

server {
    listen 443 ssl;
    server_name admin.yourdomain.com;

    # IP whitelist for admin
    allow 203.0.113.0/24;
    deny all;

    location / {
        proxy_pass http://admin:3000;
    }
}
```

## Backup Strategy

- PostgreSQL: daily pg_dump to Cloudflare R2 (7-day retention)
- Redis: RDB snapshot every hour
- Object storage: R2 versioning enabled
- Restore tested monthly

## Scaling Path

| Stage | Users | Infrastructure Change |
|---|---|---|
| MVP | 0–5K | Single VPS |
| Growth | 5K–50K | Separate DB server, Redis cluster |
| Scale | 50K–500K | Load balancer, read replicas, GPU worker |
| Large | 500K+ | Kubernetes, CDN optimization, ML recommendations |

---

# 20. MVP Execution Plan

## Revised MVP Scope (Tighter = Faster to Market)

### MVP Must-Have (8–10 weeks)
- ✅ Android app (Flutter)
- ✅ Vertical feed (static ranking, no ML)
- ✅ Story details + chapter reading
- ✅ Coin system (earn via ad + daily login)
- ✅ Chapter unlock (coins only — no subscription yet)
- ✅ Rewarded ads (AdMob)
- ✅ Basic audio (Piper TTS, 1 voice)
- ✅ User auth (email + Google)
- ✅ Admin dashboard (story + chapter management)
- ✅ Push notifications (new chapters only)
- ✅ Pre-seeded content library (30 stories)

### MVP Nice-to-Have (add if time allows)
- Bookmarks
- Comments (view only, no posting)
- Reading progress sync
- Multiple reading themes

### MVP Excluded (v1.1+)
- Subscriptions (add in week 12)
- Premium voices (ElevenLabs)
- Interactive stories
- Creator accounts
- iOS app
- Offline mode
- Complex recommendation engine
- Social features

## Week-by-Week Build Plan

| Week | Focus |
|---|---|
| 1–2 | Backend setup: FastAPI, PostgreSQL, auth, story/chapter APIs |
| 3–4 | Admin dashboard: story/chapter CRUD, AI generation panel |
| 5–6 | Flutter app: auth, home feed, story details, reader mode |
| 7 | Coin system + rewarded ads (AdMob) + chapter unlock |
| 8 | TTS integration (Piper) + audio player |
| 9 | Pre-launch content generation (30 stories via admin) |
| 10 | QA, bug fixes, performance optimization |
| 11 | Play Store submission + beta test (20 users) |
| 12 | Soft launch + monitoring |

## Soft Launch Strategy

Do NOT do a global launch first:
1. Week 12: India + Philippines only (large English-reading story app market)
2. Monitor: retention, crash rate, ad fill rate
3. Week 16: Add Southeast Asia + Middle East
4. Week 20: US + UK (highest CPM, needs high content quality)

## Validation Metrics (Before v1.1)

Before building subscriptions and premium features, validate:
- Day 7 retention ≥ 20%
- Rewarded ad completion rate ≥ 60%
- Average session duration ≥ 8 minutes
- Chapter unlock rate ≥ 25% of story readers
- Crash-free rate ≥ 98%

---

# 21. Post-MVP Roadmap

## v1.1 (Weeks 12–18)
- Subscription system (Google Play Billing)
- Premium voices (ElevenLabs tier)
- Multiple TTS voices (Piper)
- Comments (full posting + moderation)
- Bookmarks
- Offline mode (premium)
- Personalized feed (basic: genre-based)

## v1.2 (Weeks 18–24)
- Reading themes (full set)
- Ambient sounds in audio mode
- Synchronized text highlighting (audio + text)
- Streak system + achievements
- Seasonal themes (admin-pushed)
- A/B testing framework

## v2.0 (Months 6–9)
- Interactive stories (choice-based branching)
- AI character generation
- Collaborative filtering recommendations
- Creator accounts (user-uploaded stories)
- iOS app launch
- Web reader (optional)
- AI-generated story covers (Stable Diffusion integration)

## v3.0 (Months 9–18)
- Voice cloning (custom narrator voices)
- AI-generated story trailers (short video for TikTok marketing)
- Community events (reading challenges)
- Creator monetization (revenue share)
- AI story personalization (user preference learning)
- Real-time story continuation based on user choices

---

# 22. Infrastructure Cost Estimates

## Monthly Costs at MVP Scale (~1,000 DAU)

| Service | Cost/Month | Notes |
|---|---|---|
| VPS (Hetzner CX31) | $12 | 8GB RAM, 4 vCPU |
| Cloudflare R2 | ~$5 | 50GB storage |
| Cloudflare (CDN) | Free | Tier 0 |
| PostgreSQL backup | ~$2 | R2 storage |
| AI API (DeepSeek) | ~$20 | 200 chapters/month |
| ElevenLabs (premium) | ~$22 | 10,000 chars/month starter |
| Firebase | Free | Up to 10K MAU |
| AdMob | Revenue only | No cost |
| Domain + SSL | $1 | Cloudflare |
| **Total** | **~$62/month** | |

## Monthly Costs at Scale (~50,000 DAU)

| Service | Cost/Month | Notes |
|---|---|---|
| VPS cluster (3 nodes) | $150 | API + DB + Workers |
| GPU server (TTS) | $200 | Hetzner GPU instance |
| Cloudflare R2 | $50 | 3TB storage |
| PostgreSQL (read replica) | $50 | |
| AI API costs | $300 | 3,000 chapters/month |
| ElevenLabs Business | $330 | 500K chars/month |
| Firebase | $50 | Push notifications |
| Redis (managed) | $30 | |
| **Total** | **~$1,160/month** | |

At 50K DAU with 2% subscription conversion (~1,000 subscribers × $5.99) = **$5,990/month subscription revenue alone** — well above infrastructure cost.

---

# Final Product Goal

The final experience should feel like:
- **TikTok** — for discovery and addiction loops
- **Spotify** — for audio quality and player UX
- **Netflix** — for cinematic visual quality
- **WebNovel** — for monetization psychology

Every system must be:
- Dynamic and admin-controlled without app updates
- Scalable from 100 to 10 million users with infrastructure changes only
- Emotionally engaging at every touchpoint
- Financially sustainable from month 3 onward

The app wins by making users *feel something* from the stories, then building habits around daily return through streaks, cliffhangers, and personalization.

---

*Blueprint v2.0 — Updated with detailed architecture, sequenced MVP, TTS analysis, cold start strategy, full DB schema, and production-grade specifications.*
