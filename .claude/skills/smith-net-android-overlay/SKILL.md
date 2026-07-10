---
name: smith-net-android-overlay
description: Smith Net project-specific overlay on top of ecc-android-clean-architecture. Constrains generic Android Clean Architecture to Smith Net's actual layout — Jetpack Compose + Design System v2 (LocalSmithColors/SmithType/theme2) + BoundaryEngine for transport routing + on-device Llama via JNI + mesh BLE/WiFi-Direct + cord-based state via VectorClock. Use for ANY Android work in /android/.
---

# Smith Net Android overlay

This skill **layers on top of** `ecc-android-clean-architecture`. Generic Clean Architecture / KMP patterns apply where useful; Smith Net's specific structure overrides where they conflict.

## Foundation skill referenced

`ecc-android-clean-architecture` — Clean Architecture patterns for Android and Kotlin Multiplatform.

## Overrides

### Override 1: Package structure (current shipping)

```
com.guildofsmiths.trademesh
  ├── ai/                  — SmithAI (LlamaInference, AISupervisor, AIRouter, etc.)
  ├── data/                — Repositories, models, RoleContext, VectorClock, CordEntry
  ├── db/                  — local persistence
  ├── engine/              — BoundaryEngine (THE C-03 boundary singleton)
  ├── service/             — MeshService, ChatManager, GatewayClient, ReconciliationEngine
  ├── ui/                  — Compose screens
  │   ├── theme/           — Theme.kt (Material wrapper; scheme still light — removal tracked per spec §12)
  │   ├── theme2/          — Design System v2: SmithTheme (mounted at root), SmithType, Smith components, SmithStates, SmithTabular
  │   ├── components/      — shared Composables (BottomToolbar, LeftSidebar, TradePickerField, etc.)
  │   ├── dashboard/       — Dashboard + modules + viewmodel
  │   ├── jobboard/, jobpipeline/, newjob/, plan/, invoice/, expenses/, etc.
  │   ├── subscription/    — NEW from Step 11 (TierPricingScreen, SubscriptionDetailScreen)
  │   └── crew/            — NEW from Step 11 cycle 5
  ├── MainActivity.kt
  └── TradeMeshApplication.kt
```

**Don't reorganize the package structure.** New code follows the existing layout. New screens go under `ui/<domain>/`.

### Override 2: Colors from `LocalSmithColors`, styles from `SmithType`

(See `smith-net-design-system` for the full v2 rule.) Any new Composable reads
`val colors = LocalSmithColors.current` (declared right after the parameter
list — never inside a lambda parameter) and passes explicit colors with the
COLORLESS `SmithType` text styles: `Text(x, style = SmithType.body, color =
colors.ink)`. `ConsoleTheme` retains ONLY font families (`inter`,
`jetBrainsMono`, ...), string constants, and the BottomNavBar/ConsoleHeader/
ConsoleSeparator composables — its v1 color palette is DELETED; referencing it
will not compile. Never read `MaterialTheme.colorScheme`. Numeric readouts use
`SmithType.x.tabular`. Components: SmithButton / SmithDialog(+ `ops`,
`destructive`) / SmithConfirmDialog(`confirmEnabled`) / SmithSheet /
SmithLoading-Empty-ErrorState / SmithAvatar — all in `ui/theme2/` and
`ui/components/`. Sanctioned Material: `material3.Text` and the
CircularProgressIndicator inside SmithLoadingState.

### Override 3: BoundaryEngine is the routing singleton

When sending a message from Android, route via `BoundaryEngine` — not directly via `MeshService` or `ChatManager`. The boundary engine decides which transport (mesh / online / gateway) and which encryption / signing applies.

```kotlin
// ✅ CORRECT
BoundaryEngine.sendMessage(message)

// ❌ WRONG (bypasses routing logic + encryption + replay defense)
MeshService.broadcastMessage(message)
ChatManager.send(message)
```

Exception: when **inside** the BoundaryEngine itself, calling specific transport methods is correct.

### Override 4: Vector clocks on every UnifiedMessage

Every message carries `vectorClock: VectorClock` ({deviceId: counter}). When creating a new message, increment via `VectorClock.increment(deviceId)`. When receiving, merge via `VectorClock.merge(local, remote)`.

```kotlin
// ✅ CORRECT
val newClock = VectorClock.increment(currentClock, deviceId)
val msg = UnifiedMessage(..., vectorClock = newClock)

// ❌ WRONG (clockless)
val msg = UnifiedMessage(..., vectorClock = VectorClock())  // empty clock
```

### Override 5: SmithAI runs on-device only

Don't add cloud-AI calls from Android for SmithAI features. The `LlamaInference` class wraps llama.cpp via JNI. State machine: `SLEEPING → WAKING → ALIVE | RULE_BASED_FALLBACK`. The `RULE_BASED_FALLBACK` is the deterministic baseline available to all tiers.

If a server-side AI feature is genuinely needed, it goes through the backend's `llmInterface.ts` (C-04), and the Android client treats it as a normal API call — not a special "AI" path.

### Override 6: Tier-aware UI gates

(See `smith-net-tier-gating` skill.) For Android specifics:

