# ADR-0001: Phase-0 routes feature-flagged off

**Date:** 2026-05-16
**Status:** Accepted
**Phase:** 4 / Slice 4

## Context

The backend ships seven endpoints implementing the Intent → SummaryArtifact → Ledger pipeline plus a small-project shortcut:

- `POST /api/intent-authority/validate-creation`
- `POST /api/intents` (+ `:intentId/versions/...`)
- `POST /api/synthesis-authority/validate-inputs`
- `POST /api/synthesize`
- `GET /api/artifacts/:id`
- `POST /api/ledger/seal` (+ `/amend`, `GET /:id`)
- `POST /api/small-project/synthesize-and-generate-intent` (+ `/confirm-and-seal`)

These routes back the **Core Flow Redesign** (memory `project_core_flow_redesign.md`, 2026-03-20) that decomposed the overloaded Plan container into three independent containers.

The 2026-05-13 architecture audit (`docs/smith-net-architecture-audit.md`) named these "Dead Phase-0 routes" as weak point #3: implementation complete, zero UI callers, zero Android callers. The 7-phase roadmap (`docs/smith-net-implementation-roadmap.md` line 143) flagged Phase 4 as the decision point: keep warm if Phase 5 SmithAI will wire writes through this pipeline, feature-flag off otherwise.

After review: **SmithAI v1 is add-only writes** (per `project_smithai_tier_scope.md` memory) and the SmithAI Android scaffolding does not currently target the Intent/Synthesizer/Ledger contract. There is no concrete plan for Phase 5 to wire these routes; the audit was right to call them dead.

## Decision

**Feature-flag off.** `phase0Router` is mounted at runtime only when `PHASE_0_ENABLED=true` in the environment. Default is unset (off). Behavior with the flag off: every Phase-0 route returns 404 from Express's default not-found handler.

The implementation is preserved on disk:
- `backend/src/phase0Routes.ts` — the router (~160 LOC)
- `backend/src/intentService.ts`, `intentAuthority.ts`
- `backend/src/synthesizer.ts`, `synthesisAuthority.ts`
- `backend/src/ledger.ts`

This keeps the design work recoverable. Reviving means flipping the flag in `.env` and writing a caller; no implementation rebuild required.

## Consequences

**Positive:**
- Closes audit weak point #3 (dead Phase-0 routes).
- The TODO surface in the production backend shrinks: 7 endpoints stop being reachable by any client, including curl spelunkers and security scanners.
- The Intent/Synthesizer/Ledger contract stops being a maintenance target. No tests need to keep passing for these routes; no API stability promises apply.

**Negative:**
- If a future caller materializes, the flag has to flip. One-line change but does require an env edit + restart.
- The router code can drift from the rest of the backend. We accept this — the cost of reviving stale code is bounded (refactor, type-check, write new tests), and far smaller than keeping all of it warm against a future that may never come.

**Mitigations:**
- The implementation stays in the repo (not deleted). Reviving == one flag flip + writing the caller.
- The roadmap warned "let's keep it warm" is the trap to avoid (line 150). Following that warning.
- The Core Flow Redesign design doc (`docs/superpowers/specs/2026-03-20-core-flow-redesign-design.md`) is preserved as the recovery reference.

## Reversal

Set `PHASE_0_ENABLED=true` in `backend/.env` and restart. No code changes needed.

## References

- `docs/smith-net-architecture-audit.md` § Weak Point #3
- `docs/smith-net-implementation-roadmap.md` § Phase 4 line 143
- `docs/superpowers/specs/2026-03-20-core-flow-redesign-design.md` (the design being shelved)
- Memory `project_core_flow_redesign.md` (the decision being superseded)
- Memory `project_smithai_tier_scope.md` (the reason this pipeline has no caller in v1)
