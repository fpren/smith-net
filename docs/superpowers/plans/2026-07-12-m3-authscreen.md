# Plan M3: AuthScreen Crew Soft Rebuild — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the AuthScreen as a Crew Soft surface — Syne wordmark, centered SmithCard, SmithTextField inputs, SmithButton actions — with the auth state machine and all behavior byte-identical.

**Architecture:** Pure visual reskin of one file. `AuthScreen.kt`'s state vars, `performSupabaseAuth`, and every onClick/onDone lambda are preserved verbatim; only the presentation tree changes. The private `ConsoleTextField` is deleted (superseded by `theme2/SmithTextField` from M2). The ASCII box banner dies; the terminal prompts become Inter labels.

**Tech Stack:** Kotlin / Jetpack Compose, theme2 components (SmithCard/SmithTextField/SmithButton), Gradle 8.2 + JDK 17.

## Global Constraints

- Gradle: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`, run from `/Users/fegensprenelon/smith-net/android/`.
- **Maestro-pinned strings — keep VERBATIM** (`android/maestro/smithnet_solo_e2e.yaml:48,66`): `"GUILD OF SMITHS"`, `"Built for the trades"`, and the login submit button's visible text `"[▶] LOGIN"`. TestTags `solo_e2e_auth_email` / `solo_e2e_auth_password` must survive on the email/password fields.
- Behavior identical: every state var, lambda body, validation, and `performSupabaseAuth` call untouched. In-flight guards (`isLoading`, `isResending`, `isResettingPassword`) keep gating their actions.
- ASCII glyph tokens (`[▶] [✓] [!] [↻] [←] [↷] [+] [−] [...]`) are the app vocabulary — keep them in copy where they exist today. No emoji.
- No Material widgets (material3.Text allowed). Colors only via `LocalSmithColors`; radii only via `Tokens2`; no literal `RoundedCornerShape(N.dp)` (the app-wide gate from M2 must stay at zero).
- Branch: `feat/design-m3-auth` off `master`.

---

### Task 1: Rebuild AuthScreen.kt

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/AuthScreen.kt` (full rewrite of the composable body; `performSupabaseAuth` unchanged; `ConsoleTextField` deleted)

**Interfaces:**
- Consumes: `SmithCard(modifier, ops, elevated, contentPadding, content)`, `SmithTextField(value, onValueChange, modifier, placeholder, label, ops, isPassword, singleLine, keyboardType, imeAction, onImeAction)`, `SmithButton(text, onClick, modifier, variant, enabled, shape)` + `SmithButtonVariant.{Primary, Ghost}` — all in `ui/theme2/` since M2. `Tokens2.RadiusControl`.
- Produces: the rebuilt screen; nothing downstream consumes its internals.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/fegensprenelon/smith-net && git checkout -b feat/design-m3-auth master
```

- [ ] **Step 2: Rewrite the AuthScreen composable**

Replace the body of `AuthScreen` (lines 38–586) and delete `ConsoleTextField` (lines 588–633). `performSupabaseAuth` (lines 635–707) stays byte-identical. Imports: drop `BasicTextField`, `KeyboardActions`, `KeyboardOptions`, `SolidColor`, `PasswordVisualTransformation`, `VisualTransformation`; add `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.text.style.TextAlign` (already present), `com.guildofsmiths.trademesh.ui.Tokens2`, `com.guildofsmiths.trademesh.ui.theme2.SmithButton`, `com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant`, `com.guildofsmiths.trademesh.ui.theme2.SmithCard`, `com.guildofsmiths.trademesh.ui.theme2.SmithTextField`.

The new presentation tree (state block lines 44–63 unchanged):

```kotlin
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Wordmark (Maestro-pinned copy - do not reword)
        Text(
            text = "GUILD OF SMITHS",
            style = SmithType.brand.copy(color = colors.ink)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Built for the trades",
            style = SmithType.caption.copy(color = colors.inkMuted)
        )

        Spacer(modifier = Modifier.height(28.dp))

        SmithCard(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
        ) {
            if (showResetPassword) {
                ResetPasswordContent(...)
            } else if (!showOfflineMode) {
                MainAuthContent(...)
            } else {
                OfflineModeContent(...)
            }
        }
    }
