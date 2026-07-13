# Plan M4: Map Modernization — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dark-correct map tiles (ColorMatrix invert filter driven by the app theme), North Cobalt pin drawables replacing stock osmdroid markers, and the two duplicate MapView builders collapsed into one — closing the modern-look program.

**Architecture:** A new `ui/map/SmithMapKit.kt` owns the dark tile filter and the tinted pin factory. `SmithTheme` gains a `LocalSmithDark` CompositionLocal provided at its single existing resolution point (never resolve twice — the app-root rule). `CrewMapView` (DashboardModules.kt:1203) becomes the ONLY MapView builder: it absorbs `SiteMapModule`'s auto-framing behind an `autoFrame` parameter, and `SiteMapModule`'s inline `AndroidView` map (DashboardModules.kt:699-~800) is deleted in favor of a `CrewMapView` call. MapScreen (fillContainer=true) inherits everything.

**Tech Stack:** Kotlin / Compose AndroidView interop, osmdroid (MAPNIK raster tiles, `TilesOverlay.setColorFilter`), Android vector drawables, Gradle 8.2 + JDK 17.

## Global Constraints

- Gradle: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`, run from `/Users/fegensprenelon/smith-net/android/`.
- Resolve dark ONCE: `LocalSmithDark` is provided only from `SmithTheme`'s existing `dark` value; no new `resolveDark`/`isSystemInDarkTheme` calls anywhere else.
- Colors only via `LocalSmithColors` (pins tint from `colors.accent` / `colors.statusOnline` via `.toArgb()` — never hex). No literal `RoundedCornerShape(N.dp)` (M2 gate stays zero). No emoji.
- Marker behavior byte-identical: titles, snippets, click listeners, anchor `(ANCHOR_CENTER, ANCHOR_BOTTOM)`, the `bySite`-dedupe rule for job markers, the fillContainer tile-refresh hack, and SITE_COORDS crew-presence lookups all preserved.
- `SiteMapModule`'s solo/crew marker split and its `selectedSite`/`selectedJob` panel state must behave identically after the collapse.
- QR codes stay light-fixed (out of scope). Web map untouched.
- Branch: `feat/design-m4-map` off `master`.

---

### Task 1: SmithMapKit + LocalSmithDark + pin drawable

**Files:**
- Create: `android/app/src/main/res/drawable/ic_smith_pin.xml`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/map/SmithMapKit.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithTheme.kt:79` (provide LocalSmithDark)
- Test: `android/app/src/test/java/com/guildofsmiths/trademesh/ui/theme2/SmithThemeTest.kt` (or a new small test file if cleaner)

**Interfaces:**
- Consumes: `SmithTheme`'s already-resolved `dark` val (line 77).
- Produces (Task 2 relies on these exact names):

```kotlin
val LocalSmithDark: androidx.compose.runtime.ProvidableCompositionLocal<Boolean>  // theme2/SmithTheme.kt
fun MapView.applySmithMapTheme(dark: Boolean)                                     // ui/map/SmithMapKit.kt
fun smithPin(context: Context, tint: Int): Drawable?                              // ui/map/SmithMapKit.kt
```

- [ ] **Step 1: Create the branch**

```bash
cd /Users/fegensprenelon/smith-net && git checkout -b feat/design-m4-map master
```

- [ ] **Step 2: Failing test for the dark filter matrix + Local default**

Add to `SmithThemeTest.kt` (plain JVM, same idiom as existing tests):

```kotlin
    @Test
    fun localSmithDark_defaultsToLight() {
        // Static default outside any SmithTheme provider must be light.
        assertEquals(false, LocalSmithDark.defaultValueHolder())
    }
```

If `defaultValueHolder` isn't accessible on the Local (API varies), test instead via the kit's pure function — add to a new `ui/map/SmithMapKitTest.kt` (JVM):

```kotlin
package com.guildofsmiths.trademesh.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmithMapKitTest {
    @Test
    fun darkTileMatrix_invertsRgbPreservesAlpha() {
        val m = darkTileMatrixValues()
        assertEquals(-1f, m[0]); assertEquals(255f, m[4])   // R inverted + offset
        assertEquals(-1f, m[6]); assertEquals(255f, m[9])   // G
        assertEquals(-1f, m[12]); assertEquals(255f, m[14]) // B
        assertEquals(1f, m[18]); assertEquals(0f, m[19])    // A untouched
    }
}
```

Run: `cd /Users/fegensprenelon/smith-net/android && export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && ./gradlew :app:testDebugUnitTest --tests "com.guildofsmiths.trademesh.ui.map.SmithMapKitTest"`
Expected: FAIL (symbol not found).

- [ ] **Step 3: Implement**

