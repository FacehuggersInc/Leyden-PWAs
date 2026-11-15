package org.bond.pwaapp

class Hit(val url: String, val host: String, val blocked: Boolean, val source: String, val rule: String)

object Live {
    private val hits = HashMap<String, MutableList<Hit>>()
    private val pages = HashMap<String, String>()

    @Synchronized
    fun start(siteId: String, url: String) {
        hits[siteId] = mutableListOf()
        pages[siteId] = url
    }

    @Synchronized
    fun add(siteId: String, hit: Hit) {
        val list = hits.getOrPut(siteId) { mutableListOf() }
        if (list.size < 400) list += hit
    }

    @Synchronized
    fun hits(siteId: String): List<Hit> = hits[siteId]?.toList().orEmpty()

    @Synchronized
    fun page(siteId: String): String? = pages[siteId]

    @Synchronized
    fun blocked(siteId: String): Int = hits[siteId]?.count { it.blocked } ?: 0

    @Synchronized
    fun stop(siteId: String) {
        hits.remove(siteId)
        pages.remove(siteId)
    }
}
