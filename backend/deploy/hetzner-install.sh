#!/usr/bin/env bash
#
# One-shot Hetzner installer for the smith-net relay.
# Run as root on a fresh Ubuntu 22.04/24.04 VPS.
#
#   curl -fsSL https://raw.githubusercontent.com/fpren/smith-net/master/backend/deploy/hetzner-install.sh | bash -s -- relay.example.com
#
# Or copy the script to the server and run: bash hetzner-install.sh relay.example.com
#
# The single argument is the public hostname to expose with TLS.
# Expects /opt/smith-net/backend/.env to already exist, or it will be
# created from env.example.txt and must be filled in before restart.

set -euo pipefail

HOSTNAME="${1:-}"
if [[ -z "$HOSTNAME" ]]; then
  echo "usage: $0 <public-hostname>" >&2
  exit 1
fi

echo ">>> updating apt + installing deps"
apt-get update
apt-get install -y curl git nginx certbot python3-certbot-nginx build-essential

echo ">>> installing Node.js 20 LTS"
if ! command -v node >/dev/null; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

echo ">>> creating smith service user"
id smith &>/dev/null || useradd --system --home /opt/smith-net --shell /usr/sbin/nologin smith

echo ">>> cloning repo"
if [[ ! -d /opt/smith-net ]]; then
  git clone https://github.com/fpren/smith-net.git /opt/smith-net
else
  git -C /opt/smith-net pull --ff-only
fi

echo ">>> installing and building backend"
cd /opt/smith-net/backend
npm ci
npm run build

echo ">>> creating .env if missing"
if [[ ! -f .env ]]; then
  cp env.example.txt .env || touch .env
  echo "# FILL IN SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY" >>.env
  echo "PORT=3000" >>.env
  echo ">>> WARNING: .env needs values before the service will persist messages"
fi

chown -R smith:smith /opt/smith-net

echo ">>> installing systemd unit"
cat >/etc/systemd/system/smith-relay.service <<'UNIT'
[Unit]
Description=smith-net relay
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/smith-net/backend
EnvironmentFile=/opt/smith-net/backend/.env
ExecStart=/usr/bin/node dist/server.js
Restart=on-failure
RestartSec=5
User=smith
Group=smith

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now smith-relay

echo ">>> writing nginx site"
cat >/etc/nginx/sites-available/smith-relay <<NGINX
server {
    listen 80;
    server_name ${HOSTNAME};

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 3600;
    }
}
NGINX
ln -sf /etc/nginx/sites-available/smith-relay /etc/nginx/sites-enabled/smith-relay
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo ">>> requesting TLS cert"
certbot --nginx --non-interactive --agree-tos --redirect \
  --email "admin@${HOSTNAME}" -d "${HOSTNAME}" || \
  echo "!!! certbot failed — check DNS is pointed at this box and rerun: certbot --nginx -d ${HOSTNAME}"

echo ">>> done. health check:"
curl -sS "https://${HOSTNAME}/api/health" || curl -sS "http://${HOSTNAME}/api/health" || true
echo
echo "systemctl status smith-relay    # check service"
echo "journalctl -u smith-relay -f    # tail logs"
