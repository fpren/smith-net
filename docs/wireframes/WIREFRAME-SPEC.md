# Smith Net — Wireframe Specification (master)

**Scope:** component-level specs for every net-new surface from Step 3, plus the placement insertions into existing screens. Existing screens are NOT re-spec'd.

**Source:** ASCII wireframes in `docs/ux/WIREFRAMES.md` (the visual layouts) + behavioral states in `docs/journeys/STATE-COVERAGE.md`. This doc binds those to concrete component definitions.

**Theme constraint:** light mode only, ConsoleTheme tokens only, monospace everywhere, no Material widgets (per `docs/design/EXTRACTED-PATTERNS.md`).

---

## 1. Component library — net-new components to add

These 7 components must be implemented in Step 11 PRDs:

| Component | File path (planned) | Purpose | Used in |
|---|---|---|---|
| `LockedFeatureOverlay` | `ui/components/LockedFeatureOverlay.kt` | Full-bleed lock + dimmed-preview-below | N2, N3, N4, N5 (cap-hit), N10, N11 |
| `TrialBanner` | `ui/components/TrialBanner.kt` | Top global banner during any trial | N1 |
| `FounderSeatsCounter` | `ui/components/FounderSeatsCounter.kt` | Live "X of N spots left" pill | embedded in overlays + N7 |
| `TierUpgradeCTA` | `ui/components/TierUpgradeCTA.kt` | Standardized primary-CTA + secondary "Maybe later" + dismiss handler | inside LockedFeatureOverlay |
| `EntitlementLock` | `ui/components/EntitlementLock.kt` | Section-level lock (e.g. AI Assistant settings row) | N10 |
| `PdfSendCounterFooter` | `ui/components/PdfSendCounterFooter.kt` | Inline counter at bottom of send dialogs | within H1, G4 |
| `GateHitToast` | `ui/components/GateHitToast.kt` | Toast wrapper that fires telemetry first | N12 |

**Plus 2 net-new full screens:**
- `TierPricingScreen` (N7) — `ui/subscription/TierPricingScreen.kt`
- `SubscriptionDetailScreen` (N8) — `ui/subscription/SubscriptionDetailScreen.kt`

**Plus 1 net-new dialog:**
- `CancelSubscriptionDialog` — `ui/subscription/CancelSubscriptionDialog.kt`

**Plus 1 net-new flow screen:**
- `WelcomeToOpenScreen` (A4) — `ui/WelcomeToOpenScreen.kt`

**Total net-new code surfaces:** 12 components/screens.

---

## 2. `LockedFeatureOverlay` — the workhorse component

### Composable signature
```kotlin
@Composable
fun LockedFeatureOverlay(
    title: String,                              // ALL CAPS — e.g. "PLAN COMPILER"
    body: String,                                // 1-2 sentences
    tierLabel: String,                           // e.g. "SOLO · $2.99/MO"
    primaryCta: String,                          // e.g. "TRY SOLO FREE 14 DAYS — NO CC"
    secondaryCtaLabel: String = "Maybe later",
    targetTier: Tier,                            // for telemetry + trial-start API call
    triggerEvent: TelemetryEvent,                // for gate_hit.* event
    founderBonusId: String? = null,              // null hides FounderSeatsCounter
    backgroundContent: @Composable () -> Unit,   // dimmed preview below
    onPrimaryClick: () -> Unit,
    onDismiss: () -> Unit,
)
```

### Layout (per WIREFRAMES.md §N2)
- Top card region: `surface` background (`#FFFFFF`), 14dp padding, fillMaxWidth, no border
- Title: `ConsoleTheme.captionBold`, color = primary blue (`#0969DA`)
- Body: `ConsoleTheme.body`, max 2 lines, no truncation (text wraps)
- Tier label: `ConsoleTheme.bodyBold`
- Founder counter: `FounderSeatsCounter` composable (only if `founderBonusId != null`)
- Primary CTA: `Button` with primary blue fill, white text, `ConsoleTheme.captionBold`, 14dp vertical padding, fillMaxWidth
- Secondary CTA: text-link only, `ConsoleTheme.caption` muted, centered, 12dp top padding
- ConsoleSeparator divider
- Background region: 40% viewport from bottom, `Modifier.alpha(0.4f)` wrapping `backgroundContent()`

