#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 24.04 VM (Oracle Cloud, Ampere A1). Run as root from a copy of deploy/:
#   scp -r deploy ubuntu@<ip>:/tmp/ && ssh ubuntu@<ip> sudo bash /tmp/deploy/scripts/setup-vm.sh "<deploy public key>"
# It installs Docker, opens 80/443 in the VM's firewall, creates the deploy user GitHub Actions signs in as, prepares
# /opt/supportchat with its JWT keys and installs the backup and DuckDNS timers. Running it again is harmless.
set -euo pipefail

deploy_key="${1:?usage: setup-vm.sh \"ssh-ed25519 AAAA... github-actions\"}"
source_dir="$(cd "$(dirname "$0")/.." && pwd)"
app_dir=/opt/supportchat
# The backend's image runs as this uid: it must be able to read the JWT private key
backend_uid=10001

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg age python3-venv netfilter-persistent iptables-persistent \
  unattended-upgrades

# Oracle's Ubuntu images reject everything but SSH in iptables, besides the VCN's security list: open HTTP, HTTPS and
# HTTP/3 ahead of that REJECT and keep the rules across reboots. Saved before Docker exists, so its own rules (which it
# rebuilds on every start) are not frozen into the saved set
for rule in "-p tcp --dport 80" "-p tcp --dport 443" "-p udp --dport 443"; do
  # shellcheck disable=SC2086
  iptables -C INPUT -m state --state NEW $rule -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -m state --state NEW $rule -j ACCEPT
done
netfilter-persistent save

# Docker from Docker's own repository (Ubuntu's package lags behind and lacks the compose plugin)
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

# OCI CLI for the backups, in its own virtualenv; it signs in as the VM (instance principal), so it needs no keys
if [[ ! -x /opt/oci-cli/bin/oci ]]; then
  python3 -m venv /opt/oci-cli
  /opt/oci-cli/bin/pip install --quiet oci-cli
fi
ln -sf /opt/oci-cli/bin/oci /usr/local/bin/oci

# The user GitHub Actions deploys as: Docker access, no password, only the given key
id deploy &>/dev/null || useradd --create-home --shell /bin/bash --groups docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
grep -qxF "$deploy_key" /home/deploy/.ssh/authorized_keys 2>/dev/null \
  || echo "$deploy_key" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

# The application folder: the workflow keeps compose, scripts and configuration up to date; .env and secrets/ are
# written once, by hand, and never leave the VM
install -d -m 750 -o deploy -g deploy "$app_dir" "$app_dir/secrets"
cp -r "$source_dir/scripts" "$source_dir/systemd" "$source_dir/postgres" "$source_dir/monitoring" \
  "$source_dir/docker-compose.yml" "$app_dir/"
chmod +x "$app_dir"/scripts/*.sh
if [[ ! -f "$app_dir/.env" ]]; then
  install -m 600 -o deploy -g deploy "$source_dir/.env.example" "$app_dir/.env"
fi
if [[ ! -f "$app_dir/secrets/jwt-private.pem" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$app_dir/secrets/jwt-private.pem"
  openssl rsa -in "$app_dir/secrets/jwt-private.pem" -pubout -out "$app_dir/secrets/jwt-public.pem"
fi
chown "$backend_uid" "$app_dir"/secrets/jwt-*.pem
chmod 400 "$app_dir/secrets/jwt-private.pem"
chmod 444 "$app_dir/secrets/jwt-public.pem"
chown -R deploy:deploy "$app_dir/scripts" "$app_dir/systemd" "$app_dir/postgres" "$app_dir/monitoring" \
  "$app_dir/docker-compose.yml"
install -d -m 700 -o deploy -g deploy /var/backups/supportchat

# Nightly backup and the DuckDNS refresh
cp "$app_dir"/systemd/*.service "$app_dir"/systemd/*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now supportchat-backup.timer duckdns.timer

# Security updates install themselves
dpkg-reconfigure -f noninteractive unattended-upgrades

echo
echo "Done. Next: fill in $app_dir/.env (as deploy, chmod 600) and run the deploy workflow."
echo "Keep a copy of SECRETS_ENCRYPTION_KEY and of $app_dir/secrets/ outside this VM."
