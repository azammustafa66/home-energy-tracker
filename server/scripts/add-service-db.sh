#!/usr/bin/env bash
#
# Idempotently create a per-service Postgres role + database in the running
# `postgresql` container. Mirrors what docker/postgres/init/10-create-service-dbs.sh
# does on a fresh volume, but safe to run any time.
#
# Usage:
#   ./scripts/add-service-db.sh <service-name>
#
# Example:
#   ./scripts/add-service-db.sh billing
#     -> creates role `billing_service` and database `billing_service`
#
# Reads POSTGRES_USER, POSTGRES_DB, and SERVICE_DB_PASSWORD from .env.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <service-name>" >&2
  echo "Example: $0 billing" >&2
  exit 1
fi

SVC="$1"
DB="${SVC}_service"
ROLE="${SVC}_service"
CONTAINER="postgresql"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[error] .env not found at $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a

: "${POSTGRES_USER:?POSTGRES_USER not set in .env}"
: "${POSTGRES_DB:?POSTGRES_DB not set in .env}"
: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD not set in .env}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "[error] container '$CONTAINER' is not running. Start it with: docker compose up -d" >&2
  exit 1
fi

echo "[info] ensuring role and database '$DB' exist..."

# Role: CREATE ROLE has no IF NOT EXISTS, so guard with a DO block.
docker exec -i "$CONTAINER" \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${ROLE}') THEN
    CREATE ROLE ${ROLE} WITH LOGIN PASSWORD '${SERVICE_DB_PASSWORD}';
    RAISE NOTICE 'created role ${ROLE}';
  ELSE
    RAISE NOTICE 'role ${ROLE} already exists, skipping';
  END IF;
END
\$\$;
SQL

# Database: CREATE DATABASE cannot run inside a DO block (no transactions),
# so check separately and create only if missing.
DB_EXISTS=$(docker exec -i "$CONTAINER" \
  psql -tAU "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT 1 FROM pg_database WHERE datname = '${DB}';")

if [[ "$DB_EXISTS" == "1" ]]; then
  echo "[info] database ${DB} already exists, skipping CREATE"
else
  docker exec -i "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "CREATE DATABASE ${DB} OWNER ${ROLE};"
  echo "[info] created database ${DB}"
fi

docker exec -i "$CONTAINER" \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "GRANT ALL PRIVILEGES ON DATABASE ${DB} TO ${ROLE};" >/dev/null

echo "[done] '${DB}' ready. Don't forget to add '${SVC}' to SERVICE_DBS in .env"
echo "       so a future fresh \`docker compose up\` recreates it automatically."
