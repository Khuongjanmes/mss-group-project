#!/bin/sh
# Chạy một lần khi volume PostgreSQL còn trống.
set -e
for db in identity_db event_db ticket_db order_db payment_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "CREATE DATABASE $db;"
done
