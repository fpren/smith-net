# Smith Net — Micro-Interactions

**Sigma step:** 7 — Interface States
**Scope:** every interaction, transition, and feedback pattern in net-new UI + the (very limited) motion language.
**Constraint:** light mode only, monospace, no animations > 250ms, no spring physics, no Material widget animations beyond what existing app uses.

**Underlying principle:** the app's voice is *deterministic* and *unceremonious*. Most state changes snap. Motion is reserved for moments that genuinely need a "what just happened" cue.

---

## 1. Motion vocabulary (the entire vocabulary)

The app has 5 motion primitives. Anything outside these requires a design-system version bump.

| Primitive | Duration | Easing | Used for |
|---|---|---|---|
| **Snap** | 0ms | n/a | Most state changes (status pill colors, counter updates, tier-row state) |
| **Tick** | 1000ms (recurring) | n/a | Live clock display ("ON CLOCK 1h 23m 45s") — recomposes only the smallest text unit |
| **Crossfade short** | 200ms | `FastOutSlowInEasing` | Lock overlay appear/disappear |
| **Material progress** | continuous | system default | `LinearProgressIndicator` for AI model load (existing pattern, not net-new) |
| **Toast** | system default (3.5s LENGTH_SHORT, 5.5s LENGTH_LONG) | system | All transient notifications |

**Forbidden everywhere:**
- ❌ Spring physics (overshoot, bounce)
- ❌ Animations > 250ms
- ❌ Hero / shared-element transitions
- ❌ Skeleton shimmer (use literal `· · ·` text dots)
- ❌ Pulse / breathing on attention-grabbers
- ❌ Confetti / celebration on conversion
- ❌ Slide-in/slide-out for banners (snap on/off)
- ❌ Custom easing curves besides `FastOutSlowInEasing`

---

## 2. Touch feedback

### 2.1 Standard row tap (`Modifier.clickable {}`)
- **Behavior:** Material default ripple from tap point on release
- **Duration:** ~250ms ripple expansion (platform default — acceptable as platform behavior)
- **Visual:** ripple uses `{color.primary}` × 0.12 alpha — subtle

### 2.2 Primary CTA button (filled `{color.primary}`)
- **Pressed state:** fill darkens to `{color.primary}` × 0.85 (composite over background)
- **Release:** fill returns to `{color.primary}` instantly
- **Disabled:** fill `{color.surfaceVariant}`, text `{color.textMuted}`, no ripple

### 2.3 Secondary text-link CTA ("Maybe later")
- **Pressed state:** color shifts from `{color.textMuted}` to `{color.textPrimary}` instantly
- **Release:** snaps back

### 2.4 Status pill
- **Tap (when applicable):** ripple confined to pill bounds
- **State change:** snaps (e.g., dot color change from green to gray) — no animation

### 2.5 Toggle ((●))/((○))
- **Tap:** glyph swaps instantly; no slide animation; haptic feedback (light tick) on Android `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)`

---

## 3. Lock overlay (LockedFeatureOverlay) interactions

### 3.1 Appearance
- **Trigger:** caller invokes `showLockedFeatureOverlay(...)`
- **Animation:** Crossfade short (200ms `FastOutSlowInEasing`)
- **If reduced-motion enabled:** instant
- **Background dimming:** `Modifier.alpha(0.4f)` applied to the dimmed-preview content; the alpha animates with the same Crossfade

### 3.2 Background tap → dismiss
- **Trigger:** finger-up on the dimmed background area
- **Behavior:** overlay crossfades out; caller screen restored
- **Telemetry:** `tier_upgrade.cta_dismissed`

### 3.3 Primary CTA tap (e.g., "TRY SOLO FREE — NO CC")
- **Sequence:**
  1. Press: button fill darkens
  2. Release: button shows inline spinner + text changes to `STARTING TRIAL...`
  3. API call to `POST /api/me/start-trial`
  4. On success: overlay closes (Crossfade); transitions to caller screen with new entitlements; trial banner appears
  5. On failure: Toast "Couldn't start trial. Try again."; button restores
- **Max in-flight:** 5 seconds (then timeout shown as failure)

