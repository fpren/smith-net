package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.engine.BoundaryEngine

/**
 * Banner showing pending channel invites.
 */
@Composable
fun InviteBanner(
    onAccept: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val invites by BoundaryEngine.pendingInvites.collectAsState()
    
    Column(modifier = modifier.fillMaxWidth()) {
        invites.forEach { (hash, pair) ->
            val (channelName, senderName) = pair
            
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                InviteCard(
                    channelName = channelName,
                    senderName = senderName,
                    onAccept = { 
                        val channelId = BoundaryEngine.acceptInvite(hash)
                        if (channelId != null) {
                            onAccept(hash, channelId)
                        }
                    },
                    onDecline = { BoundaryEngine.declineInvite(hash) }
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InviteCard(
    channelName: String,
    senderName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.accentGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "📨 Channel Invite",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AppColors.accentGreen
                )
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "#$channelName",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.text
                )
            )
            
            Text(
                text = "from $senderName",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = AppColors.textMuted
                )
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.textMuted
                )
            ) {
                Text(
                    text = "✕",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accentGreen,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Join",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