```

Rather than literal `...` placeholders, inline the three branches directly inside the SmithCard block, preserving today's structure. The exact per-branch trees:

**MAIN branch** (replaces lines 218–495; all lambdas copied verbatim from the current file):

```kotlin
                // Mode toggle - segmented control on the M2 pattern
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgSunken, RoundedCornerShape(Tokens2.RadiusControl))
                        .padding(4.dp)
                ) {
                    Text(
                        text = "LOGIN",
                        style = SmithType.action.copy(
                            color = if (isLoginMode) colors.accent else colors.inkMuted
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .background(if (isLoginMode) colors.bgPanel else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isLoginMode = true
                                errorMessage = null
                                showTroubleOptions = false
                            }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        text = "REGISTER",
                        style = SmithType.action.copy(
                            color = if (!isLoginMode) colors.accent else colors.inkMuted
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .background(if (!isLoginMode) colors.bgPanel else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                isLoginMode = false
                                errorMessage = null
                                showTroubleOptions = false
                            }
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!isLoginMode) {
                    SmithTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "John Smith",
                        label = "Your name",
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                SmithTextField(
                    value = email,
                    onValueChange = { email = it.lowercase().trim() },
                    placeholder = "you@example.com",
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.testTag("solo_e2e_auth_email"),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password label row keeps the Forgot? link, so the field itself is label-less
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Password",
                        style = SmithType.bodySmall.copy(color = colors.inkMuted)
                    )
                    if (isLoginMode) {
                        Text(
                            text = "Forgot?",
                            style = SmithType.caption.copy(color = colors.accent),
                            modifier = Modifier
                                .clickable { showResetPassword = true }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SmithTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "min 6 characters",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = { /* verbatim onDone lambda from current lines 325-353 */ },
                    modifier = Modifier.testTag("solo_e2e_auth_password"),
                )
```

then the success/error message blocks (current lines 359–406, unchanged Text trees incl. the `[↻] RESEND CONFIRMATION EMAIL` attention link), then the submit button:

```kotlin
                Spacer(modifier = Modifier.height(20.dp))
                SmithButton(
                    text = if (isLoading) {
                        "[...] ${if (isLoginMode) "LOGGING IN" else "CREATING ACCOUNT"}"
                    } else {
                        "[▶] ${if (isLoginMode) "LOGIN" else "CREATE ACCOUNT"}"
                    },
                    onClick = { /* verbatim scope.launch lambda from current lines 419-444 */ },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
```

then the trouble-options block (current lines 455–495) with one substitution: the two `[↷]` offline-mode entry points stay as caption/action Texts exactly as today.

**RESET branch** (replaces lines 97–216): keep header `Text("[↻] RESET PASSWORD", SmithType.header.copy(color = colors.accent))` and the two explainer captions; the email field becomes `SmithTextField(value = email, onValueChange = { email = it.lowercase().trim() }, placeholder = "you@example.com", label = "Email", keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, onImeAction = { /* verbatim lines 134-150 */ })`; the `[✓]`/`[!]` message blocks unchanged; the send button becomes:

```kotlin
            SmithButton(
                text = if (isResettingPassword) "[...] SENDING..." else "[▶] SEND RESET LINK",
                onClick = { /* verbatim scope.launch lambda from current lines 186-198 */ },
                enabled = !isResettingPassword && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
```

and the back link becomes `SmithButton(text = "[←] Back to Login", onClick = { showResetPassword = false; errorMessage = null; successMessage = null }, variant = SmithButtonVariant.Ghost)`.

**OFFLINE branch** (replaces lines 497–584): keep `Text("[!] OFFLINE MODE", SmithType.header.copy(color = colors.attention))` + the two captions; name field becomes `SmithTextField(value = displayName, onValueChange = { displayName = it }, placeholder = "Your Name", label = "Your name")`; error block unchanged; `[▶] START DEMO` becomes `SmithButton(text = "[▶] START DEMO", onClick = { /* verbatim lambda lines 550-567 */ }, modifier = Modifier.fillMaxWidth())`; back link becomes `SmithButton(text = "[←] Back to Login", onClick = { showOfflineMode = false; showTroubleOptions = false }, variant = SmithButtonVariant.Ghost)`.

Every `/* verbatim ... */` marker means: copy the exact lambda body from the current file at the cited lines — do not retype it from memory. Delete `ConsoleTextField` entirely once no references remain.

- [ ] **Step 3: Verify gates**

```bash
cd /Users/fegensprenelon/smith-net
grep -n "ConsoleTextField\|╔\|╚\|║\|> email:\|> password:\|> your_name:" android/app/src/main/java/com/guildofsmiths/trademesh/ui/AuthScreen.kt
grep -rn "RoundedCornerShape([0-9]" android/app/src/main/java --include="*.kt"
grep -n "GUILD OF SMITHS\|Built for the trades\|\[▶\] LOGIN\|solo_e2e_auth_email\|solo_e2e_auth_password" android/app/src/main/java/com/guildofsmiths/trademesh/ui/AuthScreen.kt
```

Expected: first grep ZERO (terminal artifacts gone); second grep ZERO (app-wide radius gate intact); third grep shows all five pinned strings/tags present.

- [ ] **Step 4: Build + unit suite**

```bash
cd /Users/fegensprenelon/smith-net/android
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./gradlew :app:testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/AuthScreen.kt
git commit -m "feat(android): M3 - AuthScreen Crew Soft rebuild

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Verification + merge

**Files:** none.

**Interfaces:** Consumes Task 1 on `feat/design-m3-auth`. Produces M3 merged to `master`.

- [ ] **Step 1: Clean build**

```bash
cd /Users/fegensprenelon/smith-net/android
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./gradlew clean :app:testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Maestro auth-step sanity (device/emulator required)**

If a device or emulator is attached: run the solo e2e flow (`maestro test android/maestro/smithnet_solo_e2e.yaml` with the flow's EMAIL/PASSWORD env) OR at minimum install the APK and manually walk: login, register toggle, forgot-password round trip, offline demo entry, dark theme. The testTag tap targets moved from a bare Box to SmithTextField's labeled Column — the tap still lands on the field, but this is the one change only a runtime check can prove. If no device: record as deferred to the device dark-QA gate (M1+M2 precedent) and note it in the merge report.

- [ ] **Step 3: Merge**

```bash
cd /Users/fegensprenelon/smith-net
git checkout master
git merge --no-ff feat/design-m3-auth -m "Merge feat/design-m3-auth: M3 AuthScreen Crew Soft rebuild

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
