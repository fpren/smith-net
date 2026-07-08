package com.guildofsmiths.trademesh.e2e

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guildofsmiths.trademesh.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * STEP 0 of the Solo E2E spec: "App loads without crash".
 *
 * Uses ActivityScenario (not Compose/Espresso) deliberately: the bundled
 * Espresso 3.5.1 reflectively calls android.hardware.input.InputManager.getInstance,
 * which is removed on API 35+ emulators, so a ComposeTestRule launch throws on
 * those images. ActivityScenario launches the real activity through the normal
 * lifecycle without that path. A launch crash (onCreate/onStart) would leave the
 * scenario below STARTED. We assert "at least STARTED" rather than RESUMED because
 * the app may immediately surface a permission dialog or navigate to login on top,
 * which legitimately leaves MainActivity paused at STARTED.
 *
 * The app has no testTag/semantics identifiers, so this is launch-only; the full
 * flow is asserted in SoloPipelineE2ETest.
 */
@RunWith(AndroidJUnit4::class)
class SoloLaunchSmokeTest {

    @Test
    fun appLaunches_withoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MainActivity should reach at least STARTED without crashing (was ${scenario.state})",
                scenario.state.isAtLeast(Lifecycle.State.STARTED),
            )
        }
    }
}
