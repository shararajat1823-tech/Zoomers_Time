# Zoomers

An original, Android-first social media platform (photos, videos, carousels,
stories, short-form vertical video, messaging) with a modular ASP.NET Core
backend and PostgreSQL. Built to evolve into a large-scale social network and
to support future iOS and web clients on the same API.

> Original brand. Not affiliated with, and does not copy the assets, name, or
> screen designs of, any existing product.

## Documentation

| Doc | What it covers |
|-----|----------------|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | Plan, milestones, assumptions, risks |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, module boundaries, request lifecycle |
| [DATABASE_DESIGN.md](DATABASE_DESIGN.md) | Full data model, constraints, indexes, retention |
| [SECURITY.md](SECURITY.md) | AuthN/Z, privacy, media security, abuse prevention |
| [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) | Honest, live status per phase |

## Repository layout

```
backend/   ASP.NET Core 8 modular monolith + PostgreSQL (see backend/README.md)
android/   (planned) Kotlin + Jetpack Compose client — not yet added
.github/   CI workflows
```

## Status

**Phase 1 (Foundation) complete** and the **authentication slice** of Phase 2 is
implemented and verified: backend solution builds clean, initial migration
applied, and 30 automated tests (unit + integration against PostgreSQL) pass.
See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) for exactly what is done,
in progress, and pending — including the honest note that the Android client is
not yet built.

## Quick start (backend)

```bash
PGSUPER=postgres backend/scripts/dev-db-setup.sh   # create dev + test DBs
backend/scripts/db-migrate.sh                      # apply migrations
cd backend && dotnet run --project src/Zoomers.Api # run the API
```

See [backend/README.md](backend/README.md) for configuration, tests, and
migration details.
