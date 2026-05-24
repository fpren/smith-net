# FLOW-1 — First install → first invoice sent

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 1
**Success criterion:** L5 — median time from install to first invoice sent < 10 min (Free / Open tier)

---

## Scope

Brand-new contractor installs Smith Net, registers, completes onboarding, lands on the new `WelcomeToOpenScreen`, chooses to stay on Open, creates 1 job, generates an invoice, and sends a branded PDF — all under 10 minutes median.

## Screens (in order)

| Step | Screen | Origin | Spec |
|---|---|---|---|
| 1 | A1 AuthScreen | existing | unchanged |
| 2 | A3 WelcomeScreen | existing | unchanged |
| 3 | A2 OnboardingScreen | existing | unchanged |
| 4 | **A4 WelcomeToOpenScreen** | **NET-NEW** | `WIREFRAME-SPEC.md §7` |
| 5 | C1 DashboardScreen | existing-with-N | tier-aware quick action `UPGRADE` added (per `WIREFRAME-SPEC §11`) |
| 6 | D3 NewJobFlow | existing | unchanged |
| 7 | D2 JobPipelineScreen | existing | unchanged |
| 8 | F1 TimeTrackingScreen | existing | unchanged |
| 9 | H1 InvoiceScreen | existing-with-N | adds `PdfSendCounterFooter` for Open tier |
| 10 | G4 InvoicePreviewBottomSheet | existing-with-N | adds counter; calls send |
| (server) | server-side render with **N9 PDF stamp** | NET-NEW | `WIREFRAME-SPEC §12` |

## Server contract

| Endpoint | Step | Notes |
|---|---|---|
| POST /api/auth/register | 1 | returns user + tokens |
| GET /api/me/entitlements | 5+ | returns tier=open, caps={activeJobs:1, pdfSendsPerMonth:5}, founderSeatsRemaining |
| POST /api/jobs | 6 | server check: tier=open + active_jobs<1 → OK |
| POST /api/time-entries | 8 | unchanged |
| POST /api/invoices | 9 | template=standard |
| POST /api/invoices/:id/send | 10 | server renders PDF with N9 stamp injected (via `templates/invoice.html` `isOpenTier` block); emails customer; increments `pdfSendsThisMonth`; returns 200 + new counter value |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Time from install to first invoice sent < 10 min for median user | Telemetry: emit `funnel.first_invoice_sent` with timestamp delta from `funnel.signup` |
| AC-2 | A4 WelcomeToOpenScreen renders within 500ms of onboarding complete | Compose performance trace |
| AC-3 | A4 has exactly 2 CTAs: `START SOLO TRIAL — NO CC` and `Stay on Open` | Visual / E2E test |
| AC-4 | Selecting `Stay on Open` lands on C1 Dashboard with no trial banner | Espresso test |
| AC-5 | First job creation succeeds for Open tier (active_jobs == 0 → < 1 cap) | API contract test |
| AC-6 | Invoice PDF rendered server-side with `Sent via Smith Net — smithnet.app` footer for Open tier | Snapshot test of rendered HTML |
| AC-7 | Invoice email signature contains `--\nSent via Smith Net (smithnet.app)` for Open tier | Email body assertion |
| AC-8 | Invoice send increments `pdfSendsThisMonth` server-side | DB assertion |
| AC-9 | Toast confirms send: "Invoice sent · 1 of 5 free PDFs used this month" | UI test |
| AC-10 | Telemetry: `funnel.first_invoice_sent` fires with `from_tier=open` | Telemetry assertion |

## BDD scenarios (Gherkin)

```gherkin
Feature: First-time user installs and sends first invoice on Open tier

Scenario: User completes onboarding, stays on Open, sends first invoice
  Given a fresh install with no existing account
  When the user registers with valid email and password
  And completes the onboarding (name, role=solo, trade)
  Then the WelcomeToOpenScreen renders
  And it shows "WELCOME TO SMITH NET OPEN"
  When the user taps "Stay on Open"
  Then they land on the Dashboard with no trial banner
  When they tap "+ NEW JOB"
  And they fill title="Test Kitchen", client="Acme", trade="electrician"
  And they tap Save
  Then the new job is created (server returns 201)
  And the dashboard reflects 1 active job
  When they tap "Generate Invoice"
  And the invoice draft renders with line items derived from the job
  And they tap Send
  Then the server returns 200
  And the rendered PDF contains "Sent via Smith Net — smithnet.app"
  And the toast shows "Invoice sent · 1 of 5 free PDFs used this month"
  And telemetry emits funnel.first_invoice_sent with from_tier=open
```

## Edge cases

| Case | Behavior |
|---|---|
| User taps `Stay on Open` then immediately tries `+ NEW JOB` and the previous-trial flag exists | not applicable for first-time install |
| User loses connectivity during invoice send | invoice queued locally; sends on reconnect; counter increments only after server-confirmed send |
| User abandons after onboarding (does not reach A4) | onboarding state is persisted; resuming app re-enters at A4, not at A2 |
| Email send fails (SMTP error) | invoice marked `send_failed`; user sees toast "Send failed — saved as draft. Retry?"; counter NOT incremented |

## Non-goals (explicit)

- Onboarding redesign (existing OnboardingScreen ships as-is)
- Email template restyling (only N9 stamp injection for Open tier)
- iOS or web client paths (Android only for this flow)

## Linked specs

- `WIREFRAME-SPEC.md §7` (`WelcomeToOpenScreen`), `§9` (`PdfSendCounterFooter`), `§12` (PDF template)
- `STATE-COVERAGE.md` N9 (PDF stamp states)
- `SUCCESS-METRICS.md` L5 + funnel steps [1] → [6]
