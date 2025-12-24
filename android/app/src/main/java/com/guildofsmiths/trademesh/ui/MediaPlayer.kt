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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * Video display with popup viewer.
 * Shows pixel art [▶] [███▒▒▒] in chat, tap to open popup with thumbnail.
 */
@Composable
private fun VideoPlayer(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showPopup by remember { mutableStateOf(false) }
    val duration = media?.duration ?: 0L
    
    val durationSec = (duration / 1000).toInt()
    val mins = durationSec / 60
    val secs = durationSec % 60
    
    // Load video thumbnail (for popup)
    LaunchedEffect(media?.localPath) {
        media?.localPath?.let { path ->
            try {
                thumbnail = com.guildofsmiths.trademesh.service.MediaHelper.getVideoThumbnail(File(path))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video thumbnail", e)
            }
        }
    }
    
    // Pixel art in chat: [▶] [███▒▒▒] 0:15
    Row(
        modifier = modifier
            .clickable { showPopup = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[▶]", style = ConsoleTheme.body.copy(color = ConsoleTheme.accent))
        Spacer(modifier = Modifier.width(6.dp))
        VideoProgressBar(progress = 0f)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$mins:${secs.toString().padStart(2, '0')}",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
    
    // Popup modal
    if (showPopup) {
        VideoPopupPlayer(
            media = media,
            thumbnail = thumbnail,
            duration = duration,
            onDismiss = { showPopup = false },
            onOpenExternal = { openMedia(context, media) }
        )
    }
}

/**
 * Video popup with inline playback using VideoView.
 */
@Composable
private fun VideoPopupPlayer(
    media: MediaAttachment?,
    thumbnail: android.graphics.Bitmap?,
    duration: Long,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }
    
    val durationSec = (duration / 1000).toInt()
    val mins = durationSec / 60
    val secs = durationSec % 60
    
    val localPath = media?.localPath
    
    // Update progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                val vv = videoViewRef
                if (vv != null && vv.isPlaying) {
                    currentPosition = vv.currentPosition.toLong()
                    val totalDur = if (duration > 0) duration else vv.duration.toLong()
                    progress = if (totalDur > 0) {
                        currentPosition.toFloat() / totalDur.toFloat()
                    } else 0f
                }
            } catch (e: Exception) {
                // ignore
            }
            delay(200)
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }
    
    Dialog(
        onDismissRequest = {
            videoViewRef?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ConsoleTheme.background.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures {
                        videoViewRef?.stopPlayback()
                        onDismiss()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(ConsoleTheme.surface)
                    .padding(1.dp)
                    .background(ConsoleTheme.background)
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume tap */ }
                    }
            ) {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("═══ VIDEO ═══", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "[↗]",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .clickable { 
                                videoViewRef?.stopPlayback()
                                onOpenExternal()
                                onDismiss() 
                            }
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[✕]",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable {
                                videoViewRef?.stopPlayback()
                                onDismiss()
                            }
                            .padding(4.dp)
                    )
                }
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Video player area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(ConsoleTheme.surface)
                    ) {
                        if (isPlaying && localPath != null) {
                            // Show VideoView when playing
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.widget.VideoView(context).apply {
                                        setVideoPath(localPath)
                                        setOnCompletionListener {
                                            isPlaying = false
                                            progress = 0f
                                            currentPosition = 0L
                                        }
                                        setOnPreparedListener {
                                            start()
                                        }
                                        videoViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Show thumbnail when not playing
                            if (thumbnail != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = "Video preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            
                            // Play button overlay - tap to start
                            Text(
                                text = "[▶]",
                                style = ConsoleTheme.title.copy(color = ConsoleTheme.accent),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(ConsoleTheme.background.copy(alpha = 0.7f))
                                    .clickable {
                                        if (localPath != null && File(localPath).exists()) {
                                            isPlaying = true
                                        }
                                    }
                                    .padding(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Playback controls: [▶]/[■] [███▒▒▒] 0:15
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Play/Stop button
                        Text(
                            text = if (isPlaying) "[■]" else "[▶]",
                            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                            modifier = Modifier
                                .clickable {
                                    if (isPlaying) {
                                        videoViewRef?.stopPlayback()
                                        isPlaying = false
                                        progress = 0f
                                        currentPosition = 0L
                                    } else {
                                        if (localPath != null && File(localPath).exists()) {
                                            isPlaying = true
                                        }
                                    }
                                }
                                .padding(4.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        VideoProgressBar(progress = progress)
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Time display
                        val displayTime = if (isPlaying && currentPosition > 0) {
                            val posSec = (currentPosition / 1000).toInt()
                            val posMin = posSec / 60
                            val posSecs = posSec % 60
                            "$posMin:${posSecs.toString().padStart(2, '0')}"
                        } else {
                            "$mins:${secs.toString().padStart(2, '0')}"
                        }
                        Text(displayTime, style = ConsoleTheme.body.copy(color = ConsoleTheme.text))
                    }
                }
                
                // Bottom hint
                Text(
                    text = "tap [▶] to play · [■] to stop",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(8.dp)
                )
            }
        }
    }
}

/**
 * Video progress bar using block characters: [███▒▒▒]
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
 * Image display with popup viewer.
 * Shows pixel art [▣] in chat, tap to open popup with actual image.
 */
@Composable
private fun ImageThumbnail(
    media: MediaAttachment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showPopup by remember { mutableStateOf(false) }
    
    // Pixel art in chat: [▣] image
    Row(
        modifier = modifier
            .clickable { showPopup = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[▣]", style = ConsoleTheme.body.copy(color = ConsoleTheme.accent))
        Spacer(modifier = Modifier.width(6.dp))
        Text("image", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        
        // Show dimensions if available
        if (media != null && media.width > 0 && media.height > 0) {
            Text(
                " (${media.width}×${media.height})",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
            )
        }
    }
    
    // Load image when popup opens
    LaunchedEffect(showPopup) {
        if (showPopup && imageBitmap == null) {
            media?.localPath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        // Load scaled version for popup
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFile(path, options)
                        
                        val targetSize = 600
                        var sampleSize = 1
                        while (options.outWidth / sampleSize > targetSize ||
                               options.outHeight / sampleSize > targetSize) {
                            sampleSize *= 2
                        }
                        
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        imageBitmap = android.graphics.BitmapFactory.decodeFile(path, options)
                    } else {
                        Log.w(TAG, "Image file does not exist: $path")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load image", e)
                }
            }
        }
    }
    
    // Popup modal
    if (showPopup) {
        ImagePopupViewer(
            media = media,
            bitmap = imageBitmap,
            onDismiss = { showPopup = false },
            onOpenExternal = { openMedia(context, media) }
        )
    }
}

/**
 * Image popup viewer with tappable controls.
 */
@Composable
private fun ImagePopupViewer(
    media: MediaAttachment?,
    bitmap: android.graphics.Bitmap?,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ConsoleTheme.background.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(ConsoleTheme.surface)
                    .padding(1.dp)
                    .background(ConsoleTheme.background)
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume tap */ }
                    }
            ) {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("═══ IMAGE ═══", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "[↗]",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .clickable { onOpenExternal(); onDismiss() }
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[✕]",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Image preview
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(ConsoleTheme.surface)
                                .clickable { onOpenExternal(); onDismiss() }
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            
                            Text(
                                text = "[▣]",
                                style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(ConsoleTheme.background.copy(alpha = 0.7f))
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        // Loading state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(ConsoleTheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("[▣] loading...", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
                        }
                    }
                    
                    // Dimensions
                    if (media != null && media.width > 0 && media.height > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${media.width} × ${media.height}",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
                
                // Bottom hint
                Text(
                    text = "tap image or [↗] to open full",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(8.dp)
                )
            }
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
