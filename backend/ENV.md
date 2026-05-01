# Backend environment variables

`/opt/smith-net/backend/.env` on the deployed server, or a local `.env` for dev.
All values are read at startup; restart the service after changing them.

## Required

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | Postgres connection string, e.g. `postgres://smith:PASSWORD@127.0.0.1:5432/smithnet`. Without it, the relay will not persist messages and reconcile/history endpoints will error. |
| `DEFAULT_ADMIN_PASSWORD` | Password for the bootstrapped `admin@smithnet.local` user. Without it, the backend falls back to `admin123` and prints a warning. |

## Optional

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `3030` | HTTP + WS listen port (changed from 3000 — see commit changing default to avoid local-dev conflicts) |
| `HOST` | `0.0.0.0` | Listen interface |
| `JWT_SECRET` | random per-boot | Used by legacy auth paths; set to a stable value if you rotate behind a reverse proxy |

## Deprecated (safe to omit)

| Variable | Note |
|---|---|
| `SUPABASE_URL` | Backend no longer uses Supabase. |
| `SUPABASE_ANON_KEY` | Same. |
| `SUPABASE_SERVICE_ROLE_KEY` | Same. |

## Relay systemd unit

Defined at `/etc/systemd/system/smith-relay.service`. Reads `.env` via `EnvironmentFile=`. Manage with:

```bash
systemctl status smith-relay      # check state
journalctl -u smith-relay -f      # tail logs
systemctl restart smith-relay     # apply .env changes
```

## Tailscale Funnel

Public HTTPS URL for the relay, configured once via `tailscale funnel --bg --set-path=/ http://localhost:3030`. Config persists in Tailscale prefs and is re-established on tailscaled boot. (If your existing Hetzner funnel still points at `:3000`, leave it — the systemd unit pins `PORT=3000` in `.env`. The new `3030` default applies to local dev only.)

Status: `tailscale funnel status`

## Backups

Daily via `/etc/cron.d/smithnet-backup`. Both artifacts land in `/var/backups/smithnet/` with 14-day retention.

- `smithnet-YYYY-MM-DD.sql.gz` — Postgres dump, 03:00 UTC
- `uploads-YYYY-MM-DD.tar.gz` — user-uploaded media from `backend/uploads/`, 03:02 UTC

Manual dump:

```bash
sudo -u postgres pg_dump smithnet | gzip > /tmp/smithnet-$(date +%F).sql.gz
sudo tar -czf /tmp/uploads-$(date +%F).tar.gz -C /opt/smith-net/backend uploads/
```

Restore:

```bash
gunzip -c /path/to/smithnet-*.sql.gz | sudo -u postgres psql -d smithnet
sudo tar -xzf /path/to/uploads-*.tar.gz -C /opt/smith-net/backend/
```

Consider rsync'ing the backup directory off-box (to another Hetzner region or to a home NAS) for disaster recovery. Current setup only survives disk loss, not full VPS loss.
