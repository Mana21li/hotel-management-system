#!/bin/bash
# Creates hotel_catalog + hotel_booking and applies service-specific init SQL.
# Runs once on an empty Postgres volume (docker-entrypoint-initdb.d).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE hotel_catalog' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'hotel_catalog')\gexec
    SELECT 'CREATE DATABASE hotel_booking' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'hotel_booking')\gexec
EOSQL

for f in /docker-entrypoint-initdb.d/hotel-catalog/*.sql; do
  echo "Applying $(basename "$f") → hotel_catalog"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname hotel_catalog -f "$f"
done

for f in /docker-entrypoint-initdb.d/booking/*.sql; do
  echo "Applying $(basename "$f") → hotel_booking"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname hotel_booking -f "$f"
done
