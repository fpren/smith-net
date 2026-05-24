# Smith Net — Non-Functional Requirements

## 1. Determinism (the moat — must not regress)

| Req | Standard |
|---|---|
| **NFR-D1** | A compiled plan must produce identical execution traces given identical inputs across runs and devices. |
| **NFR-D2** | Cord transitions must be append-only (no mutation of past cord state). |
| **NFR-D3** | The PLAN Compiler must produce the same artifact byte-for-byte given the same source plan + same compiler version. |
| **NFR-D4** | A plan compiled at version V must run on any client that supports compiler version ≥ V (forward-compat for runtime; back-compat for source). |
| **NFR-D5** | AI features (SmithAI) must NEVER be required to advance a cord transition. AI is observation/suggestion only. |

## 2. Offline & Connectivity

| Req | Standard |
|---|---|
| **NFR-O1** | Core flows (create job, capture time, draft invoice, advance cord state) must work fully offline on Android. |
| **NFR-O2** | Mesh transport (Bluetooth + Wi-Fi-Direct) must establish a peer connection within 10s when peers are in range. |
| **NFR-O3** | Mesh-routed messages on ephemeral channels must NOT persist to cloud. |
| **NFR-O4** | Cloud sync (Supabase Realtime) must auto-resume within 5s of regaining connectivity. |
| **NFR-O5** | Conflicting edits made offline must resolve via cord-state replay, not last-write-wins. |
| **NFR-O6** | Mesh service must self-recover from `ADVERTISE_FAILED_ALREADY_STARTED` without user action. |

## 3. Performance

| Req | Standard |
|---|---|
| **NFR-P1** | App cold start to first interactive screen: < 2s on Pixel 6 / Snapdragon 778 class. |
| **NFR-P2** | Job list scroll: 60fps for lists ≤ 1000 jobs. |
| **NFR-P3** | Invoice PDF generation: < 3s for standard template, < 5s for Advanced/Enterprise. |
| **NFR-P4** | SmithAI cold-load (model into memory): < 30s on supported devices. |
| **NFR-P5** | SmithAI inference per response: median < 5s, p95 < 15s. |
| **NFR-P6** | Backend API p95 latency: < 300ms for read, < 800ms for write. |
| **NFR-P7** | Supabase Realtime broadcast latency: < 1s when both peers online. |

## 4. Battery & Resource

| Req | Standard |
|---|---|
| **NFR-B1** | Mesh service in background: < 5% additional battery drain per hour on a Pixel 6 reference device. |
| **NFR-B2** | SmithAI loaded but idle: < 200MB RAM steady-state above app baseline. |
| **NFR-B3** | Mesh service must respect Android Doze + App Standby. |
| **NFR-B4** | Mesh must auto-pause when work mode = OFF (already implemented; do not regress). |
| **NFR-B5** | App total install size: < 250MB without SmithAI model; SmithAI model downloaded on opt-in only (user-triggered). |

## 5. Security & Privacy

| Req | Standard |
|---|---|
| **NFR-S1** | All client ↔ server traffic over TLS 1.3+. |
| **NFR-S2** | All Supabase Realtime traffic over WSS. |
| **NFR-S3** | Mesh transport must encrypt payload (AES-GCM or equivalent) — even when peers are LAN-local. |
| **NFR-S4** | Passwords hashed with bcrypt (cost ≥ 12). [Already in `bcryptjs` dep.] |
| **NFR-S5** | JWT tokens signed with rotated server-side key; refresh-token rotation enforced. |
| **NFR-S6** | Row-level security (RLS) enforced on every Supabase table for the user's `auth.uid()`. |
| **NFR-S7** | Solo users must never receive crew data (already enforced in SmithAI; extend across all queries). |
| **NFR-S8** | Ephemeral channel content must be unrecoverable from server-side storage (cannot be backed up, cannot be subpoenaed). |
| **NFR-S9** | "Clear messages on this device" must wipe local SQLite + cache + any LRU memory. |
| **NFR-S10** | Privacy gating (search visibility, location toggle) must apply to all reads from any client. |
| **NFR-S11** | OWASP Top 10 (2021) compliance for backend APIs (rate-limit ✅ already in `express-rate-limit`). |

