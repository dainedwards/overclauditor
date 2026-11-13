package com.clauditor.services

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real wrapper script through bash in every mode a user can be in. */
class StatusLineWrapperTest {
    private val sgrReset = "\u001b[0m"

    private fun run(original: String?, hide: String?): Pair<String, String?> {
        val script = Files.createTempFile("wrapper", ".sh").also { Files.writeString(it, ClaudeStatusService.wrapperScriptText()) }
        val status = Files.createTempFile("status", ".json").also { Files.delete(it) }
        // On Windows, Claude Code runs commands in Git Bash; a bare "bash" from Java can be WSL's.
        val gitBash = java.io.File("C:\\Program Files\\Git\\bin\\bash.exe")
        val bash = if (com.clauditor.util.ProcessHelper.IS_WINDOWS && gitBash.isFile) gitBash.path else "bash"
        val pb = ProcessBuilder(bash, script.toString().replace('\\', '/'))
        pb.environment().apply {
            put("CLAUDITOR_STATUS_FILE", status.toString())
            if (original != null) put("CLAUDITOR_ORIGINAL_STATUSLINE", original) else remove("CLAUDITOR_ORIGINAL_STATUSLINE")
            if (hide != null) put("CLAUDITOR_HIDE_STATUSLINE", hide) else remove("CLAUDITOR_HIDE_STATUSLINE")
        }
        val p = pb.start()
        p.outputStream.use { it.write("""{"model":{"id":"m"}}""".toByteArray()) }
        val out = p.inputStream.bufferedReader().readText().replace("\r\n", "\n")
        val err = p.errorStream.bufferedReader().readText()
        assertTrue(p.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, p.exitValue(), "wrapper must always exit 0; stderr: $err")
        return out to (if (Files.exists(status)) Files.readString(status).trim() else null)
    }

    @Test
    fun `status json is always captured`() {
        assertEquals("""{"model":{"id":"m"}}""", run(null, null).second)
        assertEquals("""{"model":{"id":"m"}}""", run("echo hi", "1").second)
    }

    @Test
    fun `the user's status line shows by default`() {
        assertEquals("line from user\n", run("echo line from user", null).first)
        assertEquals("line from user\n", run("echo line from user", "0").first)
    }

    @Test
    fun `hidden prints only an SGR reset, but the user's command still runs`() {
        val marker = Files.createTempFile("ran", ".txt").also { Files.delete(it) }
        val (out, _) = run("cat > '$marker'; echo visible", "1")
        assertEquals(sgrReset, out)
        assertTrue(Files.exists(marker), "user's command must still run for its side effects")
    }

    @Test
    fun `no user status line prints only an SGR reset, so no blank row`() {
        assertEquals(sgrReset, run(null, null).first)
        assertEquals(sgrReset, run("", "0").first)
    }

    @Test
    fun `a failing user command doesn't fail the wrapper`() {
        assertEquals("", run("exit 3", "0").first)
    }

    @Test
    fun `the user's command is shell-parsed (pipes, quotes, tilde)`() {
        assertEquals("A B\n", run("echo 'a b' | tr a-z A-Z", null).first)
    }
}
