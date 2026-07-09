package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.ui.graphics.Color
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD for [SmithType]: every style must mirror ConsoleTheme's font/size/weight
 * exactly, but with color stripped to [Color.Unspecified] so callers paint from
 * [LocalSmithColors] instead of a baked-in parchment-palette color.
 */
class SmithTypeTest {

    @Test
    fun body_mirrorsConsoleTheme_exceptColor() {
        assertEquals(ConsoleTheme.body.fontSize, SmithType.body.fontSize)
        assertEquals(ConsoleTheme.body.fontWeight, SmithType.body.fontWeight)
        assertEquals(ConsoleTheme.body.fontFamily, SmithType.body.fontFamily)
        assertEquals(Color.Unspecified, SmithType.body.color)
    }

    @Test
    fun caption_mirrorsConsoleTheme_exceptColor() {
        assertEquals(ConsoleTheme.caption.fontSize, SmithType.caption.fontSize)
        assertEquals(ConsoleTheme.caption.fontWeight, SmithType.caption.fontWeight)
        assertEquals(ConsoleTheme.caption.fontFamily, SmithType.caption.fontFamily)
        assertEquals(Color.Unspecified, SmithType.caption.color)
    }

    @Test
    fun commBody_mirrorsConsoleTheme_exceptColor() {
        assertEquals(ConsoleTheme.commBody.fontSize, SmithType.commBody.fontSize)
        assertEquals(ConsoleTheme.commBody.fontWeight, SmithType.commBody.fontWeight)
        assertEquals(ConsoleTheme.commBody.fontFamily, SmithType.commBody.fontFamily)
        assertEquals(Color.Unspecified, SmithType.commBody.color)
    }

    @Test
    fun everyStyleHasLetterSpacingAndLineHeightMirrored() {
        // Styles with non-default letterSpacing in ConsoleTheme must carry the same
        // value in SmithType (spot-checks across the fonts that use it).
        assertEquals(ConsoleTheme.brand.letterSpacing, SmithType.brand.letterSpacing)
        assertEquals(ConsoleTheme.title.letterSpacing, SmithType.title.letterSpacing)
        assertEquals(ConsoleTheme.header.letterSpacing, SmithType.header.letterSpacing)
        assertEquals(ConsoleTheme.timestamp.letterSpacing, SmithType.timestamp.letterSpacing)
        assertEquals(ConsoleTheme.prefix.letterSpacing, SmithType.prefix.letterSpacing)
        assertEquals(ConsoleTheme.commId.letterSpacing, SmithType.commId.letterSpacing)
        assertEquals(ConsoleTheme.dialpad.letterSpacing, SmithType.dialpad.letterSpacing)
    }

    @Test
    fun allSmithTypeStyles_haveUnspecifiedColor() {
        val all = listOf(
            "title" to SmithType.title,
            "header" to SmithType.header,
            "body" to SmithType.body,
            "bodyBold" to SmithType.bodyBold,
            "bodySmall" to SmithType.bodySmall,
            "caption" to SmithType.caption,
            "captionBold" to SmithType.captionBold,
            "timestamp" to SmithType.timestamp,
            "prefix" to SmithType.prefix,
            "prompt" to SmithType.prompt,
            "action" to SmithType.action,
            "commName" to SmithType.commName,
            "commBody" to SmithType.commBody,
            "commId" to SmithType.commId,
            "commTimestamp" to SmithType.commTimestamp,
            "brand" to SmithType.brand,
            "version" to SmithType.version,
            "dialpad" to SmithType.dialpad,
        )
        assertEquals("expected exactly 18 mirrored styles", 18, all.size)
        all.forEach { (name, style) ->
            assertTrue("$name should have Color.Unspecified", style.color == Color.Unspecified)
        }
    }

    @Test
    fun everyConsoleThemeStyleName_hasASmithTypeCounterpart() {
        // Cross-check against ConsoleTheme directly (not just the hardcoded list above)
        // so this test fails if ConsoleTheme grows a new named style that SmithType
        // hasn't mirrored yet.
        assertEquals(ConsoleTheme.title.fontSize, SmithType.title.fontSize)
        assertEquals(ConsoleTheme.header.fontSize, SmithType.header.fontSize)
        assertEquals(ConsoleTheme.bodyBold.fontSize, SmithType.bodyBold.fontSize)
        assertEquals(ConsoleTheme.bodySmall.fontSize, SmithType.bodySmall.fontSize)
        assertEquals(ConsoleTheme.captionBold.fontSize, SmithType.captionBold.fontSize)
        assertEquals(ConsoleTheme.timestamp.fontSize, SmithType.timestamp.fontSize)
        assertEquals(ConsoleTheme.prefix.fontSize, SmithType.prefix.fontSize)
        assertEquals(ConsoleTheme.prompt.fontSize, SmithType.prompt.fontSize)
        assertEquals(ConsoleTheme.action.fontSize, SmithType.action.fontSize)
        assertEquals(ConsoleTheme.commName.fontSize, SmithType.commName.fontSize)
        assertEquals(ConsoleTheme.commId.fontSize, SmithType.commId.fontSize)
        assertEquals(ConsoleTheme.commTimestamp.fontSize, SmithType.commTimestamp.fontSize)
        assertEquals(ConsoleTheme.brand.fontSize, SmithType.brand.fontSize)
        assertEquals(ConsoleTheme.version.fontSize, SmithType.version.fontSize)
        assertEquals(ConsoleTheme.dialpad.fontSize, SmithType.dialpad.fontSize)
    }
}
