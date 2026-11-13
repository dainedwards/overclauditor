package com.clauditor.editor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorTextTest {
    @Test
    fun `cli failures never become titles or summaries`() {
        for (t in listOf("Invalid API key · Please run /login", "API Error: 529 overloaded_error", "Not logged in",
                         "Claude AI usage limit reached", "Credit balance is too low")) {
            assertTrue(ClaudeSessionEditor.looksLikeError(t), t)
            assertTrue(ClaudeSessionEditor.isCliErrorOutput(t), t)
        }
    }

    @Test
    fun `titles are conservative, summaries keep ordinary work`() {
        assertTrue(ClaudeSessionEditor.looksLikeError("Login error investigation"))
        assertFalse(ClaudeSessionEditor.isCliErrorOutput("payment tests failed on CI, retry logic"))
        assertFalse(ClaudeSessionEditor.looksLikeError("Checkout flow redesign"))
    }
}

class TitleShapeTest {
    @Test fun summarizerSentenceIsNoTitle() = org.junit.Assert.assertTrue(ClaudeSessionEditor.unfitTitle(
        "Cannot build the status line: the six message images and the most recent request image did not come through as readable content, and the working directory (`/Users/x/.claude/statusline-cache/agent`) is empty."))
    @Test fun sentenceWithFullStop() = org.junit.Assert.assertTrue(ClaudeSessionEditor.unfitTitle("I need more context to summarize this."))
    @Test fun realGistsPass() {
        for (t in listOf("Brood egg and bountyhunter texture audit", "Tripo texture fixes for models", "Overclauditor button fixes"))
            org.junit.Assert.assertFalse(t, ClaudeSessionEditor.unfitTitle(t))
    }
}
