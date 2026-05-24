# Smith Net — Flow Diagrams

Text-based diagrams of the **8 critical flows**. These are the ones most likely to break or degrade. Each diagram shows screens, actions, server calls, telemetry events, and tier-gate fires.

---

## Flow 1 — First-time install → first invoice sent (Open tier)

```
Play Store install
     │
     ▼
A1 AuthScreen ──[register]──▶ POST /api/auth/register
     │                              │
     ▼                              ▼
A3 WelcomeScreen ◀────────────  201 + tokens
     │
     ▼
A2 OnboardingScreen
   (name, role=solo, trade)
     │
     ▼
A4 WelcomeToOpenScreen [NET-NEW]
     │
     ├──[Stay on Open]──▶ C1 Dashboard
     │                         │
     │                         ▼
     │                    D3 NewJobFlow ──[Save]──▶ POST /api/jobs
     │                         │                         │
     │                         ▼                         ▼
     │                    D2 JobPipelineScreen ◀──── 201 Job
     │                         │
     │                         ├── Add Time → F1 → POST /api/time-entries
     │                         ├── Add Materials → in-place
     │                         └── [Generate Invoice] → H1 InvoiceScreen
     │                                                       │
     │                                                       ▼
     │                                                  template = Standard
     │                                                       │
     │                                                       ▼
     │                                                  G4 InvoicePreviewBottomSheet
     │                                                       │
     │                                                       ▼
     │                                                  [Send] → POST /api/invoices/:id/send
     │                                                       │
     │                                                       ▼
     │                                                  Server renders PDF with N9 stamp
     │                                                  + emails customer
     │                                                       │
     │                                                       ▼
     │                                                  Toast: "Invoice sent · 1 of 5
     │                                                          free PDFs used"
     │                                                       │
     │                                                       ▼
     │                                                  Telemetry: pdf_sent (Open)
     │
     └──[Start Solo Trial]──▶ N7 (trial flow) → C1 Dashboard
                                    │
                                    ▼
                              POST /api/me/start-trial { targetTier: 'solo' }
                                    │
                                    ▼
                              Founder seat reserved (10 min hold)
                                    │
                                    ▼
                              N1 Trial Banner appears globally
                                    │
                                    ▼
                              C1 Dashboard with Solo entitlements
```

**Success criterion (L5):** time from install → invoice sent < 10 min for median user.

---

## Flow 2 — Free user hits active-job cap → converts to Solo trial

```
C1 Dashboard
     │
     ▼
[+ NEW JOB tap]
     │
     ▼
D3 NewJobFlow (fills title, client, trade)
     │
     ▼
[Save tap] ──▶ POST /api/jobs
                    │
                    ▼
                Server checks: tier=open AND active_jobs ≥ 1
                    │
                    ▼
                403 { error: 'tier_gate_exceeded',
                      gate_id: 'active_job_cap',
                      current_tier: 'open',
                      limit: 1, current: 1 }
                    │
                    ▼
                Telemetry: gate_hit.active_job_cap
                    │
                    ▼
⤴ N4 Active-job cap overlay
     │
     ├──[Try Solo Free — No CC]──▶ POST /api/me/start-trial
     │                                   │
     │                                   ▼
     │                              Founder seat reserved
     │                                   │
     │                                   ▼
     │                              JWT re-issued with tier=solo
     │                                   │
     │                                   ▼
     │                              ⤴ N1 Trial Banner appears
     │                                   │
     │                                   ▼
     │                              ↩ Retry POST /api/jobs ▶ 201 Job
     │                                   │
     │                                   ▼
     │                              D2 JobPipelineScreen of new job
     │                                   │
     │                                   ▼
     │                              Telemetry: tier_upgrade.trial_started
     │                                  { from_tier=open, to_tier=solo,
     │                                    trigger_event=gate_hit.active_job_cap }
     │
     └──[See active job]──▶ D2 of existing active job
                                 │
                                 ▼
                            Telemetry: tier_upgrade.cta_dismissed
                                  { gate=active_job_cap }
```

**Success criteria:** ≥ 50% of `gate_hit.active_job_cap` events convert to `trial_started` (per SUCCESS-METRICS.md per-step funnel target).

---

## Flow 3 — Solo user opens AI tab → Advanced trial

