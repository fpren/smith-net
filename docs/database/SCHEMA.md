# Smith Net — Database Schema

**Authoritative store:** self-hosted Postgres (Hetzner). Migrations in `backend/migrations/`.
**Legacy / optional store:** Supabase Postgres (for desktop portal auth + optional global chat). Migrations in `supabase/migrations/`.

This document describes the **canonical** schema (Hetzner). Where tables also exist in Supabase, the column set is a **subset** — the Hetzner schema is authoritative.

---

## 1. Entity overview

```
profiles ── creates ──> intents ──> intent_versions
                                         │
                                         │ confirmed →
                                         ▼
profiles ── owns ──> jobs ─────────► summary_artifacts ──> ledger_entries
              │                          ▲      ▲                ▲
              ├── owns ── time_entries ──┘      │                │
              │                                  │                │ supersession chain
              ├── creates ── messages            │                │ (DAG)
              │                                  │                │
              └── creates ── channels ───────────┘                │
                                                                   │
                                       outputs:                    │
                                       proposals ─────► invoice_links
                                       reports
                                       invoices
                                       plan_outputs    audit_log (SHA256-checksummed)
                                       plan_snapshots  (immutable)
```

## 2. Naming convention

- **Snake_case** for columns and tables.
- **Bigint epoch ms** for timestamps in newer tables (`created_at BIGINT`); **TIMESTAMPTZ NOW()** in older Hetzner tables. (Mixed — flag for cleanup but don't break in v1.)
- **UUID v4** for ids, `gen_random_uuid()` (pgcrypto) or `uuid_generate_v4()` (uuid-ossp).
- **Status / phase enums** as `TEXT` with `CHECK` constraints (not Postgres enums) — easier to extend.

## 3. Vocabulary mapping (CRITICAL)

| Internal table | External marketing term | Used in customer-facing copy as |
|---|---|---|
| `intents` + `intent_versions` | "PLAN" | a plan |
| `summary_artifacts` | "compiled PLAN" | the compiled plan |
| `ledger_entries` | "PLAN Compiler output" | sealed plan record |
| `proposals` | "estimate" / "quote" | estimate, quote, bid |
| `invoice_links` (public-facing) | "invoice page" | invoice |
| `invoices` (internal record) | "invoice" | invoice |
| `engagements` | "lead" / "opportunity" | lead, opportunity |

**Old `Plan` interface in `types.ts` is `@deprecated Use Intent instead`** — no plans table outside the legacy `supabase-migrations/003_add_plan_management.sql` (which should be considered legacy, not canonical).

## 4. Identity & access

### `profiles`
| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (Hetzner) / UUID (Supabase, FK auth.users) | PK |
| `email` | TEXT UNIQUE NOT NULL | login |
| `display_name` | TEXT NOT NULL | shown in UI |
| `role` | TEXT NOT NULL | one of: `solo`, `team`, `lead`, `foreman`, `enterprise`, `admin` (CHECK constraint) |
| `tier` | TEXT (planned) | one of: `open`, `solo`, `advanced`, `enterprise` — **NOT YET IN SCHEMA, ADD IN STEP 11** |
| `tier_expires_at` | TIMESTAMPTZ (planned) | for trial / annual expiry |
| `organization_id` | UUID/TEXT FK | nullable (solo users have no org) |
| `phone` | TEXT | optional |
| `trade` | TEXT | one of 121 trades (metadata only) |
| `hourly_rate` | DECIMAL(10,2) DEFAULT 85.00 | per-user default for invoicing math |
| `created_at` | TIMESTAMPTZ DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ DEFAULT NOW() | |
| `is_active` | BOOLEAN DEFAULT true | soft-delete flag |

### `organizations` (legacy / Supabase)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `name` | TEXT NOT NULL | |
| `owner_id` | UUID FK auth.users | |
| `address` / `phone` / `email` | TEXT | |
| `tax_id` | TEXT | for invoicing |
| `default_tax_rate` | DECIMAL(5,2) DEFAULT 8.25 | |

**Note:** Organizations are a Supabase-era concept. With the new tier ladder (Free / Solo / Advanced / Enterprise — flat per company), the **Enterprise tier replaces "organization"** for the moment. Step 11 should decide whether to formally bring orgs into the Hetzner schema or fold them into the tier resolver as a property of the owner profile.

## 5. Channels & messages

### `channels`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `name` | TEXT NOT NULL | |
| `type` | TEXT NOT NULL CHECK | `broadcast` / `group` / `dm` / `job` |
| `visibility` | TEXT (Hetzner) | `public` / `private` / `restricted` |
| `creator_id` | UUID/TEXT | |
| `organization_id` | UUID FK (nullable) | legacy |
| `job_id` | UUID FK | links channel to a job |
| `mesh_hash` | INTEGER | 2-byte derived hash for mesh routing |
| `is_archived` / `is_deleted` | BOOLEAN DEFAULT false | |
| `requires_approval` | BOOLEAN DEFAULT false | for restricted channels |
| `persistence` | TEXT (from migration `009_channel_persistence`) | `KEEP_HISTORY` / `EPHEMERAL` |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

### `channel_members`
| Column | Type | Notes |
|---|---|---|
| `channel_id` | UUID FK | composite PK |
| `user_id` | UUID FK | composite PK |
| `role` | TEXT DEFAULT 'member' CHECK | `member` / `admin` / `owner` |
| `joined_at` | TIMESTAMPTZ | |

### `messages`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `channel_id` | UUID FK ON DELETE CASCADE | |
| `sender_id` | UUID/TEXT | |
| `sender_name` | TEXT NOT NULL | denormalized for offline display |
| `content` | TEXT NOT NULL | |
| `origin` | TEXT CHECK | `online` / `mesh` / `gateway` / `online+mesh` |
| `reply_to_id` | UUID FK | nullable |
| `is_deleted` | BOOLEAN DEFAULT false | soft delete |
| `media_type` | TEXT (mig 002) | `image` / `voice` / `video` / `file` |
| `media_url` | TEXT (mig 002) | |
| `media_filename` | TEXT (mig 002) | |
| `media_size` | BIGINT (mig 002) | |
| `media_duration` | INTEGER (mig 002) | for voice/video |
| `media_thumbnail` | TEXT | |
| `created_at` / `timestamp` | TIMESTAMPTZ / BIGINT | |

**Index:** `idx_messages_channel ON messages(channel_id, created_at DESC)`.

### `message_bus_messages` (Hetzner — vector-clock log)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `channel_id` | UUID FK | |
| `sender_id` | TEXT | |
| `sender_name` | TEXT | |
| `content` | TEXT | |
| `timestamp` | BIGINT | epoch ms |
| `vector_clock` | JSONB | `{deviceId: counter, ...}` |
| `transport_type` | TEXT | `MESH` / `ONLINE` / `GATEWAY` / `SUPABASE` |
| `media_type` / `media_url` | TEXT | |
| `ai_generated` | BOOLEAN | flag for AI-authored content |
| `ai_model` | TEXT | model id when ai_generated=true |
| `synced_at` | TIMESTAMPTZ DEFAULT NOW() | |

**Used by:** `reconciliationEngine.ts` for online ↔ offline sync.
**Conflict handling:** `ON CONFLICT (id) DO UPDATE` to ensure idempotent re-sync.

## 6. Deterministic execution pipeline (the moat)

### `engagements`
Loose intent capture (top of funnel, no facts yet).
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `name` | TEXT NOT NULL | |
| `description` | TEXT | |
| `client_name` | TEXT | |
| `location` | TEXT | |
| `created_by` | TEXT NOT NULL | |
| `intent` | TEXT NOT NULL | scope-statement seed |
| `status` | TEXT DEFAULT 'active' | `active` / `converted` / `archived` |
| `created_at` / `updated_at` | BIGINT (epoch ms) | |

### `intents`
One per Intent tracker. Holds pointer to current version.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `current_version_id` | UUID FK intent_versions | nullable until first version exists |
| `created_by` | UUID/TEXT | |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

### `intent_versions`
The state machine: `draft → proposed → confirmed → superseded`.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `intent_id` | UUID FK ON DELETE CASCADE | |
| `version_number` | INTEGER NOT NULL | starts at 1, increments per supersession |
| `status` | TEXT CHECK | `draft` / `proposed` / `confirmed` / `superseded` |
| `scope_statement` | TEXT NOT NULL | the canonical scope |
| `intended_job_ids` | UUID[] / JSONB | jobs this Intent intends to cover |
| `parties` | UUID[] / JSONB | uuids of parties (creator + counterparties) |
| `confirmed_at` | TIMESTAMPTZ | nullable |
| `confirmed_by` | UUID/TEXT | nullable |
| `superseded_by` | UUID FK | nullable, set when a newer version supersedes |
| `supersedes` | UUID FK | nullable, set on the new version pointing to old |
| `auto_generated` | BOOLEAN DEFAULT false | true if AI assist drafted (still requires human confirm) |
| `created_at` | TIMESTAMPTZ NOT NULL | |

**Validators (intentAuthority.ts):**
- `validateIntentCreation`: scope non-empty, parties ≥ 1
- `validateIntentConfirmation`: status must be `proposed`, confirmer must be in `parties`
- `validateIntentVersion`: supersession links must form a tree (no cycles)

### `summary_artifacts`
The synthesizer output — what gets sealed.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `serial` | TEXT UNIQUE NOT NULL | sequential serial from migration `004_artifact_serial_sequence` |
| `intent_version_id` | UUID FK NOT NULL | the confirmed version that produced this |
| `scope_statement` | TEXT NOT NULL | copied from intent_version (immutable) |
| `work_performed` | JSONB / TEXT[] | itemized |
| `labor_recorded` | JSONB / TEXT[] | from time_entries |
| `materials_used` | JSONB / TEXT[] | from materials |
| `contextual_notes` | JSONB / TEXT[] | from chat |
| `total_hours` | NUMERIC(10,2) | sum |
| `total_cost` | NUMERIC(12,2) | sum |
| `job_ids` | UUID[] / JSONB | refs |
| `time_entry_ids` | UUID[] / JSONB | refs |
| `chat_message_ids` | UUID[] / JSONB | refs |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

**Validators (synthesisAuthority.ts):**
- `validateSynthesisInputs`: intent must be `confirmed`, ≥ 1 closed job, ≥ 1 closed time entry
- `validateArtifact`: must have serial, scope, intent ref, ≥ 1 job, ≥ 1 time entry

### `ledger_entries`
Immutable cryptographic seal over a Summary Artifact.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `artifact_serial` | TEXT FK summary_artifacts.serial | |
| `artifact_id` | UUID FK summary_artifacts.id | |
| `sha256_hash` | TEXT NOT NULL | computed by `computeHash(artifact)` |
| `blockchain_ref` | TEXT | optional anchor (post-v1) |
| `actor_uuid` | UUID NOT NULL | who sealed |
| `supersedes` | UUID FK self | nullable |
| `superseded_by` | UUID FK self | nullable; set on amendment |
| `sealed_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

**Validators (ledgerAuthority.ts):**
- `validateSealing`: artifact must be valid, not already sealed
- `validateAmendment`: prior entry must not be already superseded
- `computeHash`: deterministic SHA256 over canonicalized artifact JSON

**Determinism guarantee:** the SAME `summary_artifact` byte-for-byte produces the SAME `sha256_hash`. Re-running the synthesis with the same inputs produces the same artifact.

## 7. Jobs, time entries, materials

### `jobs`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `organization_id` | UUID FK | nullable |
| `title` | TEXT NOT NULL | |
| `description` | TEXT | |
| `client_name` / `client_email` / `client_phone` | TEXT | |
| `location` | TEXT | |
| `location_lat` / `location_lng` | DECIMAL(10,7) | |
| `status` | TEXT NOT NULL CHECK | `backlog` / `todo` / `in_progress` / `review` / `done` / `archived` |
| `priority` | TEXT CHECK | `low` / `medium` / `high` / `urgent` |
| `created_by` | UUID/TEXT | |
| `assigned_to` | UUID[] | array of user IDs |
| `crew_size` | INTEGER DEFAULT 1 | |
| `due_date` / `started_at` / `completed_at` | TIMESTAMPTZ | nullable |
| `estimated_hours` / `actual_hours` | DECIMAL(10,2) | |
| `budget` | DECIMAL(12,2) | |
| `tools_needed` / `expenses` | TEXT | |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

### `tasks` (sub-items of a job)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `job_id` | UUID FK ON DELETE CASCADE | |
| `title` / `description` | TEXT | |
| `status` | TEXT CHECK | `pending` / `in_progress` / `done` / `blocked` |
| `assigned_to` | UUID FK | nullable |
| `created_by` | UUID FK | |
| `sort_order` | INTEGER DEFAULT 0 | |
| `completed_at` | TIMESTAMPTZ | nullable |

### `time_entries`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | TEXT | |
| `job_id` | UUID FK | |
| `duration_minutes` | INTEGER | |
| `started_at` / `ended_at` | TIMESTAMPTZ (planned) | currently only duration; should add for cross-midnight clock semantics |
| `created_at` | TIMESTAMPTZ | |

### `materials`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `job_id` | UUID FK | |
| `name` | TEXT | |
| `quantity` | NUMERIC(10,2) | |
| `unit` | TEXT | e.g. `ft`, `each`, `lb` |
| `unit_cost` | NUMERIC(10,2) | |
| `created_at` | TIMESTAMPTZ | |

## 8. Outputs (Proposals, Invoices, Reports)

### `proposals` (internal record + public client-facing page)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `uuid` | TEXT UNIQUE | URL-safe id for `/p/:uuid` public page |
| `job_id` | TEXT | denormalized (legacy, can be FK after cleanup) |
| `contractor_name` / `contractor_phone` / `contractor_license` | TEXT | |
| `client_name` / `client_address` | TEXT | |
| `scope` | TEXT | |
| `tasks` / `materials` / `equipment` | JSONB | line items |
| `labor_hours` / `labor_rate` / `labor_cost` | NUMERIC | |
| `materials_cost` / `total_cost` | NUMERIC | |
| `status` | TEXT DEFAULT 'pending' | `pending` / `accepted` / `rejected` / `expired` |
| `client_response` / `client_notes` | TEXT | |
| `expires_at` | TIMESTAMPTZ DEFAULT NOW() + 30d | |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

### `invoice_links` (public-facing shareable invoice)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `uuid` | TEXT UNIQUE | URL for `/i/:uuid` |
| `job_id` | TEXT | |
| `contractor_name` / `contractor_phone` / `contractor_license` | TEXT | |
| `client_name` / `client_address` | TEXT | |
| `work_summary` | TEXT | |
| `hours_worked` / `hourly_rate` / `labor_cost` | NUMERIC | |
| `materials` | JSONB | |
| `materials_cost` / `total_due` | NUMERIC | |
| `payment_info` | TEXT | |
| `status` | TEXT DEFAULT 'unpaid' | `unpaid` / `viewed` / `paid` |
| `created_at` | TIMESTAMPTZ | |

### `invoices` (internal full record — plan-linked)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `plan_id` | UUID FK (legacy `plans` table — to migrate to `intent_id` in Step 11) | |
| `title` | TEXT | |
| `client_name` | TEXT | |
| `line_items` | JSONB | array of `InvoiceLineItem` |
| `subtotal` / `tax` / `total` | NUMERIC | |
| `due_date` | BIGINT | epoch ms |
| `status` | TEXT DEFAULT 'draft' | `draft` / `sent` / `paid` / `overdue` |
| `template` | TEXT (planned) | `standard` / `advanced` / `enterprise` — **ADD IN STEP 11** |
| `created_at` | BIGINT | |
| `created_by` | TEXT | |
| `archived` | BOOLEAN DEFAULT false | |

### `reports`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `plan_id` | UUID FK (legacy) | migrate to `intent_id` |
| `title` / `content` | TEXT | narrative |
| `total_hours` | NUMERIC | |
| `created_at` / `created_by` | BIGINT / TEXT | |
| `archived` | BOOLEAN DEFAULT false | |

### `plan_outputs` (tracker)
What outputs were generated for which plan/intent.
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `plan_id` | UUID FK (legacy → intent_id) | |
| `type` | TEXT | `report_only` / `invoice_only` / `report_and_invoice` |
| `report_id` | UUID FK | nullable |
| `invoice_id` | UUID FK | nullable |
| `generated_at` | BIGINT | |
| `generated_by` | TEXT | |

### `plan_snapshots` (immutable archive — separate from ledger_entries)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `plan_id` | UUID FK | |
| `snapshot_type` | TEXT | `archive` / `output` |
| `data` | JSONB NOT NULL | full plan state at moment of snapshot |
| `jobs` / `time_entries` / `messages` | JSONB | denormalized for tamper detection |
| `immutable_hash` | TEXT NOT NULL | SHA256 |
| `created_at` | BIGINT | |

**Relationship to ledger_entries:** `ledger_entries` seal `summary_artifacts` (the synthesized output). `plan_snapshots` archive the *raw inputs* that produced an output. Both have SHA256 hashes; together they let you prove "this artifact was produced from these specific inputs."

## 9. Audit & retention (C-05)

### `audit_log` (file-based + planned DB)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `timestamp` | BIGINT NOT NULL | epoch ms |
| `action` | TEXT NOT NULL | from `AuditAction` enum (25+ values) |
| `actor_id` | TEXT NOT NULL | user id or `'system'` |
| `target_id` | TEXT | nullable |
| `metadata` | JSONB | |
| `ip` | TEXT | nullable |
| `user_agent` | TEXT | nullable |
| `checksum` | TEXT NOT NULL | SHA256 over the entry — tamper detection |

**Currently file-backed** (`auditLog.ts` writes to disk). **Move to Postgres in Step 11** for query + retention enforcement.

## 10. Trade-pack tables (per-trade extensions)

Extension pattern: each trade pack defines its own tables. **electricianTools.ts is the example.**

### `circuit_diagrams` (Electrician)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `job_id` | UUID FK | |
| `name` | TEXT | |
| `type` | TEXT CHECK | `panel_upgrade` / `new_installation` / `renovation` / `troubleshooting` |
| `circuit_count` | INTEGER | |
| `voltage` | INTEGER | 120 / 240 / 277 / 480 |
| `amperage` | INTEGER | |
| `phases` | INTEGER | 1 / 3 |
| `diagram_data` | JSONB / XML | nullable, future KiCad/EasyEDA integration |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

### `electrical_checklists`, `material_estimates`, `nec_checks` etc.
Mirror the TypeScript interfaces in `electricianTools.ts`. **Currently in-memory / not yet persisted** — schema design is here; persistence in Step 11.

**Future trade packs add their own tables:** `plumbing_diagrams`, `hvac_load_calcs`, etc. Each pack owns its tables; core schema stays trade-agnostic.

## 11. Tier enforcement schema (NEW — to add in Step 11)

```sql
-- profiles.tier (added):
ALTER TABLE profiles ADD COLUMN tier TEXT NOT NULL DEFAULT 'open' 
  CHECK (tier IN ('open', 'solo', 'advanced', 'enterprise'));
ALTER TABLE profiles ADD COLUMN tier_expires_at TIMESTAMPTZ;
ALTER TABLE profiles ADD COLUMN trial_started_at TIMESTAMPTZ;
ALTER TABLE profiles ADD COLUMN trial_tier TEXT 
  CHECK (trial_tier IN ('solo', 'advanced', 'enterprise'));
ALTER TABLE profiles ADD COLUMN trial_cc_captured BOOLEAN DEFAULT false;
ALTER TABLE profiles ADD COLUMN founder_pricing_locked_at TIMESTAMPTZ;

-- subscriptions table (NEW):
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id TEXT NOT NULL REFERENCES profiles(id),
  tier TEXT NOT NULL,
  cadence TEXT NOT NULL CHECK (cadence IN ('monthly','annual')),
  provider TEXT NOT NULL CHECK (provider IN ('stripe','play_billing','manual')),
  provider_subscription_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('trialing','active','past_due','canceled','expired')),
  current_period_start TIMESTAMPTZ,
  current_period_end TIMESTAMPTZ,
  founder_price_locked BOOLEAN DEFAULT false,
  cents_per_period INTEGER NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- founder_seats (NEW — for the 1000/100/10 caps):
