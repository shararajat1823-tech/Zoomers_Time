# Zoomers — Database Design

PostgreSQL 16, EF Core code-first migrations. This document is the data-model
reference: entities, relationships, constraints, indexes, and retention. The
schema is introduced incrementally per phase; this document describes the full
target model and flags which parts exist now vs. later (see
`DEVELOPMENT_STATUS.md` for live state).

## 1. Conventions

- **Primary keys**: `uuid` (v7-style time-ordered where helpful) generated
  server-side. Stable, non-enumerable, safe to expose in URLs.
- **Timestamps**: `timestamptz`, always UTC. `CreatedAt` on every table;
  `UpdatedAt` where rows mutate.
- **Soft delete**: only where retention/recovery requires it (posts, comments,
  messages, accounts) via a nullable `DeletedAt`; filtered by a global query
  filter. Most tables hard-delete.
- **Naming**: tables plural PascalCase (EF default mapping), snake_case is *not*
  used to keep EF conventions clean; explicit indexes named `IX_*`, unique
  constraints `UQ_*`, foreign keys `FK_*`.
- **Enums**: stored as `int` with a documented mapping (not PG enum types, to
  keep migrations simple and additive).
- **No binary media in rows**: media bytes live in object storage; tables store
  metadata + storage keys only.

## 2. Deletion strategy

- User account deletion is a **soft delete + scheduled hard purge** (retention
  window) so that cascading removal of posts/media/messages is controlled and
  auditable, not an accidental destructive cascade.
- Foreign keys use `ON DELETE RESTRICT` by default; explicit cascades are
  declared only where a child cannot exist without its parent and removal is
  always intended (e.g. `PostMedia` → `Post`, `StoryView` → `Story`).
- Relationship/edge tables (`Follows`, `PostLikes`, `SavedPosts`) hard-delete on
  unfollow/unlike/unsave.

## 3. Entities

### Identity & profile

**Users**
| Column | Type | Notes |
|--------|------|-------|
| Id | uuid PK | |
| Username | citext | **UQ_Users_Username** (case-insensitive unique) |
| Email | citext | nullable if phone-based; **UQ_Users_Email** when present |
| PhoneNumber | text | nullable; unique when present |
| PasswordHash | text | PBKDF2 (ASP.NET Identity hasher); never plaintext |
| EmailVerifiedAt / PhoneVerifiedAt | timestamptz? | |
| Status | int | Active, Deactivated, Suspended, PendingDeletion |
| CreatedAt / UpdatedAt / DeletedAt | timestamptz | soft delete |

Indexes: unique on Username, Email, PhoneNumber (partial where not null);
index on Status.

**UserProfiles** (1:1 with Users)
`UserId PK/FK`, `DisplayName`, `Bio`, `Website`, `AvatarMediaKey`,
`CoverMediaKey`, `IsPrivate bool`, `IsVerified bool`,
counts cached (`FollowerCount`, `FollowingCount`, `PostCount` — maintained
transactionally, treated as denormalized caches).

**UserSettings** (1:1): theme, language, notification prefs (jsonb),
comment/mention/message audience controls (int enums), online-status visibility.

**RefreshTokens**: `Id PK`, `UserId FK`, `TokenHash` (hashed, never raw),
`ExpiresAt`, `CreatedAt`, `RevokedAt?`, `ReplacedByTokenId?` (rotation chain),
`CreatedByIp?`, `UserAgent?`. Index on `UserId`, `TokenHash` unique.

**PasswordResetTokens**: `Id`, `UserId FK`, `TokenHash`, `ExpiresAt`,
`ConsumedAt?`. Single-use, short-lived.

### Social graph

**Follows**: `FollowerId FK`, `FolloweeId FK`, `CreatedAt`.
PK = composite `(FollowerId, FolloweeId)` → prevents duplicate relationships at
the DB level. Indexes on both columns for follower/following lookups. Check
constraint `FollowerId <> FolloweeId`.

