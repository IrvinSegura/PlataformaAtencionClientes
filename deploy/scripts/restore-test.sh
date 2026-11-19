#!/usr/bin/env bash
# Proves a backup restores: loads a dump made by backup.sh into a throwaway PostgreSQL container, never the live
# database, and checks that the migrations, the data and row-level security came back. Runs on the VM or on any
# machine with Docker; backup.sh runs it once a week on the night's dump.
#   scripts/restore-test.sh                                                   (the newest dump on the VM)
#   scripts/restore-test.sh /var/backups/supportchat/supportchat-20261110T033000Z.dump
#   scripts/restore-test.sh postgres/supportchat-20261110T033000Z.dump.age    (downloads it from the bucket first)
# An .age file needs the private key: AGE_IDENTITY=/path/to/key.txt. ALLOW_EMPTY=1 accepts a dump with no workspace.
set -euo pipefail

source "$(dirname "$0")/env.sh"

# The dump names the server's roles. Off the VM there is no .env: set DB_OWNER_USER and DB_APP_USER when the server's
# differ from deploy/.env.example's
setting() {
  if [[ -f "$APP_DIR/.env" ]]; then env_value "$1"; fi
}
owner="${DB_OWNER_USER:-$(setting DB_OWNER_USER)}"
owner="${owner:-supportchat_owner}"
app_role="${DB_APP_USER:-$(setting DB_APP_USER)}"
app_role="${app_role:-supportchat_app}"
local_dir=/var/backups/supportchat
container="supportchat-restore-test-$$"
work="$(mktemp -d)"
cleanup() {
  docker rm --force "$container" > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

source_file="${1:-}"
if [[ -z "$source_file" ]]; then
  source_file="$(ls -1t "$local_dir"/supportchat-* 2> /dev/null | head -n 1 || true)"
  [[ -n "$source_file" ]] || { echo "No dump in $local_dir: pass one" >&2; exit 1; }
fi
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
echo "Restore test of $(basename "$source_file")"

# The production image, with no port published; the owner is the container's superuser, as in production
docker run --detach --name "$container" --memory 2g --shm-size 256m \
  --env POSTGRES_DB=supportchat --env POSTGRES_USER="$owner" \
  --env POSTGRES_PASSWORD="restore-test-$RANDOM$RANDOM" \
  pgvector/pgvector:pg17 > /dev/null

# Over TCP, which the image's first-start server does not listen on: ready means the real server is up
for _ in $(seq 60); do
  if docker exec "$container" pg_isready --quiet --host 127.0.0.1 --username "$owner" --dbname supportchat; then
    break
  fi
  sleep 1
done

sql() {
  docker exec -i "$container" psql --quiet --no-align --tuples-only --set ON_ERROR_STOP=1 \
    --username "$owner" --dbname supportchat "$@"
}
fail() {
  echo "FAILED: $*" >&2
  exit 1
}

# A dump holds no roles: the application role exists before the restore, as on the server
sql --command "CREATE ROLE \"$app_role\" NOLOGIN"

started=$SECONDS
docker exec -i "$container" pg_restore --username "$owner" --dbname supportchat \
  --single-transaction --exit-on-error < "$source_file" || fail "pg_restore stopped on an error"
echo "Restored in $((SECONDS - started)) s"

read -r failed latest < <(sql --field-separator ' ' --command "
  SELECT count(*) FILTER (WHERE NOT success),
         (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)
  FROM flyway_schema_history")
[[ -n "${latest:-}" ]] || fail "no migration recorded in flyway_schema_history"
[[ "$failed" == 0 ]] || fail "$failed failed migrations in flyway_schema_history"
echo "Schema at migration $latest"

for table in workspaces users conversations conversation_messages documents document_chunks; do
  printf '  %-22s %s rows\n' "$table" "$(sql --command "SELECT count(*) FROM $table")"
done
workspaces="$(sql --command "SELECT count(*) FROM workspaces")"
if [[ "$workspaces" == 0 && "${ALLOW_EMPTY:-}" != 1 ]]; then
  fail "the dump has no workspace (ALLOW_EMPTY=1 accepts it)"
fi

# Isolation must survive the restore: the application role sees nothing until a workspace is bound, then only that
# workspace's rows
policies="$(sql --command "SELECT count(*) FROM pg_class WHERE relrowsecurity AND relforcerowsecurity")"
[[ "$policies" -gt 0 ]] || fail "no table enforces row-level security"
busiest="$(sql --command "
  SELECT workspace_id || ' ' || count(*) FROM conversations GROUP BY workspace_id ORDER BY count(*) DESC LIMIT 1")"
if [[ -n "$busiest" ]]; then
  read -r workspace_id expected <<< "$busiest"
  unbound="$(sql --command "SET ROLE \"$app_role\"; SELECT count(*) FROM conversations")" \
    || fail "the application role cannot read conversations: its grants did not come back"
  [[ "$unbound" == 0 ]] || fail "the application role sees $unbound conversations with no workspace bound"
  bound="$(sql --command "SET ROLE \"$app_role\"; SET app.workspace_id = '$workspace_id';
    SELECT count(*) FROM conversations")"
  [[ "$bound" == "$expected" ]] || fail "a bound workspace sees $bound of its $expected conversations"
fi
echo "Row-level security on $policies tables, application role isolated"

echo "Restore test passed"
