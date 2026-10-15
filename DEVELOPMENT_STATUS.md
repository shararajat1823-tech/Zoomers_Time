# Zoomers — Development Status

Honest, living record of what is **done / in-progress / blocked / pending**.
Updated at every milestone. "Done" means implemented, persisted, and verified
by passing tests and a successful build — never a stub or fake success.

Legend: ✅ done & verified · 🟡 in progress · ⛔ blocked · ⬜ pending

_Last updated: 2026-10-15 — session `session_01UmYnzqGPAqDnyGdJByNdUJ`_

**Latest result:** Phase 1 foundation + Phase 2 **auth**, **social graph**,
**profiles**, **posts/media**, **following feed**, **engagement**, **search &
discovery**, **hashtags/mentions**, and **settings** slices implemented and
verified — Phase 2 MVP backend is essentially complete. Backend builds clean
(0 warnings) and **59 automated tests pass** (16 unit + 43 integration against
PostgreSQL). Four EF migrations applied. No vulnerable packages in any project.

## Environment (this cloud container)

| Tool | State |
|------|-------|
| .NET 8 SDK (8.0.131) | ✅ installed (via apt) |
| NuGet reachable | ✅ |
| PostgreSQL 16 | ✅ running; `zoomers_dev` + `zoomers_test` DBs, user `zoomers` |
| Android SDK / emulator | ⛔ not available — Android code is written & reviewed but compiled/run by the developer in Android Studio |
| Docker daemon | ⛔ not running (native Postgres used instead) |

> Note: the dev Postgres is started per-session and resets on container
> restart. Migrations are the source of truth; `scripts/` re-provisions.

## Phase 1 — Foundation

| Item | Status |
|------|--------|
| PROJECT_PLAN.md | ✅ |
| ARCHITECTURE.md | ✅ |
| DATABASE_DESIGN.md | ✅ |
| SECURITY.md | ✅ |
| DEVELOPMENT_STATUS.md | ✅ (this file) |
| Backend solution skeleton builds | ✅ (5 src + 2 test projects, clean build) |
| Core infra: DbContext (+ citext, soft delete, UTC stamping) | ✅ |
| Core infra: error handling / ProblemDetails | ✅ (middleware + central error mapping) |
| Core infra: Serilog logging | ✅ |
| Core infra: health checks | ✅ (`/health/live`, `/health/ready` w/ DB probe) |
| Core infra: API versioning + Swagger | ✅ (`/api/v1`, bearer-aware Swagger) |
| Core infra: Result/pagination primitives | ✅ (Result/Error, CursorPage) |
| Initial EF migration (Users/Profiles/Settings/Tokens) | ✅ applied |
| CI workflow (build + test w/ Postgres + vuln scan) | ✅ |
| Android project skeleton | ✅ written (see Android section below) |

## Android app (`android/`)

Kotlin + Compose client with the Lumi-inspired, fully-responsive design system,
wired to the API. **Written & reviewed, not compiled here** (no Android SDK in
this container) — build in Android Studio.

| Item | Status |
|------|--------|
| Gradle project + version catalog (AGP 8.5 / Kotlin 2.0 / Compose BOM) | ✅ written |
| Design system (light+dark theme, type, shapes, components) | ✅ written |
| Responsive foundation (WindowSizeClass → bar/rail, capped widths, grid cols) | ✅ written |
| Networking (Retrofit + kotlinx-serialization, auth interceptor, refresh rotation) | ✅ written |
| Token persistence (DataStore) + repositories (auth/feed/profile) | ✅ written |
| Auth screens (login/register) + session gate | ✅ written |
| Home feed (stories, pill tabs, post cards, carousel, infinite scroll, states) | ✅ written |
| Profile (me + stats + grid + logout) | ✅ written |
| Bottom bar + side rail navigation (adaptive) | ✅ written |
| Search / Create / Reels / Messages / Comments screens | ⬜ placeholders |
| Device/emulator verification + UI tests | ⬜ developer-side |

## Phase 2 — MVP social experience

| Item | Status |
|------|--------|
| Auth: register / login / refresh (rotation + reuse detection) / logout | ✅ verified |
| Auth: password reset (request + reset, single-use hashed token) | ✅ verified |
| Auth: rate limiting (strict on auth endpoints) | ✅ verified (429 test) |
| Auth: `GET /users/me` (JWT-protected) | ✅ verified |
| Profiles: read own (/users/me), public (/users/{username}), edit (PATCH) | ✅ verified |
| Social graph: follow/unfollow, requests (accept/reject/cancel), remove follower | ✅ verified |
| Social graph: followers/following lists (keyset cursor pagination) | ✅ verified |
| Blocks: block/unblock, mutual invisibility, edge removal | ✅ verified |
| Privacy enforcement (private accounts, pending-request grants no access) | ✅ verified |
| Go-public auto-approves pending requests | ✅ verified |
| Media upload-session flow (create → upload → verify → complete) | ✅ verified |
| Posts: create (image/video/carousel), get, delete (ownership) | ✅ verified |
| Server-side media verification + authorized media download (IDOR-safe) | ✅ verified |
| Post visibility (Public/Followers/Private) + account-privacy + block-aware | ✅ verified |
| Profile post grid + saved posts (cursor) | ✅ verified |
| Following feed (keyset cursor pagination) | ✅ verified |
| Likes (idempotent, consistent counts) + saves | ✅ verified |
| Comments + replies, author/owner moderation, comment likes | ✅ verified |
| Search: users / hashtags / posts (trigram, privacy/block-aware) | ✅ verified |
| Hashtag + mention extraction on post create; hashtag feed | ✅ verified |
| Discovery: suggested users | ✅ verified |
| Settings: get/update (theme, language, audience + push prefs) | ✅ verified |
| Mute/unmute (feed exclusion); blocked + muted lists | ✅ verified |

## Phase 3 — Media & engagement
Stories, video posts, reels, background media processing, push notifications,
mentions & hashtags, feed performance. — ⬜ pending

## Phase 4 — Messaging & safety
DMs, groups, read status, attachments, blocking/muting, reporting/moderation,
deactivation/deletion. — ⬜ pending

## Phase 5 — Quality & release readiness
Accessibility, offline, performance, security review, full tests, CI/CD, crash
reporting, release builds, deployment docs. — ⬜ pending

## Next concrete steps (next session)

With the Phase 2 MVP backend essentially complete, the natural forks are:

1. **Android app** — multi-module Compose project (`core:*` + `feature:
   authentication`, `feature:home`, `feature:profile`) wired to the live API.
   Note: not device-verifiable in this cloud container (no Android SDK), so it
   is written compile-oriented and verified by the developer locally.
2. **Phase 3 backend depth** — stories & reels, background media processing
   (thumbnail/transcode worker filling the `IMediaProcessor` seam), push
   notifications (FCM behind `IPushSender`), notifications module (new follower,
   like, comment, mention, follow-request) with the mention/like/comment events
   already produced by Phase 2.
3. **Hardening** — mention notifications, comment hashtag/mention extraction,
   OpenAPI examples, load tests on feed/search.

## Known limitations (honest)

- Android app is written but **not compiled/run** in this environment (no
  SDK/emulator) — first-build nits are resolved by the developer in Android
  Studio. Token storage uses DataStore in the starter and must move to
  EncryptedSharedPreferences/Keystore before release.
- External providers (S3, Redis, FCM, video transcode) are local/in-memory
  stubs behind interfaces; real providers are a later config swap.
- Dev database is ephemeral per container session.
