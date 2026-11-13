package com.clauditor.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class WorktreeOfTest {
    @Test
    fun `finds the worktree with either slash style`() {
        assertEquals("checkout-redesign", ClaudeSessionEditor.worktreeOf("/home/a/shop/.claude/worktrees/checkout-redesign"))
        assertEquals("checkout-redesign", ClaudeSessionEditor.worktreeOf("/home/a/shop/.claude/worktrees/checkout-redesign/src"))
        assertEquals("fix", ClaudeSessionEditor.worktreeOf("C:\\Users\\a\\shop\\.claude\\worktrees\\fix"))
        assertEquals("", ClaudeSessionEditor.worktreeOf("/home/a/shop"))
    }
}
