# Zoomers — Project Plan

> Zoomers is an original, Android-first social media platform (photos, videos,
> carousels, stories, short-form vertical video, messaging). This document is
> the living plan: architecture summary, milestones, dependencies, assumptions,
> and risks. It is kept in sync with `DEVELOPMENT_STATUS.md`, which tracks
> actual implementation state.

## 1. Product summary

A premium, mobile-first social network. First release target is Android
(Kotlin + Jetpack Compose). The backend is a **modular monolith** built with
ASP.NET Core and PostgreSQL, exposing a versioned REST API (`/api/v1`) plus a
real-time channel for messaging/notifications. iOS and web are future clients
that reuse the same API contracts and business rules without a backend rewrite.

Brand identity is original: the name **Zoomers**, its own color system, type
scale, and iconography. No Instagram assets, names, or exact screen layouts are
copied.

## 2. Architecture at a glance

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Android app  │     │ (future) iOS │     │ (future) web │
│ Compose + MVVM│    │              │     │              │
└──────┬───────┘     └──────┬───────┘     └──────┬───────┘
       │  HTTPS REST /api/v1 + WebSocket (SignalR)│
       └───────────────┬──────────────────────────┘
                       ▼
          ┌─────────────────────────────┐
          │   ASP.NET Core API host      │
          │   (thin controllers)         │
          ├─────────────────────────────┤
          │ Application layer            │  use-cases per module
          │ Domain layer                 │  entities + rules
          │ Infrastructure layer         │  EF Core, storage, cache,
          │                              │  tokens, realtime, workers
          └───────────┬─────────┬───────┘
                      ▼         ▼
              ┌──────────┐  ┌──────────┐   ┌───────────────┐
              │PostgreSQL│  │  Redis*  │   │ Object store* │
              └──────────┘  └──────────┘   │ (S3-compatible)│
                                           └───────────────┘
   * Redis / object storage / FCM / video transcode are behind interfaces.
     The first implementations are local/in-memory so the system runs with
     zero external accounts; real providers are swapped in later via config.
```

See `ARCHITECTURE.md` for component responsibilities and module boundaries.

## 3. Technology choices (pinned)

| Area | Choice | Version | Notes |
|------|--------|---------|-------|
| Backend runtime | .NET | 8.0 (LTS) | ASP.NET Core Web API |
| ORM | EF Core (Npgsql) | 8.0.x | code-first migrations |
| Database | PostgreSQL | 16 | normalized relational schema |
| AuthN | JWT access + rotating refresh | — | ASP.NET Core auth + custom refresh store |
| Password hashing | ASP.NET Core Identity `PasswordHasher` (PBKDF2) | — | established, not custom crypto |
| Logging | Serilog | 8.0.x | structured, no secrets logged |
| API docs | Swashbuckle (OpenAPI) | 6.x | `/swagger` in dev |
| Realtime | SignalR | (in .NET) | messaging + notifications, API fallback |
| Caching | `IDistributedCache` | — | in-memory first, Redis later |
| Object storage | `IMediaStorage` abstraction | — | local disk first, S3 later |
| Push | `IPushSender` abstraction | — | no-op/log first, FCM later |
| Tests | xUnit + FluentAssertions + WebApplicationFactory | — | unit + integration (real Postgres) |
| Android | Kotlin / Compose / Material 3 / Hilt / Retrofit / Room / Paging 3 | current stable | future milestone |

## 4. Milestones

Implementation is **vertical slices**, not horizontal stubs: each slice goes
DB → API → tests and (later) → Android, so the system is always runnable.

### Phase 1 — Foundation  *(this session)*
- Architecture + planning docs (this file and the four companions).
- Backend solution skeleton (Domain / Application / Infrastructure / Api /
  Shared + Unit/Integration test projects) that builds clean.
- Core infrastructure: DbContext, ProblemDetails error handling, Serilog,
  health checks, API versioning, Swagger, Result/pagination primitives.
- Initial database migration.
- CI workflow (build + test with a Postgres service).

### Phase 2 — MVP social experience
- **Auth slice (started in Phase 1):** register, login, refresh (rotation),
  logout, password reset, rate limiting.
- User profiles + settings.
- Social graph: follow / unfollow, follow requests for private accounts.
- Posts + media upload-session flow (photos, carousels).
- Chronological following feed with cursor pagination.
- Likes, comments (+ replies), saves.
- Search (users, hashtags, posts) via DB indexes.
- Account privacy enforcement (server-side).
- Basic settings.

### Phase 3 — Media & engagement
Stories, video posts, reels, background media processing, push notifications,
mentions & hashtags, feed performance.

### Phase 4 — Messaging & safety
Direct + group messaging (SignalR), read status, attachments, blocking/muting,
reporting/moderation, account deactivation/deletion.

### Phase 5 — Quality & release readiness
Accessibility, offline handling, performance, security review, full test
suites, CI/CD, crash reporting, release builds, deployment docs.

## 5. Dependencies & ordering

- Core infrastructure blocks every feature module.
- Auth/Identity blocks all authenticated endpoints.
- Social graph + privacy block feed, search visibility, and messaging rules.
- Media upload-session flow blocks posts, stories, reels, message attachments.
- Notifications depend on most engagement events (likes, comments, follows).

## 6. Assumptions

1. **Greenfield repo.** The repository was emptied before this build began
   (previous contents were unrelated SQL stored procedures). Nothing to reuse.
2. **Backend-first in this environment.** The cloud container can compile and
   test .NET and run PostgreSQL, so the backend is built and verified here.
   Android code is written to compile against the API contracts but is
   *verified on a device/emulator by the developer locally*, not in this
   container (no Android SDK/emulator present).
3. **Local/in-memory providers first** for storage, cache, and push, behind
   interfaces, so the system runs without external credentials. Real providers
   (S3, Redis, FCM) are configuration swaps later.
4. **.NET 8 LTS** chosen over .NET 10 for long-term support stability.
5. **PostgreSQL 16** is the development and target database.
6. Secrets are never committed; sample env files contain placeholders only.

## 7. Risks & mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Android not verifiable in this container | Android code could drift from a working build | Keep Android behind the stable API contract; mark it unverified in `DEVELOPMENT_STATUS.md` until device-built |
| Scope is a multi-team, multi-month product | Can't finish all phases in one session | Ship runnable vertical slices; record exact progress + next steps each milestone |
| Counter consistency under concurrency (likes/follows) | Wrong counts, duplicate relationships | DB unique constraints + transactions + idempotent writes |
| Media on app server disk | Scaling/security problems | `IMediaStorage` abstraction; direct-to-storage upload-session flow; no permanent media on the API host |
| Private-content leakage (IDOR) | Serious privacy breach | Server-side object-level authorization + explicit authorization tests |
| Ephemeral dev Postgres | Dev DB resets on container restart | Migrations are the source of truth; `scripts/` re-provisions; CI uses a fresh service container |

## 8. Definition of done (per phase)

A phase is complete only when its screens/endpoints are implemented and perform
real actions, data persists, authN/authZ work, loading/empty/error/retry states
exist (client), relevant tests pass, the project builds, and docs are updated.
No fake success responses; no critical TODOs hidden behind a "complete" label.
