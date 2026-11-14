package com.clauditor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Picker screens copied from Claude Code 2.1.325 in a pty. */
class PickerScreenTest {
    private val effortAtHigh = listOf(
        "   Effort",
        "                                       Faster                             Smarter",
        "                                       ────────────────────▲────────────────",
        "                                       low     medium     high     xhigh      max      Tab to toggle",
        "   ←/→ to adjust · Enter to confirm · s for this session only · Esc to cancel"
    )
    private val effortAtMedium = effortAtHigh.toMutableList().also {
        it[2] = "                                       ──────────▲───────────────────────────────      Ultracode  off"
    }

    @Test fun effortOnTarget() = assertEquals(0, ClaudeSessionEditor.effortMove(effortAtHigh, "high"))
    @Test fun effortStepRight() = assertEquals(1, ClaudeSessionEditor.effortMove(effortAtMedium, "xhigh"))
    @Test fun effortStepLeft() = assertEquals(-1, ClaudeSessionEditor.effortMove(effortAtHigh, "low"))
    @Test fun effortUnreadable() = assertNull(ClaudeSessionEditor.effortMove(listOf("nothing here"), "low"))

    private fun models(cursor: Int) = listOf(
        "   Select model",
        "     1.  Default (recommended)  Opus 5.5 · Best for everyday, complex tasks",
        "     2.  Opus 5.5 ✔             For complex work and everyday tasks",
        "     3.  Fable 5.2              For your toughest challenges",
        "     4.  Sonnet 5.5             Most efficient for simpler tasks",
        "     5.  Haiku 5.5              Fastest for quick answers",
        "     ▸   More models",
        "   Enter to set as default · s to use this session only · Esc to cancel"
    ).mapIndexed { i, l -> if (i == cursor) l.replaceFirst("   ", " ❯ ").let { if (it.startsWith("  ❯")) it else it } else l }

    @Test fun modelOnTarget() = assertEquals(0, ClaudeSessionEditor.modelMove(models(2), "opus"))
    @Test fun modelDown() = assertEquals(1, ClaudeSessionEditor.modelMove(models(2), "sonnet"))
    @Test fun modelUp() = assertEquals(-1, ClaudeSessionEditor.modelMove(models(5), "fable"))
    @Test fun defaultRowNeverMatches() = assertEquals(1, ClaudeSessionEditor.modelMove(models(1), "opus"))
    @Test fun unknownModel() = assertNull(ClaudeSessionEditor.modelMove(models(2), "gpt"))
}

class SessionSettingsTest {
    @Test fun newestMainChainReplyWins() {
        val lines = listOf(
            """{"type":"assistant","requestedModel":"claude-opus-5-5","effort":"low","message":{}}""",
            """{"type":"assistant","requestedModel":"claude-fable-5-2","effort":"high","message":{}}""",
            """{"type":"assistant","isSidechain":true,"requestedModel":"claude-haiku-5-5","effort":"low","message":{}}""",
            """{"type":"assistant","isApiErrorMessage":true,"message":{"model":"<synthetic>"}}""",
            """{"type":"user","message":{"content":"hi"}}"""
        )
        val last = com.clauditor.util.SessionSettings.fromLines(lines)
        assertEquals("claude-fable-5-2", last.model)
        assertEquals("high", last.effort)
        assertEquals(listOf("--effort", "high"), last.args())
    }

    @Test fun noRepliesNoArgs() = assertEquals(emptyList<String>(), com.clauditor.util.SessionSettings.fromLines(listOf("""{"type":"user"}""")).args())
}
