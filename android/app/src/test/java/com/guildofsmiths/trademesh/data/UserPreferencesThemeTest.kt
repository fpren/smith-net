package com.guildofsmiths.trademesh.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.guildofsmiths.trademesh.ui.theme2.ThemePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 9 (Appearance row + dark flip): round-trip coverage for
 * UserPreferences.getThemePreference()/setThemePreference() — the
 * SharedPreferences-backed persistence for the LIGHT/DARK/SYSTEM control in
 * SettingsScreen. resolveDark() itself (the pure resolution rule) is already
 * covered by SmithThemeMountTest (Task 1); this test only covers storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserPreferencesThemeTest {

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        UserPreferences.init(app)
    }

    @After
    fun tearDown() {
        UserPreferences.clear()
        // UserPreferences is a process-wide singleton, and the Job Board tests
        // (JobBoardViewModelCreateJobTest / LoadFailureTest) never call
        // UserPreferences.init() themselves — they rely on its private `prefs`
        // field staying null (so every read/write is a silent no-op) for
        // isolation between test classes sharing this JVM/Robolectric sandbox.
        // clear() above only empties the SharedPreferences content, not the
        // field itself, so without this reset, this test would leave `prefs`
        // pointing at a live (if emptied) SharedPreferences instance and any
        // later test class in the same run that constructs a JobBoardViewModel
        // would read/write through it instead of getting the no-op it expects,
        // leaking job data across unrelated test classes. Reset the field
        // directly so this test leaves UserPreferences exactly as it found it.
        val prefsField = UserPreferences.javaClass.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(UserPreferences, null)
    }

    @Test
    fun `defaults to SYSTEM when nothing has been stored yet`() {
        assertEquals(ThemePreference.SYSTEM, UserPreferences.getThemePreference())
    }

    @Test
    fun `setThemePreference LIGHT round-trips`() {
        UserPreferences.setThemePreference(ThemePreference.LIGHT)

        assertEquals(ThemePreference.LIGHT, UserPreferences.getThemePreference())
    }

    @Test
    fun `setThemePreference DARK round-trips`() {
        UserPreferences.setThemePreference(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, UserPreferences.getThemePreference())
    }

    @Test
    fun `setThemePreference SYSTEM round-trips after switching away and back`() {
        UserPreferences.setThemePreference(ThemePreference.DARK)
        UserPreferences.setThemePreference(ThemePreference.SYSTEM)

        assertEquals(ThemePreference.SYSTEM, UserPreferences.getThemePreference())
    }

    @Test
    fun `an unrecognized stored value falls back to SYSTEM instead of crashing`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("trademesh_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_preference", "NOT_A_REAL_ENUM_VALUE")
            .apply()

        assertEquals(ThemePreference.SYSTEM, UserPreferences.getThemePreference())
    }
}
