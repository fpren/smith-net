package com.guildofsmiths.trademesh.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaHelper: Utilities for camera capture, voice recording, and file handling.
 * Provides file URIs for camera captures and manages voice recordings.
 */
object MediaHelper {
    
    private const val TAG = "MediaHelper"
    
    // ══════════════════════════════════════════════════════════════════
    // CAMERA CAPTURE
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Create a URI for camera capture.
     * Returns a FileProvider URI that can be passed to camera intent.
     */
    fun createCameraImageUri(context: Context): Pair<Uri, File>? {
        return try {
            val mediaDir = File(context.cacheDir, "media/images")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFile = File(mediaDir, "IMG_$timestamp.jpg")
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            
            Log.d(TAG, "Created camera URI: $uri -> ${imageFile.absolutePath}")
            Pair(uri, imageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera URI", e)
            null
        }
    }
    
    /**
     * Get image dimensions from a file.
     */
    fun getImageDimensions(file: File): Pair<Int, Int> {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image dimensions", e)
            Pair(0, 0)
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // VOICE RECORDING
    // ══════════════════════════════════════════════════════════════════
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0
    
    /**
     * Start voice recording.
     * Returns the file path where the recording will be saved.
     */
    fun startVoiceRecording(context: Context): String? {
        return try {
            val mediaDir = File(context.cacheDir, "media/voice")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val audioFile = File(mediaDir, "VOICE_$timestamp.m4a")
            currentRecordingFile = audioFile
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            
            recordingStartTime = System.currentTimeMillis()
            Log.i(TAG, "🎤 Voice recording started: ${audioFile.absolutePath}")
            audioFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            releaseRecorder()
            null
        }
    }
    
    /**
     * Stop voice recording.
     * Returns the file and duration if successful.
     */
    fun stopVoiceRecording(): VoiceRecordingResult? {
        return try {
            val duration = System.currentTimeMillis() - recordingStartTime
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val file = currentRecordingFile
            currentRecordingFile = null
            
            if (file != null && file.exists() && file.length() > 0) {
                Log.i(TAG, "🎤 Voice recording stopped: ${file.absolutePath}, duration: ${duration}ms")
                VoiceRecordingResult(file, duration)
            } else {
                Log.w(TAG, "Recording file is invalid or empty")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop voice recording", e)
            releaseRecorder()
            null
        }
    }
    
    /**
     * Cancel voice recording (discard without saving).
     */
    fun cancelVoiceRecording() {
        try {
            releaseRecorder()
            currentRecordingFile?.delete()
            currentRecordingFile = null
            Log.i(TAG, "🎤 Voice recording cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording", e)
        }
    }
    
    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = mediaRecorder != null
    
    /**
     * Get current audio level (0-6) based on microphone amplitude.
     * Returns 0 if not recording.
     */
    fun getAudioLevel(): Int {
        val recorder = mediaRecorder ?: return 0
        return try {
            val maxAmplitude = recorder.maxAmplitude
            // maxAmplitude ranges from 0 to ~32767
            // Convert to 0-6 scale with some sensitivity
            when {
                maxAmplitude < 200 -> 0      // Silent
                maxAmplitude < 1000 -> 1     // Very quiet
                maxAmplitude < 3000 -> 2     // Quiet
                maxAmplitude < 7000 -> 3     // Normal
                maxAmplitude < 15000 -> 4    // Loud
                maxAmplitude < 25000 -> 5    // Very loud
                else -> 6                     // Max
            }
        } catch (e: Exception) {
            0
        }
    }
    
    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
    }
    
    /**
     * Get audio duration from a file in milliseconds.
     */
    fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio duration", e)
            0L
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // VIDEO RECORDING
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Create a URI for video recording.
     * Returns a FileProvider URI that can be passed to video capture intent.
     */
    fun createVideoUri(context: Context): Pair<Uri, File>? {
        return try {
            val mediaDir = File(context.cacheDir, "media/video")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(mediaDir, "VID_$timestamp.mp4")
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
            
            Log.d(TAG, "Created video URI: $uri -> ${videoFile.absolutePath}")
            Pair(uri, videoFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create video URI", e)
            null
        }
    }
    
    /**
     * Get video metadata (duration, width, height).
     */
    fun getVideoMetadata(file: File): VideoMetadata {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            retriever.release()
            
            VideoMetadata(duration, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video metadata", e)
            VideoMetadata(0L, 0, 0)
        }
    }
    
    /**
     * Get video thumbnail bitmap.
     */
    fun getVideoThumbnail(file: File): android.graphics.Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video thumbnail", e)
            null
        }
    }
    
    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int
    )
    
    // ══════════════════════════════════════════════════════════════════
    // FILE HANDLING
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Copy a content URI to local cache and return file info.
     */
    fun copyFileFromUri(context: Context, uri: Uri): FileInfo? {
        return try {
            val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = context.contentResolver.getType(uri)
            
            val mediaDir = File(context.cacheDir, "media/files")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            
            val destFile = File(mediaDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "📁 Copied file: ${destFile.absolutePath}, size: ${destFile.length()}")
            
            FileInfo(
                file = destFile,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = destFile.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI", e)
            null
        }
    }
    
    /**
     * Get the file name from a content URI.
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }
    
    /**
     * Clean up old media files (older than 7 days).
     */
    fun cleanupOldMedia(context: Context) {
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val mediaDir = File(context.cacheDir, "media")
        
        if (mediaDir.exists()) {
            mediaDir.walkTopDown().forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                    Log.d(TAG, "Cleaned up old media: ${file.name}")
                }
            }
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ══════════════════════════════════════════════════════════════════
    
    data class VoiceRecordingResult(
        val file: File,
        val durationMs: Long
    )
    
    data class FileInfo(
        val file: File,
        val fileName: String,
        val mimeType: String?,
        val fileSize: Long
    )
}
