package com.guildofsmiths.trademesh.ui.theme2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guildofsmiths.trademesh.ui.ConsoleTheme

@Composable
fun SmithDialog(
    title: String,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    sizeFraction: Pair<Float, Float>? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSmithColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !destructive,
            usePlatformDefaultWidth = sizeFraction == null,
        ),
    ) {
        var panel = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgPanel)
        if (sizeFraction != null) {
            panel = panel
                .fillMaxWidth(sizeFraction.first)
                .fillMaxHeight(sizeFraction.second)
        } else {
            panel = panel.fillMaxWidth()
        }
        Column(modifier = panel.padding(20.dp)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = ConsoleTheme.inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.ink,
                ),
            )
            Spacer(modifier = Modifier.padding(top = 10.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
fun SmithConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmIsDanger: Boolean = true,
) {
    val colors = LocalSmithColors.current
    SmithDialog(
        title = title,
        onDismiss = onDismiss,
        destructive = true,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            SmithButton(
                text = confirmText,
                onClick = onConfirm,
                variant = if (confirmIsDanger) SmithButtonVariant.Danger else SmithButtonVariant.Primary,
            )
        },
    ) {
        Text(
            text = body,
            style = TextStyle(
                fontFamily = ConsoleTheme.inter,
                fontSize = 14.sp,
                color = colors.inkMuted,
            ),
        )
    }
}