### Interaction
- Tap on dimmed background → `onDismiss()`
- Tap on Maybe later → `onDismiss()`
- Tap on primary CTA → fires `tier_upgrade.cta_clicked` telemetry → `onPrimaryClick()`
- On render → fires `tier_upgrade.cta_shown` + `triggerEvent` (e.g. `gate_hit.active_job_cap`)
- On dismiss → fires `tier_upgrade.cta_dismissed`
- TalkBack: announces title + body + primary CTA label

### State variations
- Loading: founder counter shows `· · · LOADING SPOTS · · ·` (max 800ms)
- Server unreachable: counter hidden; primary CTA disabled showing `[ NO CONNECTION — OFFLINE ]`
- Founder seats exhausted: counter shows `0 OF ${total} SPOTS — STANDARD PRICING NOW`; CTA enabled at standard price

---

## 3. `TrialBanner` (N1)

### Composable signature
```kotlin
@Composable
fun TrialBanner(
    trialState: TrialState,                     // sealed class with day/tier info
    founderSeatsRemaining: Int? = null,
    onClick: () -> Unit,                         // routes to N7
)
```

### Layout
- fillMaxWidth, 6dp vertical + 12dp horizontal padding
- Background: `ConsoleTheme.surface` (`#FFFFFF`)
- 1dp bottom outline (`#D0D7DE`)
- Text: `ConsoleTheme.captionBold`, `#1F2328`, ALL CAPS
- Wraps to 2 lines on narrow screens — never truncate

### Copy templates (driven by `trialState`)
| trialState | Copy |
|---|---|
| `Solo(daysLeft = 1..7)` | `SOLO TRIAL · ${daysLeft} DAYS LEFT · TAP TO LOCK FOUNDER PRICING` |
| `Solo(daysLeft = 8..12)` | `SOLO TRIAL · ${daysLeft} DAYS LEFT · ${X} FOUNDER SPOTS LEFT` |
| `Solo(daysLeft = 13..14)` | `SOLO TRIAL ENDS IN ${daysLeft} DAYS · TAP TO STAY SOLO` |
| `Advanced(daysLeft = 1..14)` | `ADVANCED TRIAL · ${daysLeft} DAYS · SMITHAI IS LEARNING YOUR JOBS` |
| `Advanced(daysLeft = 15..28)` | `ADVANCED TRIAL · ${daysLeft} DAYS LEFT · ${X} LIFETIME SPOTS LEFT` |
| `Advanced(daysLeft = 29..30)` | `ADVANCED TRIAL ENDS IN ${daysLeft} DAYS · TAP TO KEEP SMITHAI` |
| `Enterprise(daysLeft = 1..13)` | `ENTERPRISE TRIAL · ${daysLeft} DAYS · INVITE YOUR CREW` |
| `SoloExpired` | `TRIAL ENDED · YOU'RE ON OPEN · TAP TO REACTIVATE` |
| `AdvancedExpiredToSolo` | `ADVANCED TRIAL ENDED · YOU'RE BACK ON SOLO` |
| `EnterpriseExpiredToAdvanced` | `ENTERPRISE TRIAL ENDED · YOU'RE BACK ON ADVANCED` |

### State source
- `TrialBannerViewModel` subscribes to `EntitlementsRepository.trialState` (server-pushed via WS or fetched via `/api/me/entitlements`)
- Day count updates locally (no server roundtrip per midnight)

### Placement
- Hosted by `MainActivity` (or `Navigation.kt`) as a **top-of-window** Composable
- Appears below system status bar, above any current screen content
- Hidden when `trialState == NoTrial`

---

## 4. `FounderSeatsCounter` (N6)

### Composable signature
```kotlin
@Composable
fun FounderSeatsCounter(
    bonusId: String,                            // matches founder_seats.bonus_id
    state: SeatsState,                          // Loading | Available(remaining, total) | Exhausted(total)
)
```

### Layout
- Row: 8dp dot + 8dp spacer + label text
- Dot: `Box(8.dp)` `CircleShape` — green `#1A7F37` (Available), gray `#7D8590` (Exhausted), gray (Loading)
- Text: `ConsoleTheme.body`
  - Available, > 100 left: default text color `#1F2328`
  - Available, 11-100 left: muted `#656D76`
  - Available, 1-10 left: primary blue `#0969DA` (urgency without red)
  - Exhausted: muted `#656D76`
  - Loading: muted

### State source
- Subscribes to live counter via WS push event `founder_seats_changed` (server-authoritative)
- Falls back to GET `/api/founder-seats/:bonusId` if WS unavailable
- Caches last value locally; if stale > 60s without update, dot dims to 50% alpha

