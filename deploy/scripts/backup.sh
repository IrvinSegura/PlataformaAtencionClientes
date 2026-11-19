#!/usr/bin/env bash
# Nightly backup (supportchat-backup.timer): a pg_dump in custom format (compressed, restorable table by table),
# optionally encrypted with age, uploaded to OCI Object Storage. The VM signs in as itself (instance principal), so
# no cloud key lives on it; the bucket's lifecycle rule deletes old dumps (see the README).
set -euo pipefail

source "$(dirname "$0")/env.sh"

namespace="$(env_value OCI_NAMESPACE)"
bucket="$(env_value OCI_BACKUP_BUCKET)"
owner="$(env_value DB_OWNER_USER)"
recipient="$(env_value BACKUP_AGE_RECIPIENT)"
ping_url="$(env_value BACKUP_PING_URL)"
local_dir=/var/backups/supportchat
: "${namespace:?OCI_NAMESPACE is missing in .env}" "${bucket:?OCI_BACKUP_BUCKET is missing in .env}"

mkdir -p "$local_dir"
umask 077
file="$local_dir/supportchat-$(date -u +%Y%m%dT%H%M%SZ).dump"

# The owner is the database's superuser: it reads every workspace's rows, row-level security does not apply to it
docker compose --project-directory "$APP_DIR" exec -T db \
  pg_dump --username "$owner" --dbname supportchat --format custom --compress 6 > "$file"

# A dump that pg_restore cannot list is not a backup
docker compose --project-directory "$APP_DIR" exec -T db pg_restore --list < "$file" > /dev/null

# Once a week (RESTORE_TEST_WEEKDAY, 1 = Monday ... 7 = Sunday, the default; any other value turns it off) the dump is
# restored into a throwaway database before it is encrypted, so no key is needed here. A failed test still uploads
# the dump, then fails the service and skips the ping, so the dead man's switch alerts
restore_weekday="$(env_value RESTORE_TEST_WEEKDAY)"
restore_test=skipped
if [[ "$(date -u +%u)" == "${restore_weekday:-7}" ]]; then
  if "$(dirname "$0")/restore-test.sh" "$file"; then restore_test=passed; else restore_test=failed; fi
fi

if [[ -n "$recipient" ]]; then
  age --recipient "$recipient" --output "$file.age" "$file"
  rm -f "$file"
  file="$file.age"
fi

oci os object put --auth instance_principal \
  --namespace "$namespace" --bucket-name "$bucket" \
  --file "$file" --name "postgres/$(basename "$file")" --no-overwrite

# The last week also stays on the VM, for a quick restore
find "$local_dir" -name 'supportchat-*' -type f -mtime +7 -delete
echo "Backup uploaded: postgres/$(basename "$file") ($(du -h "$file" | cut -f1)), restore test $restore_test"
if [[ "$restore_test" == failed ]]; then
  echo "The restore test failed: see the lines above" >&2
  exit 1
fi

# A dead man's switch (e.g. healthchecks.io) alerts when this ping stops arriving
if [[ -n "$ping_url" ]]; then
  curl --silent --show-error --max-time 10 --retry 3 "$ping_url" > /dev/null || true
fi
