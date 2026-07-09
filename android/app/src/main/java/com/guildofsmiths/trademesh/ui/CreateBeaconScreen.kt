package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.Beacon
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

/**
 * Create beacon — bold, clean form.
 */
@Composable
fun CreateBeaconScreen(
    onBackClick: () -> Unit,
    onBeaconCreated: (Beacon) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    var beaconName by remember { mutableStateOf("") }
    var beaconDescription by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = SmithType.title.copy(color = colors.ink))
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "NEW BEACON", style = SmithType.title.copy(color = colors.ink))
        }

        ConsoleSeparator()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "Create a new mesh network",
                style = SmithType.bodySmall.copy(color = colors.inkMuted)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "NETWORK NAME", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(6.dp))

            BasicTextField(
                value = beaconName,
                onValueChange = {
                    beaconName = it.take(30)
                    errorMessage = null
                },
                textStyle = SmithType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (beaconName.isEmpty()) {
                            Text(
                                text = "Market, Meetup, etc",
                                style = SmithType.body.copy(color = colors.inkMuted)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage!!,
                    style = SmithType.caption.copy(color = colors.attention)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "DESCRIPTION (OPTIONAL)", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(6.dp))

            BasicTextField(
                value = beaconDescription,
                onValueChange = { beaconDescription = it.take(100) },
                textStyle = SmithType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(colors.bgPanel)
                    .padding(14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (beaconDescription.isEmpty()) {
                            Text(
                                text = "What is this network for?",
                                style = SmithType.body.copy(color = colors.inkMuted)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (beaconName.trim().length >= 3) {
                Text(
                    text = "CREATE →",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier
                        .clickable {
                            val name = beaconName.trim()
                            if (name.length < 3) {
                                errorMessage = "Name must be at least 3 characters"
                            } else {
                                val beacon = BeaconRepository.createBeacon(name, beaconDescription.trim())
                                onBeaconCreated(beacon)
                            }
                        }
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ConsoleSeparator()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Each beacon has a unique BLE UUID",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CreateBeaconScreenPreview() {
    CreateBeaconScreen(
        onBackClick = { },
        onBeaconCreated = { }
    )
}
