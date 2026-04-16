# Phase 1: Role Context Layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing backend role system into the Android app so the UI knows who the user is and can gate features by role — the foundation for all role-separated surfaces.

**Architecture:** The backend already has a 6-tier role enum (`SOLO → TEAM_MEMBER → TEAM_LEAD → FOREMAN → ENTERPRISE → ADMIN`) with 21 permissions. `AuthService.getUserRole()` already returns the role string. We create a `RoleContext` singleton that mirrors the backend permission matrix, expose it throughout the app, and use it to conditionally render the first role-gated features: the foreman mesh/gateway section in Settings and a crew-aware onboarding step.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, Supabase Auth

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `android/.../data/RoleContext.kt` | **Create** | Singleton: current UserRole, permission matrix, `can(permission)` helper |
| `android/.../data/UserRole.kt` | **Create** | Enum mirroring backend `UserRole` + `Permission` enums |
| `android/.../service/AuthService.kt` | **Modify** | Initialize `RoleContext` on login/register, expose typed role |
| `android/.../data/UserPreferences.kt` | **Modify** | Add `getUserRole()`/`setUserRole()` for persistence alongside trade role |
| `android/.../ui/SettingsScreen.kt` | **Modify** | Replace `if (false)` with `RoleContext.can(GATEWAY_RELAY)` |
| `android/.../ui/OnboardingScreen.kt` | **Modify** | Add crew-detection step between ABOUT_YOU and DONE |
| `android/.../ui/Navigation.kt` | **Modify** | Pass `RoleContext` into nav graph, conditionally hide nav items |
| `android/.../ui/dashboard/DashboardScreen.kt` | **Modify** | Show crew status section for foreman role |

---

### Task 1: Create UserRole and Permission Enums

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/UserRole.kt`

- [ ] **Step 1: Create the UserRole enum mirroring backend**

```kotlin
package com.guildofsmiths.trademesh.data

enum class UserRole(val key: String) {
    SOLO("solo"),
    TEAM_MEMBER("team_member"),
    TEAM_LEAD("team_lead"),
    FOREMAN("foreman"),
    ENTERPRISE("enterprise"),
    ADMIN("admin");

    companion object {
        fun fromString(value: String?): UserRole =
            entries.firstOrNull { it.key == value } ?: SOLO
    }
}

enum class Permission {
    SEND_MESSAGE,
    DELETE_OWN_MESSAGE,
    DELETE_ANY_MESSAGE,
    CREATE_CHANNEL,
    DELETE_CHANNEL,
    MANAGE_CHANNEL_MEMBERS,
    CLEAR_CHANNEL,
    SEND_MEDIA,
    USE_MESH,
    GATEWAY_RELAY,
    MANAGE_USERS,
    VIEW_AUDIT_LOGS,
    MANAGE_ROLES,
    CREATE_ORG,
    MANAGE_ORG,
    INVITE_MEMBERS,
    MANAGE_CREW,
    VIEW_FINANCIALS,
    MANAGE_JOBS,
    VIEW_ALL_JOBS,
    VIEW_REPORTS,
}

val ROLE_PERMISSIONS: Map<UserRole, Set<Permission>> = mapOf(
    UserRole.SOLO to setOf(
        Permission.SEND_MESSAGE,
        Permission.DELETE_OWN_MESSAGE,
        Permission.CREATE_CHANNEL,
        Permission.SEND_MEDIA,
        Permission.USE_MESH,
        Permission.MANAGE_JOBS,
    ),
    UserRole.TEAM_MEMBER to setOf(
        Permission.SEND_MESSAGE,
        Permission.DELETE_OWN_MESSAGE,
        Permission.CREATE_CHANNEL,
        Permission.SEND_MEDIA,
        Permission.USE_MESH,
    ),
    UserRole.TEAM_LEAD to setOf(
        Permission.SEND_MESSAGE,
        Permission.DELETE_OWN_MESSAGE,
        Permission.DELETE_ANY_MESSAGE,
        Permission.CREATE_CHANNEL,
        Permission.MANAGE_CHANNEL_MEMBERS,
        Permission.CLEAR_CHANNEL,
        Permission.SEND_MEDIA,
        Permission.USE_MESH,
        Permission.INVITE_MEMBERS,
        Permission.MANAGE_CREW,
        Permission.MANAGE_JOBS,
        Permission.VIEW_ALL_JOBS,
    ),
    UserRole.FOREMAN to setOf(
        Permission.SEND_MESSAGE,
        Permission.DELETE_OWN_MESSAGE,
        Permission.DELETE_ANY_MESSAGE,
        Permission.CREATE_CHANNEL,
        Permission.DELETE_CHANNEL,
        Permission.MANAGE_CHANNEL_MEMBERS,
        Permission.CLEAR_CHANNEL,
        Permission.SEND_MEDIA,
        Permission.USE_MESH,
        Permission.GATEWAY_RELAY,
        Permission.VIEW_AUDIT_LOGS,
        Permission.INVITE_MEMBERS,
        Permission.MANAGE_CREW,
        Permission.MANAGE_JOBS,
        Permission.VIEW_ALL_JOBS,
        Permission.VIEW_FINANCIALS,
        Permission.VIEW_REPORTS,
    ),
    UserRole.ENTERPRISE to setOf(
        Permission.SEND_MESSAGE,
        Permission.DELETE_OWN_MESSAGE,
        Permission.DELETE_ANY_MESSAGE,
        Permission.CREATE_CHANNEL,
        Permission.DELETE_CHANNEL,
        Permission.MANAGE_CHANNEL_MEMBERS,
        Permission.CLEAR_CHANNEL,
        Permission.SEND_MEDIA,
        Permission.USE_MESH,
        Permission.GATEWAY_RELAY,
        Permission.VIEW_AUDIT_LOGS,
        Permission.MANAGE_USERS,
        Permission.MANAGE_ROLES,
        Permission.CREATE_ORG,
        Permission.MANAGE_ORG,
        Permission.INVITE_MEMBERS,
        Permission.MANAGE_CREW,
        Permission.MANAGE_JOBS,
        Permission.VIEW_ALL_JOBS,
        Permission.VIEW_FINANCIALS,
        Permission.VIEW_REPORTS,
    ),
    UserRole.ADMIN to Permission.entries.toSet(),
)
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/UserRole.kt
git commit -m "feat: add UserRole and Permission enums mirroring backend"
```

---

### Task 2: Create RoleContext Singleton

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/RoleContext.kt`

