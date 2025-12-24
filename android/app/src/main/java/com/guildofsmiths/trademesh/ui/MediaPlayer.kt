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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.asImageBitmap
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
 * - Voice: Playable with animated progress bar [▶] [■■■■■■□□□□]
 * - Video: Playable with block progress bar [▶] [███▒▒▒]
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
        MediaType.VIDEO -> {
            VideoPlayer(
                media = media,
                onClick = { openMedia(context, media) },
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
 * Video player with thumbnail preview and block progress bar.
 * [▶] [███▒▒▒] 0:15 - tap to play in external player
 */
@Composable
private fun VideoPlayer(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val duration = media?.duration ?: 0L
    
    // Load video thumbnail
    LaunchedEffect(media?.localPath) {
        media?.localPath?.let { path ->
            try {
                thumbnail = com.guildofsmiths.trademesh.service.MediaHelper.getVideoThumbnail(File(path))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video thumbnail", e)
            }
        }
    }
    
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        // Video thumbnail with play overlay
        if (thumbnail != null) {
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .background(ConsoleTheme.surface)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                // Play icon overlay in center
                Text(
                    text = "[▶]",
                    style = ConsoleTheme.title.copy(color = ConsoleTheme.accent),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(ConsoleTheme.background.copy(alpha = 0.7f))
                        .padding(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Video progress bar: [▶] [███▒▒▒] 0:15
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[▶]",
                style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
            )
            
            Spacer(modifier = Modifier.width(6.dp))
            
            // Block progress bar style: [███▒▒▒]
            VideoProgressBar(progress = 0f)
            
            Spacer(modifier = Modifier.width(6.dp))
            
            // Duration
            val durationSec = (duration / 1000).toInt()
            val mins = durationSec / 60
            val secs = durationSec % 60
            Text(
                text = "$mins:${secs.toString().padStart(2, '0')}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
        
        Text(
            text = "tap to play video",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

/**
 * Video progress bar using block characters: [███▒▒▒]
 * █ = filled (played), ▒ = unfilled (remaining)
 */
@Composable
private fun VideoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val totalBlocks = 6
    val filledBlocks = (progress * totalBlocks).toInt().coerceIn(0, totalBlocks)
    val emptyBlocks = totalBlocks - filledBlocks
    
    val barText = buildString {
        append("[")
        repeat(filledBlocks) { append("█") }
        repeat(emptyBlocks) { append("▒") }
        append("]")
    }
    
    Text(
        text = barText,
        style = ConsoleTheme.body.copy(color = ConsoleTheme.text),
        modifier = modifier
    )
}

/**
 * Image thumbnail - shows actual preview, tappable to open full image.
 * Shows small thumbnail with [▣] icon overlay
 */
@Composable
private fun ImageThumbnail(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // Load thumbnail
    LaunchedEffect(media?.localPath) {
        media?.localPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    // Load scaled down bitmap for thumbnail
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(path, options)
                    
                    // Calculate sample size for ~100px thumbnail
                    val targetSize = 100
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > targetSize * 2 ||
                           options.outHeight / sampleSize > targetSize * 2) {
                        sampleSize *= 2
                    }
                    
                    options.inJustDecodeBounds = false
                    options.inSampleSize = sampleSize
                    bitmap = android.graphics.BitmapFactory.decodeFile(path, options)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load thumbnail", e)
            }
        }
    }
    
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        // Show thumbnail if loaded
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 90.dp)
                    .background(ConsoleTheme.surface)
            ) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Image thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                // Overlay icon in corner
                Text(
                    text = "[▣]",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(ConsoleTheme.background.copy(alpha = 0.7f))
                        .padding(2.dp)
                )
            }
        } else {
            // Fallback to text indicator while loading
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[▣]",
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "loading image...",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        }
        
        // Tap hint
        Text(
            text = "tap to view",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
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
