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
