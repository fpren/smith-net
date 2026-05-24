# FLOW-5 — Online ↔ offline sync (mesh resilience)

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 5
**Mostly engineering; thin UX surface — only the connection-status pill changes visibly.**

---

## Scope

Two devices on the same channel exchange messages while one is offline (mesh-only) and the other is online (Hetzner WS). When the offline device regains connectivity, `BoundaryEngine` triggers `ReconciliationEngine` which reconciles via vector-clock merge. **No last-write-wins; concurrent events ordered deterministically.**

Tier impact: **none** — connectivity resilience is a Free-tier baseline feature.

## UX surfaces (minimal)

| Surface | Behavior |
|---|---|
| C1 Dashboard connection-status pill | Cycles: `MESH 3` (3 peers in range) → `OFFLINE` → `ONLINE` (when Hetzner reachable) → `GATEWAY` (when relayed) |
| K1 ChatListScreen header pill | Same indicator as C1 |
| K4 ConversationScreen per-message origin badge | `online` / `mesh` / `gateway` / `online+mesh` (already shipped — `MessageOrigin` enum) |
| K4 sync-in-progress indicator | Small text below header during reconciliation: `· · · syncing` (max 5s) |

**Existing design:** the connection pills are already shipped (commits `5119abd`, `4ca62f8`, `9c3e1ec`). No net-new UI. This PRD is mostly engineering correctness.

## Engineering contract

### Client (Android)

| Module | Responsibility |
|---|---|
| `BoundaryEngine` | Connectivity monitoring; chooses transport per message (mesh / online / gateway); fires reconciliation on connectivity restore |
| `MeshService` | BLE + WiFi-Direct transport; encrypted payload (AES-GCM — verify per SECURITY S3) |
| `ChatManager` | WS connection to `BACKEND_URL_PRIMARY` |
| `ReconciliationEngine` | Periodic + on-restore reconcile via /api/reconcile; vector-clock merge |
| `MessageBusRepository` | Local SQLite store of `UnifiedMessage` with `vectorClock`, `transportType` |
| `VectorClock` (data layer) | merge / compare / serialize utility |

### Server

| Endpoint | Behavior |
|---|---|
| POST /api/reconcile | Body: `{channelId, localMessageIds[], localClock}`. Returns: `{missingOnClient[], missingOnServer[], mergedClock}` |
| POST /api/reconcile/upload | Body: `{messages: UnifiedMessage[]}`. Inserts with `ON CONFLICT (id) DO UPDATE`. |
| WS `message` event | Real-time broadcast of new messages to subscribed clients. |
| WS `message_ack` | Per-message ack from server confirming persistence. |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Two devices online: message sent on Device A appears on Device B in < 1s (NFR-P7) | E2E test |
| AC-2 | Device B offline + same job site: Device A's message reaches B via mesh in < 10s (NFR-O2) | E2E with mesh enabled |
| AC-3 | Both offline (mesh only): messages exchanged P2P; vector clocks track ordering | E2E |
| AC-4 | Device B regains connectivity after offline period: `ReconciliationEngine.reconcile` fires within 5s (NFR-O4) | E2E |
| AC-5 | Reconciliation completes: `missingOnServer` uploaded; `missingOnClient` downloaded; both sides converge to same `mergedClock` | API + state assertion |
| AC-6 | Concurrent events on both devices: vector clocks return `0` (concurrent); both kept; ordered by `(timestamp, id)` deterministically across all clients | Determinism test |
| AC-7 | Ephemeral channels (KEEP_HISTORY=false): mesh-routed messages NEVER persist server-side | Server DB inspection — should be empty for ephemeral channel rows |
| AC-8 | Mesh service self-recovers from `ADVERTISE_FAILED_ALREADY_STARTED` without user action (already shipped, do not regress) | Regression test |
| AC-9 | Connection pill on C1 / K1 reflects current state within 1s of state change | UI test |
| AC-10 | Reconciliation idempotent: re-running reconcile with no new messages produces no-op response | API test |
| AC-11 | Mesh payload encrypted (AES-GCM) — security gap S3/G8 must be verified or fixed | Security audit |

## BDD scenarios

