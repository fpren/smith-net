# Secrets — Smith Net Backend

Operational reference for the secrets the backend reads at boot. Owned by F1.2 (auth hardening).

## Required env vars (production)

| Var | Required | Format | Notes |
|---|---|---|---|
| `JWT_SECRET` | yes (prod) | random ≥32 chars | Hard-fails boot if missing, weak, or set to the dev fallback |
| `JWT_REFRESH_SECRET` | optional | random ≥32 chars | If unset, refresh tokens are signed with `JWT_SECRET` |
| `DEFAULT_ADMIN_PASSWORD` | yes (prod) | password | Falls back to `admin123` if unset (dev only) |
| `NODE_ENV` | yes | `production` \| `development` | Gates the JWT hard-fail and CORS allowlist |
| `CORS_ALLOWED_ORIGINS` | optional | comma-separated origins | Browser origins allowed in prod. Empty by default — see CORS section below |

## Boot-time enforcement (F1.2)

`backend/src/auth.ts:resolveJwtSecret()` enforces at process start:

1. `NODE_ENV=production` + `JWT_SECRET` unset → **process crashes** with `[FATAL] JWT_SECRET env var is required in production`
2. `NODE_ENV=production` + `JWT_SECRET === 'smith-net-dev-secret-change-in-production'` → **process crashes** (dev fallback rejected in prod)
3. `JWT_SECRET.length < 32` → **process crashes** in any env
4. `NODE_ENV !== 'production'` + `JWT_SECRET` unset → boots with dev fallback + console warning

Why crash-on-boot rather than warn: tokens minted with a guessable secret are forgeable. A running-but-broken server is worse than one that refuses to start, because operators get paged immediately instead of weeks later.

## Generating a secret

```bash
# 48 random bytes → 64 base64 chars (well above the 32-char floor)
openssl rand -base64 48
# or
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

## Setting secrets on Hetzner

The backend runs under systemd. Secrets live in `/etc/smith-net/backend.env` (mode `600`, owned by the service user). Example:

```ini
NODE_ENV=production
JWT_SECRET=<paste output of openssl rand -base64 48>
JWT_REFRESH_SECRET=<separate value, same generator>
DEFAULT_ADMIN_PASSWORD=<bootstrap password — rotate after first login>
```

Reload after changes:

```bash
sudo systemctl restart smith-net-backend
sudo journalctl -u smith-net-backend -n 50
```

A successful boot logs `[Auth] Authentication module initialized`. A failed boot logs `[FATAL] JWT_SECRET …` and exits non-zero.

## Rotation procedure

Rotating `JWT_SECRET` invalidates every active access token. All clients must re-login. Plan accordingly.

1. Generate a new secret (`openssl rand -base64 48`)
2. **Optional zero-downtime path:** set the new value as `JWT_REFRESH_SECRET` first, deploy, wait for refresh tokens to drain (≤30 days TTL), then promote it to `JWT_SECRET` and clear `JWT_REFRESH_SECRET`
3. **Hard rotation path:** swap `JWT_SECRET` directly, restart, accept that all sessions are forced to re-login
4. Log the rotation in the ops journal with timestamp + reason
5. Never commit the old or new secret to git — even in private repos, even in `.env.example`

`DEFAULT_ADMIN_PASSWORD` only matters at first boot (when the in-memory user store seeds the admin row). Once set, rotate via the admin panel — env-var changes after first boot are ignored.

## Local dev

Leave `JWT_SECRET` unset. The server boots with the dev fallback and prints:

```
[Auth] JWT_SECRET unset — using dev fallback. NEVER deploy this way.
```

If the warning ever appears in production logs, treat it as a SEV-2: production was started without `NODE_ENV=production`, which means the F1.2 hard-fail was bypassed.

## CORS allowlist

Production `CORS_ALLOWED_ORIGINS` is **empty by default**. Current clients don't need it:

- **Android app** uses native HTTP (no `Origin` header) → allowed via the `if (!origin)` path
- **Desktop portal** uses relative `/api` paths (same-origin) → never triggers CORS

If you later host a browser-based portal on a real domain, add it via env var (no code deploy):

```ini
CORS_ALLOWED_ORIGINS=https://portal.example.com,https://staging.example.com
```

Restart the backend; the new origin is allowlisted on next boot. Origins are matched as exact literal strings (regex-escaped + anchored).

In `NODE_ENV=development`, `localhost`, `127.0.0.1`, and `192.168.x.x` are auto-allowed for local + LAN device dev.