**FollowRequests** (for private accounts): `Id`, `RequesterId FK`,
`TargetId FK`, `Status` (Pending/Accepted/Rejected), `CreatedAt`, `DecidedAt?`.
UQ on `(RequesterId, TargetId)` where Pending.

### Posts & media

**Posts**: `Id`, `AuthorId FK`, `Caption`, `Type` (Image/Video/Carousel/Reel),
`Visibility` (Public/Followers/Private), `LocationName?`, `AccessibilityText?`,
`LikeCount`, `CommentCount`, `CreatedAt/UpdatedAt/DeletedAt`.
Indexes: `(AuthorId, CreatedAt desc)` for profile grids; `(CreatedAt desc)` for
recency; partial index excluding soft-deleted.

**PostMedia**: `Id`, `PostId FK (cascade)`, `OrderIndex`, `MediaType`,
`StorageKey`, `ThumbnailKey?`, `Width`, `Height`, `DurationMs?`,
`VariantsJson?` (resolutions), `Status` (Pending/Ready/Failed).

**PostAudiences**: optional per-post audience restriction rows for
"close friends"-style lists (future-ready; not required for Public/Followers).

### Engagement

**PostLikes**: PK `(PostId, UserId)` (idempotent likes, no duplicates), `CreatedAt`.
**Comments**: `Id`, `PostId FK`, `AuthorId FK`, `ParentCommentId? FK` (replies),
`Body`, `LikeCount`, `CreatedAt/UpdatedAt/DeletedAt`. Index `(PostId, CreatedAt)`.
**CommentLikes**: PK `(CommentId, UserId)`.
**SavedPosts**: PK `(UserId, PostId)`, `CreatedAt`.

### Hashtags & mentions

**Hashtags**: `Id`, `Tag citext` **UQ**, `UsageCount`.
**PostHashtags**: PK `(PostId, HashtagId)`.
**Mentions**: `Id`, `SourceType` (Post/Comment/Story), `SourceId`,
`MentionedUserId FK`, `CreatedAt`. Index on `MentionedUserId`.

### Stories

**Stories**: `Id`, `AuthorId FK`, `Visibility`, `CreatedAt`, `ExpiresAt`
(default +24h), `DeletedAt?`. Index `(AuthorId, ExpiresAt)`, `(ExpiresAt)` for
the expiry sweep. Expiry is enforced **server-side** by query filter + sweep,
not a client timer.
**StoryMedia**: `Id`, `StoryId FK (cascade)`, `MediaType`, `StorageKey`,
`ThumbnailKey?`, `OverlaysJson?` (text/stickers), `DurationMs?`.
**StoryViews**: PK `(StoryId, ViewerId)`, `ViewedAt`.
**StoryReactions**: `Id`, `StoryId FK`, `UserId FK`, `Emoji`/`Body`, `CreatedAt`.

### Messaging

**Conversations**: `Id`, `Type` (Direct/Group), `Title?` (group), `CreatedById`,
`CreatedAt`, `LastMessageAt?`.
**ConversationMembers**: PK `(ConversationId, UserId)`, `Role`
(Member/Admin), `JoinedAt`, `LastReadMessageId?`, `MutedUntil?`, `LeftAt?`.
**Messages**: `Id`, `ConversationId FK`, `SenderId FK`, `Body?`,
`ReplyToMessageId? FK`, `CreatedAt`, `DeletedAt?`. Index
`(ConversationId, CreatedAt desc)` for history paging.
**MessageAttachments**: `Id`, `MessageId FK (cascade)`, `MediaType`,
`StorageKey`, `ThumbnailKey?`.
**MessageReadReceipts**: PK `(MessageId, UserId)`, `ReadAt`.

### Notifications

