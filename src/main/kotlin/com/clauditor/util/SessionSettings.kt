package com.clauditor.util

import com.google.gson.JsonParser
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * The model and effort a session last ran on, from its transcript: every main-chain reply
 * records `requestedModel` and `effort`. A resume or fork passes them back as `--model` /
 * `--effort` (session-only flags), so a session comes back as it was, not on whatever default
 * another tab saved since.
 */
object SessionSettings {
    data class Last(val model: String?, val effort: String?) {
        fun args(): List<String> =
            (model?.let { listOf("--model", it) } ?: emptyList()) + (effort?.let { listOf("--effort", it) } ?: emptyList())
    }

    private const val TAIL_BYTES = 1L * 1024 * 1024
    private val EFFORTS = setOf("low", "medium", "high", "xhigh", "max")
    private val MODEL = Regex("[A-Za-z0-9._\\[\\]-]+")

    fun read(transcript: Path?): Last {
        if (transcript == null || !Files.isRegularFile(transcript)) return Last(null, null)
        val tail = try {
            RandomAccessFile(transcript.toFile(), "r").use { raf ->
                val start = (raf.length() - TAIL_BYTES).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((raf.length() - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8).let { if (start > 0) it.substringAfter('\n') else it }
            }
        } catch (_: Exception) { return Last(null, null) }
        return fromLines(tail.lines())
    }

    /** Newest main-chain, non-error assistant line wins; model and effort each from the newest that has it. */
    internal fun fromLines(lines: List<String>): Last {
        var model: String? = null
        var effort: String? = null
        for (line in lines.asReversed()) {
            if (model != null && effort != null) break
            if (!line.contains("\"type\":\"assistant\"")) continue
            val o = try { JsonParser.parseString(line).asJsonObject } catch (_: Exception) { continue }
            if (o.get("type")?.asString != "assistant") continue
            if (o.get("isSidechain")?.asBoolean == true || o.get("isApiErrorMessage")?.asBoolean == true) continue
            if (model == null) {
                model = (o.get("requestedModel")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: o.getAsJsonObject("message")?.get("model")?.takeIf { it.isJsonPrimitive }?.asString)
                    ?.takeIf { it.matches(MODEL) && it != "<synthetic>" }
            }
            if (effort == null) effort = o.get("effort")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase()?.takeIf { it in EFFORTS }
        }
        return Last(model, effort)
    }

    /** The transcript of [sessionId]: [preferred] if it exists, else wherever ~/.claude/projects holds it. */
    fun locate(sessionId: String, preferred: Path?): Path? {
        if (preferred != null && Files.isRegularFile(preferred)) return preferred
        val root = Path.of(System.getProperty("user.home"), ".claude", "projects")
        return try {
            Files.list(root).use { dirs -> dirs.map { it.resolve("$sessionId.jsonl") }.filter { Files.isRegularFile(it) }.findFirst().orElse(null) }
        } catch (_: Exception) { null }
    }
}
