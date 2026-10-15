# Zoomers — Architecture

This document describes the system components and their responsibilities. It
covers the backend modular monolith in depth (built first) and the planned
Android client structure.

## 1. Principles

- **Modular monolith first.** One deployable backend, internally partitioned
  into modules with explicit boundaries. Microservices are introduced only when
  real scaling/team pressure justifies them.
- **Clean layering.** Dependencies point inward: `Api → Application → Domain`,
  with `Infrastructure` implementing `Application`/`Domain` abstractions.
  `Domain` depends on nothing external.
- **Thin controllers.** Controllers validate input, invoke an application
  service, and map the result to an HTTP response. No business logic or data
  access in controllers.
- **Abstractions for external services.** Storage, cache, realtime, push, and
  clock are interfaces in `Application`; `Infrastructure` provides swappable
  implementations (local → cloud) selected by configuration.
- **Server is the source of truth.** Identity, ownership, privacy, and counters
  are enforced on the server. Client-provided IDs/permissions are never trusted.

## 2. Backend project layout

```
backend/
  Zoomers.sln
  src/
    Zoomers.Domain/          # entities, value objects, domain rules, enums
    Zoomers.Application/     # use-cases (services), DTOs, abstractions (ports)
    Zoomers.Infrastructure/  # EF Core DbContext + configs + migrations,
                             #   adapters (storage, cache, tokens, push, clock)
    Zoomers.Api/             # ASP.NET Core host: controllers, middleware,
                             #   DI composition, Program.cs, SignalR hubs
    Zoomers.Shared/          # cross-cutting: Result<T>, Error, paging, guards
  tests/
    Zoomers.UnitTests/       # domain + application unit tests
    Zoomers.IntegrationTests/# API + DB tests via WebApplicationFactory + Postgres
```

Why five source projects rather than one-project-per-module-per-layer (which
would be ~60 projects): the module boundary is expressed by **folders and
namespaces** inside each layer (e.g. `Application/Identity`,
`Application/Posts`). This keeps boundaries clear while the build stays
manageable. If a module later needs independent deployment, its folder is
extracted into its own project/service with minimal churn because its
dependencies are already explicit.

## 3. Module boundaries (business responsibilities)

Each module owns its entities and use-cases. Cross-module reads go through
application services, never direct foreign data access from an unrelated module.

| # | Module | Responsibility | Key entities |
|---|--------|----------------|--------------|
| 1 | Identity | Registration, login, sessions, tokens, password reset | User, RefreshToken, PasswordResetToken |
| 2 | Profiles | Profile data, settings, edit | UserProfile, UserSettings |
| 3 | SocialGraph | Follow / unfollow, follow requests, block-aware edges | Follow, FollowRequest |
| 4 | Posts | Post + media metadata, audience | Post, PostMedia, PostAudience |
| 5 | Feed | Following & recommended feed composition | (reads Posts/SocialGraph) |
| 6 | Stories | Ephemeral stories, views, reactions, expiry | Story, StoryMedia, StoryView, StoryReaction |
| 7 | Reels | Short-form vertical video metadata | (Post subtype / ReelMetadata) |
| 8 | Engagement | Likes, comments, replies, saves | PostLike, Comment, CommentLike, SavedPost |
| 9 | Messaging | Conversations, messages, receipts | Conversation, ConversationMember, Message, MessageAttachment, MessageReadReceipt |
| 10 | Notifications | In-app + push notifications, preferences | Notification, PushToken |
| 11 | Search | Users, hashtags, posts discovery | (reads + Hashtag, PostHashtag) |
| 12 | Hashtags/Mentions | Tag extraction + linking | Hashtag, PostHashtag, Mention |
| 13 | Privacy/Access | Block, mute, visibility rules | UserBlock, UserMute |
| 14 | Moderation | Reports, actions, audit | Report, ModerationAction, AuditLog |
| 15 | Admin/Analytics | Moderation workflow, metrics | (reads + AuditLog) |
| — | Media | Upload sessions, verification, processing hooks | UploadSession |

Dependency rules:
- No circular module dependencies.
- Feed, Search, Admin are **read-oriented** composition modules.
- Privacy/Access is a **cross-cutting policy** consulted by Feed, Search,
  Posts, Stories, Messaging, Engagement.

## 4. Core infrastructure components

- **ZoomersDbContext** (Infrastructure): single EF Core context, entity
  configurations split per module via `IEntityTypeConfiguration<T>`. Migrations
  live in `Infrastructure/Migrations`.
