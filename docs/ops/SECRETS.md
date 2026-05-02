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
| `SMTP_USER` | yes (prod) | Gmail address | Gmail account used to send verification emails (F1.4). Without it, emails dry-run to console |
| `SMTP_APP_PASSWORD` | yes (prod) | 16-char Gmail App Password | NOT the regular Gmail password. Generated at myaccount.google.com/apppasswords (requires 2FA) |
| `SMTP_HOST` | optional | default `smtp.gmail.com` | Override for non-Gmail providers |
| `SMTP_PORT` | optional | default `587` | 587 = STARTTLS (recommended). 465 = implicit TLS |
| `MAIL_FROM` | optional | default `Smith Net <${SMTP_USER}>` | From-header. Gmail requires this match SMTP_USER (or be a verified alias) |
| `PUBLIC_BASE_URL` | yes (prod) | `https://...` | Where verification links point. In prod set to the Tailscale Funnel hostname |

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

## Gmail SMTP setup (F1.4)

Smith Net uses Gmail SMTP for verification emails. Daily cap is ~500 sends — fine for early-stage; revisit when registrations climb past that.

**One-time setup** on the Google account that will send mail (e.g. `innovatemobile@gmail.com`):

1. Enable 2-Step Verification at <https://myaccount.google.com/security> (required to generate App Passwords)
2. Visit <https://myaccount.google.com/apppasswords>
3. App: "Mail", Device: "Other (Smith Net backend)"
4. Copy the 16-character password it generates — you will not see it again

**Backend env** (`/etc/smith-net/backend.env` on Hetzner):

```ini
SMTP_USER=innovatemobile@gmail.com
SMTP_APP_PASSWORD=xxxxxxxxxxxxxxxx
PUBLIC_BASE_URL=https://ubuntu-8gb-ash-1.tail2523e7.ts.net
# MAIL_FROM defaults to "Smith Net <${SMTP_USER}>" — leave unset unless you need a custom From
```

Restart: `sudo systemctl restart smith-net-backend`. A successful boot logs:

```
[Email] SMTP live via smtp.gmail.com:587 as innovatemobile@gmail.com
```

If you leave `SMTP_USER` / `SMTP_APP_PASSWORD` unset (e.g., local dev), the backend logs:

```
[Email] SMTP unset — running in dry-run mode (verification links logged to console)
```

In dry-run mode, the verification link is printed to the backend log under `[email:dry-run]` — copy it from there to verify the account manually.

**Branding caveat:** until a real `smithnet.app` domain is registered with SPF/DKIM/DMARC, recipients will see `innovatemobile@gmail.com` as the sender. This is expected; deliverability is fine because Gmail-to-anywhere is well-trusted, but the From address won't say "Smith Net" until DNS is set up.

**App Password rotation:** revoke the old App Password at <https://myaccount.google.com/apppasswords>, generate a new one, update `SMTP_APP_PASSWORD`, restart. Old password stops working immediately.
