package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.ui.graphics.Color
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SmithType is the app's single typography source (M1 font flip, spec
 * 2026-07-11-modern-look-fixes-design.md): UI text = Inter, data/mono =
 * JetBrains Mono, display = Syne. Comm surfaces keep Public Sans +
 * JetBrains Mono (locked Plan 3 decision). IBM Plex is retired — no style
 * may reference it.
 */
class SmithTypeTest {

    @Test
    fun uiBodyStyles_useInter() {
        assertEquals(ConsoleTheme.inter, SmithType.body.fontFamily)
        assertEquals(ConsoleTheme.inter, SmithType.bodyBold.fontFamily)
        assertEquals(ConsoleTheme.inter, SmithType.bodySmall.fontFamily)
    }

    @Test
    fun monoDataStyles_useJetBrainsMono() {
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.version.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.caption.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.captionBold.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.timestamp.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.prefix.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.prompt.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.action.fontFamily)
    }

    @Test
    fun displayStyles_staySyne() {
        assertEquals(ConsoleTheme.syne, SmithType.brand.fontFamily)
        assertEquals(ConsoleTheme.syne, SmithType.title.fontFamily)
        assertEquals(ConsoleTheme.syne, SmithType.header.fontFamily)
    }

    @Test
    fun commStyles_keepPlan3Families() {
        assertEquals(ConsoleTheme.publicSans, SmithType.commName.fontFamily)
        assertEquals(ConsoleTheme.publicSans, SmithType.commBody.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.commId.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.commTimestamp.fontFamily)
        assertEquals(ConsoleTheme.jetBrainsMono, SmithType.dialpad.fontFamily)
    }

    @Test
    fun allSmithTypeStyles_haveUnspecifiedColor() {
        val all = listOf(
            "brand" to SmithType.brand,
            "version" to SmithType.version,
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
            "dialpad" to SmithType.dialpad,
        )
        assertEquals("expected exactly 18 styles", 18, all.size)
        all.forEach { (name, style) ->
            assertTrue("$name should have Color.Unspecified", style.color == Color.Unspecified)
        }
    }
}
