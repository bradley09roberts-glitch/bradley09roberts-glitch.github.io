#!/usr/bin/env bash
# Nightly 03:30 UK: a consistent copy of the database (sqlite3 .backup, checked, gzipped) in
# ~/backups, 14 days kept; then any records not pushed yet. The provider's own backup (the
# whole disk, daily) and the records in git are the off-machine copies (docs/server.md).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start backup
step db-backup db_backup "$FOOTY_BACKUP_DIR/footy-$(uk_date).sqlite3.gz" || true
find "$FOOTY_BACKUP_DIR" -maxdepth 1 -name 'footy-*.sqlite3.gz' -mtime +13 -print -delete || true
find "$FOOTY_STATE_DIR" -maxdepth 1 \( -name 'notified-*' -o -name 'prices-*' \
  -o -name 'doctor-notified-*' \) -mtime +14 -delete || true
push_records || true
job_finish
