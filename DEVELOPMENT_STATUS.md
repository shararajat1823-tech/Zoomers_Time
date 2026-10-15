# Zoomers — Development Status

Honest, living record of what is **done / in-progress / blocked / pending**.
Updated at every milestone. "Done" means implemented, persisted, and verified
by passing tests and a successful build — never a stub or fake success.

Legend: ✅ done & verified · 🟡 in progress · ⛔ blocked · ⬜ pending

_Last updated: 2026-10-15 — session `session_01UmYnzqGPAqDnyGdJByNdUJ`_

**This session's result:** Phase 1 foundation complete + the Phase 2
authentication slice implemented and verified. Backend builds clean (0 warnings)
and **30 automated tests pass** (16 unit + 14 integration against PostgreSQL).
Initial EF migration applied. No vulnerable packages in any project.

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
| Profiles + settings (entities + profile read) | 🟡 (entities + /me done; edit/privacy pending) |
| Social graph: follow / unfollow / requests | ⬜ |
| Posts + media upload sessions | ⬜ |
| Following feed (cursor pagination) | ⬜ |
| Likes / comments / saves | ⬜ |
| Search (users/hashtags/posts) | ⬜ |
| Privacy enforcement (server-side) | ⬜ |
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

1. **Social graph slice**: `Follow`/`FollowRequest` entities + endpoints
   (follow/unfollow, request/accept/reject, followers/following lists with
   cursor pagination), private-account enforcement, duplicate-relationship
   prevention via composite PK. Authorization tests for private access.
2. **Profiles slice**: `PATCH /users/me` (edit profile + privacy), public
   profile read with visibility rules, block-aware visibility.
3. **Posts + media slice**: `Post`/`PostMedia` entities, upload-session flow
   (`POST /media/upload-sessions` + complete), create/get/delete post,
   server-side media verification.
4. **Following feed**: cursor-paginated chronological feed over the social graph.
5. **Engagement**: likes/comments/saves with idempotency + counter consistency.
6. Then Android project skeleton (`core:*` + `feature:authentication`) wired to
   the auth API.

## Known limitations (honest)

- Android app not built/verified in this environment (no SDK/emulator).
- External providers (S3, Redis, FCM, video transcode) are local/in-memory
  stubs behind interfaces; real providers are a later config swap.
- Dev database is ephemeral per container session.
