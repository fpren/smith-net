package com.guildofsmiths.trademesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * Pixel Art Bar Rating Components
 * 
 * Replaces star ratings with retro-style filled/empty bars:
 * - ██████████ (10/10 - full)
 * - █████▁▁▁▁▁ (5/10 - half)
 * - ▁▁▁▁▁▁▁▁▁▁ (0/10 - empty)
 * 
 * Uses Unicode block characters for consistent pixel art aesthetic.
 */

// ════════════════════════════════════════════════════════════════════
// CONSTANTS
// ════════════════════════════════════════════════════════════════════

private const val FILLED_BAR = "█"
private const val EMPTY_BAR = "▁"
private const val HALF_BAR = "▄"

// ════════════════════════════════════════════════════════════════════
// DISPLAY COMPONENTS (Read-only)
// ════════════════════════════════════════════════════════════════════

/**
 * Display a bar rating as text (non-interactive).
 * 
 * @param value Current value (1-10)
 * @param maxValue Maximum value (default 10)
 * @param showValue Whether to show numeric value after bars
 * @param filledColor Color for filled bars
 * @param emptyColor Color for empty bars
 */
@Composable
fun PixelBarRating(
    value: Int,
    maxValue: Int = 10,
    showValue: Boolean = true,
    filledColor: Color = ConsoleTheme.accent,
    emptyColor: Color = ConsoleTheme.textDim,
    modifier: Modifier = Modifier
) {
    val clampedValue = value.coerceIn(0, maxValue)
    val filledBars = FILLED_BAR.repeat(clampedValue)
    val emptyBars = EMPTY_BAR.repeat(maxValue - clampedValue)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Bars
        Row {
            Text(
                text = filledBars,
                style = ConsoleTheme.body.copy(
                    fontFamily = FontFamily.Monospace,
                    color = filledColor,
                    letterSpacing = 1.sp
                )
            )
            Text(
                text = emptyBars,
                style = ConsoleTheme.body.copy(
                    fontFamily = FontFamily.Monospace,
                    color = emptyColor,
                    letterSpacing = 1.sp
                )
            )
        }
        
        // Numeric value
        if (showValue) {
            Text(
                text = "($clampedValue/$maxValue)",
                style = ConsoleTheme.caption.copy(
                    color = ConsoleTheme.textMuted
                )
            )
        }
    }
}

/**
 * Compact bar rating for dashboard metrics.
 * 
 * @param value Current value (1-10)
 * @param maxValue Maximum value (default 10)
 * @param label Optional label text
 */
