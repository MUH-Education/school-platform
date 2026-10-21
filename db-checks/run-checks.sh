#!/usr/bin/env bash
# Make a fresh database, run every migration, load the sample data, run the checks.
# Needs psql, and a PostgreSQL you can log in to (for example the one from compose.yaml).
#
#   PGHOST=localhost PGPORT=5432 PGUSER=school PGPASSWORD=school ./db-checks/run-checks.sh
#
# It uses its own database "school_check", so your dev data is not touched.
set -euo pipefail
cd "$(dirname "$0")/.."

DB=school_check
MIG=src/main/resources/db/migration
P="psql -v ON_ERROR_STOP=1 -q"

$P -d postgres -c "drop database if exists $DB" -c "create database $DB"
for f in $(ls $MIG/V*.sql | sort -V); do
  echo "migration  $(basename "$f")"
  $P -d $DB -f "$f"
done
echo "later      marks.sql (checked, then removed again)"
$P -d $DB -c "begin;" -f src/main/resources/db/later/marks.sql -c "rollback;"

$P -d $DB -v dev=1 -f src/main/resources/db/dev/sample-data.sql 2>&1 | grep -v NOTICE
$P -d $DB -f db-checks/checks.sql 2>&1 | sed 's/^psql:[^ ]* //; s/^NOTICE:  //'