### 3.4 Secondary "Maybe later" tap
- **Trigger:** finger-up on text-link
- **Behavior:** Crossfade out + Toast "Maybe later. Free tier active."
- **Telemetry:** `tier_upgrade.cta_dismissed`

### 3.5 Founder counter live update (during overlay open)
- **Trigger:** WS push `founder_seats_changed`
- **Behavior:** text snaps to new count; if dropping to next tier (e.g., > 100 → 11-100), label color transitions instantly (no fade)

---

## 4. Trial banner (N1) interactions

### 4.1 Appearance (trial start)
- **Trigger:** trial activation succeeds
- **Behavior:** snap-in (no slide animation); pushes content below it down by banner height
- **Why no slide:** consistent with the snap-everywhere principle; users notice the new banner via state-recompose, not via motion

### 4.2 Disappearance (conversion or expiry)
- **Trigger:** trial converts to paid (banner removed) OR new state takes over (e.g., trial expired → "TRIAL ENDED" copy)
- **Behavior:** snap

### 4.3 Day-counter recomposition (midnight tick)
- **Trigger:** crossing midnight changes `daysLeft` value
- **Behavior:** text snaps to new value; recomposes only the affected text node

### 4.4 Tap
- **Behavior:** standard ripple → routes to N7 TierPricingScreen (no transition customization)

---

## 5. PDF send counter footer (N5) interactions

### 5.1 Counter increment after successful send
- **Trigger:** send completes server-side
- **Behavior:** text snaps from `${n} of 5` to `${n+1} of 5`
- **At cap:** text snaps to `5 of 5 free sends used this month — full`

### 5.2 6th-send overlay invocation
- **Trigger:** user taps Send on the 6th attempt
- **Behavior:** Send button briefly shows spinner + `SENDING...`; on 403 from server, overlay Crossfade-in
- **No flash of partial success**

---

## 6. Subscription cancellation flow micro-interactions

### 6.1 Cancel-subscription row tap → dialog open
- **Behavior:** dialog Crossfade-in (200ms); background screen dims to 0.4 alpha
- **Background dimming:** included in same Crossfade

### 6.2 [[ KEEP SOLO ]] tap
- **Behavior:** dialog Crossfade-out; background restores; no API call; no Toast (silent)

### 6.3 [ Cancel anyway ] tap
- **Behavior:**
  1. Button shows inline spinner + `CANCELING...`
  2. API call (max 5s)
  3. On success: dialog Crossfade-out; N8 re-renders with cancelled state; Toast "Solo cancels May 30. You can reactivate any time."
  4. On failure: Toast "Couldn't cancel. Try again."; dialog stays open

### 6.4 Outside-area tap (background dim)
- **Behavior:** does NOT dismiss confirmation dialogs — explicit choice required
- **Why:** prevents accidental destructive-action confirmation — applies to CancelSubscriptionDialog AND DeleteAccountDialog

### 6.5 Back-button (system)
- **Behavior:** equivalent to KEEP SOLO (the safer choice)

---

## 7. Mesh / connectivity micro-interactions

### 7.1 Connection-state pill update
- **Trigger:** `BoundaryEngine` state change (online → offline → mesh → gateway)
- **Behavior:** dot color snaps; label text snaps; no transition
- **Latency target:** within 1s of state change (per AC-9 of FLOW-5)

### 7.2 ReconciliationEngine in-progress indicator
- **Render:** small text below header `· · · syncing`
- **Behavior:** appears when reconcile starts; disappears when complete; max display 5s; if ongoing > 5s, replaces with `Reconciling [N] messages...`
- **Animation:** the `· · ·` dots are static; no animated dots (intentional — snap principle)

### 7.3 Mesh peer in/out
- **Trigger:** new peer joins or leaves
- **Behavior:** pill counter snaps to new value; if peer leaves, no Toast (peer presence is ambient, not actionable)

---

## 8. AI Assistant interactions (existing, mostly unchanged)

### 8.1 Load model button tap (Advanced+)
- **Behavior:** existing pattern — `LinearProgressIndicator` shows 0%-100% over duration of model download (~30s on supported devices)
- **State transitions:** SLEEPING → WAKING (with progress) → ALIVE — all snap-driven

