# Zoomers Backend

ASP.NET Core (.NET 8) modular-monolith API for the Zoomers social platform.
PostgreSQL + EF Core. See the repo-root docs (`ARCHITECTURE.md`,
`DATABASE_DESIGN.md`, `SECURITY.md`, `PROJECT_PLAN.md`) for the full design.

## Prerequisites

- .NET 8 SDK
- PostgreSQL 16 (local or container)

## Project layout

```
src/
  Zoomers.Domain          entities + domain rules
  Zoomers.Application     use-cases, DTOs, abstractions (ports)
  Zoomers.Infrastructure  EF Core, migrations, provider adapters
  Zoomers.Api             ASP.NET Core host (thin controllers, DI, middleware)
  Zoomers.Shared          Result/Error, cursor pagination
tests/
  Zoomers.UnitTests         domain + security unit tests
  Zoomers.IntegrationTests  API + DB tests via WebApplicationFactory + Postgres
```

## Configuration (no secrets in source control)

Secrets come from environment variables (or a secret manager). Copy
`backend/.env.sample` and fill in real values. Required:

- `ConnectionStrings__Default` — PostgreSQL connection string
- `Jwt__SigningKey` — ≥32-byte secret for signing access tokens

`appsettings.Development.json` contains **local-only, non-production** values
(a localhost DB string and a clearly-labeled dev signing key) so the app runs
out of the box in development. Production MUST override these via environment.

## First-time setup

```bash
# 1. Create the dev + test databases (needs a Postgres superuser)
PGSUPER=postgres backend/scripts/dev-db-setup.sh

# 2. Apply migrations
backend/scripts/db-migrate.sh
```

## Run

```bash
cd backend
dotnet run --project src/Zoomers.Api
# Swagger UI (Development): http://localhost:5xxx/swagger
# Health: /health/live and /health/ready
```

## Test

Integration tests need a reachable PostgreSQL `zoomers_test` database. Defaults
target `localhost:5432` with user `zoomers`; override with
`ConnectionStrings__Default` and `Jwt__SigningKey`.

```bash
cd backend
dotnet test                 # all tests
dotnet test tests/Zoomers.UnitTests          # unit only (no DB needed)
dotnet test tests/Zoomers.IntegrationTests   # integration (needs Postgres)
```

## Migrations

```bash
cd backend
dotnet tool restore
dotnet dotnet-ef migrations add <Name> \
  --project src/Zoomers.Infrastructure --startup-project src/Zoomers.Api \
  --output-dir Persistence/Migrations
```

## Implemented so far

Phase 1 foundation + the authentication slice: register, login,
refresh-with-rotation (+ reuse detection), logout, forgot/reset password,
`GET /api/v1/users/me`. See `../DEVELOPMENT_STATUS.md` for the live status.
