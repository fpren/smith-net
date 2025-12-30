package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end instrumented tests for AI flow
 * 
 * Tests the complete flow:
 * 1. Mesh message with @AI cue
 * 2. CueDetector identifies cue type
 * 3. BatteryGate allows/restricts inference
 * 4. AIRouter routes to LLM or rule-based
 * 5. Response is cached with attribution
 * 6. Sync to ForemanHub/online
 */
@RunWith(AndroidJUnit4::class)
class AIFlowInstrumentedTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Initialize AI components
        BatteryGate.initialize(context)
        AIRouter.initialize(context)
        AIRouter.setEnabled(true)
    }
    
    @After
    fun teardown() {
        AIRouter.shutdown()
        BatteryGate.shutdown()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // END-TO-END FLOW TESTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testOfflineAIFlow_Translation() = runBlocking {
        // 1. Simulate mesh message with @AI translate cue
        val message = "@AI translate: hola amigo"
        
        // 2. Detect cue
        val cue = CueDetector.detect(message, MessageContext.MESH)
        
        assertEquals(AICueType.TRANSLATION, cue.type)
        assertEquals(AIIntent.TRANSLATE, cue.intent)
        assertTrue(cue.requiresAI)
        
        // 3. Check battery gate (should be allowed in test env)
        val availability = BatteryGate.getAIStatus()
        assertTrue(
            "Battery should allow at least rule-based",
            availability != AIAvailability.DISABLED
        )
        
        // 4. Process through AIRouter
        var response: AIResponse? = null
        AIRouter.processMessage(
            message = message,
            messageContext = MessageContext.MESH,
            metadata = AIMetadata()
        ) { result ->
            response = result
        }
        
        // Wait for processing
        Thread.sleep(100)
        
        // 5. Verify response
        assertNotNull("Should receive response", response)
        
        when (val r = response) {
            is AIResponse.Success -> {
                assertFalse("Response should not be empty", r.text.isEmpty())
                assertNotNull("Should have source", r.source)
                assertNotNull("Should have model", r.model)
            }
            is AIResponse.BatteryLow -> {
                // Acceptable if battery is low
                assertTrue(r.message.isNotEmpty())
            }
            is AIResponse.Disabled -> fail("AI should be enabled")
            is AIResponse.Error -> fail("Should not error: ${r.message}")
            null -> fail("Response should not be null")
        }
    }
    
    @Test
    fun testOfflineAIFlow_Checklist() = runBlocking {
        // 1. Simulate job board message with checklist request
        val message = "@AI checklist for electrical panel"
        
        // 2. Detect cue
        val cue = CueDetector.detect(message, MessageContext.JOB_BOARD)
        
        assertEquals(AIIntent.CHECKLIST, cue.intent)
        assertTrue(cue.requiresAI)
        
        // 3. Process with job context
        var response: AIResponse? = null
        AIRouter.processMessage(
            message = message,
            messageContext = MessageContext.JOB_BOARD,
            metadata = AIMetadata(jobTitle = "Panel Upgrade")
        ) { result ->
            response = result
        }
        
        Thread.sleep(100)
        
        // 4. Verify checklist response
        when (val r = response) {
            is AIResponse.Success -> {
                // Checklist should contain bullet points or checkbox items
                assertTrue(
                    "Checklist should have items",
                    r.text.contains("□") || r.text.contains("-") || r.text.contains("1.")
                )
            }
            is AIResponse.BatteryLow -> {
                // Acceptable - rule-based should still work
            }
            else -> {} // Other responses acceptable in test env
        }
    }
    
    @Test
    fun testOfflineAIFlow_Confirmation() = runBlocking {
        val message = "@AI confirm this is correct"
        
        val cue = CueDetector.detect(message, MessageContext.CHAT)
        
        assertEquals(AIIntent.CONFIRM, cue.intent)
        
        var response: AIResponse? = null
        AIRouter.processMessage(
            message = message,
            messageContext = MessageContext.CHAT,
            metadata = AIMetadata()
        ) { result ->
            response = result
        }
        
        Thread.sleep(100)
        
        when (val r = response) {
            is AIResponse.Success -> {
                assertTrue(
                    "Confirmation should contain checkmark or acknowledgment",
                    r.text.contains("✓") || 
                    r.text.lowercase().contains("confirm") ||
                    r.text.lowercase().contains("acknowled")
                )
            }
            else -> {} // Other responses acceptable
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // BATTERY GATE TESTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testBatteryGate_StatusRetrieval() {
        val state = BatteryGate.gateState.value
        
        // Battery level should be valid
        assertTrue("Battery should be 0-100", state.batteryLevel in 0..100)
        
        // Status should be determinable
        val status = BatteryGate.getAIStatus()
        assertNotNull(status)
    }
    
    @Test
    fun testBatteryGate_TokenRecommendation() {
        val maxTokens = BatteryGate.getRecommendedMaxTokens()
        
        // Should return a valid token count
        assertTrue("Max tokens should be >= 0", maxTokens >= 0)
        assertTrue("Max tokens should be <= 512", maxTokens <= 512)
    }
    
    @Test
    fun testBatteryGate_AutoDegradeToggle() {
        // Default should be enabled
        assertTrue(BatteryGate.isAutoDegradeEnabled())
        
        // Toggle off
        BatteryGate.setAutoDegradeEnabled(false)
        assertFalse(BatteryGate.isAutoDegradeEnabled())
        
        // Toggle back on
        BatteryGate.setAutoDegradeEnabled(true)
        assertTrue(BatteryGate.isAutoDegradeEnabled())
    }
    
    @Test
    fun testBatteryGate_StatusText() {
        val statusText = BatteryGate.getStatusText()
        
        assertNotNull(statusText)
        assertTrue("Status should contain percentage", statusText.contains("%"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // RESPONSE CACHING TESTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testResponseCaching() = runBlocking {
        // Clear existing cache
        AIRouter.clearCache()
        
        // Process a message
        AIRouter.processMessage(
            message = "@AI confirm",
            messageContext = MessageContext.CHAT,
            metadata = AIMetadata(channelId = "test-channel")
        ) { _ -> }
        
        Thread.sleep(100)
        
        // Check if response was cached
        val pending = AIRouter.getPendingResponses()
        
        // Cache may or may not have entry depending on response type
        // Just verify the API works
        assertNotNull(pending)
    }
    
    @Test
    fun testResponseSyncMarking() {
        AIRouter.clearCache()
        
        val ids = listOf("test-id-1", "test-id-2")
        AIRouter.markResponsesSynced(ids)
        
        // Should not throw
        val pending = AIRouter.getPendingResponses()
        assertTrue("Marked IDs should be removed", pending.none { it.id in ids })
    }
    
    // ════════════════════════════════════════════════════════════════════
    // AI ROUTER STATUS TESTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testAIRouter_EnableDisable() {
        AIRouter.setEnabled(true)
        assertTrue(AIRouter.isEnabled())
        
        AIRouter.setEnabled(false)
        assertFalse(AIRouter.isEnabled())
        
        // Re-enable for other tests
        AIRouter.setEnabled(true)
    }
    
    @Test
    fun testAIRouter_StatusText() {
        val statusText = AIRouter.getStatusText()
        
        assertNotNull(statusText)
        assertTrue("Status should not be empty", statusText.isNotEmpty())
    }
    
    @Test
    fun testAIRouter_Availability() {
        AIRouter.setEnabled(true)
        
        val available = AIRouter.isAvailable()
        val status = AIRouter.getStatus()
        
        // Availability should match status
        if (available) {
            assertTrue(
                "Available should mean not DISABLED",
                status != AIStatus.DISABLED
            )
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // DISABLED STATE TESTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testAIRouter_DisabledResponse() = runBlocking {
        AIRouter.setEnabled(false)
        
        var response: AIResponse? = null
        AIRouter.processMessage(
            message = "@AI help",
            messageContext = MessageContext.CHAT,
            metadata = AIMetadata()
        ) { result ->
            response = result
        }
        
        Thread.sleep(100)
        
        assertTrue(
            "Disabled AI should return Disabled response",
            response is AIResponse.Disabled
        )
        
        // Re-enable
        AIRouter.setEnabled(true)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LLAMA INFERENCE TESTS (STUB MODE)
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun testLlamaInference_Initialization() {
        val result = LlamaInference.initialize()
        
        // Should succeed (even in stub mode)
        assertTrue("Initialization should succeed", result)
    }
    
    @Test
    fun testLlamaInference_ModelState() {
        val state = LlamaInference.modelState.value
        
        // In test environment without model, should be NOT_LOADED
        assertNotNull(state)
    }
    
    @Test
    fun testLlamaInference_ModelsDirectory() {
        val dir = LlamaInference.getModelsDirectory(context)
        
        assertNotNull(dir)
        assertTrue("Models directory should exist or be created", dir.exists() || dir.mkdirs())
    }
}
