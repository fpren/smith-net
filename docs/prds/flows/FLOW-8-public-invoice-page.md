# FLOW-8 — Public invoice page view

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 8
**Audience:** the contractor's customer (NOT a Smith Net user). No auth required.

---

## Scope

A customer receives an email with a link to `/i/:uuid` (or `/p/:uuid` for proposals). They open it in a browser. The server-rendered HTML page shows the invoice (or proposal) with all line items, payment instructions, and — if the contractor is Open tier — the N9 Smith Net branding stamp footer. The page records `viewed_at` on first view.

This is the most sensitive surface for **passive distribution** of Smith Net to non-users.

## Surfaces

| Surface | Origin | Details |
|---|---|---|
| `templates/invoice.html` (Express render) | existing-with-N9 | Adds conditional `{{#if isOpenTier}}` block for Smith Net branding stamp footer |
| `templates/proposal.html` (Express render) | existing-with-N9 | Same N9 stamp pattern |
| Customer's email client | n/a | Receives HTML email with link + (Open-tier only) branded `--\nSent via Smith Net (smithnet.app)` signature |
| Browser viewing the page | n/a | No client JS required; pure HTML |

## Server contract

| Endpoint | Behavior |
|---|---|
| GET /i/:uuid | (existing) Loads `invoice_links` row by uuid; renders `templates/invoice.html` with values; injects `isOpenTier` boolean for N9 stamp; updates `viewed_at` on first view; rate-limited per UUID (60 views/min). Returns 200 HTML or 404. |
| GET /p/:uuid | (existing) Same for proposals via `templates/proposal.html`. |
| POST /api/invoices/:id/send (server-side trigger) | When invoice is sent: looks up contractor's tier; if Open, sets `isOpenTier=true` for the rendered PDF AND injects branded email signature; emails customer. |
| Per-UUID rate limit | Express rate-limit by URL path (60 req/min/UUID) — defends against enumeration. |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | GET /i/:uuid returns 200 with rendered HTML for valid UUID | E2E |
| AC-2 | GET /i/:uuid returns 404 with `<h2>Invoice not found</h2>` for unknown UUID | E2E |
| AC-3 | First view sets `viewed_at` on `invoice_links` row | DB assertion |
| AC-4 | Subsequent views do NOT update `viewed_at` | DB assertion |
| AC-5 | Per-UUID rate limit (60 req/min) returns 429 on exceedance | Load test |
| AC-6 | If contractor is Open tier, rendered HTML contains: `Sent via Smith Net — smithnet.app` footer block | HTML snapshot |
| AC-7 | If contractor is Solo / Advanced / Enterprise, footer block is absent | HTML snapshot |
| AC-8 | Page renders correctly without JS (pure HTML/CSS) | Browser test with JS disabled |
| AC-9 | Page mobile-responsive (works on phones) | Browser test on multiple viewport sizes |
| AC-10 | UUID has ≥ 122 bits of entropy (`replace(gen_random_uuid()::text, '-', '')` confirmed) | Schema review |
| AC-11 | Telemetry: contractor sees in-app notification when invoice viewed by client | UI test |
| AC-12 | invoice status updates `unpaid` → `viewed` on next contractor app open after first view | UI test |

## BDD scenarios

```gherkin
Feature: Customer views public invoice page

Scenario: Customer opens email link to /i/:uuid
  Given a contractor on Open tier sent an invoice with uuid "abc123"
  When the customer clicks the link in the email
  And the browser navigates to https://app.smithnet.app/i/abc123
  Then the server returns 200 with rendered HTML
  And the page shows invoice line items, totals, payment instructions
  And the page shows the Smith Net branding footer "Sent via Smith Net — smithnet.app"
  And the invoice's viewed_at is set in the database
  And on next contractor app open, the invoice status is "viewed"

Scenario: Solo tier invoice has no branding
  Given a contractor on Solo tier sent an invoice with uuid "def456"
  When the customer opens /i/def456
  Then the page renders normally
  But the Smith Net branding footer is NOT present
  And the email signature does NOT contain "Sent via Smith Net"

Scenario: Invalid UUID returns 404
  When a request comes for /i/nonexistent-uuid-xyz
  Then the server returns 404 with body containing "Invoice not found"

Scenario: Rate limit on enumeration attempts
  Given an attacker makes 100 requests to /i/* with random UUIDs in 1 minute
  Then after 60 requests the server returns 429 Too Many Requests
  And the rate limit is per-UUID (different UUIDs reset their own counters)

Scenario: Contractor sees viewing notification
  Given the customer has just viewed an invoice for the first time
  When the contractor opens the Smith Net app
  Then they see a notification "Invoice [name] viewed by client"
  And the invoice status badge changes from "unpaid" to "viewed" in the invoice list
```

## Edge cases

| Case | Behavior |
|---|---|
| Customer opens link on a phone with an outdated browser | HTML/CSS is plain; works on IE11 / Safari 10+ / any mobile browser; no Web Components / ES6 modules |
| Email link arrives in spam folder | not Smith Net's problem to fix; but DKIM/SPF must be set up server-side for reliable delivery |
| Customer forwards email to a colleague who also views | both views count; first sets `viewed_at`; second does not update it; contractor sees one viewing notification only |
| Contractor downgrades from Solo → Open after sending | already-sent PDF (which the customer already received) is unchanged; the public page rendering pulls latest tier and could now show the Open stamp if rerendered — **decision: lock the stamp to the tier at SEND time, not at view time** (see `templates/invoice.html` data binding) |
| Payment integration (Step 11+) link click on the page | future enhancement; for v1 the page is read-only |
| Customer attempts to print | print CSS optimized — no nav, no header bars; just the invoice content + (Open-only) stamp |

## Security

- UUID is the access control. 122 bits entropy is sufficient for v1 (no PII in the URL).
- Per-UUID rate limit prevents brute-force enumeration.
- Per `SECURITY.md §12`: no auth required (intentional — customers without accounts must view).
- Page does NOT include any user PII beyond what the contractor entered for their client (already shared via the invoice).
- HTTPS enforced via Tailscale Funnel.

## Non-goals

- Customer-facing payment portal (out of scope v1; future Stripe Checkout / similar)
- Customer-facing reply / dispute mechanism (use email reply for v1)
- Customer login / account creation (intentional — keep this surface low-friction)
- Branding customization for paid tiers (Solo+ just removes the stamp; no alternate brand)
- "Powered by" backlinks beyond the N9 stamp

## Linked specs

- `WIREFRAME-SPEC.md §12` (PDF stamp + email signature server-side template)
- `STATE-COVERAGE.md` N9 (4 states: stamp present / absent / tier downgrade between draft & send / retroactive sends)
- `SECURITY.md §12` (billing & financial security)
- `SCHEMA.md §8` (invoice_links table)
- Existing code: `backend/src/server.ts` (GET /i/:uuid handler), `backend/src/templates/invoice.html`, `backend/src/invoiceLinks.ts`
