# Smith Net — PRD Index

**Sigma step:** 11 — PRD Generation
**Source:** `FEATURE-BREAKDOWN.md` (12 pitches → ~30 PRDs)
**Format:** every PRD is a vertical slice (DB + service + UI + tests + BDD) sized for one implementer ~3-10 days.

**Status legend:** ⬜ pending | 🟡 in progress | ✅ shipped

---

## Cycle 1 — Substrate (auth + tier resolver)

| ID | PRD | Pitch | Status | File |
|---|---|---|---|---|
| F1.1 | Remove X-User-Id "simplified auth" header | 1 | ⬜ | [F1.1-remove-x-user-id.md](F1.1-remove-x-user-id.md) |
| F1.2 | JWT secret production hardening + CORS lock | 1 | ⬜ | [F1.2-jwt-secret-cors.md](F1.2-jwt-secret-cors.md) |
| F1.3 | Password floor + per-account lockout | 1 | ⬜ | [F1.3-password-lockout.md](F1.3-password-lockout.md) |
| F1.4 | Email verification on signup | 1 | ⬜ | [F1.4-email-verification.md](F1.4-email-verification.md) |
| F1.5 | Zod schema validation at every endpoint | 1 | ⬜ | [F1.5-zod-validation.md](F1.5-zod-validation.md) |
| F2.1 | Tier columns + subscriptions table migrations (M1, M2) | 2 | ⬜ | [F2.1-tier-schema-migrations.md](F2.1-tier-schema-migrations.md) |
| F2.2 | TierResolver service + entitlements endpoint + JWT roll | 2 | ⬜ | [F2.2-tier-resolver-service.md](F2.2-tier-resolver-service.md) |

## Cycle 2 — Revenue plumbing (billing + caps + telemetry)

| ID | PRD | Pitch | Status |
|---|---|---|---|
| F5.1 | Founder seats reservation service (atomic) | 5 | ⬜ |
| F5.2 | Telemetry sink (gate_hit_events table + endpoint) | 5 | ⬜ |
| F3.1 | Stripe Checkout + webhook handlers | 3 | ⬜ |
| F3.2 | Play Billing client + server verification | 3 | ⬜ |
| F3.3 | Subscription state reconciler + provider sync cron | 3 | ⬜ |
| F6.1 | Server enforcement middleware + tier_gate_exceeded contract | 6 | ⬜ |

## Cycle 3 — UX (tier-gate UI + trial + branding)

| ID | PRD | Pitch | Status |
|---|---|---|---|
| F4.1 | LockedFeatureOverlay component + 4 content variants | 4 | ⬜ |
| F4.2 | TrialBanner + FounderSeatsCounter + GateHitToast | 4 | ⬜ |
| F4.3 | TierPricingScreen (N7) | 4 | ⬜ |
| F4.4 | SubscriptionDetailScreen + Cancel / Delete dialogs (N8) | 4 | ⬜ |
| F4.5 | EntitlementLock + PdfSendCounterFooter + WelcomeToOpenScreen | 4 | ⬜ |
| F4.6 | Wire net-new UI into existing screens (Q2, D3, H1, G4, E1, C1, MainActivity) | 4 | ⬜ |
| F7.1 | Trial mechanics: start-trial endpoint + cron + downgrade | 7 | ⬜ |
| F8.1 | Branded PDF stamp (template edits) | 8 | ⬜ |
| F8.2 | PDF send queue + month-end cron | 8 | ⬜ |

## Cycle 4 — Polish + security

| ID | PRD | Pitch | Status |
|---|---|---|---|
| F9.1 | Advanced invoice template | 9 | ⬜ |
| F9.2 | Enterprise invoice template (multi-payer + milestone) | 9 | ⬜ |
| F12.1 | Mesh encryption audit + signing + replay protection | 12 | ⬜ |
| F11.1 | Audit log to DB + retention enforcement cron | 11 | ⬜ |

## Cycle 5 — Enterprise

| ID | PRD | Pitch | Status |
|---|---|---|---|
| F10.1 | Organizations + crew membership schema | 10 | ⬜ |
| F10.2 | Crew invite + accept flow | 10 | ⬜ |
| F10.3 | Shared jobs across crew (org_id on jobs) | 10 | ⬜ |
| F10.4 | DispatchScreen Enterprise-tier surfacing | 10 | ⬜ |

## Total: 30 PRDs

| Cycle | PRDs | Estimated wall time at 1 engineer | At 2 engineers |
|---|---|---|---|
| 1 | 7 | 6 weeks | 4 weeks |
| 2 | 6 | 6 weeks | 4 weeks |
| 3 | 9 | 8 weeks | 5 weeks |
| 4 | 4 | 4 weeks | 3 weeks |
| 5 | 4 | 6 weeks | 4 weeks |
| **Total** | **30** | **~30 weeks** | **~20 weeks** |

---

## How to use this index

1. Step 11 generates each PRD's full content in its own file.
2. Implementer picks up the next available PRD with status `⬜`, marks `🟡`, ships, marks `✅`.
3. Cross-cycle dependencies are documented per PRD; honor the cycle order.
4. PRDs may be parallelized within a cycle if no shared file conflicts.

## PRD authoring status

- **All 30 PRDs across 5 cycles: ✅ COMPLETE.**
- Status legend updates to be applied as implementer picks up each PRD: ⬜ → 🟡 → ✅
