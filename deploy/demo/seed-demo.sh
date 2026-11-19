#!/usr/bin/env bash
# Prepares the public demo through the platform's own API: a workspace for a fictitious coffee shop, Café Aurora,
# with its knowledge base (knowledge/, next to this script) and its widget allowed on the demo page.
#
# Usage:
#   DEMO_URL=https://chat.example.com DEMO_EMAIL=demo@example.com DEMO_PASSWORD='...' deploy/demo/seed-demo.sh
#
# Safe to run again: it signs in when the account exists and skips documents already uploaded. It prints the values
# for DEMO_WORKSPACE_ID and DEMO_WIDGET_KEY; put them in /opt/supportchat/.env and restart the backend and web
# containers, so the workspace answers with the economical model, under its daily cap, and /demo shows the widget.
# Requires curl and jq.
set -euo pipefail

: "${DEMO_URL:?Set DEMO_URL, e.g. https://chat.example.com}"
: "${DEMO_EMAIL:?Set DEMO_EMAIL, the address the demo account signs in with}"
: "${DEMO_PASSWORD:?Set DEMO_PASSWORD, at least 12 characters}"
DEMO_ORIGIN="${DEMO_ORIGIN:-$DEMO_URL}"
KNOWLEDGE_DIR="$(cd "$(dirname "$0")/knowledge" && pwd)"
API="${DEMO_URL%/}/api/v1"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done

# Prints the body; fails with the problem details on any status above 299
call() {
  local method="$1" path="$2" body status
  shift 2
  body="$(mktemp)"
  status="$(curl -sS -o "$body" -w '%{http_code}' -X "$method" "$API$path" "$@")"
  if [ "$status" -gt 299 ]; then
    echo "$method $path answered $status: $(cat "$body")" >&2
    rm -f "$body"
    return 1
  fi
  cat "$body"
  rm -f "$body"
}

credentials="$(jq -n --arg email "$DEMO_EMAIL" --arg password "$DEMO_PASSWORD" '{email: $email, password: $password}')"
if session="$(call POST /auth/login -H 'Content-Type: application/json' -d "$credentials" 2>/dev/null)"; then
  echo "Signed in as $DEMO_EMAIL"
else
  registration="$(jq -n --arg email "$DEMO_EMAIL" --arg password "$DEMO_PASSWORD" \
    '{email: $email, password: $password, displayName: "Equipo Café Aurora", workspaceName: "Café Aurora (demo)",
      acceptTerms: true}')"
  session="$(call POST /auth/register -H 'Content-Type: application/json' -d "$registration")"
  echo "Created the demo account $DEMO_EMAIL"
fi
token="$(jq -r .accessToken <<<"$session")"
auth=(-H "Authorization: Bearer $token")

uploaded="$(call GET '/knowledge/documents?size=100' "${auth[@]}" | jq -r '.items[].fileName')"
for file in "$KNOWLEDGE_DIR"/*.md; do
  name="$(basename "$file")"
  # The first heading is the title visitors see when an answer cites the document
  title="$(sed -n '1s/^# *//p' "$file")"
  if grep -qxF "$name" <<<"$uploaded"; then
    echo "Already uploaded: $name"
  elif ! error="$(call POST /knowledge/documents "${auth[@]}" -F "file=@$file;type=text/markdown" \
      -F "title=$title" 2>&1 >/dev/null)"; then
    # The same content, uploaded before under an earlier file name
    if grep -q DUPLICATE_DOCUMENT <<<"$error"; then
      echo "Already uploaded: $name (under an earlier name)"
    else
      echo "$error" >&2
      exit 1
    fi
  else
    echo "Uploaded: $name as \"$title\""
  fi
done

settings="$(jq -n --arg origin "$DEMO_ORIGIN" '{botName: "Aurora",
  welcomeMessage: "¡Hola! Soy Aurora, la asistente de Café Aurora. Pregúntame por nuestros cafés, envíos, pagos o cómo prepararlo.",
  allowedOrigins: [$origin]}')"
workspace="$(call PUT /workspace/widget-settings "${auth[@]}" -H 'Content-Type: application/json' -d "$settings")"

cat <<DONE

The demo workspace is ready. Add these lines to /opt/supportchat/.env and restart (docker compose up -d):

DEMO_WORKSPACE_ID=$(jq -r .id <<<"$workspace")
DEMO_WIDGET_KEY=$(jq -r .widgetKey <<<"$workspace")

Then open ${DEMO_URL%/}/demo. Documents are searchable once processed; the panel's knowledge page shows their status.
DONE
