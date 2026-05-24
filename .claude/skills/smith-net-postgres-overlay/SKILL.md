---
name: smith-net-postgres-overlay
description: Smith Net project-specific overlay on top of ecc-postgres-patterns. Constrains Postgres patterns to Smith Net's Hetzner-canonical schema (NOT Supabase), raw pg driver (NOT an ORM), append-only audit log, multi-authority validators, deterministic SHA256-sealed Ledger. Use for ANY Postgres / SQL work in /backend/migrations/ or backend services.
---

# Smith Net Postgres overlay

This skill **layers on top of** `ecc-postgres-patterns`. Generic Postgres best practices apply, but Smith Net's specific architecture overrides where they conflict.

## Foundation skill referenced

`ecc-postgres-patterns` — generic PostgreSQL patterns from Supabase best-practice playbook.

## Overrides

### Override 1: Hetzner self-hosted Postgres is canonical, NOT Supabase

The foundation skill assumes Supabase. **For Smith Net:** primary store is **self-hosted Postgres on Hetzner** behind Tailscale Funnel.

- **Migrations canonical dir:** `backend/migrations/` (NOT `supabase/migrations/`)
- **Driver:** raw `pg@8` Node driver (NOT Supabase JS, NOT Prisma, NOT Drizzle)
- **Auth:** JWT (HS256) middleware-checked at app layer (NOT Supabase Auth)
- **Realtime:** custom WebSocket via `ws@8` (NOT Supabase Realtime — disabled by default)
- **Storage:** local disk via `multer` (NOT Supabase Storage)

Supabase tables in `supabase/migrations/` are **legacy**; don't add new features there. Desktop portal still uses Supabase Auth — that's the only legitimate Supabase consumer.

### Override 2: No ORM. Parameterized queries only.

Don't introduce Prisma, Drizzle, Knex, TypeORM, or any ORM. Use raw `pg.query` with `$1, $2 ...` parameter placeholders. The discipline is intentional. Maintain it.

```typescript
// ✅ CORRECT
const { rows } = await pg.query(
  `SELECT * FROM jobs WHERE created_by = $1 AND status = $2`,
  [userId, status]
);

// ❌ FORBIDDEN
const jobs = await prisma.jobs.findMany({ where: { createdBy: userId, status } });
const jobs = await db.select().from(jobs).where(...);
```

### Override 3: Multi-authority validator pattern

State-mutation endpoints follow: validator first, then mutate.

```typescript
// ✅ CORRECT
const validation = validateIntentCreation(scope, parties);
if (!validation.valid) return { error: validation.message };
await pg.query(`INSERT INTO intents ...`);

// ❌ WRONG (inline validation; mutation first)
await pg.query(`INSERT INTO intents ...`);  // validation as afterthought
if (scope.length === 0) throw new Error('...');
```

When adding new state-mutating endpoints, extract validation to a sibling `*Authority.ts` file (`intentAuthority`, `synthesisAuthority`, `ledgerAuthority` are templates).

### Override 4: Append-only audit log enforced at DB level

The `audit_log` table (per F11.1) has UPDATE / DELETE blocked by triggers. Compliance classes (`admin`, `security`) are NEVER deleted by retention cron. **Don't add code paths that mutate audit rows** — even "fix a typo" updates are forbidden.

### Override 5: Append-only Ledger entries (deterministic moat)

`ledger_entries.sha256_hash` is computed by `ledger.computeHash(artifact)`. Once sealed:
- The `summary_artifact` it references must NEVER be mutated (verify via `/api/ledger/verify/:entryId`)
- Amendments create new entries with `supersedes` linking; never UPDATE the prior entry
- DB trigger (planned per F4 / Step 11) blocks UPDATE on `ledger_entries.superseded_by IS NOT NULL`

When working on synthesizer / ledger code, re-read `.claude/skills/smith-net-determinism/SKILL.md`.

### Override 6: ID conventions

- **UUIDs everywhere.** Generate via `gen_random_uuid()` (pgcrypto) or `uuid_generate_v4()` (uuid-ossp). Don't use auto-incrementing integers for primary keys.
- **Timestamps:** newer tables use `BIGINT` epoch ms; older Hetzner tables use `TIMESTAMPTZ NOW()`. Both coexist (mixed — flag for future cleanup, don't change in flight).
- **Status / phase enums:** TEXT with CHECK constraints, not Postgres ENUMs. Easier to extend.

### Override 7: RLS on Supabase tables; service-layer auth on Hetzner tables

Supabase tables enable RLS. **Hetzner tables don't have RLS** — equivalent enforcement happens in Express middleware (`requireRole`, `requirePermission`, `requireTier`, `requireCap`, `buildJobVisibilityClause`). Don't try to enable RLS on Hetzner-side tables — RLS requires PostgREST or a similar context that Hetzner doesn't have.

### Override 8: Indexes follow access patterns (per `SCHEMA.md §12`)

- Hot read paths must have indexes: `idx_messages_channel`, `idx_intent_versions_intent`, `idx_ledger_entries_serial`, `idx_proposals_uuid`, etc.
- Partial indexes when applicable (e.g., `WHERE status = 'held'` on `founder_seats.held_until`)
- Don't add indexes speculatively — measure with EXPLAIN first

### Override 9: Concurrency safety — `FOR UPDATE SKIP LOCKED` for slot reservations

Founder seat reservation (per F5.1) uses `SELECT ... FOR UPDATE SKIP LOCKED` to allow concurrent claims without lock contention. Apply the same pattern when implementing finite-pool reservations.

### Override 10: Migration naming

`NNN_description.sql` + matching `NNN_description.down.sql`. Numbers are gapless, monotonic. Currently:
- `001_initial_schema.sql` (legacy/Supabase only)
- Hetzner: `002_full_schema.sql` baseline + future numbered (Step 11 PRDs add 005 through 019)

Always include `BEGIN; ... COMMIT;` and idempotent guards (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, etc.).

## When generic Postgres patterns and this overlay conflict

This overlay wins. If `ecc-postgres-patterns` suggests an ORM, ignore. If it suggests RLS for everything, only apply to Supabase tables. If it suggests Supabase Edge Functions, use Express endpoints instead.

## Don't do

- ❌ Introduce an ORM (Prisma, Drizzle, Knex, TypeORM, Kysely, etc.)
- ❌ Add migrations to `supabase/migrations/` for new features
- ❌ Use Supabase JS client in backend (it's for desktop portal only)
- ❌ Concatenate SQL strings (always parameterized `$1`, `$2`)
- ❌ Add UPDATE / DELETE paths on `audit_log` or sealed `ledger_entries`
- ❌ Use auto-incrementing integer IDs for new tables (UUID only)
- ❌ Try to enable RLS on Hetzner tables (use service-layer auth)
- ❌ Create indexes without EXPLAIN-confirming a need
- ❌ Skip `BEGIN/COMMIT` in migrations
- ❌ Reach for Supabase Edge Functions / Storage / Realtime / Auth in backend code

## Linked specs

- Foundation: `ecc-postgres-patterns` skill
- `docs/database/SCHEMA.md` — full entity model
- `docs/architecture/ARCHITECTURE.md §1, §11` — backend topology
- `docs/security/SECURITY.md §6, §7, §13` — security boundaries
- `.claude/skills/smith-net-architecture/SKILL.md` — Step 12 architecture rules
- `.claude/skills/smith-net-determinism/SKILL.md` — Step 12 determinism rules
