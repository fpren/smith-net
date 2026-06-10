#!/usr/bin/env bash
#
# Repeatable deploy for the smith-net relay + portal.
# Run as root on the server after the one-shot hetzner-install.sh.
#
#   bash /opt/smith-net/backend/deploy/deploy.sh
#
# Pulls master, rebuilds backend + portal, restarts services, syncs the
# portal static root. nginx needs no reload for content-only updates.

set -euo pipefail

echo ">>> pulling master"
git -C /opt/smith-net pull --ff-only

echo ">>> building backend"
cd /opt/smith-net/backend
npm ci
npm run build

echo ">>> applying any new migrations"
# Migrations are plain SQL applied in order. Not all files are idempotent,
# so track the last applied filename and only run newer ones.
ENV_DB_URL="$(grep -E '^DATABASE_URL=' .env | cut -d= -f2-)"
applied_marker=/opt/smith-net/.last-migration
last_applied="$(cat "$applied_marker" 2>/dev/null || echo '')"
for f in $(ls migrations/*.sql | sort -V); do
  if [[ "$f" > "$last_applied" ]]; then
    echo "    applying $f"
    psql "${ENV_DB_URL}" -v ON_ERROR_STOP=1 -q -f "$f"
    echo "$f" > "$applied_marker"
  fi
done

echo ">>> building portal"
cd /opt/smith-net/desktop/portal
npm ci
npm run build
rsync -a --delete dist/ /var/www/smithnet-portal/

chown -R smith:smith /opt/smith-net

echo ">>> restarting services"
systemctl restart smith-relay
systemctl restart smith-worker

echo ">>> health check"
sleep 2
curl -fsS http://127.0.0.1:3000/api/health && echo
systemctl is-active smith-relay smith-worker
echo ">>> deploy complete"
