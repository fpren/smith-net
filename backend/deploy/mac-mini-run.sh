#!/usr/bin/env bash
#
# Run the smith-net relay on a Mac Mini (or any macOS box) via pm2.
# Survives reboots. Binds to port 3000 on the LAN IP.
#
# Usage:
#   bash mac-mini-run.sh             # install + start
#   pm2 logs smith-relay             # tail logs
#   pm2 restart smith-relay          # after code change
#   pm2 stop smith-relay             # stop

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT/backend"

if ! command -v node >/dev/null; then
  echo ">>> installing Node.js via brew"
  brew install node
fi
if ! command -v pm2 >/dev/null; then
  echo ">>> installing pm2"
  npm install -g pm2
fi

if [[ ! -f .env ]]; then
  echo ">>> creating .env from template — edit before restart if you want persistence"
  cp env.example.txt .env 2>/dev/null || touch .env
  echo "PORT=3000" >>.env
fi

echo ">>> installing deps + building"
npm ci
npm run build

echo ">>> starting via pm2"
pm2 delete smith-relay 2>/dev/null || true
pm2 start dist/server.js --name smith-relay --update-env
pm2 save

echo ">>> enabling launch-at-boot (one-time setup):"
echo "    pm2 startup   # run and follow the printed command"

LAN_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "UNKNOWN")
echo
echo ">>> smith-relay running at http://${LAN_IP}:3000"
echo ">>> health: curl http://${LAN_IP}:3000/api/health"