@Composable
fun CompactBarRating(
    value: Int,
    maxValue: Int = 10,
    label: String? = null,
    filledColor: Color = ConsoleTheme.accent,
    modifier: Modifier = Modifier
) {
    val clampedValue = value.coerceIn(0, maxValue)
    val filledBars = FILLED_BAR.repeat(clampedValue)
    val emptyBars = EMPTY_BAR.repeat(maxValue - clampedValue)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Bars (smaller)
        Text(
            text = filledBars + emptyBars,
            style = ConsoleTheme.caption.copy(
                fontFamily = FontFamily.Monospace,
                color = filledColor,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
        
        // Label
        label?.let {
            Text(
                text = it,
                style = ConsoleTheme.caption.copy(
                    color = ConsoleTheme.textMuted,
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * Mini bar indicator for quick metrics (5 bars max).
 * Used in dashboard quick stats.
 */
@Composable
fun MiniBarIndicator(
    value: Int,
    maxValue: Int = 5,
    label: String,
    filledColor: Color = ConsoleTheme.accent,
    modifier: Modifier = Modifier
) {
    val clampedValue = value.coerceIn(0, maxValue)
    val filledBars = FILLED_BAR.repeat(clampedValue)
    val emptyBars = EMPTY_BAR.repeat(maxValue - clampedValue)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = filledBars + emptyBars,
            style = ConsoleTheme.caption.copy(
                fontFamily = FontFamily.Monospace,
                color = filledColor,
                fontSize = 11.sp
            )
        )
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(
                color = ConsoleTheme.textMuted,
                fontSize = 11.sp
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// INTERACTIVE COMPONENTS (Clickable)
// ════════════════════════════════════════════════════════════════════

/**
 * Interactive bar rating selector.
 * User can click to select a rating value.
 * 
 * @param value Current selected value
 * @param onValueChange Callback when value changes
 * @param maxValue Maximum value (default 10)
 * @param enabled Whether selection is enabled
 */
@Composable
fun InteractiveBarRating(
    value: Int,
    onValueChange: (Int) -> Unit,
    maxValue: Int = 10,
    enabled: Boolean = true,
    filledColor: Color = ConsoleTheme.accent,
    emptyColor: Color = ConsoleTheme.textDim,
    modifier: Modifier = Modifier
) {
    val clampedValue = value.coerceIn(0, maxValue)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        (1..maxValue).forEach { barIndex ->
            val isFilled = barIndex <= clampedValue
            
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(24.dp)
                    .background(
                        color = if (isFilled) filledColor else emptyColor.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .then(
                        if (enabled) {
                            Modifier.clickable { onValueChange(barIndex) }
                        } else Modifier
                    )
            )
        }
        
        // Show numeric value
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$clampedValue/$maxValue",
            style = ConsoleTheme.bodyBold.copy(
                color = if (clampedValue > 0) filledColor else ConsoleTheme.textMuted
            )
        )
    }
}

/**
 * Large interactive bar selector for feedback dialogs.
 * Shows bars with labels and hover states.
 */
@Composable
fun LargeFeedbackBarSelector(
    value: Int,
    onValueChange: (Int) -> Unit,
    maxValue: Int = 10,
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        1 to "Poor",
        2 to "Below Avg",
        3 to "Fair",
        4 to "Acceptable",
        5 to "Average",
        6 to "Good",
        7 to "Very Good",
        8 to "Great",
        9 to "Excellent",
        10 to "Outstanding"
    )
    
    val currentLabel = labels.find { it.first == value }?.second ?: ""
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Bar selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..maxValue).forEach { barIndex ->
                val isFilled = barIndex <= value
                val barColor = when {
                    barIndex <= 3 -> Color(0xFFE74C3C)  // Red - Poor
                    barIndex <= 5 -> Color(0xFFF39C12)  // Orange - Average
                    barIndex <= 7 -> Color(0xFF3498DB)  // Blue - Good
                    else -> Color(0xFF27AE60)           // Green - Excellent
                }
                
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(32.dp)
                        .background(
                            color = if (isFilled) barColor else ConsoleTheme.textDim.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onValueChange(barIndex) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = barIndex.toString(),
                        style = ConsoleTheme.caption.copy(
                            color = if (isFilled) Color.White else ConsoleTheme.textDim,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
        
        // Current value and label
        if (value > 0) {
            Text(
                text = "$value/10 - $currentLabel",
                style = ConsoleTheme.bodyBold.copy(
                    color = when {
                        value <= 3 -> Color(0xFFE74C3C)
                        value <= 5 -> Color(0xFFF39C12)
                        value <= 7 -> Color(0xFF3498DB)
                        else -> Color(0xFF27AE60)
                    }
                )
            )
        } else {
            Text(
                text = "Tap a bar to rate",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PERFORMANCE METRIC DISPLAYS
// ════════════════════════════════════════════════════════════════════

/**
 * Performance metric row with label, bars, and value.
 */
@Composable
fun PerformanceMetricBar(
    label: String,
    value: Int,
    maxValue: Int = 10,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    val color = when {
        value <= 3 -> Color(0xFFE74C3C)  // Red
        value <= 5 -> Color(0xFFF39C12)  // Orange
        value <= 7 -> Color(0xFF3498DB)  // Blue
        else -> Color(0xFF27AE60)        // Green
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        // Label
        Text(
            text = label,
            style = ConsoleTheme.body.copy(color = ConsoleTheme.text),
            modifier = Modifier.weight(0.35f)
        )
        
        // Bars
        PixelBarRating(
            value = value,
            maxValue = maxValue,
            showValue = false,
            filledColor = color,
            modifier = Modifier.weight(0.4f)
        )
        
        // Value and description
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(0.25f)
        ) {
            Text(
                text = "$value/$maxValue",
                style = ConsoleTheme.bodyBold.copy(color = color)
            )
            description?.let {
                Text(
                    text = it,
                    style = ConsoleTheme.caption.copy(
                        color = ConsoleTheme.textMuted,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

/**
 * Benchmark comparison bar with vs industry indicator.
 */
@Composable
fun BenchmarkComparisonBar(
    label: String,
    yourValue: Double,
    marketValue: Double,
    unit: String = "",
    higherIsBetter: Boolean = true,
    modifier: Modifier = Modifier
) {
    val difference = yourValue - marketValue
    val percentDiff = if (marketValue > 0) ((difference / marketValue) * 100).toInt() else 0
    
    val isPositive = if (higherIsBetter) difference > 0 else difference < 0
    val statusColor = when {
        isPositive && kotlin.math.abs(percentDiff) >= 15 -> Color(0xFF27AE60)  // Green
        isPositive -> Color(0xFF3498DB)                                         // Blue
        kotlin.math.abs(percentDiff) <= 5 -> Color(0xFFF39C12)                  // Yellow
        else -> Color(0xFFE74C3C)                                               // Red
    }
    
    val statusIcon = when {
        isPositive && kotlin.math.abs(percentDiff) >= 15 -> "🟢"
        isPositive -> "🔵"
        kotlin.math.abs(percentDiff) <= 5 -> "🟡"
        else -> "🔴"
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        // Label
        Text(
            text = label,
            style = ConsoleTheme.body,
            modifier = Modifier.weight(0.3f)
        )
        
        // Values comparison
        Text(
            text = "${formatValue(yourValue)}$unit vs ${formatValue(marketValue)}$unit",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.weight(0.4f)
        )
        
        // Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(0.3f)
        ) {
            Text(text = statusIcon, style = ConsoleTheme.caption)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${if (difference >= 0) "+" else ""}$percentDiff%",
                style = ConsoleTheme.captionBold.copy(color = statusColor)
            )
        }
    }
}

private fun formatValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.1f", value)
    }
}

// ════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ════════════════════════════════════════════════════════════════════

/**
 * Convert a percentage (0-100) to bar value (1-10).
 */
fun percentToBarValue(percent: Double): Int {
    return ((percent / 100.0) * 10).toInt().coerceIn(1, 10)
}

/**
 * Convert bar value (1-10) to percentage (0-100).
 */
fun barValueToPercent(barValue: Int): Double {
    return (barValue.coerceIn(1, 10) / 10.0) * 100
}

/**
 * Generate bar string for text display.
 */
fun generateBarString(value: Int, maxValue: Int = 10): String {
    val clampedValue = value.coerceIn(0, maxValue)
    return FILLED_BAR.repeat(clampedValue) + EMPTY_BAR.repeat(maxValue - clampedValue)
}