- [ ] **Step 1: Create the RoleContext object**

```kotlin
package com.guildofsmiths.trademesh.data

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object RoleContext {
    var role by mutableStateOf(UserRole.SOLO)
        private set

    val permissions: Set<Permission>
        get() = ROLE_PERMISSIONS[role] ?: emptySet()

    fun can(permission: Permission): Boolean =
        permissions.contains(permission)

    fun isAtLeast(minimumRole: UserRole): Boolean =
        role.ordinal >= minimumRole.ordinal

    fun isSolo(): Boolean = role == UserRole.SOLO
    fun isForeman(): Boolean = isAtLeast(UserRole.FOREMAN)
    fun isAdmin(): Boolean = isAtLeast(UserRole.ADMIN)

    fun setRole(newRole: UserRole) {
        role = newRole
    }

    fun setRoleFromString(roleString: String?) {
        role = UserRole.fromString(roleString)
    }

    fun reset() {
        role = UserRole.SOLO
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/RoleContext.kt
git commit -m "feat: add RoleContext singleton with permission checking"
```

---

### Task 3: Wire RoleContext into AuthService

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/service/AuthService.kt`

- [ ] **Step 1: Read the current AuthService**

Read the file to find the exact `saveAuthData()` method and `getUserRole()` method.

- [ ] **Step 2: Add RoleContext initialization to saveAuthData()**

In the `saveAuthData()` method (around line 301-318), after the line that saves `KEY_USER_ROLE`, add:

```kotlin
RoleContext.setRoleFromString(role)
```

- [ ] **Step 3: Add RoleContext initialization on app startup**

Find the `init` block or the method called when the service is created. Add initialization that reads the persisted role and sets RoleContext:

```kotlin
// In the init block or constructor
RoleContext.setRoleFromString(getUserRole())
```

- [ ] **Step 4: Clear RoleContext on logout**

Find the logout/signout method and add:

```kotlin
RoleContext.reset()
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/service/AuthService.kt
git commit -m "feat: wire RoleContext into AuthService login/logout lifecycle"
```

---

### Task 4: Initialize RoleContext on App Startup

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/TradeMeshApplication.kt`

- [ ] **Step 1: Read the Application class**

Read the file to find where services are initialized.

- [ ] **Step 2: Add RoleContext initialization in onCreate()**

After `AuthService` is available, add:

```kotlin
// Initialize role context from persisted auth data
val authService = AuthService(this)
RoleContext.setRoleFromString(authService.getUserRole())
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/TradeMeshApplication.kt
git commit -m "feat: initialize RoleContext from persisted role on app startup"
```

---

