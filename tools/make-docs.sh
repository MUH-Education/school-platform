#!/usr/bin/env bash
# Write docs/03-data-model.md and docs/diagrams/* from a fresh database.
# Needs psql, Python 3 with pyyaml, and (for the pictures) mmdc:  npm i -g @mermaid-js/mermaid-cli
#
#   PGHOST=localhost PGPORT=5432 PGUSER=school PGPASSWORD=school ./tools/make-docs.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DB=school_doc
P="psql -v ON_ERROR_STOP=1 -q"
$P -d postgres -c "drop database if exists $DB" -c "create database $DB"
for f in $(ls src/main/resources/db/migration/V*.sql | sort -V); do $P -d $DB -f "$f"; done
$P -d $DB -f src/main/resources/db/later/marks.sql
$P -d $DB -v dev=1 -f src/main/resources/db/dev/sample-data.sql 2>&1 | grep -v NOTICE
python3 tools/make_data_model.py $DB

if command -v mmdc >/dev/null; then
  for f in docs/diagrams/*.mmd; do
    mmdc -q -i "$f" -o "${f%.mmd}.svg" -b white ${MMDC_PUPPETEER:+-p "$MMDC_PUPPETEER"}
  done
  echo "Pictures in docs/diagrams/*.svg"
fi
$P -d postgres -c "drop database $DB"
