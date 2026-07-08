package com.guildofsmiths.trademesh.ui.theme2

import com.guildofsmiths.trademesh.ui.Tokens2
import org.junit.Assert.assertEquals
import org.junit.Test

class SmithThemeTest {
    @Test
    fun lightColorsMirrorTokens() {
        val c = smithColorsFor(dark = false)
        assertEquals(Tokens2.Light.BgBase, c.bgBase)
        assertEquals(Tokens2.Light.Accent, c.accent)
        assertEquals(Tokens2.Light.StatusError, c.statusError)
    }

    @Test
    fun darkColorsMirrorTokens() {
        val c = smithColorsFor(dark = true)
        assertEquals(Tokens2.Dark.BgBase, c.bgBase)
        assertEquals(Tokens2.Dark.Accent, c.accent)
    }
}