### Task 5: Gate Foreman Features in SettingsScreen

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/SettingsScreen.kt`

- [ ] **Step 1: Read SettingsScreen to find the if(false) block**

Read lines 130-270 to see the exact structure of the hidden mesh/gateway section.

- [ ] **Step 2: Replace `if (false)` with RoleContext check**

Find the line (around line 137):
```kotlin
if (false) { // TODO: Show when foreman/dispatcher mode is enabled
```

Replace with:
```kotlin
if (RoleContext.can(Permission.GATEWAY_RELAY)) {
```

Add the import at the top of the file:
```kotlin
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/SettingsScreen.kt
git commit -m "feat: gate mesh/gateway settings behind GATEWAY_RELAY permission"
```

---

### Task 6: Add Crew Detection to Onboarding

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt`

- [ ] **Step 1: Read the onboarding flow**

Read the file to understand the current screen enum and flow structure.

- [ ] **Step 2: Add CREW_CHECK screen to the flow**

Find the `OnboardingScreen` enum (around line 40-44). Add a new screen between ABOUT_YOU and DONE:

```kotlin
enum class OnboardingStep {
    TRADE,
    ABOUT_YOU,
    CREW_CHECK,  // New
    DONE
}
```

- [ ] **Step 3: Create the crew detection composable**

Add a new composable within the onboarding file. This screen asks one question: "Do you manage a crew?" with two options:

```kotlin
@Composable
fun CrewCheckContent(
    onSolo: () -> Unit,
    onForeman: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "How do you work?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A2520),
        )
        Text(
            text = "This shapes your SmithNet experience. You can change this later in Settings.",
            fontSize = 13.sp,
            color = Color(0xFF8C8478),
        )

        // Solo option
        Surface(
            onClick = onSolo,
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFAFAF8),
            border = BorderStroke(0.5.dp, Color(0x12000000)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "I work solo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A2520),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jobs, time tracking, invoicing — just for me.",
                    fontSize = 12.sp,
                    color = Color(0xFF8C8478),
                )
            }
        }

        // Foreman option
        Surface(
            onClick = onForeman,
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFAFAF8),
            border = BorderStroke(0.5.dp, Color(0x12000000)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "I manage a crew",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2A2520),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crew tracking, dispatch, team invoicing, mesh relay.",
                    fontSize = 12.sp,
                    color = Color(0xFF8C8478),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Wire the crew check into the onboarding flow**

In the main onboarding `when` block that switches on the current step, add the CREW_CHECK case. On "solo" selection, keep `UserRole.SOLO`. On "foreman" selection, set `RoleContext.setRole(UserRole.FOREMAN)` and persist it. Then advance to DONE.

Find the navigation logic and ensure ABOUT_YOU → CREW_CHECK → DONE.

- [ ] **Step 5: Persist the selected role**

After the user picks solo or foreman, call:
```kotlin
// Solo path
RoleContext.setRole(UserRole.SOLO)
authService.updateUserRole("solo")

// Foreman path  
RoleContext.setRole(UserRole.FOREMAN)
authService.updateUserRole("foreman")
```

Note: `updateUserRole()` may need to be added to `AuthService` — it should save to SharedPreferences and optionally POST to the backend to update the user's role.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/OnboardingScreen.kt
git commit -m "feat: add crew detection step to onboarding flow"
```

---

### Task 7: Add updateUserRole to AuthService

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/service/AuthService.kt`

- [ ] **Step 1: Add updateUserRole method**

Add a method that persists the role locally and updates the backend:

```kotlin
fun updateUserRole(role: String) {
    prefs.edit().putString(KEY_USER_ROLE, role).apply()
    RoleContext.setRoleFromString(role)
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/service/AuthService.kt
git commit -m "feat: add updateUserRole method to AuthService"
```

---

### Task 8: Role-Aware Navigation

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt`

- [ ] **Step 1: Read Navigation.kt**

Read the file to understand how the bottom toolbar and sidebar are structured, what items are displayed, and how navigation is triggered.

- [ ] **Step 2: Add role-based visibility to nav items**

The goal is NOT to block navigation (users can still deep-link), but to **hide nav items** that aren't relevant to the user's role. 

Find the bottom toolbar or sidebar composable. For items that should be hidden for certain roles, wrap them in a role check:

```kotlin
// Example: Only show Job Pipeline for users who can manage jobs
if (RoleContext.can(Permission.MANAGE_JOBS)) {
    // Job Pipeline nav item
}

// Example: Only show Time Tracking for everyone (all roles can clock in)
// Always visible — no gate needed
```

The specific gating:
- **Dashboard**: Always visible
- **Job Pipeline**: Visible when `RoleContext.can(Permission.MANAGE_JOBS)` (hidden for TEAM_MEMBER)
- **Time Tracking**: Always visible
- **Messaging** (Beacons/Channels): Always visible
- **Invoice**: Visible when `RoleContext.can(Permission.VIEW_FINANCIALS)` or `RoleContext.isSolo()` (solo users invoice for themselves)
- **Archive**: Always visible

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt
git commit -m "feat: add role-based visibility to navigation items"
```

---

### Task 9: Verify End-to-End

- [ ] **Step 1: Build the app**

```bash
cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install and test on emulator**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew installDebug
adb shell monkey -p com.guildofsmiths.trademesh -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 3: Manual verification checklist**

1. Fresh install → onboarding should show the new "How do you work?" screen after About You
2. Select "I work solo" → Settings should NOT show Mesh Connection / Gateway Relay sections
3. Go to Settings → Change role (if exposed) or clear data → re-onboard as "I manage a crew"
4. Settings should NOW show Mesh Connection / Gateway Relay sections
5. Navigation items should reflect role (Job Pipeline visible for solo/foreman, hidden for team_member)

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: address issues found during role context verification"
```
