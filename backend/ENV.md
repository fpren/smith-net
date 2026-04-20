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
| `PORT` | `3000` | HTTP + WS listen port |
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

Public HTTPS URL for the relay, configured once via `tailscale funnel --bg --set-path=/ http://localhost:3000`. Config persists in Tailscale prefs and is re-established on tailscaled boot.

Status: `tailscale funnel status`

## Backups

Daily at 03:00 UTC via `/etc/cron.d/smithnet-backup`. Dumps land in `/var/backups/smithnet/` with 14-day retention.

Manual dump:

```bash
sudo -u postgres pg_dump smithnet | gzip > /tmp/smithnet-$(date +%F).sql.gz
```

Restore:

```bash
gunzip -c /path/to/dump.sql.gz | sudo -u postgres psql -d smithnet
```