```
Q2 SettingsScreen (Solo user)
     │
     ▼
Scroll to AI ASSISTANT section
     │
     ▼
N10 lock-state row visible:
   "● Locked — Advanced tier
    Tap to learn what SmithAI does"
     │
     ▼
[tap row]
     │
     ▼
Telemetry: gate_hit.ai_tab
     │
     ▼
⤴ N10 Locked-feature overlay
   ┌──────────────────────────┐
   │ SMITHAI                  │
   │ On-device. No cloud.     │
   │ ADVANCED · $9.99/MO      │
   │ ● 47 OF 100 LIFETIME     │
   │   SPOTS LEFT             │
   │ [TRY ADVANCED FREE 30D]  │
   │ Maybe later              │
   └──────────────────────────┘
     │
     ├──[Try Advanced Free 30D]──▶ POST /api/me/start-trial
     │                                  { targetTier: 'advanced' }
     │                                       │
     │                                       ▼
     │                                  Lifetime-template seat reserved
     │                                       │
     │                                       ▼
     │                                  JWT re-issued with tier=advanced
     │                                       │
     │                                       ▼
     │                                  ⤴ N1 Trial Banner updates
     │                                       │
     │                                       ▼
     │                                  Q2 AI ASSISTANT section transforms:
     │                                  N10 lock removed → existing UI shown
     │                                       │
     │                                       ▼
     │                                  [Load Model] action available
     │                                       │
     │                                       ▼
     │                                  ModelDownloader.startDownload (~2GB)
     │                                       │
     │                                       ▼
     │                                  AgentInitializer.wakeAgent()
     │                                       │
     │                                  SLEEPING → WAKING (0%-100%) → ALIVE
     │                                       │
     │                                       ▼
     │                                  Telemetry: tier_upgrade.trial_started
     │                                       │
     │                                       ▼
     │                                  Day 30: trial converts or expires
     │                                       │
     │                                       ▼
     │                                  if no CC: ⤴ N1 banner: "ADVANCED
     │                                  TRIAL ENDED · YOU'RE BACK ON SOLO"
     │                                  Model file kept on device, unloaded
     │
     └──[Maybe later]──▶ ↩ Q2 SettingsScreen
                              │
                              ▼
                         Telemetry: cta_dismissed
```

**Failure mode:** model load fails (low-spec device) → `RULE_BASED_FALLBACK` activates → Q2 shows `MODEL UNSUPPORTED ON THIS DEVICE — DOWNGRADE TO SOLO?` + no-fee downgrade CTA.

---

## Flow 4 — Solo+ user composes a Plan and seals it

```
E1 PlanScreen (Solo+ user)
     │
     ▼
[+ New Engagement]
     │
     ▼
POST /api/engagements { name, description, intent }
     │
     ▼
Engagement created (status: active)
     │
     ▼
[Convert to Intent]
     │
     ▼
POST /api/engagements/:id/convert
     │
     ▼
{ engagement, intent, intentVersion v1 (status: draft) }
     │
     ▼
Edit IntentVersion v1 (scope_statement, parties, intended_job_ids)
     │
     ▼
[Propose] ──▶ POST /api/intents/:versionId/propose
                    │
                    ▼
            intentAuthority.validateIntentCreation: OK
                    │
                    ▼
            status → proposed
     │
     ▼
[Confirm] (must be a party) ──▶ POST /api/intents/:versionId/confirm
                                       │
                                       ▼
                               intentAuthority.validateIntentConfirmation: OK
                                       │
                                       ▼
                               status → confirmed, confirmed_by, confirmed_at
     │
     ▼
   (work happens; jobs close; time entries close; messages logged)
     │
     ▼
[Synthesize] ──▶ POST /api/synthesize
                       { intentVersionId, jobIds[], timeEntryIds[], chatMessageIds[] }
                              │
                              ▼
                      synthesisAuthority.validateSynthesisInputs:
                      - Intent confirmed ✓
                      - ≥ 1 closed Job ✓
                      - ≥ 1 closed TimeEntry ✓
                              │
                              ▼
                      Pulls work_performed / labor_recorded /
                      materials_used / contextual_notes
                              │
                              ▼
                      Computes total_hours, total_cost
                              │
                              ▼
                      Assigns next serial (artifact_serial_sequence)
                              │
                              ▼
                      INSERT summary_artifacts
                              │
                              ▼
                      201 SummaryArtifact
     │
     ▼
[Seal in Ledger] ──▶ POST /api/ledger/seal
                          { artifactId, actorUuid }
                                │
                                ▼
                        ledgerAuthority.validateSealing:
                        - artifact valid ✓
                        - not already sealed ✓
                                │
                                ▼
                        computeHash(artifact) → sha256_hash
                                │
                                ▼
                        INSERT ledger_entries
                                │
                                ▼
                        201 LedgerEntry
     │
     ▼
Outputs:
  ├── [Generate Invoice] → H1 InvoiceScreen pre-populated from artifact
  ├── [Generate Report] → J1 ReportScreen with narrative
  └── [Copy public link] → /p/:uuid (proposal) or /i/:uuid (invoice)
```

