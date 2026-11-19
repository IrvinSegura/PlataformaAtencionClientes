#!/usr/bin/env bash
# Restores a dump made by backup.sh, replacing the current database. Stops the backend while it runs.
#   scripts/restore.sh /var/backups/supportchat/supportchat-20261110T030000Z.dump
#   scripts/restore.sh postgres/supportchat-20261110T030000Z.dump.age   (downloads it from the bucket first)
# An .age file needs the private key: AGE_IDENTITY=/path/to/key.txt scripts/restore.sh ...
set -euo pipefail

source "$(dirname "$0")/env.sh"

source_file="${1:?usage: restore.sh <local dump | bucket object name>}"
owner="$(env_value DB_OWNER_USER)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ ! -f "$source_file" ]]; then
  oci os object get --auth instance_principal \
    --namespace "$(env_value OCI_NAMESPACE)" --bucket-name "$(env_value OCI_BACKUP_BUCKET)" \
    --name "$source_file" --file "$work/$(basename "$source_file")"
  source_file="$work/$(basename "$source_file")"
fi
if [[ "$source_file" == *.age ]]; then
  age --decrypt --identity "${AGE_IDENTITY:?set AGE_IDENTITY to the age private key file}" \
    --output "$work/restore.dump" "$source_file"
  source_file="$work/restore.dump"
fi

read -r -p "This replaces the database with $(basename "$source_file"). Type RESTORE to go on: " answer
[[ "$answer" == "RESTORE" ]] || { echo "Cancelled"; exit 1; }

cd "$APP_DIR"
docker compose stop backend
# --clean drops each object before recreating it; the application role and its grants come back with the dump
docker compose exec -T db pg_restore --username "$owner" --dbname supportchat --clean --if-exists \
  --single-transaction --exit-on-error < "$source_file"
docker compose start backend
echo "Restored. The backend is starting again: docker compose logs -f backend"
