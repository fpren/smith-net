package com.guildofsmiths.trademesh.ui.theme2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pure-JVM coverage for [resolveDark]: the dark-mode resolution rule shared by
 * [SmithTheme] and (eventually, Task 9) the root chrome. darkEnabled is the master
 * kill switch — while it is false (Plans 4-5), every preference resolves to light,
 * regardless of system theme.
 */
class SmithThemeMountTest {

    @Test
    fun darkEnabledFalse_forcesLight_regardlessOfPreferenceOrSystem() {
        assertFalse(resolveDark(ThemePreference.LIGHT, systemDark = false, darkEnabled = false))
        assertFalse(resolveDark(ThemePreference.LIGHT, systemDark = true, darkEnabled = false))
        assertFalse(resolveDark(ThemePreference.DARK, systemDark = false, darkEnabled = false))
        assertFalse(resolveDark(ThemePreference.DARK, systemDark = true, darkEnabled = false))
        assertFalse(resolveDark(ThemePreference.SYSTEM, systemDark = false, darkEnabled = false))
        assertFalse(resolveDark(ThemePreference.SYSTEM, systemDark = true, darkEnabled = false))
    }

    @Test
    fun lightPreference_isAlwaysLight_whenEnabled() {
        assertEquals(false, resolveDark(ThemePreference.LIGHT, systemDark = false, darkEnabled = true))
        assertEquals(false, resolveDark(ThemePreference.LIGHT, systemDark = true, darkEnabled = true))
    }

    @Test
    fun darkPreference_isAlwaysDark_whenEnabled() {
        assertEquals(true, resolveDark(ThemePreference.DARK, systemDark = false, darkEnabled = true))
        assertEquals(true, resolveDark(ThemePreference.DARK, systemDark = true, darkEnabled = true))
    }

    @Test
    fun systemPreference_followsSystem_whenEnabled() {
        assertEquals(false, resolveDark(ThemePreference.SYSTEM, systemDark = false, darkEnabled = true))
        assertEquals(true, resolveDark(ThemePreference.SYSTEM, systemDark = true, darkEnabled = true))
    }
}
