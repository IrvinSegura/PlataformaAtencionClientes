#!/usr/bin/env bash
# Creates the JWT signing keys of the production-like environment, once. They are throwaway keys, kept out of git.
set -euo pipefail

secrets="$(dirname "$0")/secrets"
mkdir -p "$secrets"
if [ ! -f "$secrets/jwt-private.pem" ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$secrets/jwt-private.pem"
  openssl rsa -in "$secrets/jwt-private.pem" -pubout -out "$secrets/jwt-public.pem"
fi
# The backend runs as a non-root user that must read them
chmod 644 "$secrets"/*.pem
echo "Keys ready in $secrets"
