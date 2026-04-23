package com.guildofsmiths.trademesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.TradesList
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import androidx.compose.material3.Text

/**
 * Shared searchable trade picker over TradesList.ALL_TRADES (121 entries).
 * Used by Onboarding, Profile, and job creation. Behaves identically so a
 * change once propagates everywhere.
 *
 * - Blank query shows first 20 trades.
 * - Search filters + shows up to 15 matches.
 * - Selected trade is echoed in an accent pill above the search field.
 */
@Composable
fun TradePickerField(
    selected: String,
    onTradeSelected: (String) -> Unit,
    placeholder: String = "Search trades (${TradesList.ALL_TRADES.size}+ available)",
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val filtered = remember(query) {
        if (query.isBlank()) TradesList.ALL_TRADES.take(20)
        else TradesList.search(query).take(15)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (selected.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.accent.copy(alpha = 0.08f))
                    .clickable { expanded = true }
                    .padding(16.dp)
            ) {
                Text(
                    text = selected,
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(16.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (expanded && filtered.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                filtered.forEach { trade ->
                    Text(
                        text = trade,
                        style = ConsoleTheme.bodySmall.copy(
                            color = if (trade == selected) ConsoleTheme.accent else ConsoleTheme.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTradeSelected(trade)
                                query = ""
                                expanded = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .padding(horizontal = 16.dp)
                            .background(ConsoleTheme.text.copy(alpha = 0.04f))
                    )
                }
            }
        }
    }
}