- **Error handling**: a global exception-handling middleware maps domain/app
  errors and unhandled exceptions to RFC 7807 `ProblemDetails`. No stack traces
  or internal data leak to clients in production.
- **Result<T> / Error** (Shared): application services return `Result<T>`
  carrying a typed `Error` (code + message + kind: Validation, NotFound,
  Forbidden, Conflict, Unauthorized, Unexpected). Controllers map error kinds to
  HTTP status codes centrally.
- **Logging**: Serilog, structured, request-scoped correlation id. Secrets,
  passwords, tokens, and message bodies are never logged.
- **Health checks**: `/health/live` and `/health/ready` (readiness includes a
  DB probe).
- **API versioning**: URL segment `/api/v1`.
- **OpenAPI**: Swashbuckle, served at `/swagger` in Development.
- **Rate limiting**: ASP.NET Core rate limiter; stricter policies on auth and
  verification endpoints.
- **Pagination**: cursor-based (`PagedResult<T>` + opaque cursor) for all large
  collections (feed, comments, search, followers).

## 5. Abstractions (ports) and their first implementations

| Port (Application) | First impl (Infrastructure) | Later impl |
|--------------------|------------------------------|-----------|
| `IMediaStorage` | `LocalFileMediaStorage` (dev disk) | S3-compatible |
| `IDistributedCache` (framework) | in-memory | Redis |
| `ITokenService` | JWT + rotating refresh (HMAC/RSA) | (same, keys via KMS) |
| `IPasswordHasher` | ASP.NET Core Identity PBKDF2 hasher | (same) |
| `IPushSender` | `LoggingPushSender` (no-op/log) | FCM |
| `IRealtimeNotifier` | SignalR hub broadcaster | (same, backplane w/ Redis) |
| `IClock` | `SystemClock` (UTC) | (test fakes) |
| `IMediaProcessor` | `QueuedMediaProcessor` (stub thumbnails) | real transcode/thumbnail worker |

All timestamps are **UTC** in storage and transport; the client converts to
local time.

## 6. Request lifecycle (example: like a post)

1. `POST /api/v1/posts/{postId}/likes` with `Idempotency-Key` header.
2. Auth middleware resolves the current user from the JWT.
3. `LikesController` calls `EngagementService.LikePostAsync(userId, postId, idemKey)`.
4. Service checks post visibility (Privacy/Access + post audience), enforces
   block rules, then performs an **idempotent** insert (unique constraint on
   `(PostId, UserId)`); duplicate requests are no-ops returning the current
   state.
5. Like count is maintained transactionally; a notification event is enqueued.
6. Service returns `Result<LikeState>`; controller returns `200 OK` with the
   current like state. On failure, the error kind maps to the right status code.

## 7. Realtime

SignalR hubs (`MessagingHub`, `NotificationsHub`) push messages and
notifications to connected clients. Every realtime payload has an equivalent
REST endpoint so clients can synchronize after reconnecting (missed-message
sync by cursor). Messages are **persisted before** delivery is reported.

## 8. Background work

A hosted background service processes a durable work queue (DB-backed table
initially) for: media thumbnail/transcode jobs, notification fan-out/delivery,
story expiry sweeps, and abandoned-upload cleanup. Expensive work never runs
synchronously in a request path.

## 9. Android client (planned structure)

Multi-module Gradle project mirroring the feature set:

```
app
core:designsystem   # colors, type, spacing, shapes, reusable components, theming
core:common         # Result, dispatchers, utilities
core:model          # domain models shared across features
core:network        # Retrofit/Ktor client, DTOs, auth interceptor, token refresh
core:database       # Room: offline cache, drafts
core:testing        # test fixtures, fakes
feature:authentication  feature:home     feature:search
feature:createpost      feature:stories  feature:reels
feature:profile         feature:notifications  feature:messaging
feature:settings
```

Patterns: MVVM with `ViewModel` + `StateFlow`; Navigation Compose; Hilt DI;
coroutines for async; Paging 3 for feeds/comments/search; Room for offline
cache and drafts; WorkManager for uploads/retries; secure token storage via
`EncryptedSharedPreferences`/Keystore. Presentation/domain/data are separated
within each feature. Platform specifics sit behind interfaces so iOS can reuse
API contracts and business rules.

## 10. What is NOT built now (deliberate extension points)

Microservices, live streaming, voice/video calls, ad system, subscriptions,
advanced ML ranking. These have **stable seams** (feed ranking strategy
interface, media provider interface, auth provider abstraction, relationship
types) but no speculative empty abstractions beyond those seams.
