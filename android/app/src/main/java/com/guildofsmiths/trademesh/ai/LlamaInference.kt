package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * LlamaInference - JNI wrapper for llama.cpp on-device LLM inference
 * 
 * Provides Kotlin interface to native llama.cpp library for running
 * Qwen3-1.7B (or similar GGUF models) on Android devices.
 * 
 * Features:
 * - Model loading/unloading
 * - Text generation with configurable parameters
 * - Cancellation support
 * - Thread-safe operations
 */
object LlamaInference {
    
    private const val TAG = "LlamaInference"
    
    // Default inference parameters
    private const val DEFAULT_CONTEXT_SIZE = 2048
    private const val DEFAULT_MAX_TOKENS = 256
    private const val DEFAULT_TEMPERATURE = 0.7f
    private const val DEFAULT_THREADS = 4
    
    // Model state
    private val _modelState = MutableStateFlow(ModelState.NOT_LOADED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    
    private val _modelInfo = MutableStateFlow<ModelInfo?>(null)
    val modelInfo: StateFlow<ModelInfo?> = _modelInfo.asStateFlow()
    
    private var isInitialized = false
    private var modelPath: String? = null
    
    // Load native library
    init {
        try {
            System.loadLibrary("llama_jni")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // NATIVE METHODS (JNI)
    // ════════════════════════════════════════════════════════════════════
    
    private external fun nativeInit(): Boolean
    private external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeCancelGeneration()
    private external fun nativeUnloadModel()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeFree()
    private external fun nativeGetModelInfo(): String
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the llama backend. Call once at app startup.
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        return try {
            val result = nativeInit()
            isInitialized = result
            Log.i(TAG, "Initialization: ${if (result) "SUCCESS" else "FAILED"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error", e)
            false
        }
    }
    
    /**
     * Load a GGUF model from the specified path.
     * 
     * @param path Full path to the .gguf model file
     * @param contextSize Token context window size (default 2048)
     * @param threads Number of CPU threads to use (default 4)
     * @return true if model loaded successfully
     */
    suspend fun loadModel(
        path: String,
        contextSize: Int = DEFAULT_CONTEXT_SIZE,
        threads: Int = DEFAULT_THREADS
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized, initializing now")
            if (!initialize()) {
                _modelState.value = ModelState.ERROR
                return@withContext false
            }
        }
        
        // Verify file exists
        val modelFile = File(path)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: $path")
            _modelState.value = ModelState.ERROR
            return@withContext false
        }
        
        Log.i(TAG, "Loading model: $path (ctx=$contextSize, threads=$threads)")
        _modelState.value = ModelState.LOADING
        
        try {
            val result = nativeLoadModel(path, contextSize, threads)
            
            if (result) {
                modelPath = path
                _modelState.value = ModelState.READY
                updateModelInfo()
                Log.i(TAG, "Model loaded successfully")
            } else {
                _modelState.value = ModelState.ERROR
                Log.e(TAG, "Failed to load model")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading model", e)
            _modelState.value = ModelState.ERROR
            false
        }
    }
    
    /**
     * Generate text from a prompt.
     * 
     * @param prompt Input prompt text
     * @param maxTokens Maximum tokens to generate (default 256)
     * @param temperature Sampling temperature 0.0-1.0 (default 0.7)
     * @return Generated text response
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        temperature: Float = DEFAULT_TEMPERATURE
    ): GenerationResult = withContext(Dispatchers.IO) {
        if (_modelState.value != ModelState.READY) {
            Log.w(TAG, "Model not ready, state: ${_modelState.value}")
            return@withContext GenerationResult.Error("Model not loaded")
        }
        
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Generating response (maxTokens=$maxTokens, temp=$temperature)")
        
        try {
            val response = nativeGenerate(prompt, maxTokens, temperature)
            val duration = System.currentTimeMillis() - startTime
            
            Log.i(TAG, "Generation complete in ${duration}ms, response length: ${response.length}")
            
            if (response.startsWith("[Error:")) {
                GenerationResult.Error(response)
            } else {
                GenerationResult.Success(
                    text = response,
                    durationMs = duration,
                    tokensGenerated = estimateTokenCount(response)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            GenerationResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Cancel ongoing text generation.
     */
    fun cancelGeneration() {
        Log.d(TAG, "Cancelling generation")
        try {
            nativeCancelGeneration()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling generation", e)
        }
    }
    
    /**
     * Unload the current model and free memory.
     */
    fun unloadModel() {
        Log.i(TAG, "Unloading model")
        try {
            nativeUnloadModel()
            modelPath = null
            _modelState.value = ModelState.NOT_LOADED
            _modelInfo.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }
    
    /**
     * Check if a model is currently loaded.
     */
    fun isModelLoaded(): Boolean {
        return try {
            nativeIsModelLoaded()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Free all resources. Call at app shutdown.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down llama inference")
        try {
            nativeFree()
            isInitialized = false
            _modelState.value = ModelState.NOT_LOADED
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
    
    /**
     * Get the path to the models directory in app storage.
     */
    fun getModelsDirectory(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Check if the default model file exists.
     */
    fun isDefaultModelDownloaded(context: Context): Boolean {
        val modelFile = File(getModelsDirectory(context), "qwen3-1.7b-q4.gguf")
        return modelFile.exists()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    private fun updateModelInfo() {
        try {
            val infoJson = nativeGetModelInfo()
            val json = JSONObject(infoJson)
            
            _modelInfo.value = ModelInfo(
                vocabSize = json.optInt("vocab_size", 0),
                contextSize = json.optInt("context_size", 0),
                isLoaded = json.optBoolean("loaded", false),
                isStub = json.optBoolean("stub", false)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get model info", e)
        }
    }
    
    private fun estimateTokenCount(text: String): Int {
        // Rough estimate: ~4 characters per token for English
        return (text.length / 4).coerceAtLeast(1)
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * Model loading state
 */
enum class ModelState {
    NOT_LOADED,
    LOADING,
    READY,
    ERROR
}

/**
 * Model information
 */
data class ModelInfo(
    val vocabSize: Int,
    val contextSize: Int,
    val isLoaded: Boolean,
    val isStub: Boolean = false
)

/**
 * Result of text generation
 */
sealed class GenerationResult {
    data class Success(
        val text: String,
        val durationMs: Long,
        val tokensGenerated: Int
    ) : GenerationResult()
    
    data class Error(val message: String) : GenerationResult()
}
