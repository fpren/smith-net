package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.Tokens2

/**
 * smith net v2 text input. Crew: bgSunken well, RadiusControl(10), accent
 * focus border. Ops: RadiusOps(0), hairline border, mono-upper label.
 * BasicTextField only — Material TextField is extinct outside ui/theme/.
 */
@Composable
fun SmithTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    ops: Boolean = false,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val colors = LocalSmithColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (ops) Tokens2.RadiusOps else Tokens2.RadiusControl)
    val borderColor = when {
        focused -> colors.accent
        ops -> colors.line
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(modifier = modifier) {
        if (label != null) {
            androidx.compose.material3.Text(
                text = if (ops) label.uppercase() else label,
                style = (if (ops) SmithType.caption else SmithType.bodySmall)
                    .copy(color = colors.inkMuted),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (ops) colors.bgPanel else colors.bgSunken, shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            interactionSource = interaction,
            textStyle = SmithType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onSend = { onImeAction?.invoke() },
                onSearch = { onImeAction?.invoke() },
            ),
            singleLine = singleLine,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        androidx.compose.material3.Text(
                            text = placeholder,
                            style = SmithType.body.copy(color = colors.inkMuted),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
