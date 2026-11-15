package org.bond.pwaapp

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class BlockingActivity : Screen() {
    private var site: Site? = null
    private val defaults by lazy { Defaults.get(this) }
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        site = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        guarded = site
        Blocker.ensure(this)
    }

    private fun saveSite() {
        site?.let { SiteStore.save(this, it) }
        redraw()
    }

    private fun saveDefaults() {
        Defaults.save(this, defaults)
        redraw()
    }

    override fun render() {
        val current = site?.let { SiteStore.get(this, it.id) }
        site = current
        if (current != null) {
            header("Blocking", "${current.label} · ${current.host}", site = current)
        } else {
            header("Blocking", "lists and rules for every app")
        }

        val level = current?.blocking ?: defaults.blocking
        content.put(sectionTitle(if (current == null) "LEVEL FOR NEW APPS" else "LEVEL", Glyphs.SHIELD))
        val levels = line(8)
        for ((key, detail) in listOf("off" to "nothing blocked", "standard" to "ads and trackers", "strict" to "+ hides leftovers")) {
            levels.put(levelCard(key, detail, key == level) {
                if (current != null) {
                    current.blocking = key
                    if (key != "off") current.lastLevel = key
                    saveSite()
                } else {
                    defaults.blocking = key
                    saveDefaults()
                }
            }, 0, WRAP_CONTENT, 1f)
        }
        content.put(levels)

        if (current != null) {
            val memory = MemoryStore.get(this, current.id)
            val page = Live.page(current.id)
            content.put(readouts(listOf(
                Triple("THIS PAGE", if (page != null) Live.blocked(current.id).toString() else "—", Glyphs.SHIELD),
                Triple("TODAY", if (current.keeps(Kinds.BLOCKED)) memory.blockedToday().toString() else "off", Glyphs.CLOCK),
                Triple("LISTS", Blocker.lastUpdate(this).let { if (it == 0L) "none" else shortAgo(it) }, Glyphs.LIST),
            )))
        }

        content.put(sectionTitle("LISTS", Glyphs.LIST, "shared by every app"))
        val lists = card()
        for (list in Blocker.lists(this)) {
            val have = Blocker.downloaded(this, list.key)
            val count = Blocker.ruleCount(list.key)
            val detail = list.detail + when {
                !have -> " · not downloaded yet"
                count > 0 -> " · " + String.format(java.util.Locale.US, "%,d rules", count)
                else -> ""
            }
            lists.switch(Glyphs.LIST, list.name, detail, list.key in defaults.lists) { on ->
                if (on) defaults.lists += list.key else defaults.lists -= list.key
                Defaults.save(this, defaults)
                if (on && !have) update(true) else Blocker.reload(this) { redraw() }
            }
        }
        lists.action(Glyphs.PLUS, "your list by address", "any list in the same format, like EasyList", "add") {
            promptText("List address", "https://", "https://example.com/list.txt", true) { typed ->
                val address = normalizeAddress(typed)
                if (hostOf(address).isEmpty()) return@promptText
                if (address !in defaults.customLists) defaults.customLists += address
                defaults.lists += Blocker.customKey(address)
                Defaults.save(this, defaults)
                update(true)
            }
        }
        for (address in defaults.customLists.toList()) {
            lists.action(Glyphs.LINK, hostOf(address), address, "remove") {
                defaults.customLists -= address
                defaults.lists -= Blocker.customKey(address)
                Blocker.forget(this, Blocker.customKey(address))
                saveDefaults()
            }
        }
        lists.switch(Glyphs.WIFI, "update lists on Wi-Fi only", "every 4 days, when you open Leyden", defaults.wifiOnly) {
            defaults.wifiOnly = it
            Defaults.save(this, defaults)
        }
        val last = Blocker.lastUpdate(this)
        lists.action(Glyphs.RELOAD, "update now", if (updating || Blocker.updating) "updating…" else if (last == 0L) "never updated" else "updated " + ago(last), "update", enabled = !updating && !Blocker.updating) { update(true) }
        content.put(lists.view)

        if (current != null) {
            val hits = Live.hits(current.id)
            if (hits.isNotEmpty()) {
                content.put(sectionTitle("ON THIS PAGE", Glyphs.GLOBE, hostOf(Live.page(current.id))))
                val list = card()
                val grouped = hits.groupBy { it.host }.entries.sortedWith(compareByDescending<Map.Entry<String, List<Hit>>> { entry -> entry.value.any { it.blocked } }.thenBy { it.key }).take(30)
                for ((host, group) in grouped) {
                    val blocked = group.any { it.blocked }
                    val first = group.first { it.blocked == blocked }
                    val detail = plural(group.size, "request") + " · " + if (blocked) first.source else if (first.source.isNotEmpty()) "allowed by " + first.source else "allowed"
                    list.action(if (blocked) Glyphs.SHIELD else Glyphs.CHECK, host, detail, if (blocked) "allow" else "block", primary = !blocked) {
                        current.rules.removeAll { it == "||$host^" || it == "@@||$host^" }
                        current.rules += if (blocked) "@@||$host^" else "||$host^"
                        saveSite()
                        toast(if (blocked) "Allowed $host in ${current.label}. Reload to see it." else "Blocked $host in ${current.label}. Reload to see it.")
                    }
                }
                content.put(list.view)
            }
        }

        val rules = current?.rules ?: defaults.rules
        content.put(sectionTitle(if (current != null) "YOUR RULES FOR ${current.label.uppercase()}" else "YOUR RULES FOR EVERY APP", Glyphs.PEN))
        val ruleCard = card()
        if (rules.isEmpty()) ruleCard.custom(note("None yet. Rules use the EasyList format: ||ads.example.com^ blocks a server, @@||cdn.example.com^ always allows one, ##.promo-bar hides matching parts of pages.").apply { setPadding(0, dp(12), 0, dp(12)) })
        for (rule in rules.toList()) {
            val meaning = when {
                rule.startsWith("@@") -> "always allow"
                rule.contains("##") -> "hide"
                else -> "block"
            }
            ruleCard.action(null, rule, meaning, "remove") {
                rules.remove(rule)
                if (current != null) saveSite() else saveDefaults()
            }
        }
        content.put(ruleCard.view)
        val tools = line(8)
        tools.put(secondaryButton("Add rule", Glyphs.PLUS) {
            promptText("New rule", "", "||ads.example.com^", true) { typed ->
                val rule = typed.trim()
                if (rule.isEmpty()) return@promptText
                val check = Filters("check")
                check.add(rule)
                if (check.networkCount + check.cosmeticCount == 0 && !rule.contains("#@#")) {
                    toast("That rule isn’t supported")
                    return@promptText
                }
                if (rule !in rules) rules += rule
                if (current != null) saveSite() else saveDefaults()
            }
        }, 0, dp(46), 1f)
        if (current != null && rules.isNotEmpty()) {
            tools.put(secondaryButton("Copy to other apps", Glyphs.COPY) { copyRules(current) }, 0, dp(46), 1f)
        }
        content.put(tools)
        if (current != null) content.put(note("Rules for every app are in Settings › blocking. Hidden elements you picked live under insertions."))
    }

    private fun levelCard(name: String, detail: String, picked: Boolean, onClick: () -> Unit): View {
        val box = column(3)
        box.gravity = Gravity.CENTER_HORIZONTAL
        if (picked) {
            val r = dpf(14f)
            val layers = android.graphics.drawable.LayerDrawable(arrayOf(rounded(T.accentEdge, r), rounded(0x47FFFFFF, r), rounded(T.accent, r)))
            layers.setLayerInset(1, 0, 0, 0, dp(2))
            layers.setLayerInset(2, 0, dp(1), 0, dp(2))
            box.background = ripple(layers)
        } else {
            box.background = secondaryBack()
        }
        box.setPadding(dp(6), dp(12), dp(6), dp(14))
        box.isClickable = true
        box.contentDescription = "$name, $detail" + if (picked) ", chosen" else ""
        box.setOnClickListener { onClick() }
        val title = text(name, 14f, if (picked) T.onAccent else T.ink, Fonts.sansBold)
        title.gravity = Gravity.CENTER
        box.put(title)
        val words = text(detail, 11f, if (picked) T.onAccent else T.muted)
        words.gravity = Gravity.CENTER
        box.put(words)
        return box
    }

    private fun update(force: Boolean) {
        updating = true
        redraw()
        Blocker.update(this, force) { problem ->
            updating = false
            if (problem != null) toast(problem) else toast("Lists updated")
            if (!isDestroyed) redraw()
        }
    }

    private fun copyRules(from: Site) {
        val others = SiteStore.all(this).filter { it.id != from.id }
        if (others.isEmpty()) {
            toast("There are no other apps yet")
            return
        }
        pickMany("Copy ${from.label}’s rules to…", others.map { it.label }) { picked ->
            for (index in picked) {
                val target = others[index]
                for (rule in from.rules) if (rule !in target.rules) target.rules += rule
                SiteStore.save(this, target)
            }
            if (picked.isNotEmpty()) toast("Copied to ${plural(picked.size, "app")}")
        }
    }
}
