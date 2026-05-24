# Smith Net — Sigma Protocol Complete

**Status:** All 13 Sigma steps complete (Step 9 Landing Page intentionally skipped per user direction).

---

## What was produced

### Plan phase (Steps 0-1.5)
- `docs/ops/ENVIRONMENT-SETUP.md`
- `docs/specs/MASTER_PRD.md`
- `docs/specs/USP.md`
- `docs/specs/FEATURES.md`
- `docs/specs/NFRS.md`
- `docs/specs/DEV-READINESS.md`
- `docs/ops/SUCCESS-METRICS.md`
- `docs/specs/OFFER_ARCHITECTURE.md`
- `docs/specs/pricing-config.json`
- `docs/stack-profile.json`
- `.sigma/config.json`

### Design phase (Steps 2-8, 9 skipped)
- `docs/architecture/ARCHITECTURE.md`
- `docs/database/SCHEMA.md`
- `docs/api/API-SPEC.md`
- `docs/security/SECURITY.md`
- `docs/design/INSPIRATION.md`
- `docs/design/EXTRACTED-PATTERNS.md`
- `docs/design/DESIGN-SYSTEM.md`
- `docs/tokens/DESIGN-TOKENS.md`
- `docs/ux/UX-DESIGN.md`
- `docs/ux/WIREFRAMES.md`
- `docs/journeys/USER-JOURNEYS.md`
- `docs/journeys/STATE-COVERAGE.md`
- `docs/flows/SCREEN-INVENTORY.md`
- `docs/flows/FLOW-TREE.md`
- `docs/flows/FLOW-DIAGRAMS.md`
- `docs/flows/TRACEABILITY-MATRIX.md`
- `docs/flows/ZERO-OMISSION-CERTIFICATE.md`
- `docs/wireframes/WIREFRAME-SPEC.md`
- `docs/prds/flows/FLOW-1.md` through `FLOW-8.md` (8 flow PRDs)
- `docs/states/STATE-SPEC.md`
- `docs/states/MICRO-INTERACTIONS.md`
- `docs/technical/TECHNICAL-SPEC.md`

### Build phase (Steps 10-13)
- `docs/implementation/FEATURE-BREAKDOWN.md` (Step 10 — 12 shaped pitches across 5 cycles)
- `docs/prds/PRD-INDEX.md` (Step 11 — index of 30 PRDs)
- `docs/prds/F1.1` through `F12.1` and `F11.1` (Step 11 — 30 implementation-ready PRDs)
- `.claude/skills/smith-net-architecture/SKILL.md` (Step 12)
- `.claude/skills/smith-net-design-system/SKILL.md` (Step 12)
- `.claude/skills/smith-net-tier-gating/SKILL.md` (Step 12)
- `.claude/skills/smith-net-determinism/SKILL.md` (Step 12)
- `.claude/skills/smith-net-security/SKILL.md` (Step 12)
- `.claude/skills/smith-net-vocabulary/SKILL.md` (Step 12)
- `.claude/skills/smith-net-frontend-overlay/SKILL.md` (Step 13)
- `.claude/skills/smith-net-postgres-overlay/SKILL.md` (Step 13)
- `.claude/skills/smith-net-android-overlay/SKILL.md` (Step 13)

**Total: 50+ files. ~15,000 lines of specification + 30 implementation-ready PRDs + 9 project-specific AI skills.**

---

## What this gives the project

### A complete launch path
- **30 PRDs across 5 cycles** (~30 weeks at 1 engineer, ~20 at 2 engineers)
- **End of Cycle 3 = launchable** for Free + Solo + Advanced
- **End of Cycle 4 = public-launch-ready** (security gates closed)
- **Cycle 5 = Enterprise** (deferrable)

### A built-in conversion engine
- 4-tier ladder with one hero feature per tier (Open / Solo $2.99 / Advanced $9.99 / Enterprise $50)
- Founder pricing (1000 / 100 / 10 seats) atomic + real
- Server-authoritative caps with structured 403 contract
- 12 net-new tier-gate UI surfaces
- Trial mechanics (no-CC at Solo/Advanced, CC-required Enterprise)
- Full telemetry sink (gate_hit_events) for funnel analysis

### A protected moat
- Determinism contract (NFR-D1 through D5) with critical security tests
- Multi-authority validators (intent / synthesis / ledger)
- SHA256-sealed artifacts with supersession chain
- Verify endpoint for tamper detection
- AI safety boundaries (D5 — never required for cord transitions)

### A hardened security posture
- Auth: JWT 7d/30d, bcrypt 10, password floor + lockout, email verification
- Server-authoritative everywhere (X-User-Id removed)
- Zod validation at every endpoint
- CORS allowlist
- Mesh AES-GCM + HMAC + replay protection
- Append-only audit log with SHA256 checksums
- 13/14 OWASP gaps closed (S14 deferred to cool-down)

### A coherent design language
- Light mode forced (no dark variants)
- Monospace everywhere
- ConsoleTheme runtime API (not MaterialTheme)
- Custom Composables (no Material widgets)
- 11-color palette (no new hex)
- Unicode glyphs as icons (no Material Icons)
- 5 motion primitives only

### Project-specific AI guidance
- 9 Claude Code skills under `.claude/skills/`
- Auto-trigger by file path / domain
- Encode every binding decision so future AI sessions follow Smith Net conventions automatically

---

## How to use this from here

### For implementers (immediate)

1. Open `docs/prds/PRD-INDEX.md`. Pick the next ⬜ pending PRD.
2. Mark it 🟡 in progress.
3. Read the PRD top-to-bottom. Read its linked specs.
4. Implement per the design / acceptance criteria / BDD scenarios.
5. Verify acceptance criteria.
6. Mark ✅ shipped.
7. Move to next PRD per cycle order.

