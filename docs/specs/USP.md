# Smith Net — Unique Selling Proposition

## The one-sentence USP

**Smith Net is the only contractor app where your plan compiles to a deterministic execution that runs the same way every time, on every device, with or without internet — for less than the price of one bid in any other tool.**

## The three-pillar positioning

1. **Determinism is the product.** Other contractor apps are CRUD-with-AI. Smith Net is a compiler. You define a plan once, it compiles, it executes the same way forever — no AI drift, no surprise behavior, no "where did my form go" moments. *(Moat: PLAN Compiler + cord-based state model.)*

2. **Connectivity is optional.** Built mesh-first. Bluetooth + Wi-Fi-Direct fallback when the job site has no signal. Supabase Realtime when it does. Same plan runs in either mode. *(Moat: integrated mesh transport, not a competitor add-on.)*

3. **Price is non-negotiable.** $2.99 to unlock the moat. $9.99 to add on-device AI. $50 for a full crew. The category leaders charge $50-$199 *just to start*. *(Moat: price floor that's uneconomic for sales-led competitors to match.)*

## Why each non-moat thing is *not* the USP

| Thing competitors will copy | Why it's not the USP |
|---|---|
| Trade-specific features (plumbing/electrical/HVAC modules) | We deliberately don't fork. Trade is metadata; the platform stays one product. |
| On-device AI assistant (SmithAI) | It's a paid value-add in Advanced. The moat doesn't *need* AI — that's the point. |
| 121-trade picker | Lookup table. Easy to copy. |
| Integrated invoicing | JobNimbus / JobTread also have it. Smith Net's edge is the *deterministic* link between job → cord → invoice, not the invoice feature itself. |

## Competitive comparison

| Capability | Smith Net | JobTread | Knowify | Joist | ServiceTitan | Houzz Pro | JobNimbus |
|---|---|---|---|---|---|---|---|
| **Entry price (paid)** | **$2.99/mo** | ~$199/mo | ~$78/mo | $0 / ad-supported | ~$398/mo | ~$85/mo | ~$25/user/mo |
| **Has free tier** | ✅ Open | ❌ | ❌ | ✅ basic | ❌ | ❌ | ❌ |
| **Deterministic plan execution** | ✅ PLAN Compiler | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Cord-based state model** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Works offline (P2P mesh)** | ✅ Bluetooth+WiFi-Direct | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **On-device AI assistant** | ✅ Advanced ($9.99) | ❌ | ❌ | ❌ | partial cloud | ❌ | ❌ |
| **Integrated invoicing** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Trade-agnostic (one product, all trades)** | ✅ | ⚠️ tilted to remodel | ⚠️ tilted to specialty | ✅ | ❌ HVAC/plumbing focus | ⚠️ design-build tilt | ⚠️ roofing tilt |
| **Setup time to first invoice** | < 10 min | ~hours (sales call required) | ~hours | ~minutes | ~days | ~hours | ~hours |
| **Vendor lock contract** | ❌ month-to-month | ⚠️ annual common | ⚠️ annual common | ❌ | ⚠️ annual | ⚠️ annual | ⚠️ annual |

## The pitch in 3 lines (for ad copy / landing)

> **Your plan, compiled.**
> Works offline. Runs on Android. $2.99/mo to unlock.
> Built by a contractor for contractors who'd rather work than fight their software.

## Things that signal we're winning

- A test user says "I made a plan in week one and it just *kept working* in week three when my crew added a step" → cord state model is landing
- A test user opens the AI Assistant tab on Solo and immediately wants to upgrade → tier ladder is shaped right
- A test user invoices on the train with no signal and the customer gets the PDF → mesh + deterministic + email send-queue is landing
- A competitor publishes a "now with AI" announcement → confirms AI is *not* the moat (everyone has it; we have determinism)

## Things that signal we're losing

- Test users churn off Free in week 1 because "1 active job" felt punitive (not earned-friction)
- Solo conversion stalls because users wanted AI at $2.99
- Mesh battery drain shows up in support tickets faster than mesh value shows up in retention
- A new entrant ships a deterministic-plan competitor at $0 (would force a re-think on free tier scope, not on price)
