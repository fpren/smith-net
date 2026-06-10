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
apt-get install -y curl git rsync nginx certbot python3-certbot-nginx build-essential postgresql postgresql-contrib

echo ">>> installing Node.js 20 LTS"
if ! command -v node >/dev/null; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

echo ">>> creating smith service user"
id smith &>/dev/null || useradd --system --home /opt/smith-net --shell /usr/sbin/nologin smith

echo ">>> provisioning postgres role + database (idempotent)"
systemctl enable --now postgresql
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='smith'" | grep -q 1; then
  DB_PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 24)"
  sudo -u postgres psql -c "CREATE ROLE smith LOGIN PASSWORD '${DB_PASSWORD}'"
  echo ">>> generated postgres password for role smith -- written to .env as DATABASE_URL"
else
  DB_PASSWORD=""
fi
sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='smithnet'" | grep -q 1 || \
  sudo -u postgres createdb -O smith smithnet

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
  echo "PORT=3000" >>.env
  if [[ -n "${DB_PASSWORD}" ]]; then
    echo "DATABASE_URL=postgres://smith:${DB_PASSWORD}@127.0.0.1:5432/smithnet" >>.env
  fi
  echo "JWT_SECRET=$(head -c 48 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 48)" >>.env
  echo ">>> WARNING: .env still needs DEFAULT_ADMIN_PASSWORD, SMTP_USER, SMTP_APP_PASSWORD before launch"
fi

echo ">>> applying schema migrations"
ENV_DB_URL="$(grep -E '^DATABASE_URL=' .env | cut -d= -f2- || true)"
if [[ -n "${ENV_DB_URL}" ]]; then
  for f in $(ls migrations/*.sql | sort -V); do
    psql "${ENV_DB_URL}" -v ON_ERROR_STOP=0 -q -f "$f" || echo "    (non-fatal) $f reported errors -- check if already applied"
  done
else
  echo "!!! DATABASE_URL missing from .env -- apply migrations manually after filling it in"
fi

echo ">>> building portal"
cd /opt/smith-net/desktop/portal
npm ci
npm run build
mkdir -p /var/www/smithnet-portal
rsync -a --delete dist/ /var/www/smithnet-portal/

chown -R smith:smith /opt/smith-net

echo ">>> installing systemd units"
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

# Background-job worker (email, geocode, audit flush). Without this,
# background_jobs rows are enqueued but never processed.
cat >/etc/systemd/system/smith-worker.service <<'UNIT'
[Unit]
Description=smith-net background worker
After=network.target smith-relay.service

[Service]
Type=simple
WorkingDirectory=/opt/smith-net/backend
EnvironmentFile=/opt/smith-net/backend/.env
ExecStart=/usr/bin/node dist/workers/runner.js
Restart=on-failure
RestartSec=5
User=smith
Group=smith

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now smith-relay
systemctl enable --now smith-worker

echo ">>> writing nginx site"
cat >/etc/nginx/sites-available/smith-relay <<NGINX
server {
    listen 80;
    server_name ${HOSTNAME};

    # Portal SPA (PWA) served as static root; backend owns /api, /media, /p, /i/.
    root /var/www/smithnet-portal;
    index index.html;

    # Ubuntu 22.04 ships nginx 1.18 whose mime.types predates wasm --
    # without the explicit type, WebAssembly.instantiateStreaming rejects
    # application/octet-stream.
    location = /smithcore.wasm {
        default_type application/wasm;
        add_header Cache-Control "public, max-age=86400";
    }

    location /api {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_read_timeout 3600;
    }
    location /media {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }
    location /p {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }
    location /i/ {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }

    # Hashed build assets are immutable; the SW + shell must never be cached
    # or deploys take the browser heuristic-cache lifetime to appear.
    location /assets/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }
    location = /sw.js          { add_header Cache-Control "no-cache"; }
    location = /registerSW.js  { add_header Cache-Control "no-cache"; }
    location = /index.html     { add_header Cache-Control "no-cache"; }

    # SPA fallback for client-side routes (/console/...).
    location / {
        try_files \$uri /index.html;
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
echo "systemctl status smith-relay     # check API service"
echo "systemctl status smith-worker    # check background worker"
echo "journalctl -u smith-relay -f     # tail API logs"
echo "journalctl -u smith-worker -f    # tail worker logs"