### Reservation flow (server-side, not in this component)
- When user taps the parent overlay's primary CTA → server reserves a seat with 10-min hold
- Local count decrements optimistically; reconciles on checkout completion
- Hold expires → local count restores

---

## 5. `TierPricingScreen` (N7)

### Composable signature
```kotlin
@Composable
fun TierPricingScreen(
    currentTier: Tier,
    entitlements: Entitlements,
    founderSeats: FounderSeatsState,
    onUpgrade: (targetTier: Tier, cadence: Cadence) -> Unit,
    onStartTrial: (targetTier: Tier) -> Unit,
    onBack: () -> Unit,
    initialScrollToTier: Tier? = null,           // when navigated from a specific lock CTA
)
```

### Layout (per WIREFRAMES.md §N7)
- Standard header row: `← UPGRADE` (back arrow Unicode glyph + ALL CAPS title)
- ConsoleSeparator
- Vertical scroll
- 4 tier sections in fixed order: Open, Solo, Advanced, Enterprise
- Each section separated by ConsoleSeparator
- Annual toggle below the 4 sections — `[ ○ Monthly  ●  Annual — save 16.7% ]`
- Anchor table at very bottom (the "WHY OUR PRICE LOOKS LIKE A TYPO" panel)

### Per-tier section layout
- Tier name: `ConsoleTheme.title` (e.g. `SMITH NET SOLO`)
- Price: `ConsoleTheme.bodyBold` (e.g. `$2.99/MO`)
- One-line hero: `ConsoleTheme.body`
- `WHAT'S INCLUDED:` `ConsoleTheme.captionBold` + bulleted list (8dp dots) of 4-6 items
- `BONUSES:` `ConsoleTheme.captionBold` + ★-bulleted list (use `★` Unicode glyph)
- For founder bonus rows: render `FounderSeatsCounter` inline
- CTA region:
  - If `tier == currentTier`: `[ CURRENT TIER ]` muted text, no action
  - If `tier > currentTier`: primary `[[ TRY ${TIER} FREE ${daysX} DAYS — NO CC ]]` + secondary `[ Start immediately ($X/mo) ]`
  - If `tier < currentTier`: `[ DOWNGRADE TO ${TIER} ]` (secondary, requires confirmation)

### Annual toggle behavior
- Default: monthly
- On toggle on: re-render prices as annual; show "save $X / yr" line under price
- Persists per-session (resets on screen leave)

### Anchor table
- Rendered as a single block of `ConsoleTheme.body` text:
```
WHY OUR PRICE LOOKS LIKE A TYPO

JobTread        $199/mo
Houzz Pro        $85/mo
Knowify          $78/mo
ServiceTitan    $398/mo
Smith Net Solo  $2.99/mo

Same problem. Different math.
```
- Mono-aligned via `FontFamily.Monospace` natural alignment

### Loading / offline states
- On open: render skeletons (3 lines per section, monospace dashes) for max 800ms
- Network unavailable: render last-cached entitlements; CTA shows `[ NO CONNECTION — TRY AGAIN ]` (disabled)

---

## 6. `SubscriptionDetailScreen` (N8)

### Composable signature
```kotlin
@Composable
fun SubscriptionDetailScreen(
    subscription: SubscriptionState,             // sealed class: OpenTier | TrialActive | PaidActive | Canceled
    onBack: () -> Unit,
    onChangeTier: (Tier, Cadence) -> Unit,
    onSwitchAnnual: () -> Unit,
    onUpdateCard: () -> Unit,
    onExportData: () -> Unit,
    onCancelSubscription: () -> Unit,
    onReactivate: () -> Unit,
    onDeleteAccount: () -> Unit,
)
```

### Layout (per WIREFRAMES.md §N8)
- Standard header `← SUBSCRIPTION`
- ConsoleSeparator-separated sections (per state of `subscription`):
  - CURRENT TIER (always)
  - NEXT BILL (paid only)
  - FOUNDER PRICING (if locked)
  - CHANGE TIER
  - PAYMENT METHOD (paid only)
  - DATA (always)
- `Cancel subscription` is a normal-styled row — not red
- `Delete account` is a normal-styled row — fires confirmation dialog (red text only inside the confirmation dialog body)

### `CancelSubscriptionDialog`
- Custom Composable (NOT Material `AlertDialog`)
- Layout per WIREFRAMES.md §N8 cancel-confirmation block
- Body: explains period-end behavior + data preservation
- Two buttons: `[[ KEEP ${TIER} ]]` (primary, kept on left) and `[ Cancel anyway ]` (secondary, on right)

