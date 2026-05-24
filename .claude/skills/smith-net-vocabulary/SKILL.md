---
name: smith-net-vocabulary
description: Internal code vocabulary vs external marketing vocabulary for Smith Net — code uses Intent / SummaryArtifact / Ledger / VectorClock / Cord; marketing uses PLAN Compiler, cord-based state model, Smith Mesh, SmithAI. Use when writing code (use code names), writing user-facing copy / docs / commit messages (use marketing names), or naming new entities.
---

# Smith Net — Vocabulary

The codebase and the marketing have **two languages** for the same things. Use the right one in the right place.

## The mapping

| Internal code term | External / marketing | Where each appears |
|---|---|---|
| `Intent` + `IntentVersion` (statuses: draft, proposed, confirmed, superseded) | a "PLAN" | code: types.ts, schema, API; marketing: USP, OFFER_ARCHITECTURE, app UI |
| `SummaryArtifact` (synthesizer output) | a "compiled PLAN" | code: synthesizer.ts, ledger.ts, schema; marketing: pricing copy |
| `LedgerEntry` (SHA256-sealed, supersession chain) | "PLAN Compiler output" | code: ledger.ts, schema; marketing: PLAN sealed |
| `intentAuthority` + `synthesisAuthority` + `ledgerAuthority` | the "PLAN Compiler" engine | code: 3 separate files; marketing: a single product term |
| `VectorClock` + `CordEntry` + `CordRepository` + `CordMessageClass` | "cord-based state model" | code: data layer; marketing: USP positioning |
| `BoundaryEngine` (Android) + `messageBus` (backend) + `MeshService` | "Smith Mesh" | code: services; marketing: USP |
| `electricianTools.ts` and friends | a "trade pack" (one of N) | code: per-trade modules; marketing: feature lists |
| `LlamaInference` + `AISupervisor` + `AmbientRuleEngine` + `AIRouter` | "SmithAI" | code: ai/ package; marketing: Advanced tier hero |
| `RULE_BASED_FALLBACK` state | "deterministic baseline" | code: AI state enum; marketing: Free tier hero |
| `org_members` | "crew" | code: tables; marketing: Enterprise tier feature |
| `subscriptions.cents_per_period` | "$2.99/mo" / "$9.99/mo" / "$50/mo" | code: integer cents; marketing: dollar prices |
| `tier_gate_exceeded` 403 response | "Locked" overlay with upgrade CTA | code: error contract; marketing: lock overlays |
| `entitlements.caps.*` | "What's included" bullets | code: cap matrix; marketing: pricing-screen bullet lists |
| `founder_seats` table + `bonus_id` | "Founder Pricing Lock", "Lifetime Template Library", "Founder Annual Pricing" | code: technical; marketing: bonus names |
| `gate_hit_events` | (telemetry — no marketing name) | code-internal only |

## Tier name canonicalization

Code uses lowercase enums; marketing uses Title Case with "Smith Net" prefix:

| Code | Marketing |
|---|---|
| `tier: 'open'` | "Smith Net Open" (or just "Open") |
| `tier: 'solo'` | "Smith Net Solo" |
| `tier: 'advanced'` | "Smith Net Advanced" |
| `tier: 'enterprise'` | "Smith Net Enterprise" |

## Product-name canonicalization

| Form | When to use |
|---|---|
| **Smith Net** | product name (two words, capital S, capital N) — marketing copy, in-app branding |
| `smith-net` | code identifiers, package names, file paths |
| `smithnet` | URLs / domain (`smithnet.app`) |
| `SmithAI` | AI product name (one word, capital S, A, I) — marketing |
| `PLAN Compiler` | the moat marketing term (PLAN in caps to signal product term) |

## When writing code

Use **internal code names**:

```typescript
// ✅ CORRECT
async function confirmIntent(versionId: string) { ... }
async function sealArtifact(artifactId: string) { ... }
const cordEntry = CordEntry.create(...);

// ❌ WRONG (marketing name in code)
async function confirmPlan(versionId: string) { ... }
```

## When writing user-facing copy (UI, docs, marketing, support replies)

Use **external marketing names**:

```kotlin
// ✅ CORRECT
Text("PLAN COMPILER", style = ConsoleTheme.captionBold)
Text("Your plan, compiled. Runs the same way every time.", style = ConsoleTheme.body)

// ❌ WRONG (code name in user-facing text)
Text("INTENT SYNTHESIZER", ...)
```

## When writing commit messages, PR titles, technical docs (e.g., ARCHITECTURE.md)

Use code names (this is engineer-facing):

```
✅ feat(intentService): add validateIntentConfirmation party check
✅ docs(architecture): explain ledgerAuthority hash determinism

❌ feat(plan-compiler): add confirmation check  (commit messages are engineer-facing)
```

## When writing user-visible commit messages or release notes

Use marketing names (these go in the changelog the user sees):

```
✅ "PLAN Compiler now supports per-job draft auto-save"
❌ "intentService.autosaveDraft now persists to plan_summaries"
```

## What about hybrid contexts (e.g., spec docs)?

In Step 1-12 spec docs, code names appear in technical sections (ARCHITECTURE, SCHEMA, API-SPEC, SECURITY) and marketing names appear in user-facing sections (USP, OFFER_ARCHITECTURE, marketing copy). Where docs cross both audiences, **provide both with a clear mapping** (this skill is itself the mapping).

## Names that are the same in both contexts

Some names don't have a marketing/code split:

- **Smith Net Open / Solo / Advanced / Enterprise** (tier names — same)
- **Engagement** (top-of-funnel intent capture — same)
- **Job, Time Entry, Material, Invoice, Proposal, Report** (core entities — same)
- **Crew, Foreman, Lead, Member** (role names — same)

## Names being retired

| Term | Status |
|---|---|
| `Plan` interface (in `types.ts`) | `@deprecated Use Intent instead` — being retired (B19, deferred cool-down) |
| 3-6-9 pyramid pricing (`pricingTiers.ts` legacy) | Being retired by F2.x — replaced by 4-tier ladder |
| K2 ChannelListScreen vs K3 ChannelsScreen | One to be removed in cool-down (consolidation flagged in Step 4) |
| Supabase `organizations` table | Replaced by Hetzner `organizations` table (F10.1) |

When you see these, prefer the new term in any new code.

## Don't do

- ❌ Write user-facing copy with code names like "Confirm Intent" or "Synthesize Artifact"
- ❌ Write code with marketing names like `confirmPlan()` or `compilePlanArtifact()`
- ❌ Mix the two in the same surface (don't say "Confirm PLAN" — pick one)
- ❌ Introduce a new vocabulary split without updating this skill + documenting the mapping
- ❌ Use "PLAN" as a code identifier (it's a marketing term)
- ❌ Use "Intent" in marketing copy (use "PLAN")
- ❌ Use "SmithAI" in code identifiers (use the actual class name like `LlamaInference`, `AIRouter`, `AISupervisor`)

## Linked specs

- `docs/architecture/ARCHITECTURE.md §2` — full vocabulary table
- `docs/database/SCHEMA.md §3` — internal table → external term mapping
- `docs/stack-profile.json` `vocabulary_mapping` — machine-readable
- `docs/specs/USP.md` — marketing voice + how the moat is named externally