```gherkin
Feature: Online ↔ offline sync via mesh + reconciliation

Scenario: Two online devices exchange messages
  Given Device A and Device B are both online and subscribed to channel #general
  When Device A sends "hello"
  Then Device B receives "hello" within 1 second
  And the message has origin="online"

Scenario: Device offline gets messages via mesh
  Given Device A is online and Device B is offline (in BLE range)
  When Device A sends "test message"
  Then Device A's BoundaryEngine relays via mesh
  And Device B receives "test message" with origin="mesh" within 10 seconds

Scenario: Device reconciles after going back online
  Given Device A and Device B are both offline (mesh-connected)
  When Device A sends "M1" (vector_clock {A:1})
  And Device B sends "M2" (vector_clock {B:1})
  And both messages stored locally with transport_type=MESH
  And Device B regains internet
  Then ReconciliationEngine fires within 5 seconds
  And POST /api/reconcile returns {missingOnClient:[M1?,...], missingOnServer:[M2,...], mergedClock:{A:1,B:1}}
  And Device B uploads M2 via /api/reconcile/upload
  And the server inserts M2 with ON CONFLICT DO UPDATE
  And Device B receives any prior server-side messages

Scenario: Concurrent events ordered deterministically
  Given Device A sends M_X with vector_clock {A:5}
  And Device B sends M_Y with vector_clock {B:3}
  And clocks compare returns 0 (concurrent)
  When all clients render the channel after sync
  Then M_X and M_Y appear in the same order on every client (sorted by timestamp, then id)

Scenario: Ephemeral channel does not persist server-side
  Given a channel with persistence=EPHEMERAL
  When Device A sends "ephemeral msg" via mesh
  Then the message reaches Device B
  And the server database has NO row in messages or message_bus_messages for that message
  And no audit-log entry for persistence
```

## Edge cases

| Case | Behavior |
|---|---|
| Device joins mesh but BLE pairing fails | Existing self-recover from ADVERTISE_FAILED_ALREADY_STARTED handles; backoff retry |
| Server unreachable during reconcile attempt | retry with exponential backoff (max 5 attempts, then quiet until next connectivity change) |
| Conflicting same-id message on both sides (rare) | server keeps the one with higher vector clock; client mirrors |
| Vector clock corrupted (parse failure) | server treats as if clock is `{}`; reconcile sends ALL server messages as `missingOnClient` (safe overcorrection) |
| Channel deleted server-side while device offline | reconcile returns 404 for that channel; client soft-deletes locally |
| User on multiple devices (same account) | each device maintains its own `deviceId` in vector clock; merging works across all devices owned by user |

## Performance & non-functional requirements

| NFR | Target | Verification |
|---|---|---|
| NFR-O1 | Core flows work fully offline | E2E with airplane mode |
| NFR-O2 | Mesh peer connect within 10s | Pairing benchmark |
| NFR-O3 | Ephemeral channels don't persist | Server DB inspection |
| NFR-O4 | Auto-resume sync within 5s of reconnection | E2E |
| NFR-O5 | Conflicts resolve via cord/vector replay (not last-write-wins) | Concurrency test |
| NFR-O6 | Self-recover from ADVERTISE_FAILED | Regression |
| NFR-P7 | Realtime broadcast latency < 1s | Network test |
| NFR-B1 | Mesh background battery < 5%/hr on Pixel 6 | Telemetry over 24h |
| NFR-S3 | Mesh payload encrypted | Security audit |

## Non-goals

- Voice/video over mesh (text + small media only)
- Relay-of-relay (gateway-of-gateway) — single-hop relay only for v1
- Cross-channel reconciliation (per-channel only)
- iOS-side mesh (Android only — iOS has no equivalent BLE-mesh API surface in v1)

## Linked specs

- `ARCHITECTURE.md §6` (multi-transport routing diagram)
- `FLOW-DIAGRAMS.md` Flow 5
- `NFRS.md §2` (offline & connectivity), `§4` (battery), `§5` (security mesh)
- `SECURITY.md §11` (mesh & gateway security)
- Existing code: `android/.../engine/BoundaryEngine.kt`, `service/MeshService.kt`, `service/ReconciliationEngine.kt`, `data/VectorClock.kt`, `backend/src/reconciliationEngine.ts`, `vectorClock.ts`
