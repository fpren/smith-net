package com.guildofsmiths.trademesh.ui.theme2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * TDD for [TextStyle.tabular]: applies tabular (monospaced-digit) numeral
 * font features without mutating the receiver style (TextStyle.copy semantics).
 */
class SmithTabularTest {

    @Test
    fun tabular_setsTnumFontFeatureSettings() {
        assertEquals("tnum", SmithType.caption.tabular.fontFeatureSettings)
    }

    @Test
    fun tabular_leavesOriginalStyleUnchanged() {
        val original = SmithType.caption
        val tabularized = original.tabular

        assertNotEquals(original.fontFeatureSettings, tabularized.fontFeatureSettings)
        assertEquals(null, original.fontFeatureSettings)
        assertEquals(original.fontSize, tabularized.fontSize)
        assertEquals(original.fontWeight, tabularized.fontWeight)
        assertEquals(original.fontFamily, tabularized.fontFamily)
        assertEquals(original.color, tabularized.color)
    }
}
