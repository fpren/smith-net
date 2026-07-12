package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.Tokens2

/**
 * smith net v2 container. Crew: RadiusCard(20), soft shadow, BORDERLESS
 * (the borderless design philosophy — shadow replaces hairline). Ops:
 * RadiusOps(0), 1dp colors.line hairline, never a shadow.
 */
@Composable
fun SmithCard(
    modifier: Modifier = Modifier,
    ops: Boolean = false,
    elevated: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSmithColors.current
    val shape = RoundedCornerShape(if (ops) Tokens2.RadiusOps else Tokens2.RadiusCard)
    var m = modifier
    if (!ops && elevated) m = m.shadow(2.dp, shape)
    m = m.clip(shape).background(colors.bgPanel)
    if (ops) m = m.border(1.dp, colors.line, shape)
    Column(modifier = m.padding(contentPadding), content = content)
}
