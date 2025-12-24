package com.guildofsmiths.trademesh.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "MediaPlayer"

/**
 * Interactive media display component.
 * - Voice: Playable with animated progress bar
 * - Image: Tappable to open full screen
 * - File: Tappable to open with system app
 */
@Composable
fun InteractiveMediaPlayer(
    mediaType: MediaType,
    media: MediaAttachment?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    when (mediaType) {
        MediaType.VOICE -> {
            VoicePlayer(
                media = media,
                modifier = modifier
            )
        }
        MediaType.IMAGE -> {
            ImageThumbnail(
                media = media,
                onClick = { openMedia(context, media) },
                modifier = modifier
            )
        }
        MediaType.FILE -> {
            FileAttachment(
                media = media,
                onClick = { openMedia(context, media) },
                modifier = modifier
            )
        }
        MediaType.TEXT -> {
            // No media display needed
        }
    }
}

/**
 * Voice player with pixel art play button and animated progress bar.
 * [▶] [■■■■■■□□□□] 5s  →  [⏸] [■■■■■■■■□□] 3s
 */
@Composable
private fun VoicePlayer(
    media: MediaAttachment?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableStateOf(0L) }
    val duration = media?.duration ?: 0L
    
    // MediaPlayer instance
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100),
        label = "progress"
    )
    
    // Cleanup MediaPlayer on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            try {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    currentPosition = player.currentPosition.toLong()
                    progress = if (duration > 0) {
                        currentPosition.toFloat() / duration.toFloat()
                    } else {
                        player.currentPosition.toFloat() / player.duration.toFloat()
                    }
                }
            } catch (e: Exception) {
                // Player may be released
            }
            delay(100)
        }
    }
    
    Row(
        modifier = modifier
            .clickable {
                if (isPlaying) {
                    // Stop playing
                    mediaPlayer?.pause()
                    isPlaying = false
                } else {
                    // Start playing
                    val localPath = media?.localPath
                    val remotePath = media?.remotePath
                    
                    val uri = when {
                        localPath != null && File(localPath).exists() -> {
                            Uri.fromFile(File(localPath))
                        }
                        remotePath != null -> {
                            Uri.parse(remotePath)
                        }
                        else -> null
                    }
                    
                    if (uri != null) {
                        try {
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(context, uri)
                                prepare()
                                setOnCompletionListener {
                                    isPlaying = false
                                    progress = 0f
                                    currentPosition = 0L
                                }
                                start()
                            }
                            isPlaying = true
                            Log.d(TAG, "▶ Playing voice: $uri")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to play voice", e)
                            isPlaying = false
                        }
                    }
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Stop button - [▶] play, [■] stop (matching style)
        Text(
            text = if (isPlaying) "[■]" else "[▶]",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Progress bar: [■■■■■■□□□□]
        PixelProgressBar(
            progress = animatedProgress,
            totalBars = 10
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Duration / remaining time
        val displayTime = if (isPlaying && currentPosition > 0) {
            val remaining = ((duration - currentPosition) / 1000).coerceAtLeast(0)
            "${remaining}s"
        } else {
            "${(duration / 1000)}s"
        }
        
        Text(
            text = displayTime,
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

/**
 * Pixel art progress bar using block characters.
 * [■■■■■■□□□□] - filled = ■, empty = □
 */
@Composable
private fun PixelProgressBar(
    progress: Float,
    totalBars: Int = 10,
    modifier: Modifier = Modifier
) {
    val filledBars = (progress * totalBars).toInt().coerceIn(0, totalBars)
    val emptyBars = totalBars - filledBars
    
    val barText = buildString {
        append("[")
        repeat(filledBars) { append("■") }
        repeat(emptyBars) { append("□") }
        append("]")
    }
    
    Text(
        text = barText,
        style = ConsoleTheme.body.copy(color = ConsoleTheme.text),
        modifier = modifier
    )
}

/**
 * Image thumbnail - tappable to open full image.
 * [▣] image - tap to view
 */
@Composable
private fun ImageThumbnail(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Image icon
        Text(
            text = "[▣]",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Label
        Text(
            text = "tap to view image",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
        
        // Show dimensions if available
        if (media != null && media.width > 0 && media.height > 0) {
            Text(
                text = " (${media.width}×${media.height})",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
        }
    }
}

/**
 * File attachment - tappable to open with system app.
 * [■] document.pdf - tap to open
 */
@Composable
private fun FileAttachment(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File icon
        Text(
            text = "[■]",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Filename
        Text(
            text = media?.fileName ?: "document",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.text)
        )
        
        // File size if available
        if (media != null && media.fileSize > 0) {
            val sizeText = formatFileSize(media.fileSize)
            Text(
                text = " ($sizeText)",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
    }
}

/**
 * Open media with appropriate app.
 */
private fun openMedia(context: Context, media: MediaAttachment?) {
    if (media == null) return
    
    val localPath = media.localPath
    val remotePath = media.remotePath
    
    try {
        val uri = when {
            localPath != null && File(localPath).exists() -> {
                // Use FileProvider for local files
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(localPath)
                )
            }
            remotePath != null -> {
                Uri.parse(remotePath)
            }
            else -> {
                Log.w(TAG, "No valid path for media")
                return
            }
        }
        
        val mimeType = media.mimeType ?: when (media.type) {
            MediaType.IMAGE -> "image/*"
            MediaType.VOICE -> "audio/*"
            MediaType.FILE -> "*/*"
            else -> "*/*"
        }
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(intent)
        Log.d(TAG, "Opening media: $uri ($mimeType)")
        
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open media", e)
    }
}

/**
 * Format file size for display.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}