- Read entitlements from `EntitlementsRepository.entitlements: StateFlow<Entitlements?>`
- Conditional render on `entitlements.caps.<capName>`
- For locked features: render `LockedFeatureOverlay` (per F4.1) with appropriate `LockVariant`
- For section-level locks: render `EntitlementLock` (per F4.5)

### Override 7: Role-aware UI gates

(See existing pattern in DashboardScreen.kt.) For role gating:

- Read role from `RoleContext.role` (or `RoleContext.can(Permission.X)`)
- Conditional render on the permission check
- **Hidden, not greyed** — feature absent for the role

### Override 8: ViewModel pattern

Existing pattern: `XxxViewModel : ViewModel()` with StateFlows. Compose calls `viewModel = viewModel()`. Don't introduce Hilt / Koin / Dagger for v1 — keep dep-injection manual. Don't introduce a new state-management library (no Redux, no MVI, no MOLECULE) — Compose state + ViewModel + Repository is the established pattern.

### Override 9: Repository pattern

Repositories are `object` singletons in `data/` package (e.g., `JobRepository`, `MessageRepository`, `BeaconRepository`, `CordRepository`). They expose StateFlows for observable state and suspend functions for actions. **Don't create new Repository instances** — extend the singleton or add new functions to an existing one.

### Override 10: Coroutines + StateFlow only

- No RxJava (this codebase doesn't use it; don't introduce)
- No LiveData (Compose-native StateFlow)
- No Flow.collect with manual side-effects in Composables — use `LaunchedEffect` for side-effects, `collectAsState()` for reads

### Override 11: Live data ticking pattern

For time-sensitive UI (e.g. "ON CLOCK Xh Ym Zs"):

```kotlin
LaunchedEffect(isClockedIn) {
    while (isClockedIn) {
        nowMs = System.currentTimeMillis()
        delay(1000)
    }
}
```

Never use `Timer`, never use `Handler`. Always coroutine `delay`.

### Override 12: Build flags govern beta features

`BuildFlags.SEED_DEMO_DATA` — gate demo data behind this. `BuildFlags.SUPABASE_ENABLED = false` — Supabase Realtime path. New beta features get `BuildFlags.<NAME>` flags, not feature-flag services.

### Override 13: 401s refresh the session

REST calls that carry a Bearer token wrap in `AuthedRequest.withAuthRetry`
(one refresh + one retry); pick the refresh matching the client's token store
— `AuthService.refreshToken()` for HttpClientFactory-routed clients,
`SupabaseAuth.refreshSession()` for clients reading SupabaseAuth directly (two
independent stores; refreshing the wrong one silently no-ops). Build the
Request INSIDE the retried block so the retry re-reads the fresh token. Both
refresh paths are single-flight — never add a parallel refresh.

### Override 14: Maestro guard

`android/maestro/smithnet_solo_e2e.yaml` pins visible strings (bracketed nav
labels from BottomNavBar, `[▶] LOGIN`, onboarding/proposal flow copy) and
`solo_e2e_*` testTags. Grep the yaml before renaming ANY user-visible string;
tags survive refactors on the same logical control.

## When generic Android Clean Architecture and this overlay conflict

This overlay wins. The foundation skill provides the Clean Architecture lens; Smith Net's actual structure constrains it. If you find a generic recommendation (e.g., "wrap repositories in a UseCase layer") that doesn't match the existing code: don't introduce it speculatively. Match the code as it is.

## Don't do

- ❌ Reorganize `/android/app/src/main/java/com/guildofsmiths/trademesh/` package structure
- ❌ Introduce Hilt / Koin / Dagger / RxJava / LiveData / Redux / MVI / Molecule / Square Anvil
- ❌ Use Material Compose widgets (AlertDialog/ModalBottomSheet/Button/TextField widgets — the purge is complete; Smith* components only; `material3.Text` allowed)
- ❌ Read from `MaterialTheme.*` or the deleted ConsoleTheme colors (use `LocalSmithColors` + `SmithType`)
- ❌ Bypass `BoundaryEngine` for outbound messages
- ❌ Send messages without VectorClock
- ❌ Call cloud AI from Android directly (server-side `llmInterface.ts` only, via API)
- ❌ Use `Timer` / `Handler.postDelayed` (use coroutine `delay`)
- ❌ Create new Repository singletons when an existing one fits
- ❌ Add `SUPABASE_ENABLED = true` for new features (Hetzner path is canonical)
- ❌ Resolve dark ad-hoc: theme flows from `SmithTheme(darkEnabled = true, themePreference, resolvedDark)` at the app root (UserPreferences.ThemePreference). One `resolveDark()` result feeds palette AND status bar — never resolve twice, never read `isSystemInDarkTheme()` for colors in a screen
- ❌ Persist ephemeral channel content locally past session (per ChannelPersistence.EPHEMERAL)

## Linked specs

- Foundation: `ecc-android-clean-architecture` skill
- `docs/architecture/ARCHITECTURE.md §1, §6` — Android in the system
- `docs/specs/DEV-READINESS.md §1.6` — Android subsystem inventory
- `.claude/skills/smith-net-design-system/SKILL.md` — Design System v2 rules
- `docs/superpowers/specs/2026-07-08-design-system-v2-design.md` — the v2 spec
- `.claude/skills/smith-net-determinism/SKILL.md` — VectorClock + Cord rules
- `.claude/skills/smith-net-architecture/SKILL.md` — overall conventions
