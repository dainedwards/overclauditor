package com.clauditor.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screens copied from Claude Code 2.1.323 running in a pty. */
class DraftDetectTest {
    private val rule = "─".repeat(60)
    private fun screen(input: String) = listOf(
        "  some reply text",
        "❯ an older prompt in the scrollback",
        "                                        ◐ medium · /effort",
        rule, input, rule,
        "  ⏵⏵ bypass permissions on (shift+tab to cycle)"
    )

    @Test fun typedTextIsADraft() = assertTrue(ClaudeSessionEditor.draftIn(screen("❯ my draft")))
    @Test fun emptyBoxIsNot() = assertFalse(ClaudeSessionEditor.draftIn(screen("❯ ")))
    @Test fun placeholderIsNot() = assertFalse(ClaudeSessionEditor.draftIn(screen("❯ Try \"how does <filepath> work?\"")))
    @Test fun menuCursorIsNot() = assertFalse(ClaudeSessionEditor.draftIn(listOf("Pick one", "❯ No, exit", "  Yes, I trust this folder")))
}
