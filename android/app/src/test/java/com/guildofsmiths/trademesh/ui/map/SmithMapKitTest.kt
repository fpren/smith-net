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