**Notifications**: `Id`, `RecipientId FK`, `Type` (int enum), `ActorId? FK`,
`EntityType?`, `EntityId?`, `DataJson?`, `IsRead bool`, `CreatedAt`,
`GroupKey?` (for grouping). Index `(RecipientId, CreatedAt desc)`,
`(RecipientId, IsRead)`. Dedup via `(RecipientId, Type, EntityId, ActorId)`
natural key where applicable.
**PushTokens**: `Id`, `UserId FK`, `Token`, `Platform` (Android/iOS/Web),
`CreatedAt`, `LastSeenAt`, `RevokedAt?`. UQ on `Token`.

### Privacy, safety, moderation

**UserBlocks**: PK `(BlockerId, BlockedId)`, `CreatedAt`. Consulted everywhere
for visibility/interaction.
**UserMutes**: PK `(MuterId, MutedId)`, `MutePosts bool`, `MuteStories bool`.
**Reports**: `Id`, `ReporterId FK`, `TargetType` (User/Post/Story/Comment/Message),
`TargetId`, `Reason` (int enum), `Details?`, `Status`
(Open/UnderReview/Actioned/Dismissed), `CreatedAt`, `ReviewedById?`, `ReviewedAt?`.
**ModerationActions**: `Id`, `ModeratorId FK`, `TargetType`, `TargetId`,
`ActionType` (RemoveContent/Suspend/Restrict/Warn/Dismiss), `Notes?`, `CreatedAt`.
**AuditLogs**: `Id`, `ActorId?`, `Action`, `EntityType?`, `EntityId?`,
`MetadataJson?`, `Ip?`, `CreatedAt`. Append-only; important security/admin
events.

### Media pipeline

**UploadSessions**: `Id`, `UserId FK`, `Purpose` (PostMedia/StoryMedia/Avatar/
MessageAttachment), `StorageKey`, `ContentType`, `MaxBytes`, `Status`
(Pending/Uploaded/Verified/Completed/Abandoned), `CreatedAt`, `ExpiresAt`,
`CompletedAt?`. Short-lived; abandoned sessions cleaned up by a sweep.

## 4. Indexing summary (query-pattern driven)

| Query | Index |
|-------|-------|
| Login by username/email | UQ on Username / Email |
| Profile post grid | `Posts(AuthorId, CreatedAt desc)` |
| Following feed | `Follows(FollowerId)` + `Posts(AuthorId, CreatedAt desc)` |
| Comments page | `Comments(PostId, CreatedAt)` |
| Story feed / expiry | `Stories(AuthorId, ExpiresAt)`, `Stories(ExpiresAt)` |
| Conversation history | `Messages(ConversationId, CreatedAt desc)` |
| Notifications list | `Notifications(RecipientId, CreatedAt desc)` |
| Hashtag / username search | `citext` unique + trigram (`pg_trgm`) index for prefix/fuzzy |

Full-text/trigram search uses PostgreSQL `pg_trgm` + GIN indexes on usernames,
display names, captions, and tags. A dedicated search engine is introduced only
if/when DB search stops scaling.

## 5. Consistency & concurrency

- Duplicate relationships/reactions impossible by composite PK / unique
  constraints.
- Counters (`LikeCount`, `FollowerCount`, …) updated inside the same
  transaction as the edge change; reconcilable by periodic recount job.
- Idempotency: like/save/follow writes are safe to retry (insert-or-ignore
  semantics); write endpoints sensitive to retries accept an `Idempotency-Key`.

## 6. Migrations & retention

- All schema changes via EF Core migrations; `scripts/db-migrate.sh` applies
  them; CI runs migrations against a fresh Postgres service.
- Retention sweeps (background worker): expired stories, expired/revoked
  refresh & reset tokens, abandoned upload sessions, old audit logs beyond the
  policy window, and hard-purge of accounts past the deletion grace period.

## 7. High-volume considerations (future)

Posts, Messages, Notifications, StoryViews are the high-growth tables.
Strategies documented for later: time-based partitioning
(`Messages`/`Notifications` by month), read replicas for feed/search,
materialized follower-count maintenance, and moving the work queue to a
dedicated broker. Not implemented now — noted so the schema choices (uuid PKs,
UTC timestamps, append-only logs) stay compatible with them.