### `DeleteAccountDialog`
- Same Composable pattern as CancelSubscriptionDialog
- Body: warns about 30-day cooling-off, irreversibility after that
- Two buttons: `[[ KEEP ACCOUNT ]]` and `[ Delete anyway ]` (the only place red text is acceptable for inline action — `[ Delete anyway ]` text in error red `#CF222E`)

---

## 7. `WelcomeToOpenScreen` (A4)

### Composable signature
```kotlin
@Composable
fun WelcomeToOpenScreen(
    founderSeats: SeatsState,
    onStartSoloTrial: () -> Unit,
    onStayOnOpen: () -> Unit,
)
```

### Layout (per WIREFRAMES.md §WelcomeToOpenScreen)
- Full-screen with vertical scroll (no back arrow — this is a one-way transition from onboarding)
- Title `WELCOME TO SMITH NET OPEN` in `ConsoleTheme.title`
- "You're on the Free tier." subline `ConsoleTheme.body`
- `WHAT YOU HAVE:` section (5 bullet items with 8dp green dots)
- ConsoleSeparator
- `WANT TO TRY SOLO FOR 14 DAYS?` section + bullets + founder counter
- Two CTAs:
  - Primary `[[ START SOLO TRIAL — NO CC ]]`
  - Secondary `[ Stay on Open ]`
- 16dp page padding

### State source
- `EntitlementsRepository` for founder seats
- No back button — this screen is the only path from onboarding to dashboard

---

## 8. `EntitlementLock` (N10 — settings section variant)

### Composable signature
```kotlin
@Composable
fun EntitlementLock(
    sectionTitle: String,                        // e.g. "AI ASSISTANT"
    bodyText: String,                            // e.g. "Tap to learn what SmithAI does"
    targetTier: Tier,
    trialDuration: Int,                          // e.g. 30
    onClick: () -> Unit,                         // routes to overlay → N7
)
```

### Layout
- Wraps a standard section (per EXTRACTED-PATTERNS §7) with:
- Section header `${sectionTitle}` `ConsoleTheme.captionBold`
- Spacer 10dp
- Surface row: `● Locked — ${TargetTier} tier` + sub-line `${bodyText}` + `>` chevron
- Spacer 8dp
- Text-link below: `[ Try ${TargetTier} free ${trialDuration} days — no CC ]` (`ConsoleTheme.caption`, primary blue)
- Spacer 16dp + ConsoleSeparator + Spacer 12dp

### Interaction
- Whole row tappable → `onClick()` → opens `LockedFeatureOverlay` for that tier

---

## 9. `PdfSendCounterFooter`

### Composable signature
```kotlin
@Composable
fun PdfSendCounterFooter(
    sendsUsedThisMonth: Int,
    cap: Int,
    nextResetDays: Int,                          // for "Next month: in X days" within overlays
)
```

### Layout
- Inline at bottom of `H1 InvoiceScreen` send dialog and `G4 InvoicePreviewBottomSheet`
- Single text line, `ConsoleTheme.caption` muted color
- Above ConsoleSeparator

### Copy
- `0 of 5 free sends used this month` (start of month)
- `${count} of 5 free sends used this month` (1-3 sends used)
- `4 of 5 free sends used this month — 1 left` (one left, slightly emphasized)
- `5 of 5 free sends used this month — full` (cap reached)

### Hidden when
- User tier is Solo or higher (no cap)

### Triggers `LockedFeatureOverlay` (N5 variant)
- When `[Send]` is tapped and `sendsUsedThisMonth >= cap` and tier == open

---

## 10. `GateHitToast`

### Function signature (not a Composable — wrapper around Toast)
```kotlin
fun showGateHitToast(
    context: Context,
    message: String,
    gateId: String,                              // for telemetry
    metadata: Map<String, Any> = emptyMap(),
) {
    TelemetryService.emit("gate_hit.${gateId}", metadata)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
```

### Use cases
- After a user dismisses a lock overlay (Maybe later) — message: "Maybe later. Free tier active."
- On repeat gate-attempt within session — message: "Cap reached. Upgrade in Settings > Subscription."

---

## 11. Net-new wiring into existing screens

### `Q2 SettingsScreen.kt` — net-new edits

