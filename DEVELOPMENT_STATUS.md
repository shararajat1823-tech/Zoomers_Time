# Zoomers — Development Status

Honest, living record of what is **done / in-progress / blocked / pending**.
Updated at every milestone. "Done" means implemented, persisted, and verified
by passing tests and a successful build — never a stub or fake success.

Legend: ✅ done & verified · 🟡 in progress · ⛔ blocked · ⬜ pending

_Last updated: 2026-10-15 — session `session_01UmYnzqGPAqDnyGdJByNdUJ`_

**Latest result:** Phase 1 foundation + Phase 2 **auth**, **social graph**, and
**profiles** slices implemented and verified. Backend builds clean (0 warnings)
and **42 automated tests pass** (16 unit + 26 integration against PostgreSQL).
Two EF migrations applied. No vulnerable packages in any project.

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
| Posts + media upload sessions | ⬜ |
| Following feed (cursor pagination) | ⬜ |
| Likes / comments / saves | ⬜ |
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

1. **Posts + media slice**: `Post`/`PostMedia`/`UploadSession` entities, the
   upload-session flow (`POST /media/upload-sessions` + complete) with
   server-side media verification, create/get/delete post, profile post grid.
   Block-aware + private-visibility enforcement reusing `CanViewContentAsync`.
2. **Following feed**: cursor-paginated chronological feed over the social graph.
3. **Engagement**: likes/comments/saves with idempotency + counter consistency.
4. **Search**: users/hashtags via pg_trgm indexes.
5. Then Android project skeleton (`core:*` + `feature:authentication`) wired to
   the auth API.

## Known limitations (honest)

- Android app not built/verified in this environment (no SDK/emulator).
- External providers (S3, Redis, FCM, video transcode) are local/in-memory
  stubs behind interfaces; real providers are a later config swap.
- Dev database is ephemeral per container session.
