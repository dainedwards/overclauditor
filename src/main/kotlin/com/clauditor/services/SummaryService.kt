package com.clauditor.services

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.intellij.openapi.diagnostic.Logger
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The built-in session summary (summary.source "builtin"): two lines, what the session is
 * about and what to check for the latest request, from a background `claude -p --model haiku`
 * run. Regenerated only when the recent prompts change, at most one run per session at a time.
 * Written to the IDE system folder (OcPaths)/summary/<sessionId>.summary. Runs with
 * --no-session-persistence so no transcript lands in ~/.claude/projects.
 */
object SummaryService {
    private val log = Logger.getInstance(SummaryService::class.java)
    private val dir: Path by lazy { com.clauditor.util.OcPaths.dataDir.resolve("summary") }
    /** The summarizer's cwd: a folder of ours, never a real project. */
    private val agentCwd: Path by lazy { dir.resolve("agent") }
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Transcript size at the last check: nothing new appended means nothing to do. */
    private val checkedSize = ConcurrentHashMap<String, Long>()

    fun summaryFile(sessionId: String): Path = dir.resolve("$sessionId.summary")

    /** Start a regeneration if this session's recent prompts changed since the last summary. */
    fun ensure(sessionId: String, transcript: Path) {
        if (sessionId in running) return
        val size = try { Files.size(transcript) } catch (_: Exception) { return }
        if (checkedSize.put(sessionId, size) == size) return
        val prompts = recentPrompts(transcript)
        if (prompts.isEmpty()) return
        val input = "RECENT MESSAGES (oldest to newest):\n" + prompts.joinToString("\n") { it.take(200) } +
            "\n\nMOST RECENT REQUEST:\n" + prompts.last().take(400)
        val hash = sha(FORMAT + input)
        val hashFile = dir.resolve("$sessionId.hash")
        if (runCatching { Files.readString(hashFile) }.getOrNull() == hash) return
        if (!running.add(sessionId)) return
        com.clauditor.util.ClauditorExecutor.submit {
            try {
                Files.createDirectories(agentCwd)  // also creates dir
                val claude = com.clauditor.settings.ClauditorSettings.getInstance().resolveClaudeBinary()
                val proc = ProcessBuilder(claude, "-p", "--no-session-persistence", "--model", "haiku", "--settings", "{\"disableAllHooks\":true}", PROMPT + input)
                    .directory(agentCwd.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .also { it.environment()["CLAUDE_CODE_DISABLE_AUTO_MEMORY"] = "1" }  // no memory folder under ~/.claude/projects
                    .start()
                val out = proc.inputStream.bufferedReader().readText()
                if (!proc.waitFor(90, TimeUnit.SECONDS)) { proc.destroyForcibly(); return@submit }
                // A failed run (logged out, rate limited) prints its error on stdout: never save that.
                if (proc.exitValue() != 0) return@submit
                val lines = out.lines().map { it.trim().replace("\\", "") }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@submit
                Files.writeString(summaryFile(sessionId), lines.take(2).joinToString("\n") + "\n")
                Files.writeString(hashFile, hash)
            } catch (e: Exception) {
                log.info("Overclauditor: summary failed for $sessionId: ${e.message}")
            } finally {
                running.remove(sessionId)
            }
        }
    }

    /** The last few real user prompts, from the transcript's tail (meta lines and command wrappers skipped). */
    internal fun recentPrompts(transcript: Path): List<String> {
        val tail = try {
            RandomAccessFile(transcript.toFile(), "r").use { raf ->
                val start = (raf.length() - TAIL_BYTES).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((raf.length() - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8).let { if (start > 0) it.substringAfter('\n') else it }
            }
        } catch (_: Exception) { return emptyList() }
        val out = ArrayList<String>()
        for (line in tail.lineSequence()) {
            if (!line.contains("\"type\":\"user\"")) continue
            val text = userText(line) ?: continue
            if (WRAPPER.containsMatchIn(text)) continue
            out += text.replace(Regex("\\s+"), " ").trim()
        }
        return out.takeLast(RECENT)
    }

    private fun userText(line: String): String? = try {
        val r = JsonReader(StringReader(line))
        r.beginObject()
        var type: String? = null
        var meta = false
        var text: String? = null
        while (r.hasNext()) when (r.nextName()) {
            "type" -> type = if (r.peek() == JsonToken.STRING) r.nextString() else { r.skipValue(); null }
            "isMeta" -> meta = if (r.peek() == JsonToken.BOOLEAN) r.nextBoolean() else { r.skipValue(); false }
            "message" -> {
                val m = JsonParser.parseReader(r).asJsonObject
                val c = m.get("content")
                text = when {
                    c == null -> null
                    c.isJsonPrimitive -> c.asString
                    c.isJsonArray -> c.asJsonArray.filter { it.isJsonObject && it.asJsonObject.get("type")?.asString == "text" }
                        .joinToString(" ") { it.asJsonObject.get("text").asString }.ifEmpty { null }
                    else -> null
                }
            }
            else -> r.skipValue()
        }
        if (type == "user" && !meta) text?.trim()?.ifEmpty { null } else null
    } catch (_: Exception) { null }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private const val TAIL_BYTES = 4L * 1024 * 1024
    private const val RECENT = 6
    private const val FORMAT = "v1\n"
    private val WRAPPER = Regex("^</?(command-|local-command|task-notification|bash-|system-reminder)")
    private val PROMPT = """
        Below are the recent user messages from a coding-assistant session. Write a two-line status line.

        Line 1: what this session is about, 2 to 5 words, max 35 characters.
        Line 2: what to check when testing the MOST RECENT REQUEST, max 80 characters: the 2 or 3 most important checks as short noun phrases, comma-separated.

        Reply with exactly two lines and nothing else. No labels, no quotes, no preamble, no trailing period, no dashes. Drop filler words (the, a, that, should, make sure, verify). Example line 2: 'login keeps session, error on bad password, logout clears token'.

    """.trimIndent() + "\n"
}
