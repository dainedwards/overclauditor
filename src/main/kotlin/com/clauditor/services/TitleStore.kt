package com.clauditor.services

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The title each session's tab and list row show, pinned so it changes only on a few events
 * (see ClaudeSessionEditor.retitle): a new worktree, the first file write, an explicit
 * /rename, or a new title from the CLI itself. Kept in the IDE's system folder (OcPaths).
 */
object TitleStore {
    data class Entry(
        val title: String,
        /** Worktree the session was in when pinned ("" for the main checkout). */
        val worktree: String = "",
        /** The session has written or edited a file since the title was first pinned. */
        val workStarted: Boolean = false,
        /** The CLI's ai-title at pin time, so a NEW one from the CLI can be noticed. */
        val aiTitle: String? = null,
        /** The /rename title at pin time, likewise. */
        val renamed: String? = null
    )

    private val file: Path by lazy { com.clauditor.util.OcPaths.dataDir.resolve("titles.json") }
    private val gson = Gson()
    private val entries: MutableMap<String, Entry> by lazy {
        val m = ConcurrentHashMap<String, Entry>()
        try {
            val type = object : TypeToken<Map<String, Entry>>() {}.type
            Files.newBufferedReader(file).use { gson.fromJson<Map<String, Entry>>(it, type) }?.let { m.putAll(it) }
        } catch (_: Exception) {}
        m
    }

    fun get(sessionId: String): Entry? = entries[sessionId]

    @Synchronized
    fun put(sessionId: String, entry: Entry) {
        if (entries[sessionId] == entry) return
        entries[sessionId] = entry
        try {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("titles.json.tmp")
            Files.newBufferedWriter(tmp).use { gson.toJson(HashMap(entries), it) }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {}
    }
}
