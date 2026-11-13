package com.clauditor.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.AppExecutorUtil
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Plan usage limits (5h, weekly, per-model weekly) from the same endpoint `/usage` reads,
 * authenticated with the CLI's own OAuth token. Polls only while some editor is listening.
 */
@Service(Service.Level.APP)
class ClaudeUsageService : Disposable {
    data class Limit(val label: String, val percent: Double?, val resetsAt: Instant?)

    @Volatile var limits: List<Limit> = emptyList()
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var poll: ScheduledFuture<*>? = null
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Synchronized
    fun addListener(l: () -> Unit) {
        listeners.add(l)
        if (poll == null) {
            poll = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(::fetch, 0, POLL_SECONDS, TimeUnit.SECONDS)
        } else if (limits.isNotEmpty()) l()
    }

    @Synchronized
    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
        if (listeners.isEmpty()) { poll?.cancel(false); poll = null }
    }

    private fun fetch() {
        try {
            val token = readToken() ?: return
            val req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/api/oauth/usage"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer $token")
                .header("anthropic-beta", "oauth-2025-04-20")
                .GET().build()
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return
            limits = parse(JsonParser.parseString(resp.body()).asJsonObject)
            ApplicationManager.getApplication().invokeLater { listeners.forEach { it() } }
        } catch (_: Exception) {
        }
    }

    private fun parse(obj: JsonObject): List<Limit> {
        fun instant(s: String?) = s?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val out = mutableListOf<Limit>()
        val five = obj.get("five_hour")?.takeIf { it.isJsonObject }?.asJsonObject
        out += Limit("5h", five?.get("utilization")?.asDouble, instant(five?.get("resets_at")?.asString))
        val list = obj.get("limits")?.takeIf { it.isJsonArray }?.asJsonArray
        if (list != null) {
            for (e in list) {
                val l = e.asJsonObject
                val label = when (l.get("kind")?.asString) {
                    "weekly_all" -> "7d"
                    "weekly_scoped" -> l.getAsJsonObject("scope")?.getAsJsonObject("model")
                        ?.get("display_name")?.asString ?: continue
                    else -> continue
                }
                out += Limit(label, l.get("percent")?.asDouble, instant(l.get("resets_at")?.asString))
            }
        } else {
            val seven = obj.get("seven_day")?.takeIf { it.isJsonObject }?.asJsonObject
            out += Limit("7d", seven?.get("utilization")?.asDouble, instant(seven?.get("resets_at")?.asString))
        }
        return out
    }

    /** The CLI's OAuth token: macOS keychain, else ~/.claude/.credentials.json. Re-read each poll so CLI refreshes apply. */
    private fun readToken(): String? {
        val raw = try {
            val p = ProcessBuilder("security", "find-generic-password", "-s", "Claude Code-credentials", "-w")
                .redirectErrorStream(false).start()
            val text = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && text.isNotEmpty()) text else null
        } catch (_: Exception) { null } ?: try {
            java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("user.home"), ".claude", ".credentials.json"))
        } catch (_: Exception) { null } ?: return null
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.getAsJsonObject("claudeAiOauth").get("accessToken").asString
        }.getOrNull()
    }

    override fun dispose() { poll?.cancel(false) }

    companion object {
        private const val POLL_SECONDS = 120L
        fun getInstance(): ClaudeUsageService =
            ApplicationManager.getApplication().getService(ClaudeUsageService::class.java)
    }
}
