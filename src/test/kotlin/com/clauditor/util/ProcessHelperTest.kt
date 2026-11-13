package com.clauditor.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The plugin's own process path (PATH parsing, program extensions, stdin redirect) on the real
 * OS. Each check runs only when the tool is installed, so it is a no-op on bare CI images.
 */
class ProcessHelperTest {
    private fun onPath(name: String) = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator).any { dir ->
        listOf("", ".exe", ".cmd").any { java.io.File(dir, name + it).isFile }
    }

    @Test
    fun `git resolves and runs`() {
        if (!onPath("git")) return
        val git = ProcessHelper.which("git")
        assertTrue(git != null, "git is on PATH but which() missed it")
        val r = ProcessHelper.execWithTimeout(arrayOf("git", "--version"), 15_000)
        assertEquals(0, r.exitCode, r.output)
        assertTrue(r.output.startsWith("git version"), r.output)
    }

    @Test
    fun `claude resolves and runs (npm installs it as claude-dot-cmd on Windows)`() {
        if (!onPath("claude")) return
        val claude = ProcessHelper.which("claude")
        assertTrue(claude != null, "claude is on PATH but which() missed it")
        val r = ProcessHelper.execWithTimeout(arrayOf(claude!!, "--version"), 30_000)
        assertEquals(0, r.exitCode, r.output)
        assertTrue(r.output.contains("Claude Code"), r.output)
    }

    @Test
    fun `augmented PATH keeps the OS separator`() {
        val path = ProcessHelper.augmentedEnv()["PATH"].orEmpty()
        if (ProcessHelper.IS_WINDOWS) assertTrue(path.contains(";") && path.contains(":\\"), path.take(200))
        else assertTrue(!path.contains(";"), path.take(200))
    }
}
