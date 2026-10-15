#!/usr/bin/env bash
# Provisions a local PostgreSQL dev + test database for Zoomers.
# Requires a running PostgreSQL 16 server and a superuser (default: postgres).
# Safe to re-run.
set -euo pipefail

PGSUPER="${PGSUPER:-postgres}"
DB_USER="${DB_USER:-zoomers}"
DB_PASSWORD="${DB_PASSWORD:-devpassword}"

echo "Creating role '${DB_USER}' and databases (if missing)..."

psql -v ON_ERROR_STOP=1 -U "${PGSUPER}" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}' CREATEDB;
  END IF;
END
\$\$;
SQL

for db in zoomers_dev zoomers_test; do
  if ! psql -U "${PGSUPER}" -tAc "SELECT 1 FROM pg_database WHERE datname = '${db}'" | grep -q 1; then
    createdb -U "${PGSUPER}" -O "${DB_USER}" "${db}"
    echo "  created ${db}"
  else
    echo "  ${db} already exists"
  fi
done

echo "Done. Apply migrations with: backend/scripts/db-migrate.sh"