### 8.2 Model loaded → ALIVE
- **Behavior:** progress bar disappears; status pill snaps to `● SMITHAI · ALIVE`
- **No celebration animation**

### 8.3 RULE_BASED_FALLBACK kick-in
- **Trigger:** model load failure or battery gate
- **Behavior:** status pill snaps to `● SMITHAI · RULE_BASED`; user-facing copy explains in muted caption below

---

## 9. Founder counter live behavior

### 9.1 Initial paint (loading)
- **Render:** `· · · LOADING SPOTS · · ·` static text
- **Replaces with actual count when API returns (max 800ms wait)**

### 9.2 Live decrement (someone else takes a seat)
- **Trigger:** WS push `founder_seats_changed`
- **Behavior:** number snaps to new value; if crossing threshold (>100 → 11-100, or 11-100 → 1-10), label color also snaps to new state
- **No "spinning down" animation** — snap

### 9.3 Reservation hold (user clicked CTA, server holds 10min)
- **Behavior:** local count decrements optimistically by 1; if checkout completes, count stays decremented; if hold expires (10min), count restores by 1

### 9.4 Stale (no refresh > 60s)
- **Behavior:** dot color stays as previous state but reduces to 50% alpha (subtle, snap)
- **Restored on next refresh**

---

## 10. Toast usage rules

| Use case | LENGTH | Copy template |
|---|---|---|
| Cap-overlay dismissed | LENGTH_SHORT | "Maybe later. Free tier active." |
| Repeat cap-attempt mid-session | LENGTH_SHORT | "Cap reached. Upgrade in Settings > Subscription." |
| Send queued for next month | LENGTH_LONG | "Send saved as draft. Will send Day 1 of next month if still on Open." |
| Invoice sent (Open) | LENGTH_SHORT | "Invoice sent · ${n} of 5 free PDFs used this month" |
| Trial-start failure | LENGTH_SHORT | "Couldn't start trial. Try again." |
| Cancel succeeded | LENGTH_SHORT | "Solo cancels May 30. You can reactivate any time." |
| Reactivate succeeded | LENGTH_SHORT | "Solo reactivated. Next bill May 30." |
| Network error fallback | LENGTH_SHORT | "Couldn't reach server. Retry." |
| Copy-to-clipboard confirm | LENGTH_SHORT | "Copied" |
| Generic success | LENGTH_SHORT | (be specific — generic "Saved" forbidden) |

