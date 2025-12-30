package com.guildofsmiths.trademesh.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CueDetector - AI cue and language detection
 */
class CueDetectorTest {
    
    // ════════════════════════════════════════════════════════════════════
    // CUE DETECTION
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect should return NONE for messages without AI cue`() {
        val cue = CueDetector.detect("Hello, how are you?")
        
        assertEquals(AICueType.NONE, cue.type)
        assertEquals(AIIntent.NONE, cue.intent)
        assertFalse(cue.requiresAI)
    }
    
    @Test
    fun `detect should identify explicit @AI cue`() {
        val cue = CueDetector.detect("@AI what tools do I need?")
        
        assertNotEquals(AICueType.NONE, cue.type)
        assertTrue(cue.requiresAI)
    }
    
    @Test
    fun `detect should be case insensitive for @ai cue`() {
        val variations = listOf(
            "@ai help me",
            "@AI help me",
            "@Ai help me",
            "@aI help me"
        )
        
        variations.forEach { message ->
            val cue = CueDetector.detect(message)
            assertTrue("Should detect AI cue in: $message", cue.requiresAI)
        }
    }
    
    @Test
    fun `detect should identify @assistant cue`() {
        val cue = CueDetector.detect("@assistant what's next?")
        
        assertTrue(cue.requiresAI)
    }
    
    @Test
    fun `detect should identify @smith cue`() {
        val cue = CueDetector.detect("@smith checklist for electrical")
        
        assertTrue(cue.requiresAI)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // INTENT DETECTION
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect should identify translation intent`() {
        val cue = CueDetector.detect("@AI translate: hola amigo")
        
        assertEquals(AICueType.TRANSLATION, cue.type)
        assertEquals(AIIntent.TRANSLATE, cue.intent)
    }
    
    @Test
    fun `detect should identify checklist intent`() {
        val cue = CueDetector.detect("@AI checklist for bathroom renovation")
        
        assertEquals(AIIntent.CHECKLIST, cue.intent)
    }
    
    @Test
    fun `detect should identify task help intent`() {
        val cue = CueDetector.detect("@AI what tasks are next?")
        
        assertTrue(cue.intent in listOf(AIIntent.TASK_HELP, AIIntent.QUESTION))
    }
    
    @Test
    fun `detect should identify confirmation intent`() {
        val cue = CueDetector.detect("@AI is this correct?")
        
        assertEquals(AIIntent.CONFIRM, cue.intent)
    }
    
    @Test
    fun `detect should identify time tracking intent`() {
        val cue = CueDetector.detect("@AI how many hours today?")
        
        assertEquals(AIIntent.TIME_TRACKING, cue.intent)
    }
    
    @Test
    fun `detect should identify job help intent`() {
        val cue = CueDetector.detect("@AI what materials for the job?")
        
        assertEquals(AIIntent.JOB_HELP, cue.intent)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LANGUAGE DETECTION
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect should identify Spanish phrases`() {
        val cue = CueDetector.detect("@AI hola, necesito ayuda")
        
        assertEquals("es", cue.detectedLanguage)
        assertTrue(cue.needsTranslation)
    }
    
    @Test
    fun `detect should identify French phrases`() {
        val cue = CueDetector.detect("@AI bonjour, comment allez-vous?")
        
        assertEquals("fr", cue.detectedLanguage)
        assertTrue(cue.needsTranslation)
    }
    
    @Test
    fun `detect should not mark English as needing translation`() {
        val cue = CueDetector.detect("@AI what is the next step?")
        
        assertEquals("en", cue.detectedLanguage)
        assertFalse(cue.needsTranslation)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // CONTEXT
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect should preserve message context`() {
        val cue = CueDetector.detect(
            "@AI what materials?", 
            context = MessageContext.JOB_BOARD
        )
        
        assertEquals(MessageContext.JOB_BOARD, cue.context)
        assertEquals(AIIntent.CHECKLIST, cue.intent) // Checklist for job context
    }
    
    @Test
    fun `detect should preserve mesh context`() {
        val cue = CueDetector.detect(
            "@AI translate: gracias",
            context = MessageContext.MESH
        )
        
        assertEquals(MessageContext.MESH, cue.context)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // QUERY EXTRACTION
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `detect should extract query from message`() {
        val cue = CueDetector.detect("@AI what tools do I need?")
        
        assertNotNull(cue.extractedQuery)
        assertTrue(cue.extractedQuery!!.contains("tools"))
        assertFalse(cue.extractedQuery!!.contains("@AI"))
    }
    
    @Test
    fun `detect should extract translation text`() {
        val cue = CueDetector.detect("@AI translate: buenos dias")
        
        assertNotNull(cue.extractedQuery)
        assertTrue(cue.extractedQuery!!.contains("buenos dias"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // COMPLEXITY ESTIMATION
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `estimateComplexity should return MINIMAL for short messages`() {
        val complexity = CueDetector.estimateComplexity("@AI help")
        
        assertEquals(CueComplexity.MINIMAL, complexity)
    }
    
    @Test
    fun `estimateComplexity should return MEDIUM for translation requests`() {
        val complexity = CueDetector.estimateComplexity("@AI translate: hola")
        
        assertEquals(CueComplexity.MEDIUM, complexity)
    }
    
    @Test
    fun `estimateComplexity should return HIGH for long messages`() {
        val longMessage = "@AI " + "word ".repeat(120)
        val complexity = CueDetector.estimateComplexity(longMessage)
        
        assertEquals(CueComplexity.HIGH, complexity)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // QUICK CHECK
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `hasAICue should quickly detect presence of cue`() {
        assertTrue(CueDetector.hasAICue("@AI help"))
        assertTrue(CueDetector.hasAICue("Hey @assistant"))
        assertTrue(CueDetector.hasAICue("@smith checklist"))
        
        assertFalse(CueDetector.hasAICue("Hello there"))
        assertFalse(CueDetector.hasAICue("Email me at user@ai.com"))
    }
}
