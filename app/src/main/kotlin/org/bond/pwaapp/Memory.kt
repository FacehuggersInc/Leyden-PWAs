package org.bond.pwaapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Visit(val title: String, val url: String, val at: Long) {
    fun toJson(): JSONObject = JSONObject().put("title", title).put("url", url).put("at", at)
}

class Memory {
    val recent = mutableListOf<Visit>()
    var title = ""
    var unread = -1
    val blocked = linkedMapOf<String, Int>()
    val opens = mutableListOf<Long>()
    var timeSpent = 0L
    val shortcuts = linkedMapOf<String, Int>()

    val lastOpened: Long
        get() = opens.lastOrNull() ?: 0L

    fun blockedToday(): Int = blocked[MemoryStore.today()] ?: 0

    fun toJson(): JSONObject = JSONObject()
        .put("recent", JSONArray(recent.map { it.toJson() }))
        .put("title", title)
        .put("unread", unread)
        .put("blocked", JSONObject(blocked as Map<*, *>))
        .put("opens", JSONArray(opens))
        .put("timeSpent", timeSpent)
        .put("shortcuts", JSONObject(shortcuts as Map<*, *>))

    companion object {
        fun fromJson(json: JSONObject): Memory {
            val memory = Memory()
            json.optJSONArray("recent")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    memory.recent += Visit(item.optString("title"), item.optString("url"), item.optLong("at"))
                }
            }
            memory.title = json.optString("title")
            memory.unread = json.optInt("unread", -1)
            json.optJSONObject("blocked")?.let { days -> days.keys().forEach { memory.blocked[it] = days.optInt(it) } }
            json.optJSONArray("opens")?.let { array -> for (index in 0 until array.length()) memory.opens += array.optLong(index) }
            memory.timeSpent = json.optLong("timeSpent")
            json.optJSONObject("shortcuts")?.let { used -> used.keys().forEach { memory.shortcuts[it] = used.optInt(it) } }
            return memory
        }
    }
}

object MemoryStore {
    private const val RECENT_LIMIT = 12
    private const val OPENS_LIMIT = 200
    private const val DAYS_LIMIT = 30
    private val cache = HashMap<String, Memory>()
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = synchronized(day) { day.format(Date()) }

    private fun file(context: Context, id: String): File {
        val folder = File(context.filesDir, "memory")
        folder.mkdirs()
        return File(folder, "$id.json")
    }

    @Synchronized
    fun get(context: Context, id: String): Memory {
        cache[id]?.let { return it }
        val file = file(context, id)
        val memory = if (file.isFile) try {
            Memory.fromJson(JSONObject(file.readText()))
        } catch (error: JSONException) {
            Memory()
        } else Memory()
        cache[id] = memory
        return memory
    }

    @Synchronized
    fun save(context: Context, id: String) {
        val memory = cache[id] ?: return
        val file = file(context, id)
        val empty = memory.recent.isEmpty() && memory.title.isEmpty() && memory.unread < 0 && memory.blocked.isEmpty() && memory.opens.isEmpty() && memory.timeSpent == 0L && memory.shortcuts.isEmpty()
        if (empty) {
            file.delete()
            return
        }
        file.writeText(memory.toJson().toString())
    }

    fun size(context: Context, id: String): Long = file(context, id).takeIf { it.isFile }?.length() ?: 0L

    @Synchronized
    fun page(context: Context, site: Site, title: String?, url: String?) {
        val memory = get(context, site.id)
        val name = title?.trim().orEmpty()
        val address = url.orEmpty()
        if (site.keeps(Kinds.RECENT) && address.startsWith("http") && site.inScope(address)) {
            memory.recent.removeAll { it.url == address }
            memory.recent.add(0, Visit(name.ifEmpty { pathOf(address) }, address, System.currentTimeMillis()))
            while (memory.recent.size > RECENT_LIMIT) memory.recent.removeAt(memory.recent.size - 1)
        }
        if (site.keeps(Kinds.TITLE) && name.isNotEmpty() && site.inScope(address)) memory.title = name
        if (site.keeps(Kinds.UNREAD) && site.inScope(address)) memory.unread = unreadFrom(name) ?: 0
    }

    @Synchronized
    fun blocked(context: Context, site: Site, count: Int) {
        if (count <= 0 || !site.keeps(Kinds.BLOCKED)) return
        val memory = get(context, site.id)
        val key = today()
        memory.blocked[key] = (memory.blocked[key] ?: 0) + count
        while (memory.blocked.size > DAYS_LIMIT) memory.blocked.remove(memory.blocked.keys.first())
    }

    @Synchronized
    fun opened(context: Context, site: Site) {
        if (!site.keeps(Kinds.OPENS)) return
        val memory = get(context, site.id)
        memory.opens += System.currentTimeMillis()
        while (memory.opens.size > OPENS_LIMIT) memory.opens.removeAt(0)
    }

    @Synchronized
    fun spent(context: Context, site: Site, millis: Long) {
        if (millis <= 0 || !site.keeps(Kinds.OPENS)) return
        get(context, site.id).timeSpent += millis
    }

    @Synchronized
    fun shortcut(context: Context, site: Site, name: String) {
        if (!site.keeps(Kinds.SHORTCUTS)) return
        val used = get(context, site.id).shortcuts
        used[name] = (used[name] ?: 0) + 1
    }

    @Synchronized
    fun forget(context: Context, id: String, kind: String) {
        if (kind == Kinds.TITLE) Snapshots.drop(context, id)
        val memory = get(context, id)
        when (kind) {
            Kinds.RECENT -> memory.recent.clear()
            Kinds.TITLE -> memory.title = ""
            Kinds.UNREAD -> memory.unread = -1
            Kinds.BLOCKED -> memory.blocked.clear()
            Kinds.OPENS -> {
                memory.opens.clear()
                memory.timeSpent = 0L
            }
            Kinds.SHORTCUTS -> memory.shortcuts.clear()
        }
        save(context, id)
    }

    @Synchronized
    fun forgetAll(context: Context, id: String) {
        Snapshots.drop(context, id)
        cache[id] = Memory()
        save(context, id)
    }

    @Synchronized
    fun drop(context: Context, id: String) {
        cache.remove(id)
        file(context, id).delete()
    }

    fun describe(memory: Memory, kind: String): String = when (kind) {
        Kinds.RECENT -> if (memory.recent.isEmpty()) "nothing yet" else plural(memory.recent.size, "page") + ", newest: " + memory.recent.first().title
        Kinds.TITLE -> if (memory.title.isEmpty()) "nothing yet" else "“${memory.title}”"
        Kinds.UNREAD -> if (memory.unread < 0) "nothing yet" else "${memory.unread}, read from the title"
        Kinds.BLOCKED -> if (memory.blocked.isEmpty()) "nothing yet" else "${memory.blocked.values.sum()} over ${plural(memory.blocked.size, "day")}"
        Kinds.OPENS -> if (memory.opens.isEmpty()) "nothing yet" else "${plural(memory.opens.size, "time")}, ${duration(memory.timeSpent)} in all"
        else -> if (memory.shortcuts.isEmpty()) "nothing yet" else memory.shortcuts.entries.joinToString(", ") { "${it.key} ${it.value}" }
    }
}
