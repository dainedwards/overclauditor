package com.clauditor.util

import com.google.gson.JsonParser
import java.io.RandomAccessFile
import java.nio.file.Path

/**
 * The built-in "copy" button: what to copy out of Claude's latest reply. The last fenced code
 * block (where Claude puts prompts, commands and snippets), else the whole reply. Lines made only
 * of quote marks (''''') that some users fence copyable text with are stripped from the edges.
 */
object LastReply {
    private const val TAIL_BYTES = 4L * 1024 * 1024
    private val FENCE = Regex("(?m)^[ \t]*(```+|~~~+)[^\\n]*\\n(.*?)\\n[ \t]*\\1[ \t]*$", RegexOption.DOT_MATCHES_ALL)
    private val QUOTE_RULE = Regex("^\\s*'{3,}\\s*$")
    /** Local-command records (/model, /clear output) aren't prompts: they don't start a new reply. */
    private val WRAPPER = Regex("^\\s*</?(command-|local-command|task-notification|bash-|system-reminder)")

    /** The text the copy button should put on the clipboard, or null if there's no reply yet. */
    fun copyText(transcript: Path): String? = latestReplyText(transcript)?.let(::pick)

    /** Last fenced block of [reply] (quote-rule lines trimmed off its edges), else [reply] itself. */
    fun pick(reply: String): String {
        val block = FENCE.findAll(reply).lastOrNull()?.groupValues?.get(2) ?: return reply.trim()
        val lines = block.lines().toMutableList()
        while (lines.isNotEmpty() && (lines.first().isBlank() || QUOTE_RULE.matches(lines.first()))) lines.removeAt(0)
        while (lines.isNotEmpty() && (lines.last().isBlank() || QUOTE_RULE.matches(lines.last()))) lines.removeAt(lines.lastIndex)
        return lines.joinToString("\n")
    }

    /** All assistant text after the last real user prompt (one reply may span many lines). */
    fun latestReplyText(transcript: Path): String? {
        val tail = try {
            RandomAccessFile(transcript.toFile(), "r").use { raf ->
                val start = (raf.length() - TAIL_BYTES).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((raf.length() - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8).let { if (start > 0) it.substringAfter('\n') else it }
            }
        } catch (_: Exception) { return null }
        val parts = ArrayList<String>()
        for (line in tail.lineSequence()) {
            if (line.isBlank()) continue
            val o = try { JsonParser.parseString(line).asJsonObject } catch (_: Exception) { continue }
            val type = o.get("type")?.takeIf { it.isJsonPrimitive }?.asString
            val content = o.getAsJsonObject("message")?.get("content")
            when (type) {
                "user" -> {
                    // A typed prompt starts a new reply; tool results (arrays without text) don't.
                    val wrapper = content?.isJsonPrimitive == true && WRAPPER.containsMatchIn(content.asString)
                    val isPrompt = !wrapper && o.get("isMeta")?.asBoolean != true && (content?.isJsonPrimitive == true ||
                        content?.isJsonArray == true && content.asJsonArray.any { it.isJsonObject && it.asJsonObject.get("type")?.asString == "text" })
                    if (isPrompt) parts.clear()
                }
                "assistant" -> if (content?.isJsonArray == true) {
                    for (b in content.asJsonArray) {
                        if (b.isJsonObject && b.asJsonObject.get("type")?.asString == "text") {
                            b.asJsonObject.get("text")?.asString?.let { parts += it }
                        }
                    }
                }
            }
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }
}