## 6. Reliability

| Req | Standard |
|---|---|
| **NFR-R1** | Crash-free user rate (Android, daily): ≥ 99.5%. |
| **NFR-R2** | Crash-free session rate (Android, daily): ≥ 99.9%. |
| **NFR-R3** | Backend API uptime: ≥ 99.9% monthly (≤ 43min downtime/mo). |
| **NFR-R4** | Supabase RLS misconfiguration must fail closed (deny by default). |
| **NFR-R5** | Database migrations must be idempotent + reversible. |

## 7. Scalability

| Req | Standard |
|---|---|
| **NFR-SC1** | Backend must support 1000 concurrent WebSocket connections per node. |
| **NFR-SC2** | Supabase project sized for 10k MAU + 100k jobs + 1M messages at v1 launch. |
| **NFR-SC3** | Storage tier (PDFs + media) must scale to 1TB without architectural change. |
| **NFR-SC4** | Mesh peer set per job site: tested up to 12 peers; degrades gracefully beyond. |

## 8. Accessibility

| Req | Standard |
|---|---|
| **NFR-A1** | WCAG 2.1 AA compliance for desktop portal text + interactive elements. |
| **NFR-A2** | Android TalkBack support on all primary flows (jobs, invoicing, comms). |
| **NFR-A3** | Color contrast ≥ 4.5:1 for body text, ≥ 3:1 for large text. |
| **NFR-A4** | All interactive targets ≥ 44dp on Android. |
| **NFR-A5** | Dynamic type support (Android system font scaling). |

## 9. Internationalization (post-v1)

| Req | Standard |
|---|---|
| **NFR-I1** | v1 ships en-US only. All user-facing strings in resource files (no inline strings). |
| **NFR-I2** | Currency, date, time formatted via locale-aware formatters from day one. |
| **NFR-I3** | Multi-currency on invoices: deferred to post-v1 (single-currency USD for launch). |
| **NFR-I4** | RTL layout: not required v1; do not break existing layouts when added. |

## 10. Observability

| Req | Standard |
|---|---|
| **NFR-OB1** | Structured logging (JSON) on backend; log level configurable per env. |
| **NFR-OB2** | Crash reporting on Android (Crashlytics or equivalent). |
| **NFR-OB3** | Telemetry events for: tier-gate hit, tier upgrade triggered, plan compile, cord transition, mesh connect/disconnect, AI load/unload. |
| **NFR-OB4** | No PII or job content in telemetry — IDs and event types only. |
| **NFR-OB5** | A free user's "what gate did they hit" journey must be reconstructable from telemetry to inform Step 1.5 tuning. |

## 11. Compliance & Legal

| Req | Standard |
|---|---|
| **NFR-CL1** | GDPR-ready data export + delete on request (even though NA-first, build for global from day one). |
| **NFR-CL2** | CCPA opt-out flow available. |
| **NFR-CL3** | Terms of Service + Privacy Policy linked from in-app + accepted on signup. |
| **NFR-CL4** | App Store / Play Store policy compliance for billing (use platform IAP where required). |

## 12. Build, Release, Dev Loop

| Req | Standard |
|---|---|
| **NFR-DV1** | All builds reproducible from a clean checkout + lockfile. |
| **NFR-DV2** | Database migrations run automatically on backend boot in dev; manual + reviewed in prod. |
| **NFR-DV3** | Beta builds gated by `BuildFlags.SEED_DEMO_DATA` (already in place — do not regress). |
| **NFR-DV4** | Smoke test suite must run < 5 min in CI. |
| **NFR-DV5** | Android Play Store internal-testing track used for private testing (current state). |
