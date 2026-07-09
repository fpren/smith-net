package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.ui.text.TextStyle

/** Terminal Grade numeric alignment: fixed-width (tabular) digits for columns. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