**Determinism guarantee:** re-running `synthesize` with the same `(intentVersionId, jobIds, timeEntryIds)` produces a SummaryArtifact with the same content → same `computeHash` → same `sha256_hash`. `/api/ledger/verify/:entryId` confirms.

---

## Flow 5 — Online ↔ offline sync (BoundaryEngine + ReconciliationEngine)

```
Two devices, same channel
     │
     ▼
DEVICE A (online via Hetzner WS)        DEVICE B (offline → mesh only)
     │                                       │
     │ User sends message M1                 │ User sends message M2
     │ via ChatManager → WS                  │ via MeshService → BLE
     │                                       │
     ▼                                       ▼
Backend: insert message_bus_messages       MeshService stores locally
   transport_type: ONLINE                   transport_type: MESH
   vector_clock: { A: 5 }                   vector_clock: { B: 3 }
     │
     ▼
WS broadcast: M1 to all connected clients
     │
     │  (Device B not connected to backend)
     │
     ▼
Other devices update; B doesn't
     │
     │
     ▼
DEVICE B regains connectivity
     │
     ▼
ConnectivityManager fires NetworkAvailable
     │
     ▼
BoundaryEngine.onConnectivityRestored
     │
     ▼
ReconciliationEngine.reconcile(channelId)
     │
     ▼
POST /api/reconcile {
  channelId,
  localMessageIds: [M2, ...],
  localClock: { B: 3, A: 4 (last known) }
}
     │
     ▼
Server compares with message_bus_messages for channel
     │
     ▼
Returns:
  missingOnClient: [M1, ...]   (server has, client doesn't)
  missingOnServer: [M2]        (client has, server doesn't)
  mergedClock: { A: 5, B: 3 }
     │
     ▼
Client uploads missingOnServer:
POST /api/reconcile/upload { messages: [M2] }
     │
     ▼
Server: INSERT ... ON CONFLICT (id) DO UPDATE
     │
     ▼
Client merges missingOnClient into local store
   ordered by vectorClock.compare; ties by (timestamp, id)
     │
     ▼
Both sides now share mergedClock; conversation is consistent
     │
     ▼
WS event: message_ack for M2 (so other clients receive it via push)
```

**Conflict resolution:** vector clocks tell us if events are concurrent (`compare()` returns 0). Concurrent events keep both, ordered by `(timestamp, id)` deterministically. **No last-write-wins.** `KEEP_HISTORY=false` channels: M1 and M2 still reconcile, but messages are never persisted server-side beyond the broadcast — the reconciler treats those channels as ephemeral.

---

## Flow 6 — Trial expiration → downgrade (Solo no CC)

```
Day 14 of Solo trial
     │
     ▼
Cron job (server) checks expiring trials
     │
     ▼
For users with trial_ends_at = today AND no payment_method:
     │
     ▼
UPDATE subscriptions SET status='expired'
UPDATE profiles SET tier='open'
     │
     ▼
Push notification (Android FCM): "Solo trial ended.
You're back on Open. Reactivate any time."
     │
     ▼
Server returns 401 with `tier_changed` flag on next request
     │
     ▼
Client detects, refreshes JWT (POST /api/auth/refresh)
     │
     ▼
New JWT carries tier=open
     │
     ▼
Client UI re-renders:
  - N1 Trial Banner: "TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE"
  - E1 PlanScreen reverts to N3 lock overlay
  - D3 NewJobFlow reactivates active-job cap
  - H1 InvoiceScreen reactivates PDF cap
  - Q2 Settings SUBSCRIPTION row shows "Open · $0/mo"
     │
     ▼
USER DATA PRESERVED:
  - All jobs visible (only 1 "active" rule reapplies)
  - All sealed Ledger entries still readable
  - Plans created during trial: still readable, can't compile new ones
  - Sent invoices: history visible; new sends throttled to 5/mo
  - SmithAI never enabled (Solo never had it)
     │
     ▼
Telemetry: tier_upgrade.trial_expired
     │
     ▼
Email cohort follow-up (day 1 post-expiry):
  "Your Solo trial ended. 740 of 1000 founder spots
   are still open if you reactivate."
```

**Re-conversion path:** any tier-gate hit (active job cap, PDF send cap, PLAN compile attempt) fires a normal upgrade overlay. Founder pricing is still available if seats remain. No special "we miss you" UX.

---

## Flow 7 — Subscription cancellation (Solo paid)

