package com.guildofsmiths.trademesh.service

import android.util.Log
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MediaUploadManager: Handles uploading media files to the backend server.
 * Supports images, voice notes, and files with progress tracking.
 */
object MediaUploadManager {
    
    private const val TAG = "MediaUploadManager"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /** Backend URL for media uploads */
    private var backendUrl: String = "http://192.168.8.163:3000"
    
    /** Upload queue */
    private val _uploadQueue = MutableStateFlow<List<UploadTask>>(emptyList())
    val uploadQueue: StateFlow<List<UploadTask>> = _uploadQueue
    
    /** Current upload progress (messageId -> progress 0..100) */
    private val _uploadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Int>> = _uploadProgress
    
    /** OkHttp client with longer timeouts for uploads */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Set the backend URL.
     */
    fun setBackendUrl(url: String) {
        backendUrl = url.trimEnd('/')
        Log.d(TAG, "Backend URL set to: $backendUrl")
    }
    
    /**
     * Upload media for a message.
     * Returns the remote URL on success, null on failure.
     */
    suspend fun uploadMedia(message: Message): String? {
        val media = message.media ?: return null
        val localPath = media.localPath ?: return null
        val file = File(localPath)
        
        if (!file.exists()) {
            Log.e(TAG, "Media file not found: $localPath")
            return null
        }
        
        Log.i(TAG, "📤 Uploading media: ${file.name} (${file.length()} bytes)")
        
        return try {
            // Determine content type
            val contentType = (media.mimeType ?: when (media.type) {
                MediaType.IMAGE -> "image/jpeg"
                MediaType.VOICE -> "audio/mp4"
                MediaType.FILE -> "application/octet-stream"
                else -> "application/octet-stream"
            }).toMediaType()
            
            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    media.fileName ?: file.name,
                    file.asRequestBody(contentType)
                )
                .addFormDataPart("messageId", message.id)
                .addFormDataPart("channelId", message.channelId)
                .addFormDataPart("senderId", message.senderId)
                .addFormDataPart("mediaType", media.type.name)
                .build()
            
            val request = Request.Builder()
                .url("$backendUrl/api/media/upload")
                .post(requestBody)
                .build()
            
            // Update progress
            _uploadProgress.value = _uploadProgress.value + (message.id to 0)
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "{}")
                val remoteUrl = json.optString("url", null)
                
                Log.i(TAG, "✅ Upload complete: $remoteUrl")
                _uploadProgress.value = _uploadProgress.value + (message.id to 100)
                
                remoteUrl
            } else {
                Log.e(TAG, "❌ Upload failed: ${response.code} - ${response.message}")
                _uploadProgress.value = _uploadProgress.value - message.id
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload error", e)
            _uploadProgress.value = _uploadProgress.value - message.id
            null
        }
    }
    
    /**
     * Queue a media upload for background processing.
     */
    fun queueUpload(message: Message, onComplete: (String?) -> Unit) {
        val task = UploadTask(message, onComplete)
        _uploadQueue.value = _uploadQueue.value + task
        
        scope.launch {
            val result = uploadMedia(message)
            onComplete(result)
            _uploadQueue.value = _uploadQueue.value.filter { it.message.id != message.id }
        }
    }
    
    /**
     * Send a media message to the backend (metadata + optional inline base64).
     * Used when media is small enough to inline or when upload isn't needed.
     */
    fun sendMediaMessage(message: Message, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val media = message.media
                val json = JSONObject().apply {
                    put("id", message.id)
                    put("channelId", message.channelId)
                    put("senderId", message.senderId)
                    put("senderName", message.senderName)
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    put("mediaType", message.mediaType.name)
                    
                    if (media != null) {
                        put("media", JSONObject().apply {
                            put("type", media.type.name)
                            put("mimeType", media.mimeType)
                            put("fileName", media.fileName)
                            put("fileSize", media.fileSize)
                            put("duration", media.duration)
                            put("width", media.width)
                            put("height", media.height)
                            media.remotePath?.let { put("url", it) }
                        })
                    }
                    
                    message.recipientId?.let { put("recipientId", it) }
                    message.recipientName?.let { put("recipientName", it) }
                }
                
                val requestBody = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url("$backendUrl/api/messages")
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Media message sent: ${message.id}")
                    onComplete(true)
                } else {
                    Log.e(TAG, "❌ Failed to send media message: ${response.code}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending media message", e)
                onComplete(false)
            }
        }
    }
    
    /**
     * Upload media and send the message in one operation.
     */
    fun uploadAndSendMedia(message: Message, onComplete: (Boolean) -> Unit) {
        scope.launch {
            // First upload the media file
            val remoteUrl = uploadMedia(message)
            
            if (remoteUrl != null) {
                // Update message with remote URL
                val updatedMessage = message.copy(
                    media = message.media?.copy(
                        remotePath = remoteUrl,
                        isQueued = false
                    )
                )
                
                // Send the message with media URL
                sendMediaMessage(updatedMessage) { success ->
                    onComplete(success)
                }
            } else {
                Log.e(TAG, "Upload failed, cannot send media message")
                onComplete(false)
            }
        }
    }
    
    /**
     * Cancel an upload.
     */
    fun cancelUpload(messageId: String) {
        _uploadQueue.value = _uploadQueue.value.filter { it.message.id != messageId }
        _uploadProgress.value = _uploadProgress.value - messageId
        Log.d(TAG, "Upload cancelled: $messageId")
    }
    
    /**
     * Get upload progress for a message (0-100).
     */
    fun getProgress(messageId: String): Int {
        return _uploadProgress.value[messageId] ?: 0
    }
    
    /**
     * Upload task data class.
     */
    data class UploadTask(
        val message: Message,
        val onComplete: (String?) -> Unit
    )
}
