package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

/**
 * Welcome screen — Big bold Smith Net branding.
 */
@Composable
fun WelcomeScreen(
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    var userName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Big brand — S P A C E D
        Text(
            text = ConsoleTheme.APP_NAME,
            style = SmithType.brand.copy(fontSize = 36.sp, letterSpacing = 4.sp, color = colors.ink)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "v${ConsoleTheme.APP_VERSION}",
            style = SmithType.version.copy(color = colors.inkMuted)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "mesh communication",
            style = SmithType.bodySmall.copy(color = colors.inkMuted),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "WHAT'S YOUR NAME?",
            style = SmithType.captionBold.copy(color = colors.inkMuted),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        BasicTextField(
            value = userName,
            onValueChange = {
                userName = it.take(20)
                errorMessage = null
            },
            textStyle = SmithType.header.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (userName.trim().length >= 2) {
                        onComplete(userName.trim())
                    } else {
                        errorMessage = "Name must be at least 2 characters"
                    }
                }
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .padding(16.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (userName.isEmpty()) {
                        Text(
                            text = "Enter your name",
                            style = SmithType.header.copy(color = colors.inkMuted)
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                style = SmithType.caption.copy(color = colors.attention),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (userName.trim().length >= 2) {
            Text(
                text = "JOIN →",
                style = SmithType.action.copy(fontSize = 18.sp, color = colors.accent),
                modifier = Modifier
                    .clickable { onComplete(userName.trim()) }
                    .padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your name will be visible to others nearby",
            style = SmithType.caption.copy(color = colors.inkMuted),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "made by ${ConsoleTheme.STUDIO}",
            style = SmithType.caption.copy(color = colors.inkMuted),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun WelcomeScreenPreview() {
    WelcomeScreen(onComplete = { })
}
