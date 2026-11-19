#!/usr/bin/env bash
# Shared by the server scripts: reads single values from /opt/supportchat/.env without sourcing it, since compose's
# .env format is not shell syntax (values may hold spaces, quotes or $).

APP_DIR="${APP_DIR:-/opt/supportchat}"

env_value() {
  local name="$1" line
  line="$(grep -E "^${name}=" "$APP_DIR/.env" | tail -n 1 || true)"
  printf '%s' "${line#*=}"
}
