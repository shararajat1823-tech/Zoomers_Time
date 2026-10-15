#!/usr/bin/env bash
# Applies EF Core migrations to the configured database.
# Reads ConnectionStrings__Default from the environment (falls back to the
# Development appsettings when ASPNETCORE_ENVIRONMENT=Development).
set -euo pipefail

cd "$(dirname "$0")/.."

export ASPNETCORE_ENVIRONMENT="${ASPNETCORE_ENVIRONMENT:-Development}"

dotnet tool restore
dotnet dotnet-ef database update \
  --project src/Zoomers.Infrastructure \
  --startup-project src/Zoomers.Api