```
Q2 SettingsScreen → SUBSCRIPTION row tap
     │
     ▼
N8 Subscription Detail Screen
     │
     ▼
[Cancel subscription tap]
     │
     ▼
Custom Composable confirmation dialog (NOT Material AlertDialog):
   ┌──────────────────────────────────┐
   │ CANCEL SUBSCRIPTION?             │
   │                                  │
   │ Your Solo features stay until    │
   │ end of current period (May 30).  │
   │                                  │
   │ After that you'll be on Open.    │
   │ Your data stays.                 │
   │                                  │
   │ [KEEP SOLO]  [Cancel anyway]     │
   └──────────────────────────────────┘
     │
     ├──[KEEP SOLO]──▶ ↩ N8 (no change)
     │
     └──[Cancel anyway]──▶ POST /api/me/cancel
                                │
                                ▼
                           Stripe / Play Billing API:
                           cancel at period end
                                │
                                ▼
                           UPDATE subscriptions SET status='canceled'
                           subscriptions.current_period_end remains
                                │
                                ▼
                           profile.tier remains 'solo' until period end
                                │
                                ▼
                           N8 re-renders:
                             - tier row: "Smith Net Solo (canceling May 30)"
                             - "Next bill" row: "none — cancels at period end"
                             - new row: "> Reactivate subscription"
                                │
                                ▼
                           Toast: "Solo cancels May 30. You can reactivate
                                   any time."
                                │
                                ▼
                           Telemetry: tier_downgrade.canceled
                             { from_tier=solo, to_tier=open,
                               days_active=90, ltv_usd=8.97 }
                                │
                                ▼
                           [if user taps Reactivate before period end]
                                ▼
                           POST /api/me/reactivate
                                ▼
                           Stripe / Play Billing: undo cancel
                                ▼
                           subscriptions.status='active'
                                ▼
                           Toast: "Solo reactivated. Next bill May 30."
```

**Period-end behavior:** when `current_period_end` arrives, cron transitions tier (same as Flow 6 trial expiration); FCM push, JWT refresh, UI re-renders. Re-conversion uses founder seat IF the original founder lock flag was set.

---

## Flow 8 — Public-facing invoice page view (no auth)

```
Customer receives email with link to /i/:uuid
     │
     ▼
Browser opens https://app.smithnet.app/i/abc123def...
     │
     ▼
Express handler: GET /i/:uuid
     │
     ▼
invoiceLinkService.getByUuid(uuid)
     │
     ├── not found ──▶ 404 "<h2>Invoice not found</h2>"
     │
     └── found ──▶ Render templates/invoice.html with values
                       │
                       ▼
                   If invoice owner is Open tier:
                   inject N9 stamp footer
                       │
                       ▼
                   Mark viewed_at = NOW() if first view
                       │
                       ▼
                   200 HTML
                       │
                       ▼
                   Customer sees rendered invoice
                       │
                       ▼
                   Per-UUID rate limit (60 views/min) prevents enumeration
                       │
                       ▼
                   Telemetry on contractor side:
                     "Invoice viewed by client" notification
                       │
                       ▼
                   In-app: invoice status updates `unpaid` → `viewed`
                   (next time contractor opens H1 / invoice list)
```

**Security:** UUID is the access control (122-bit entropy). Per-UUID rate limit prevents brute-force enumeration. No PII in URL — just the UUID.

---

## Flow coverage check

| Flow | Documented | Critical-path? | NFR cross-refs |
|---|---|---|---|
| 1 — First install → first invoice | ✅ | yes (L1, L5) | NFR-P1, NFR-P3 |
| 2 — Cap-hit → trial conversion | ✅ | yes (L2, conversion math) | NFR-OB3 |
| 3 — AI tab → Advanced | ✅ | yes (L3) | NFR-P4, NFR-P5, NFR-B2 |
| 4 — Plan compose → seal | ✅ | yes (the moat) | NFR-D1, NFR-D2, NFR-D3, NFR-D4, NFR-D5 |
| 5 — Online ↔ offline sync | ✅ | yes (mesh moat) | NFR-O1, NFR-O4, NFR-O5 |
| 6 — Trial expiry → downgrade | ✅ | yes | (none — admin) |
| 7 — Cancellation | ✅ | yes | NFR-CL1 (data preservation) |
| 8 — Public invoice page | ✅ | yes | NFR-S1, NFR-SC3 |

**Not (yet) diagrammed but not critical:**
- Mesh BLE/WiFi-Direct pairing handshake (engineering detail; in MeshService.kt)
- BootReceiver auto-start of services
- LocationService geofence triggers
- AmbientObserver / CueDetector for AI proactive suggestions
- ExpenseCsvImport flow
- Notification permission grant flow

These are sub-flows of larger flows above; will be detailed in Step 5 (Wireframe Prototypes) per-flow PRDs if they need explicit UX work.