CREATE TABLE founder_seats (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tier TEXT NOT NULL CHECK (tier IN ('solo','advanced','enterprise')),
  bonus_id TEXT NOT NULL,  -- e.g. 'founder_pricing_lock', 'lifetime_template_library'
  seat_number INTEGER NOT NULL,
  total_seats INTEGER NOT NULL,
  claimed_by TEXT REFERENCES profiles(id),
  claimed_at TIMESTAMPTZ,
  UNIQUE (tier, bonus_id, seat_number)
);

-- gate_hit_events (NEW — for telemetry, see SUCCESS-METRICS):
CREATE TABLE gate_hit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id_hash TEXT NOT NULL,  -- SHA256(profile.id) — no PII
  event TEXT NOT NULL,  -- gate_hit.active_job_cap, etc.
  current_tier TEXT NOT NULL,
  metadata JSONB DEFAULT '{}',
  occurred_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_gate_hit_events_event ON gate_hit_events(event, occurred_at DESC);
```

## 12. Indexes (current + planned)

| Index | Table(s) | Purpose |
|---|---|---|
| `idx_messages_channel` | `messages(channel_id, created_at DESC)` | hot path for channel scrollback |
| `idx_iv_intent` | `intent_versions(intent_id)` | get all versions of an intent |
| `idx_iv_status` | `intent_versions(status)` | filter by status (e.g. all confirmed) |
| `idx_sa_intent_version` | `summary_artifacts(intent_version_id)` | find artifact for an intent version |
| `idx_sa_serial` | `summary_artifacts(serial)` | serial lookup |
| `idx_le_artifact` | `ledger_entries(artifact_serial)` | get all ledger entries for an artifact (chain) |
| `idx_le_sealed` | `ledger_entries(sealed_at DESC)` | recent seals |
| `idx_proposals_uuid` | `proposals(uuid)` | public-page lookup |
| `idx_invoice_links_uuid` | `invoice_links(uuid)` | public-page lookup |
| `idx_engagements_status` | `engagements(status)` | active engagements list |
| `idx_subscriptions_profile` (planned) | `subscriptions(profile_id, status)` | tier resolver hot path |
| `idx_gate_hit_events_event` (planned) | `gate_hit_events(event, occurred_at DESC)` | telemetry queries |

## 13. Row-Level Security (RLS)

Currently enabled on the legacy `plan_management` Supabase tables (`engagements`, `plans`, `proposals`, etc.) with permissive policies (`USING (true)`). **These are too loose for v1 launch.**

**v1 RLS rules to enforce:**
- `intents` / `intent_versions`: only readable by parties named in the intent_version
- `summary_artifacts`: only readable by parties of the linked intent_version
- `ledger_entries`: only readable by parties of the linked artifact's intent
- `jobs`: only readable by `created_by` or `assigned_to` array members
- `messages`: only readable by `channel_members` of the channel
- `subscriptions`: only readable by self
- `gate_hit_events`: only writable by service-role (no client INSERT)

**Hetzner side has no RLS** — equivalent enforcement happens in Express middleware. Audit each `apiRouter` endpoint for the equivalent check before launch.

## 14. Migration plan

### Existing migrations (status quo)

| Tree | File | Status |
|---|---|---|
| backend/migrations/ | `002_full_schema.sql` (no `001` named — it's the relay tables baseline expected to exist) | ✅ Hetzner canonical |
| supabase/migrations/ | `000_profiles_only.sql`, `001_initial_schema.sql`, `002_message_bus.sql`, `003_intent_synthesizer_ledger.sql`, `004_artifact_serial_sequence.sql`, `005_proposals.sql`, `006_invoice_links.sql`, `007_wage_data.sql`, `008_profiles_discoverability.sql`, `009_channel_persistence.sql` | ✅ Supabase canonical |
| supabase-migrations/ | `002_add_media_support.sql`, `003_add_plan_management.sql` | 🟡 Surface dir; `003` defines the legacy `plans` table — being replaced by `intents` |

### v1-launch migrations to add (Step 11 PRDs)

| # | Migration | Purpose |
|---|---|---|
| M1 | `add_tier_columns_to_profiles.sql` | tier, tier_expires_at, trial_*, founder_pricing_locked_at |
| M2 | `create_subscriptions_table.sql` | tracks Stripe / Play Billing subscription state |
| M3 | `create_founder_seats_table.sql` | enforces 1000/100/10 caps server-authoritative |
| M4 | `create_gate_hit_events_table.sql` | telemetry sink for tier-gate events |
| M5 | `add_template_to_invoices.sql` | invoice template selector (standard / advanced / enterprise) |
| M6 | `migrate_plans_to_intents.sql` | data migration from legacy `plans` to `intents` (idempotent) |
| M7 | `tighten_rls_policies.sql` | replace permissive `USING (true)` with role-aware policies |
| M8 | `move_audit_log_to_db.sql` | persist `auditLog.ts` entries to a queryable table |

### Migrations to retire (post-launch cleanup)

- `supabase-migrations/003_add_plan_management.sql` — superseded by `intents` model (after M6 data migration completes).

## 15. Notes for downstream Sigma steps

- **Step 7 (Interface States):** the channel `visibility` (`public` / `private` / `restricted` / `requiresApproval`) and the AccessStatus enum (`granted` / `pending` / `can_request` / `denied`) drive a substantial UI state surface — call this out.
- **Step 11 (PRDs):** every PRD that touches tier-gated features must validate against the `subscriptions` table via the tier resolver, not by reading `profiles.tier` directly (avoid drift).
- **Step 12 (Context Engine):** the `intent → summary_artifact → ledger_entry` pipeline IS the project's identity. Any AI rule must protect it from being shortcut.
