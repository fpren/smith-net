package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model Downloader - Downloads AI models from Hugging Face
 * 
 * Supports downloading GGUF models for local inference.
 * Shows progress and handles resume on failure.
 */
object ModelDownloader {
    
    private const val TAG = "ModelDownloader"
    
    // Download state
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    private var currentDownload: DownloadJob? = null
    private var isCancelled = false
    
    // ════════════════════════════════════════════════════════════════════
    // AVAILABLE MODELS
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Available models for download
     */
    val availableModels = listOf(
        ModelInfo(
            id = "qwen3-0.6b-q4",
            name = "Qwen3 0.6B",
            description = "Tiny, fast responses (~400MB)",
            filename = "qwen3-0.6b-q4.gguf",
            sizeBytes = 420_000_000L, // ~400MB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            recommended = false
        ),
        ModelInfo(
            id = "qwen3-1.7b-q4",
            name = "Qwen3 1.7B",
            description = "Balanced speed & quality (~1.1GB)",
            filename = "qwen3-1.7b-q4.gguf",
            sizeBytes = 1_100_000_000L, // ~1.1GB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            recommended = true
        ),
        ModelInfo(
            id = "qwen3-3b-q4",
            name = "Qwen3 3B",
            description = "Higher quality (~2GB)",
            filename = "qwen3-3b-q4.gguf",
            sizeBytes = 2_000_000_000L, // ~2GB
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            recommended = false
        )
    )
    
    /**
     * Get the recommended model
     */
    fun getRecommendedModel(): ModelInfo = availableModels.first { it.recommended }
    
    /**
     * Check which models are already downloaded
     */
    fun getDownloadedModels(context: Context): List<ModelInfo> {
        val modelsDir = LlamaInference.getModelsDirectory(context)
        return availableModels.filter { model ->
            File(modelsDir, model.filename).exists()
        }
    }
    
    /**
     * Check if a specific model is downloaded
     */
    fun isModelDownloaded(context: Context, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val modelsDir = LlamaInference.getModelsDirectory(context)
        return File(modelsDir, model.filename).exists()
    }
    
    /**
     * Get the path to a downloaded model
     */
    fun getModelPath(context: Context, modelId: String): String? {
        val model = availableModels.find { it.id == modelId } ?: return null
        val modelsDir = LlamaInference.getModelsDirectory(context)
        val file = File(modelsDir, model.filename)
        return if (file.exists()) file.absolutePath else null
    }
    
    // ════════════════════════════════════════════════════════════════════
    // DOWNLOAD OPERATIONS
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Download a model by ID
     */
    suspend fun downloadModel(context: Context, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId }
        if (model == null) {
            Log.e(TAG, "Model not found: $modelId")
            _downloadState.value = DownloadState.Error("Model not found")
            return false
        }
        
        return downloadModel(context, model)
    }
    
    /**
     * Download a model
     */
    suspend fun downloadModel(context: Context, model: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        if (_downloadState.value is DownloadState.Downloading) {
            Log.w(TAG, "Download already in progress")
            return@withContext false
        }
        
        isCancelled = false
        _downloadState.value = DownloadState.Downloading(model, 0f)
        _downloadProgress.value = 0f
        
        val modelsDir = LlamaInference.getModelsDirectory(context)
        val targetFile = File(modelsDir, model.filename)
        val tempFile = File(modelsDir, "${model.filename}.tmp")
        
        Log.i(TAG, "Starting download: ${model.name} -> ${targetFile.absolutePath}")
        Log.i(TAG, "URL: ${model.downloadUrl}")
        
        try {
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "TradeMesh-Android/1.0")
            
            // Support resume if temp file exists
            val existingSize = if (tempFile.exists()) tempFile.length() else 0L
            if (existingSize > 0) {
                connection.setRequestProperty("Range", "bytes=$existingSize-")
                Log.i(TAG, "Resuming download from byte $existingSize")
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.i(TAG, "Response code: $responseCode")
            
            if (responseCode !in listOf(200, 206)) {
                Log.e(TAG, "HTTP error: $responseCode")
                _downloadState.value = DownloadState.Error("HTTP error: $responseCode")
                return@withContext false
            }
            
            val totalSize = connection.contentLengthLong + existingSize
            Log.i(TAG, "Total size: $totalSize bytes (${totalSize / 1_000_000}MB)")
            
            // Open streams
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, existingSize > 0)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = existingSize
            var lastProgressUpdate = System.currentTimeMillis()
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    Log.i(TAG, "Download cancelled")
                    inputStream.close()
                    outputStream.close()
                    connection.disconnect()
                    _downloadState.value = DownloadState.Cancelled
                    return@withContext false
                }
                
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                // Update progress every 100ms
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 100) {
                    val progress = if (totalSize > 0) downloadedBytes.toFloat() / totalSize else 0f
                    _downloadProgress.value = progress
                    _downloadState.value = DownloadState.Downloading(model, progress)
                    lastProgressUpdate = now
                }
            }
            
            inputStream.close()
            outputStream.close()
            connection.disconnect()
            
            // Rename temp file to final
            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Download complete: ${targetFile.absolutePath}")
                _downloadProgress.value = 1f
                _downloadState.value = DownloadState.Complete(model)
                return@withContext true
            } else {
                Log.e(TAG, "Failed to rename temp file")
                _downloadState.value = DownloadState.Error("Failed to save file")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            return@withContext false
        }
    }
    
    /**
     * Cancel the current download
     */
    fun cancelDownload() {
        Log.i(TAG, "Cancelling download")
        isCancelled = true
    }
    
    /**
     * Delete a downloaded model
     */
    fun deleteModel(context: Context, modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val modelsDir = LlamaInference.getModelsDirectory(context)
        val file = File(modelsDir, model.filename)
        
        return if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Deleted model ${model.name}: $deleted")
            deleted
        } else {
            false
        }
    }
    
    /**
     * Reset state to idle
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
        _downloadProgress.value = 0f
    }
    
    // ════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════════════
    
    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val filename: String,
        val sizeBytes: Long,
        val downloadUrl: String,
        val recommended: Boolean = false
    ) {
        val sizeMB: Int get() = (sizeBytes / 1_000_000).toInt()
        val sizeDisplay: String get() = when {
            sizeBytes >= 1_000_000_000 -> "%.1fGB".format(sizeBytes / 1_000_000_000f)
            else -> "${sizeMB}MB"
        }
    }
    
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val model: ModelInfo, val progress: Float) : DownloadState()
        data class Complete(val model: ModelInfo) : DownloadState()
        data class Error(val message: String) : DownloadState()
        object Cancelled : DownloadState()
    }
    
    data class DownloadJob(
        val model: ModelInfo,
        val startTime: Long = System.currentTimeMillis()
    )
}

