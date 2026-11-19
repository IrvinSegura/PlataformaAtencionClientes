#!/usr/bin/env bash
# Points <DUCKDNS_SUBDOMAIN>.duckdns.org at this VM's public address (duckdns.timer, every 5 minutes). With a reserved
# public IP it only confirms the same address; it matters when the VM gets a new ephemeral one.
set -euo pipefail

source "$(dirname "$0")/env.sh"

subdomain="$(env_value DUCKDNS_SUBDOMAIN)"
token="$(env_value DUCKDNS_TOKEN)"
if [[ -z "$subdomain" || -z "$token" ]]; then
  exit 0
fi

# An empty ip lets DuckDNS take the address the request comes from. Its API only takes the token in the query
# string, so nothing here prints the request
answer="$(curl --silent --show-error --max-time 30 --get "https://www.duckdns.org/update" \
  --data-urlencode "domains=$subdomain" --data-urlencode "token=$token" --data-urlencode "ip=")"
if [[ "$answer" != OK* ]]; then
  echo "DuckDNS did not accept the update: $answer" >&2
  exit 1
fi
