# Zoomers — Development Status

Honest, living record of what is **done / in-progress / blocked / pending**.
Updated at every milestone. "Done" means implemented, persisted, and verified
by passing tests and a successful build — never a stub or fake success.

Legend: ✅ done & verified · 🟡 in progress · ⛔ blocked · ⬜ pending

_Last updated: 2026-10-15 — session `session_01UmYnzqGPAqDnyGdJByNdUJ`_

**Latest result:** Phase 1 foundation + Phase 2 **auth**, **social graph**,
**profiles**, **posts/media**, **following feed**, and **engagement** slices
implemented and verified. Backend builds clean (0 warnings) and **52 automated
tests pass** (16 unit + 36 integration against PostgreSQL). Three EF migrations
applied. No vulnerable packages in any project.

## Environment (this cloud container)

| Tool | State |
|------|-------|
| .NET 8 SDK (8.0.131) | ✅ installed (via apt) |
| NuGet reachable | ✅ |
| PostgreSQL 16 | ✅ running; `zoomers_dev` + `zoomers_test` DBs, user `zoomers` |
| Android SDK / emulator | ⛔ not available in this container — Android code is written but device-verified by the developer locally |
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
| Android project skeleton | ⬜ (deferred; backend-first this session) |

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
| Search (users/hashtags/posts) | ⬜ |
| Basic settings | ⬜ |

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

1. **Search & discovery**: username/display-name/hashtag/caption search via
   `pg_trgm` GIN indexes; hashtag extraction + linking on post create;
   explore/suggested users. Respect blocks + privacy.
2. **Settings**: expose/update `UserSettings` (theme, language, audience
   controls, notification prefs); blocked/muted account lists.
3. **Hashtags & mentions**: parse captions/comments, link entities, mention
   notifications.
4. Then Android project skeleton (`core:*` + `feature:authentication`,
   `feature:home`) wired to the API.

## Phase 3 preview (media & engagement depth)
Stories, reels, background media processing (thumbnails/transcode worker),
push notifications, improved feed ranking seam.

## Known limitations (honest)

- Android app not built/verified in this environment (no SDK/emulator).
- External providers (S3, Redis, FCM, video transcode) are local/in-memory
  stubs behind interfaces; real providers are a later config swap.
- Dev database is ephemeral per container session.