`ic_smith_pin.xml` — a teardrop map pin, 26×34dp, white fill so runtime tint owns the color, with a punched center dot:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="26dp" android:height="34dp"
    android:viewportWidth="26" android:viewportHeight="34">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M13,0 C5.82,0 0,5.82 0,13 C0,22.75 13,34 13,34 C13,34 26,22.75 26,13 C26,5.82 20.18,0 13,0 Z" />
    <path
        android:fillColor="#00000000"
        android:pathData="M13,8 a5,5 0 1,0 0.001,0 Z"
        android:fillType="evenOdd" />
</vector>
```

(If the transparent inner dot renders solid under tint, switch the outer path to `fillType="evenOdd"` with both subpaths in one path element — implementer verifies visually via the build only; exact dot rendering is a device-QA item.)

`SmithMapKit.kt`:

```kotlin
package com.guildofsmiths.trademesh.ui.map

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.guildofsmiths.trademesh.R
import org.osmdroid.views.MapView

/**
 * smith net v2 map theming. MAPNIK raster tiles are light-only; in dark
 * theme we invert them via a ColorMatrix on the tiles overlay (concat'd
 * with a mild desaturation so water/parks keep plausible hues). Pins are
 * ic_smith_pin tinted from LocalSmithColors at the call site — cobalt
 * accent for jobs, statusOnline for crew presence.
 */

// Exposed for the JVM unit test: the raw inversion matrix values.
fun darkTileMatrixValues(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

private val darkTileFilter: ColorMatrixColorFilter by lazy {
    val invert = ColorMatrix(darkTileMatrixValues())
    val desat = ColorMatrix().apply { setSaturation(0.85f) }
    invert.postConcat(desat)
    ColorMatrixColorFilter(invert)
}

fun MapView.applySmithMapTheme(dark: Boolean) {
    overlayManager.tilesOverlay.setColorFilter(if (dark) darkTileFilter else null)
}

fun smithPin(context: Context, tint: Int): Drawable? =
    ContextCompat.getDrawable(context, R.drawable.ic_smith_pin)?.mutate()?.apply {
        setTint(tint)
    }
```

`SmithTheme.kt` — add above `SmithTheme`:

```kotlin
/** Resolved dark flag for non-Compose consumers (osmdroid tile filter). Provided by [SmithTheme]. */
val LocalSmithDark = staticCompositionLocalOf { false }
```

and change line 79 to provide both:

```kotlin
    CompositionLocalProvider(LocalSmithColors provides colors, LocalSmithDark provides dark, content = content)
```

- [ ] **Step 4: Test passes + build**

```bash
./gradlew :app:testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL, new test green, existing SmithThemeTest suite untouched-green.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net
git add android/app/src/main/res/drawable/ic_smith_pin.xml android/app/src/main/java/com/guildofsmiths/trademesh/ui/map/SmithMapKit.kt android/app/src/main/java/com/guildofsmiths/trademesh/ui/theme2/SmithTheme.kt android/app/src/test/java/com/guildofsmiths/trademesh/ui/map/SmithMapKitTest.kt
git commit -m "feat(android): M4 - SmithMapKit dark tile filter + cobalt pin + LocalSmithDark

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: One MapView builder — theme + pins in CrewMapView, SiteMapModule collapse

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardModules.kt` — `CrewMapView` (~1203-1326) gains theme/pins/autoFrame; `SiteMapModule`'s inline `AndroidView` map block (~694-800, the embedded `Box`) is replaced by a `CrewMapView` call; the module-local `siteCoords` copy (~650-654) is deleted (CrewMapView's `SITE_COORDS` already holds the same three entries).

**Interfaces:**
- Consumes: `LocalSmithDark`, `MapView.applySmithMapTheme(dark)`, `smithPin(context, tint)` from Task 1; `LocalSmithColors` (accent, statusOnline); `androidx.compose.ui.graphics.toArgb`.
- Produces: `CrewMapView(crew, activeJobs, onSiteClick, onJobClick, fillContainer, autoFrame: Boolean = false, embeddedHeight: Dp = 200.dp)` — MapScreen's existing call keeps compiling (new params defaulted).

- [ ] **Step 1: Theme + pins inside CrewMapView**

At the top of `CrewMapView` add:

```kotlin
    val dark = LocalSmithDark.current
    val jobPinTint = colors.accent.toArgb()
    val crewPinTint = colors.statusOnline.toArgb()
```

In the `factory` block after `setTileSource(...)`: `applySmithMapTheme(dark)`.
At the top of the `update` block (runs on recomposition, so theme flips live): `mapView.applySmithMapTheme(dark)`.
On every crew-site `Marker(...)`: add `icon = smithPin(mapView.context, crewPinTint)`.
On every job `Marker(...)`: add `icon = smithPin(mapView.context, jobPinTint)`.

- [ ] **Step 2: autoFrame + embeddedHeight params**

Extend the signature:

```kotlin
fun CrewMapView(
    crew: List<com.guildofsmiths.trademesh.data.CrewPresenceInfo>,
    activeJobs: List<Job> = emptyList(),
    onSiteClick: (siteAddress: String) -> Unit = {},
    onJobClick: (jobId: String) -> Unit = {},
    fillContainer: Boolean = false,
    autoFrame: Boolean = false,
    embeddedHeight: Dp = 200.dp,
)
```

`embeddedHeight` replaces the hardcoded `200.dp` in `sizeModifier`. For `autoFrame`, collect every placed GeoPoint into a `placedCoords` list inside `update` (both marker loops), and port `SiteMapModule`'s framing block VERBATIM (current lines ~760-800: the `framed` guard, single-point center+zoom 15.0, multi-point `BoundingBox.fromGeoPointsSafe(...).increaseByScale(1.3f)` + `zoomToBoundingBox(box, false, 24)`, and its `postDelayed` retry if present) — guarded by `if (autoFrame && ...)`. The `framed` flag becomes a `remember { mutableStateOf(false) }` inside CrewMapView.

- [ ] **Step 3: Collapse SiteMapModule's inline map**

Replace `SiteMapModule`'s embedded map `Box`+`AndroidView` (~694-800) with:

```kotlin
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (isSolo) {
                CrewMapView(
                    crew = emptyList(),
                    activeJobs = activeJobs,
                    onJobClick = { jobId ->
                        selectedJob = activeJobs.firstOrNull { it.id == jobId }
                        selectedSite = null
                    },
                    autoFrame = true,
                    embeddedHeight = 180.dp,
                )
            } else {
                CrewMapView(
                    crew = crew,
                    onSiteClick = { site ->
                        selectedSite = site
                        selectedJob = null
                    },
                    autoFrame = true,
                    embeddedHeight = 180.dp,
                )
            }
        }