**Forbidden Toast use:**
- ❌ Toast for confirmations of irreversible actions (use a dialog)
- ❌ Toast for errors that require user action (use inline error state)
- ❌ Toast longer than 5.5s (use a banner instead — but we don't have banners; if you need a banner, escalate to design)
- ❌ Multiple Toasts in rapid succession (queue them; show only the most recent)

---

## 11. Haptic feedback

**Sparing use** — haptic should be reserved for confirmations the user explicitly initiated.

| Action | Haptic |
|---|---|
| Toggle switch ((●))/((○)) | `HapticFeedbackConstants.CONFIRM` (light tick) |
| Long-press (e.g., on a list item to open contextual menu — existing pattern) | `HapticFeedbackConstants.LONG_PRESS` |
| Cap-hit overlay invocation | NONE — no haptic on automatic UI events |
| Trial start success | NONE — Toast suffices |
| Successful invoice send | NONE — Toast suffices |
| Pull-to-refresh release | system default (existing app already does this if present) |

**Forbidden haptic use:**
- ❌ Haptic on every button tap (annoying)
- ❌ Haptic on screen transitions
- ❌ Vibration patterns (single ticks only)

---

## 12. Loading & skeleton patterns

The app does NOT use skeleton shimmer. Loading states use:

| Loading surface | Pattern |
|---|---|
| TierPricingScreen sections | 3 lines per section of monospace dashes `─ ─ ─ ─` (snap-replace with content) |
| FounderSeatsCounter | literal text `· · · LOADING SPOTS · · ·` |
| ReconciliationEngine | small text `· · · syncing` below header |
| AI model load | existing `LinearProgressIndicator` (Material default — exception) |
| API call in flight (CTA pressed) | inline spinner replacing button text + caption changes (e.g., "STARTING TRIAL...") |
| Initial app launch | minimal — Android system splash screen only; no custom splash with logo wash |

**Max wait before showing a loading affordance:** 200ms. Below that, snap directly to loaded state to avoid flicker.
**Max time loading affordance shown before showing a result or error:** 5s for API calls; 30s for AI model load (per NFR-P4).

---

## 13. Empty states

| Surface | Empty content |
|---|---|
| C1 Dashboard, no jobs | Shows GETTING STARTED tiles per existing commit `2b2e02a` (gated on real state — never demo data unless `BuildFlags.SEED_DEMO_DATA=true`) |
| D1 JobBoardScreen, no jobs | Centered message: `NO JOBS YET` `{type.captionBold}` muted + `[ + NEW JOB ]` text-link |
| K1 ChatListScreen, no chats | `NO CONVERSATIONS YET` muted; `[ + START ONE ]` link |
| E1 PlanScreen (Solo+), no engagements | `NO ENGAGEMENTS YET` + `[ + NEW ENGAGEMENT ]` |
| H1 InvoiceScreen list, no invoices | `NO INVOICES YET` + `[ Generate from a job ]` |
| Search no-results | `NO RESULTS FOR "${query}"` muted |

**Style rule:** all empty states are ALL-CAPS body text + a single text-link to start the right action. No illustrations, no spot-art, no emoji, no "Looks like a quiet day!" friendliness.

---

## 14. Error states (inline)

| Surface | Error content |
|---|---|
| Form field validation | red text below field (`{color.error}`) — short, specific (`Required`, `Must be at least 6 chars`, `Email format invalid`) |
| Network error in screen body | full-width row at top of screen body: `Couldn't reach server. [ Retry ]` muted text + primary blue link |
| API error in dialog body | red text in dialog body (`{color.error}`); dialog stays open; primary action button re-enabled |
| Tier-gate 403 | NEVER an inline error — always invokes `LockedFeatureOverlay` |
| AI model load failed | inline status: `MODEL UNSUPPORTED ON THIS DEVICE — DOWNGRADE TO SOLO?` + downgrade CTA (existing pattern) |

**Forbidden error patterns:**
- ❌ Big red banners across the screen
- ❌ Modal error dialogs for routine failures
- ❌ Generic "Something went wrong" — always specific
- ❌ Error states that don't tell the user what to do next

---

## 15. Disabled states

| Element | Disabled render |
|---|---|
| Primary CTA button | fill `{color.surfaceVariant}`, text `{color.textMuted}`, no ripple, `Modifier.alpha(1.0f)` (don't dim — recolor) |
| Secondary text-link | text `{color.textMuted}` × 0.5 alpha, no ripple |
| Form input | bg `{color.surfaceVariant}`, text `{color.textMuted}`, border `{color.outlineVariant}` |
| Toggle ((●))/((○)) | gray glyph, no ripple, no haptic |
| Section row (whole row disabled) | `Modifier.alpha(0.5f)` on entire row contents; no ripple |

**Tier-gated UI is NOT "disabled" — it's locked with CTA.** Disabled is for genuine "you can't do this right now" (e.g., network offline, action in flight).

---

## 16. Focus / TalkBack interactions

### 16.1 Focus visualization
- **Default:** Compose default focus indicator (2dp outline `{color.primary}` `#0969DA`)
- **No custom focus animations**

### 16.2 TalkBack flow on overlay open
1. Focus moves to overlay top card automatically
2. Reads: title → body → tier label → founder counter (if present) → primary CTA → secondary CTA
3. User can navigate with swipe gestures
4. Tap-target is the entire CTA region

### 16.3 TalkBack on lock state row (EntitlementLock)
- Reads: section title + "Locked, requires Advanced tier" + body + chevron-right hint

### 16.4 TalkBack on PDF counter footer
- Reads: "${count} of 5 free PDF sends used this month" — no extra context (caption only)

### 16.5 TalkBack on TrialBanner
- Reads: full copy verbatim (since it's already informational + actionable)

---

## 17. Keyboard interactions (desktop portal — when relevant)

The desktop portal is online-only and currently in-progress. When net-new components ship there:

| Key | Behavior |
|---|---|
| `Tab` | focus advances through interactive elements in DOM order |
| `Shift+Tab` | focus reverses |
| `Enter` / `Space` | activates focused button / link |
| `Esc` | dismisses overlay (equivalent to background tap or "Maybe later") — does NOT dismiss confirmation dialogs (they require explicit choice) |
| `Cmd/Ctrl+Enter` | submits form (where applicable) |

---

## 18. Cross-component interaction sequencing rules

| Rule | Reason |
|---|---|
| Only ONE overlay open at a time | Prevents stacked confusion |
| Toast queue: only display most recent | Avoid Toast pile-up during bursts (e.g., reconciliation sync) |
| If a Toast is showing AND overlay opens, dismiss Toast immediately | Overlay is the higher-priority surface |
| Trial banner does NOT compete with overlays | Banner sits below status bar; overlays render above everything (z-order) |
| Founder counter updates DON'T trigger Toasts | Live updates inside overlay only |
| Cap-hit overlays don't auto-dismiss | User must explicitly choose CTA or "Maybe later" |
| Confirmation dialogs don't auto-dismiss on outside-tap | Explicit choice required |

---

## 19. Performance budgets

| Interaction | Budget |
|---|---|
| Overlay appear after CTA tap | < 200ms (Crossfade duration) |
| Toast appear after action | < 100ms |
| Status pill recompose | < 16ms (one frame) |
| Trial banner recompose | < 16ms |
| Founder counter snap-update | < 16ms |
| Tier resolver re-fetch on tier change | < 500ms (NFR-P6 backend) |
| Overlay fade-out + caller screen restore | < 300ms total |

If any interaction exceeds budget, file a performance issue — don't ship.

---

## 20. Coverage

| Component | Interaction patterns covered |
|---|---|
| LockedFeatureOverlay | appear, dismiss, primary CTA, secondary CTA, founder counter live update, network failure, reduced motion |
| TrialBanner | appear, disappear, day-counter tick, tap, copy variants |
| FounderSeatsCounter | loading, live decrement, threshold transitions, hold reservation, stale |
| TierPricingScreen | annual toggle, tier section variants, CTA states, loading skeletons, network unavailable |
| SubscriptionDetailScreen | per-state row variants, dialog invocation, payment-failure red text |
| CancelSubscriptionDialog | open, KEEP, Cancel-anyway, network failure, back-button |
| DeleteAccountDialog | open, KEEP, Delete-anyway with red text |
| EntitlementLock | tap → overlay, model-kept-after-downgrade caption |
| PdfSendCounterFooter | counter increment, cap-reached transition |
| GateHitToast | dismiss-confirm, mid-session reminder |
| Mesh / connectivity | pill state changes, reconciliation in-progress, peer in/out |
| AI Assistant | model load, ALIVE transition, RULE_BASED_FALLBACK |
| Toasts (general) | LENGTH usage, queue rules, forbidden uses |
| Haptics | toggle, long-press, forbidden uses |
| Loading patterns | skeleton dashes, literal `· · ·`, inline spinners, max-wait |
| Empty states | per-surface variants, style rule (no illustrations) |
| Error states | per-surface variants, forbidden patterns |
| Disabled states | per-element render rules |
| Focus / TalkBack | flow on overlay, on lock states, on counters |
| Keyboard (desktop) | Tab/Enter/Esc semantics |
| Sequencing | one overlay rule, Toast priority, banner z-order |
| Performance | per-interaction budgets |

---

## Linked specs

- `STATE-SPEC.md` — every state of every component (108 enumerated)
- `WIREFRAME-SPEC.md` — composable signatures
- `DESIGN-TOKENS.md` — token references
- `DESIGN-SYSTEM.md §10` — motion principles
- `EXTRACTED-PATTERNS.md` — original existing-app patterns this builds on
