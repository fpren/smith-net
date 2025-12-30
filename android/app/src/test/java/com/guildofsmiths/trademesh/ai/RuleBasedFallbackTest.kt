package com.guildofsmiths.trademesh.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for RuleBasedFallback - pre-defined responses
 */
class RuleBasedFallbackTest {
    
    // ════════════════════════════════════════════════════════════════════
    // TIME TRACKING RESPONSES
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `getResponse should return clock in response`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.TIME_TRACKING,
            originalMessage = "@AI clock in",
            extractedQuery = "clock in"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("Clocked in"))
    }
    
    @Test
    fun `getResponse should return break response`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.TIME_TRACKING,
            originalMessage = "@AI break",
            extractedQuery = "break"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("Break"))
    }
    
    @Test
    fun `getResponse should return lunch response`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.TIME_TRACKING,
            originalMessage = "@AI lunch break",
            extractedQuery = "lunch"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("Lunch") || response.contains("lunch"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // CONFIRMATION RESPONSES
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `getResponse should return confirmation for yes`() {
        val cue = AICue(
            type = AICueType.CONFIRMATION,
            intent = AIIntent.CONFIRM,
            originalMessage = "@AI is this correct? yes",
            extractedQuery = "is this correct? yes"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("✓") || response.contains("Confirmed") || response.contains("Acknowledged"))
    }
    
    @Test
    fun `getResponse should return confirmation for ok`() {
        val cue = AICue(
            type = AICueType.CONFIRMATION,
            intent = AIIntent.CONFIRM,
            originalMessage = "@AI ok",
            extractedQuery = "ok"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("✓") || response.contains("Acknowledged"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // TRADE CHECKLISTS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `getTradeChecklist should return electrical checklist`() {
        val checklist = RuleBasedFallback.getTradeChecklist("electrical")
        
        assertTrue(checklist.contains("power"))
        assertTrue(checklist.contains("□"))
    }
    
    @Test
    fun `getTradeChecklist should return plumbing checklist`() {
        val checklist = RuleBasedFallback.getTradeChecklist("plumbing")
        
        assertTrue(checklist.contains("water"))
        assertTrue(checklist.contains("leak"))
    }
    
    @Test
    fun `getTradeChecklist should return painting checklist`() {
        val checklist = RuleBasedFallback.getTradeChecklist("painting")
        
        assertTrue(checklist.contains("primer") || checklist.contains("coat"))
    }
    
    @Test
    fun `getTradeChecklist should return general checklist for unknown trade`() {
        val checklist = RuleBasedFallback.getTradeChecklist("unknown")
        
        assertTrue(checklist.contains("job scope") || checklist.contains("tools") || checklist.contains("Safety"))
    }
    
    @Test
    fun `getResponse should detect trade from job context`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.CHECKLIST,
            originalMessage = "@AI checklist",
            extractedQuery = "checklist"
        )
        val metadata = AIMetadata(jobTitle = "Electrical panel upgrade")
        
        val response = RuleBasedFallback.getResponse(cue, metadata)
        
        assertTrue(response.contains("power") || response.contains("□"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // TRANSLATIONS
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `getSimpleTranslation should translate Spanish hola`() {
        val translation = RuleBasedFallback.getSimpleTranslation("hola", "es")
        
        assertEquals("Hello", translation)
    }
    
    @Test
    fun `getSimpleTranslation should translate Spanish gracias`() {
        val translation = RuleBasedFallback.getSimpleTranslation("gracias", "es")
        
        assertEquals("Thank you", translation)
    }
    
    @Test
    fun `getSimpleTranslation should translate French bonjour`() {
        val translation = RuleBasedFallback.getSimpleTranslation("bonjour", "fr")
        
        assertEquals("Hello", translation)
    }
    
    @Test
    fun `getSimpleTranslation should return null for unknown phrase`() {
        val translation = RuleBasedFallback.getSimpleTranslation("unknown phrase", "es")
        
        assertNull(translation)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // JOB HELP
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `getResponse should return materials response`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.JOB_HELP,
            originalMessage = "@AI what materials?",
            extractedQuery = "what materials?"
        )
        
        val response = RuleBasedFallback.getResponse(cue)
        
        assertTrue(response.contains("Materials") || response.contains("materials") || response.contains("Job Board"))
    }
    
    @Test
    fun `getResponse should include job title in context`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.JOB_HELP,
            originalMessage = "@AI job status",
            extractedQuery = "job status"
        )
        val metadata = AIMetadata(jobTitle = "Kitchen Renovation")
        
        val response = RuleBasedFallback.getResponse(cue, metadata)
        
        assertTrue(response.contains("Kitchen Renovation") || response.contains("Job Board"))
    }
    
    // ════════════════════════════════════════════════════════════════════
    // HAS RESPONSE CHECK
    // ════════════════════════════════════════════════════════════════════
    
    @Test
    fun `hasResponse should return false for NONE cue`() {
        val cue = AICue(
            type = AICueType.NONE,
            intent = AIIntent.NONE,
            originalMessage = "hello",
            extractedQuery = null
        )
        
        assertFalse(RuleBasedFallback.hasResponse(cue))
    }
    
    @Test
    fun `hasResponse should return true for confirmation cue`() {
        val cue = AICue(
            type = AICueType.CONFIRMATION,
            intent = AIIntent.CONFIRM,
            originalMessage = "@AI yes",
            extractedQuery = "yes"
        )
        
        assertTrue(RuleBasedFallback.hasResponse(cue))
    }
    
    @Test
    fun `hasResponse should return true for checklist cue`() {
        val cue = AICue(
            type = AICueType.TASK_HELP,
            intent = AIIntent.CHECKLIST,
            originalMessage = "@AI checklist",
            extractedQuery = "checklist"
        )
        
        assertTrue(RuleBasedFallback.hasResponse(cue))
    }
}