```

Keep the module's header row and the existing `selectedSite`/`selectedJob` detail panels below it untouched. Delete the now-unused local `siteCoords` map and the `LaunchedEffect(Unit) { Configuration... }` duplicate (CrewMapView has its own), plus any imports the compiler flags as unused. NOTE the behavior deltas to preserve/accept: the old inline map used zoom 11.5 vs CrewMapView's 12.0 (initial zoom is immediately overridden by autoFrame framing — acceptable); the old solo marker snippet/title text matches CrewMapView's job-marker text already (verify strings side-by-side — they were copied from the same map-wiring fix); the old non-solo snippet was `"$activeOnSite/${members.size} on site"` while CrewMapView's is `"$names ($activeCount/${members.size} on site)"` — CrewMapView's richer snippet WINS (document in the report as the one intentional copy delta).

- [ ] **Step 4: Gates + build**

```bash
cd /Users/fegensprenelon/smith-net
grep -n "TileSourceFactory.MAPNIK" android/app/src/main/java --include="*.kt" -r
grep -rn "RoundedCornerShape([0-9]" android/app/src/main/java --include="*.kt"
grep -n "siteCoords" android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardModules.kt
cd android && export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home && ./gradlew :app:testDebugUnitTest assembleDebug
```

Expected: exactly ONE `MAPNIK` hit (CrewMapView's factory); radius gate zero; `siteCoords` local copy gone (only `SITE_COORDS` remains); BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardModules.kt
git commit -m "feat(android): M4 - dark map tiles + cobalt pins; single MapView builder

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Verification + merge (closes the modern-look program)

**Files:** none.

**Interfaces:** Consumes Tasks 1-2 on `feat/design-m4-map`. Produces M4 merged; program M1-M4 complete.

- [ ] **Step 1: Clean build + full gates**

```bash
cd /Users/fegensprenelon/smith-net/android
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
./gradlew clean :app:testDebugUnitTest assembleDebug
cd /Users/fegensprenelon/smith-net
grep -rn "RoundedCornerShape([0-9]" android/app/src/main/java --include="*.kt"
grep -rn "[Pp]lex" android/app/src/main --include="*.kt" | grep -viE "complex|duplex"
```

Expected: BUILD SUCCESSFUL; both greps zero.

- [ ] **Step 2: Device/emulator visual pass (or record deferral)**

If a device is attached: open the dashboard map module and the full Map screen in BOTH themes — dark tiles must invert (no bright rectangle), pins render cobalt (jobs) / green (crew), marker taps still open the right panels, autoFrame still frames pins. If no device: record as deferred to the device dark-QA gate, which now carries M1-M4.

- [ ] **Step 3: Merge**

```bash
cd /Users/fegensprenelon/smith-net
git checkout master
git merge --no-ff feat/design-m4-map -m "Merge feat/design-m4-map: M4 dark map tiles + cobalt pins - modern-look program complete

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
