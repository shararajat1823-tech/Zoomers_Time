# Zoomers — Security Design

Security is a first-class requirement, implemented from Phase 1. This document
describes authentication, authorization, privacy, storage, and abuse
prevention. Established libraries and mechanisms are used throughout; no custom
cryptography is invented.

## 1. Authentication

- **Password hashing**: ASP.NET Core Identity `PasswordHasher<T>` (PBKDF2 with
  per-password salt and high iteration count). Passwords are never stored or
  logged in plaintext. Hash format is upgradable.
- **Sessions / tokens**:
  - Short-lived **JWT access token** (default 15 min) signed with a secret/key
    from configuration (never committed). Carries `sub` (user id), `jti`, and
    minimal claims.
  - **Refresh token** (default 30 days), opaque and random, stored **hashed**
    server-side in `RefreshTokens`. On refresh the token is **rotated**: the old
    one is revoked and chained (`ReplacedByTokenId`) and a new one issued. Reuse
    of a revoked token triggers revocation of the whole chain (theft detection).
  - **Logout** revokes the presented refresh token; **session revocation**
    revokes all of a user's refresh tokens (active-sessions screen).
- **Verification**: email/phone verification tokens are single-use,
  time-limited, and hashed at rest.
- **Password reset**: single-use, short-lived, hashed reset token; the reset
  endpoint and request endpoint are rate limited; responses are generic (do not
  reveal whether an account exists).
- **Provider abstraction**: `IAuthProvider` seam allows adding Google Sign-In
  and other identity providers later without changing core flows. Google
  Sign-In (Android) validates the Google ID token server-side.
- **Mobile credential storage**: Android stores tokens in
  `EncryptedSharedPreferences` backed by the Android Keystore — never in plain
  shared preferences or logs.

## 2. Authorization

- **Every protected endpoint** requires a valid access token; `[Authorize]` by
  default, explicit `[AllowAnonymous]` only on public endpoints.
- **Object-level authorization (anti-IDOR)**: ownership and visibility are
  checked for every resource access using the *authenticated* user id from the
  token — never a client-supplied user id. Editing/deleting content verifies the
  caller owns it (or has an authorized moderation role).
- **Privacy enforcement** (server-side, never client-trusted):
  - Private accounts: non-followers cannot read posts, media, stories, or
    restricted follower/following lists. A *pending* follow request grants no
    access.
  - Blocks: blocked users cannot view content, interact, message, or appear to
    each other in search/feed (mutually invisible interactions).
  - Mutes: muted accounts' posts/stories are hidden from the muter's feeds
    without notifying the muted user.
- **Roles**: ordinary users vs. moderators/admins. Admin/moderation endpoints
  require elevated role claims checked on the server; they are never exposed to
  ordinary mobile clients.

## 3. Media security

- Private media is **never** served from a public, guessable URL. Access goes
  through server-side authorization; downloads use **short-lived signed URLs**
  (or an authorizing proxy) scoped to the requesting user.
- Uploads use **upload sessions** with short-lived, scoped upload URLs; the
  server **verifies** the uploaded object (type, size, dimensions/duration)
  before marking media ready. Media is not trusted purely on client claims.
- No uploaded media is stored permanently on the API host's local disk in
  production (the dev `LocalFileMediaStorage` is a development-only adapter).

## 4. Input handling & common web risks

- **SQL injection**: all data access via EF Core parameterized queries; no
  string-concatenated SQL. Raw SQL (if ever needed) uses parameters.
- **Mass assignment**: API request DTOs are explicit; entities are never bound
  directly from request bodies. Server sets ownership, timestamps, counters, and
  status — clients cannot.
- **Validation**: request validation (FluentValidation/DataAnnotations) on all
  inputs; standardized `ProblemDetails` error responses.
- **Output**: API returns DTOs, not entities; no internal fields leaked.
- **Error safety**: production error responses never contain stack traces,
  connection strings, or internal identifiers. A correlation id is returned for
  support.

## 5. Abuse prevention & rate limiting

- Global and per-endpoint rate limits (ASP.NET Core rate limiter). Stricter
  limits on login, registration, verification, password reset, and messaging.
- Login/verification use generic error messages and rate limiting to resist
  brute force and user enumeration.
- Private-resource endpoints avoid oracles that reveal existence of
  non-authorized data (consistent 404/403 behavior).
- Idempotency keys on retry-sensitive writes prevent duplicate likes/follows/
  posts from network retries.
- Basic suspicious-activity signals (rapid follows, repeated reports) recorded
  for moderation; no intrusive monitoring beyond a clear safety purpose.

## 6. Transport & secrets

- **HTTPS** enforced in deployed environments (HSTS). Android uses TLS with the
  platform trust store (no plaintext traffic, no cleartext permitted in release
  network config).
- **Secrets** (JWT signing key, DB credentials, provider keys) come from
  environment variables / secret manager. They are **never committed**. The repo
  ships `*.sample`/`*.example` config with placeholders only.
- Dependency vulnerability scanning in CI (`dotnet list package --vulnerable`
  and Dependabot-style updates). No known-vulnerable packages shipped.

## 7. Auditing

- Important security and admin events (login from new device, password change,
  session revocation, role changes, moderation actions, account deletion) are
  written to append-only `AuditLogs` with actor, action, target, and metadata.
  Audit entries never contain secrets or message bodies.

## 8. Privacy & data lifecycle

- Account **deactivation** (reversible) and **deletion** (soft delete + grace
  period, then hard purge of personal content and media) are supported
  server-side.
- Data **export** (where supported) returns the user's own data.
- Retention policies remove expired stories, tokens, abandoned uploads, and
  aged logs.
- Push notifications do **not** include sensitive message content unless the
  user's settings and the security model explicitly allow it.

## 9. Cryptography & claims discipline

- Only established libraries/algorithms (PBKDF2 hashing, standard JWT signing,
  TLS). No home-grown crypto.
- The product does **not** claim end-to-end encryption. Transport security
  (TLS) and encryption-at-rest (provider-level) are distinct from E2EE and are
  described as such. E2EE would require a separate, independently reviewed
  design before any such claim is made.

## 10. Threat-model checklist (tested)

Authorization/privacy tests cover, at minimum: unauthorized access to private
posts/media returns 403/404; editing/deleting another user's content is
rejected; a pending follow request grants no access; a blocked user cannot
view/interact/message; expired/revoked tokens are rejected; refresh-token reuse
revokes the chain; rate limits trigger on abusive auth traffic.