```kotlin
// At top of Settings body (above existing PROFILE section):
SubscriptionSection(currentTier = ..., onTap = { navigate(SubscriptionDetail) })
ConsoleSeparator()
Spacer(12.dp)

// Existing PROFILE section unchanged below.

// Existing AI ASSISTANT section becomes conditional:
if (entitlements.smithAI) {
    ExistingAiSection()  // shipped today — unchanged
} else {
    EntitlementLock(
        sectionTitle = "AI ASSISTANT",
        bodyText = "Tap to learn what SmithAI does",
        targetTier = Tier.Advanced,
        trialDuration = 30,
        onClick = { showLockedFeatureOverlay(N10_AI_VARIANT) }
    )
}
```

### `D3 NewJobFlow.kt` — net-new edit
```kotlin
// In Save handler:
val response = jobsApi.create(...)
if (response.statusCode == 403 && response.error == "tier_gate_exceeded") {
    showLockedFeatureOverlay(
        variant = N4_ACTIVE_JOB_CAP,
        triggerEvent = "gate_hit.active_job_cap",
    )
    return@onSave
}
// ... existing success path
```

### `H1 InvoiceScreen.kt` + `G4 InvoicePreviewBottomSheet.kt` — net-new edit
```kotlin
// At bottom of send dialog body, before send button:
if (currentTier == Tier.Open) {
    PdfSendCounterFooter(
        sendsUsedThisMonth = entitlements.pdfSendsThisMonth,
        cap = 5,
        nextResetDays = entitlements.daysUntilMonthReset,
    )
}

// In Send handler:
val response = invoicesApi.send(...)
if (response.statusCode == 403 && response.error == "tier_gate_exceeded") {
    showLockedFeatureOverlay(
        variant = N5_PDF_CAP,
        triggerEvent = "gate_hit.pdf_send_cap",
    )
    return@onSend
}
```

### `E1 PlanScreen.kt` — net-new edit
```kotlin
// At top of PlanScreen body:
if (currentTier < Tier.Solo) {
    LockedFeatureOverlay(
        variant = N3_PLAN_PREVIEW,
        backgroundContent = { PlanPreviewSampleContent(userJobs) },  // dimmed live preview
        triggerEvent = "gate_hit.plan_compiler_preview",
    )
    return@PlanScreen
}
// ... existing PLAN UI
```

### `C1 DashboardScreen.kt` — net-new edit (tile)
```kotlin
// Inside getQuickActions():
val upgradeTile: QuickAction? = when (currentTier) {
    Tier.Open -> QuickAction("UPGRADE", onClick = { navigate(N7_PRICING) })
    Tier.Solo -> QuickAction("ADD SMITHAI", onClick = { navigate(N7_PRICING, scrollTo = Tier.Advanced) })
    Tier.Advanced -> QuickAction("ADD CREW", onClick = { navigate(N7_PRICING, scrollTo = Tier.Enterprise) })
    Tier.Enterprise -> null
}
val actions = baseActions + listOfNotNull(upgradeTile)
```

### `MainActivity.kt` — net-new edit (banner host)
```kotlin
// At top of root scaffold:
if (trialState != NoTrial) {
    TrialBanner(trialState = trialState, onClick = { navigate(N7_PRICING) })
}
// ... existing nav host
```

---

## 12. Server-side wireframes (template + counter logic)

### `templates/invoice.html` — net-new edit (N9 PDF stamp)
```html
<!-- Existing invoice content -->
<table class="line-items">...</table>

<!-- Net-new conditional footer block -->
{{#if isOpenTier}}
<div class="smith-net-footer" style="font-family: monospace; color: #656D76; font-size: 10px; padding-top: 24px; border-top: 1px solid #D0D7DE; text-align: center;">
  Sent via Smith Net — smithnet.app<br/>
  A deterministic tool for contractors. Try free →
</div>
{{/if}}
```

### `templates/proposal.html` — same pattern

### Email signature (server-side email render, Open tier only)
```
{{user_email_body}}

--
Sent via Smith Net (smithnet.app)
```

### Server-side counter logic (per-flow PRDs detail this; summary):
- `gate_hit.*` events insert into `gate_hit_events` table with `user_id_hash` (SHA256 of profile.id) — no PII
- 403 `tier_gate_exceeded` response includes `gate_id`, `limit`, `current`, `current_tier` for client UX

---

## 13. Coverage matrix — wireframe → component → spec