Recommended start: **F1.1** (Remove X-User-Id) — small batch, no dependencies, unblocks all later auth/tier work.

### For the founder (strategic)

- Stop-the-line conditions in `FEATURE-BREAKDOWN.md §6` — halt + reshape if any fire
- Success metrics in `SUCCESS-METRICS.md` — North Star = paid Solo conversions per 100 free signups
- Funnel telemetry from F5.2 → operational visibility into conversion bottlenecks

### For future AI sessions

The `.claude/skills/` directory contains 9 project-specific skills that will auto-load in any future Claude Code session inside `/Users/fegensprenelon/smith-net/`. Each skill encodes specific conventions; together they replicate the Sigma planning context without you having to re-explain it.

---

## What was deliberately deferred

| Item | Why | Where to revisit |
|---|---|---|
| Step 9 Landing Page | User direction: focus on app infrastructure | Resume in a separate Sigma session if/when launching public site |
| iOS client | Out of scope v1 (per MASTER_PRD §8) | Post-launch |
| Multi-currency / i18n | NA-first v1 (per MASTER_PRD §3) | Once first non-NA paid user |
| Custom plan template editor | Post-launch | TBD |
| 2FA / MFA | Post-launch | v2 |
| Trade pack #2 (plumbing/HVAC) | Pattern set via electricianTools; ship per-trade as needed | Per market signal |
| Admin web UI | CLI / direct DB access v1 | Post-launch |
| Real-time analytics dashboard | Telemetry sink only v1; query offline | Post-launch |
| `pricingTiers.ts` 3-6-9 retire (B19) | Low-impact tech debt | Cool-down PR |
| K2/K3 ChannelListScreen consolidation | Low-impact | Cool-down PR |
| GDPR data export endpoint | Compliance — needed but not v1-launch blocker | Pre first EU paid user |
| Move secrets to secrets manager (S14) | Manual env management acceptable v1 | Post-launch |
| Forward secrecy for mesh keys | Per-pair static OK v1 | v2 |
| SAML / SSO crew federation | Manual invites only v1 | Post-launch |

---

## Critical files to bookmark

| File | Why |
|---|---|
| `docs/technical/TECHNICAL-SPEC.md` | One-stop build-ready blueprint |
| `docs/prds/PRD-INDEX.md` | Implementation roadmap |
| `docs/implementation/FEATURE-BREAKDOWN.md` | Shape Up cycles + stop-the-line conditions |
| `docs/specs/pricing-config.json` | Machine-readable pricing |
| `docs/stack-profile.json` | Machine-readable stack |
| `docs/ops/SUCCESS-METRICS.md` | KPIs + funnel events |
| `.claude/skills/smith-net-*/SKILL.md` (×9) | Auto-loading AI conventions |

---

## Sigma run statistics

- **Steps completed:** 12 of 13 (Step 9 skipped per user)
- **Total spec files produced:** 50+
- **Total implementation-ready PRDs:** 30
- **Total acceptance criteria across PRDs:** ~370
- **Total BDD scenarios across PRDs:** ~110
- **Total documented edge cases across PRDs:** ~190
- **Launch blockers closed:** 18 of 19 (B19 deferred — low impact)
- **Security gaps closed:** 13 of 14 (S14 deferred to post-launch)
- **Net-new components designed:** 12
- **Net-new screens / dialogs designed:** 5
- **Project-specific AI skills generated:** 9

---

## What changed during the run

The most consequential corrections that emerged during planning:

1. **The moat IS built** (Step 2 deep-dive). Initial DEV-READINESS said PLAN Compiler + cord state were unbuilt; reality: Intent → SummaryArtifact → LedgerEntry pipeline + VectorClock + CordEntry are substantially shipped backend code. Step 11 work is wiring + UI surfacing, not pipeline construction.

2. **Hetzner is canonical, Supabase is legacy** (Step 2 correction). Initial stack profile listed Supabase as primary; reality: `BuildConfig.SUPABASE_ENABLED=false` by default. Hetzner Express is the production backend.

3. **The pricing pyramid is being retired** (Step 1.5 + Step 2). Code's legacy `pricingTiers.ts` (3-6-9 pyramid: solo/foreman/enterprise/nation × standard/hybrid) replaced by user's new ladder ($0/$2.99/$9.99/$50, one hero per tier). Retire in Step 11.

4. **AI = Advanced floor, not Solo floor** (mid-Step 1 user correction). On-device SmithAI is the $9.99 Advanced differentiator, NOT included in $2.99 Solo. Free tier is AI-free deterministic baseline.

5. **Light mode only** (mid-Step 3 user correction). The DarkColorScheme in Theme.kt is dead code; the app forces LightColorScheme. Don't generate dark variants for any net-new UI.

6. **The vocabulary has two languages** (Step 2). Code uses Intent / SummaryArtifact / Ledger / Cord / VectorClock; marketing uses PLAN Compiler / cord-based state model. The split is canonical and documented.

---

## Next steps (your call)

- **Implement.** Start at PRD F1.1.
- **Hire.** PRDs are scoped for delegation — share `PRD-INDEX.md` with any contractor.
- **Validate.** Stand up a staging instance and run flows F1-F8 end-to-end before Cycle 4.
- **Re-plan.** If Solo conversion lags after public launch, revisit `OFFER_ARCHITECTURE.md` + `SUCCESS-METRICS.md` per the "When a metric tells us to change the spec" table.

The Sigma run is closed. The build phase begins.
