package com.clauditor.plugin

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Claude Code plugin shipped in the jar and loaded with --plugin-dir. */
class BundledCommandsTest {
    private fun raw(rel: String) = javaClass.getResourceAsStream("/claude-plugin/$rel")?.use { String(it.readBytes()) }
    private fun res(rel: String) = raw(rel)

    @Test
    fun `bundled files use LF line endings (CRLF breaks the bash script and frontmatter)`() {
        for (f in listOf("commands/merge.md", "commands/push.md", "commands/pull.md", "scripts/git-merge-lock.sh", ".claude-plugin/plugin.json")) {
            assertFalse(raw(f)!!.contains('\r'), "$f has CRLF line endings; .gitattributes should force LF")
        }
    }

    @Test
    fun `manifest names the plugin overclauditor, so commands are slash-overclauditor-colon-x`() {
        val m = assertNotNull(res(".claude-plugin/plugin.json"))
        assertEquals("overclauditor", JsonParser.parseString(m).asJsonObject.get("name").asString)
    }

    @Test
    fun `commands exist, have frontmatter, and never point into the user's claude folder`() {
        for (c in listOf("merge", "push", "pull")) {
            val text = assertNotNull(res("commands/$c.md"), "missing $c.md")
            assertTrue(text.startsWith("---\ndescription:"), "$c.md frontmatter")
            assertFalse(text.contains("~/.claude"), "$c.md must use \${CLAUDE_PLUGIN_ROOT}, not ~/.claude")
            assertFalse(Regex("`/(merge|push|pull)`").containsMatchIn(text), "$c.md names an unnamespaced command")
        }
        assertTrue(res("commands/merge.md")!!.contains("\${CLAUDE_PLUGIN_ROOT}/scripts/git-merge-lock.sh"))
        assertNotNull(res("scripts/git-merge-lock.sh"))
    }
}