| Wireframe ID | Spec'd component | This doc § | UX-DESIGN ref | STATE-COVERAGE ref |
|---|---|---|---|---|
| N1 Trial banner | `TrialBanner` | §3 | §3 N1 | N1 (13 states) |
| N2 PLAN compose lock | `LockedFeatureOverlay` (PLAN variant) | §2 | §3 N2/N3 | N2/N3/N10/N11 (11 states) |
| N3 PLAN preview lock | `LockedFeatureOverlay` (PLAN variant) | §2 | §3 N2/N3 | N2/N3/N10/N11 |
| N4 Active-job cap | `LockedFeatureOverlay` (cap variant) | §2 | §3 N4 | N4 (7 states) |
| N5 PDF send cap | `PdfSendCounterFooter` + `LockedFeatureOverlay` | §9, §2 | §3 N5 | N5 (9 states) |
| N6 Founder counter | `FounderSeatsCounter` | §4 | §3 N6 | N6 (8 states) |
| N7 Pricing screen | `TierPricingScreen` | §5 | §3 N7 | N7 (12 states) |
| N8 Subscription detail | `SubscriptionDetailScreen` + dialogs | §6 | §3 N8 | N8 (11 states) |
| N9 PDF stamp | server-side template block | §12 | §3 N9 | N9 (4 states) |
| N10 AI lock | `EntitlementLock` | §8 | §3 N10 | N10 (5 states) |
| N11 Crew invite lock | `LockedFeatureOverlay` (Crew variant) | §2 | §3 N11 | N11 (4 states) |
| N12 Tier-gate Toast | `GateHitToast` | §10 | §3 N12 | N12 (2 states) |
| WelcomeToOpenScreen (A4) | `WelcomeToOpenScreen` | §7 | §5 | (no state matrix — entry-only) |

---

## 14. Per-flow PRD index

Each critical flow from FLOW-DIAGRAMS.md has a per-flow PRD:

| Flow ID | Title | File |
|---|---|---|
| FLOW-1 | First install → first invoice sent | `docs/prds/flows/FLOW-1-first-install-to-first-invoice.md` |
| FLOW-2 | Cap-hit → trial conversion | `docs/prds/flows/FLOW-2-cap-hit-to-trial.md` |
| FLOW-3 | AI tab → Advanced trial | `docs/prds/flows/FLOW-3-ai-tab-to-advanced.md` |
| FLOW-4 | Plan compose → seal | `docs/prds/flows/FLOW-4-plan-compose-to-seal.md` |
| FLOW-5 | Online ↔ offline sync | `docs/prds/flows/FLOW-5-online-offline-sync.md` |
| FLOW-6 | Trial expiration → downgrade | `docs/prds/flows/FLOW-6-trial-expiry.md` |
| FLOW-7 | Cancellation | `docs/prds/flows/FLOW-7-cancellation.md` |
| FLOW-8 | Public invoice page view | `docs/prds/flows/FLOW-8-public-invoice-page.md` |

(Landing page wireframe is OUT OF SCOPE per user direction.)

---

## 15. Net-new component effort estimate (forward look for Step 11)

| Component | Effort (days) | Owner step |
|---|---|---|
| LockedFeatureOverlay | 2 | Step 11 PRD F2 |
| TrialBanner | 1 | Step 11 PRD F2 |
| FounderSeatsCounter | 1 | Step 11 PRD F2 |
| TierUpgradeCTA | 0.5 | Step 11 PRD F2 |
| EntitlementLock | 0.5 | Step 11 PRD F3 |
| PdfSendCounterFooter | 0.5 | Step 11 PRD F2 |
| GateHitToast | 0.5 | Step 11 PRD F2 |
| TierPricingScreen | 3 | Step 11 PRD F2 |
| SubscriptionDetailScreen | 2 | Step 11 PRD F2 |
| CancelSubscriptionDialog | 0.5 | Step 11 PRD F7 |
| DeleteAccountDialog | 0.5 | Step 11 PRD F7 |
| WelcomeToOpenScreen | 1 | Step 11 PRD F1 |
| Server: tier resolver + entitlements | 4 | Step 11 PRD F4 |
| Server: founder_seats reservation | 2 | Step 11 PRD F2 |
| Server: gate_hit_events telemetry sink | 1 | Step 11 PRD F2 |
| Server: invoice template (Free stamp) | 1 | Step 11 PRD F9 |
| Server: invoice template (Advanced) | 2 | Step 11 PRD F9 |
| Server: invoice template (Enterprise) | 3 | Step 11 PRD F9 |
| **Total (engineering)** | **~25 days** | — |

(Add testing, QA, copy review, design polish on top — multiplier ~1.4×.)
