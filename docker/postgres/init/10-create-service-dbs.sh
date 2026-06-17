#!/bin/bash
set -e

# Reads SERVICE_DBS (comma-separated, e.g. "user,device,billing") and for each
# entry creates a role <name>_service and a database <name>_service owned by it.
# Password for every service role comes from SERVICE_DB_PASSWORD.
# Runs only on first container start (when the data volume is empty).

if [ -z "$SERVICE_DBS" ]; then
  echo "[init] SERVICE_DBS not set; nothing to create."
  exit 0
fi

if [ -z "$SERVICE_DB_PASSWORD" ]; then
  echo "[init] SERVICE_DB_PASSWORD not set; refusing to create roles."
  exit 1
fi

for svc in $(echo "$SERVICE_DBS" | tr ',' ' '); do
  db="${svc}_service"
  role="${svc}_service"
  echo "[init] creating role and database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE $role WITH LOGIN PASSWORD '$SERVICE_DB_PASSWORD';
    CREATE DATABASE $db OWNER $role;
    GRANT ALL PRIVILEGES ON DATABASE $db TO $role;
EOSQL
done
