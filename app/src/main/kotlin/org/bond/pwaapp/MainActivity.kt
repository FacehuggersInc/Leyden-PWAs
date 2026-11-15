package org.bond.pwaapp

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Screen() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Blocker.ensure(this)
        Proxy.apply(this)
        Share.publish(this)
        if (savedInstanceState == null) Brand.splash(this)
        if (!Defaults.get(this).firstRunDone) startActivity(Intent(this, FirstRunActivity::class.java))
    }

    private fun running(): Set<String> {
        val manager = getSystemService(ActivityManager::class.java) ?: return emptySet()
        return manager.appTasks.mapNotNull { task ->
            val data = try {
                task.taskInfo?.baseIntent?.data
            } catch (error: IllegalArgumentException) {
                null
            }
            if (data?.scheme == "pwaapp") data.lastPathSegment else null
        }.toSet()
    }

    private var query = ""
    private var listArea: LinearLayout? = null
    private var stat: TextView? = null

    override fun render() {
        Look.refresh(this)
        root.setBackgroundColor(T.bg)
        val sites = SiteStore.all(this)
        val live = running()
        val head = line(10)
        head.put(Brand.wordmark(this, 20f), 0, WRAP_CONTENT, 1f)
        head.put(squareKey(Glyphs.SETTINGS, "Settings for all apps") { startActivity(Intent(this, GlobalActivity::class.java)) }, dp(44), dp(44))
        content.put(head)
        if (sites.isNotEmpty()) {
            val search = field(query, "Search your apps, or type an address", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI)
            search.imeOptions = EditorInfo.IME_ACTION_GO
            search.setOnEditorActionListener { view, _, _ ->
                val typed = view.text.toString().trim()
                val matches = matching(SiteStore.all(this), typed)
                if (matches.size == 1) Shortcuts.open(this, matches[0]) else if (looksLikeAddress(typed)) addSite(typed)
                true
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty().trim()
                    paintList(SiteStore.all(this@MainActivity), live)
                }
            })
            content.put(search, MATCH_PARENT, dp(46))
            val line = mono("", 11f, T.muted, 0f)
            line.setPadding(dp(2), 0, dp(2), 0)
            stat = line
            content.put(line)
            paintStat(sites, live, null)
            measureAll(sites) { total -> paintStat(sites, live, total) }
        }
        val area = column(12)
        listArea = area
        content.put(area)
        paintList(sites, live)
        bottom.put(primaryButton("Add a site", Glyphs.PLUS) { startActivity(Intent(this, AddActivity::class.java)) }, MATCH_PARENT, dp(52))
        showBottom()
    }

    private fun paintStat(sites: List<Site>, live: Set<String>, storage: Long?) {
        val view = stat ?: return
        val memories = sites.associate { it.id to MemoryStore.get(this, it.id) }
        val unread = sites.filter { it.keeps(Kinds.UNREAD) && !it.locks }.sumOf { memories[it.id]?.unread?.coerceAtLeast(0) ?: 0 }
        val blocked = sites.filter { it.keeps(Kinds.BLOCKED) && !it.locks }.sumOf { memories[it.id]?.blockedToday() ?: 0 }
        val runningCount = live.count { id -> sites.any { it.id == id } }
        val parts = mutableListOf(plural(sites.size, "app"))
        val runningText = "$runningCount running"
        parts += runningText
        if (unread > 0) parts += "$unread unread"
        if (sites.any { it.keeps(Kinds.BLOCKED) }) parts += "$blocked blocked today"
        if (storage != null) parts += bytes(storage)
        val joined = parts.joinToString(" · ")
        val styled = android.text.SpannableString(joined)
        if (runningCount > 0) {
            val start = joined.indexOf(runningText)
            styled.setSpan(android.text.style.ForegroundColorSpan(T.accent), start, start + runningText.length, 0)
        }
        view.text = styled
    }

    private fun measureAll(sites: List<Site>, done: (Long) -> Unit) {
        var left = sites.size
        var total = 0L
        for (site in sites) {
            Storage.measure(site) { measured ->
                total += measured.storage
                left--
                if (left == 0) done(total)
            }
        }
    }

    private fun looksLikeAddress(typed: String): Boolean =
        typed.startsWith("http") || (!typed.contains(' ') && (typed.contains('.') || typed.contains(':')))

    private fun addSite(typed: String) {
        startActivity(Intent(this, AddActivity::class.java).putExtra(AddActivity.ADDRESS, typed))
    }

    private fun matching(sites: List<Site>, typed: String): List<Site> {
        val low = typed.lowercase()
        if (low.isEmpty()) return emptyList()
        return sites.filter { it.label.lowercase().contains(low) || (!it.locks && (it.host.contains(low) || it.url.lowercase().contains(low))) }
    }

    private fun paintList(sites: List<Site>, live: Set<String>) {
        val area = listArea ?: return
        area.removeAllViews()
        if (sites.isEmpty()) {
            area.put(empty("Add a site to turn it into an app with its own icon and window. You can also share a page to Leyden from your browser."))
            return
        }
        if (query.isNotEmpty()) {
            results(area, sites, live)
            return
        }
        val memories = sites.associate { it.id to MemoryStore.get(this, it.id) }
        val now = sites.filter { it.id in live }
        if (now.isNotEmpty()) {
            area.put(sectionTitle("RUNNING · ${now.size}", Glyphs.PLAY))
            if (now.size <= 2) {
                val row = line(10)
                now.forEach { row.put(runCard(it, memories[it.id] ?: Memory()), 0, WRAP_CONTENT, 1f) }
                area.put(row)
            } else {
                val scroller = android.widget.HorizontalScrollView(this)
                scroller.isHorizontalScrollBarEnabled = false
                val row = line(10)
                now.forEach { row.put(runCard(it, memories[it.id] ?: Memory()), dp(160), WRAP_CONTENT) }
                scroller.addView(row)
                area.put(scroller)
            }
        }
        val defaults = Defaults.get(this)
        val groups = defaults.groups
        val tab = defaults.homeTab.takeIf { id -> id == "all" || groups.any { it.id == id } } ?: "all"
        area.put(tabs(groups, tab, sites, live))
        val ordered = sites.sortedBy { it.label.lowercase() }
        if (tab == "all") {
            for (group in groups) section(area, group.name.uppercase(), ordered.filter { it.group == group.id }, memories, live, group)
            val loose = ordered.filter { site -> groups.none { it.id == site.group } }
            if (loose.isNotEmpty() || groups.isEmpty()) section(area, if (groups.isEmpty()) "ALL APPS" else "NOT IN A GROUP", loose, memories, live, null)
        } else {
            val group = groups.first { it.id == tab }
            section(area, group.name.uppercase(), ordered.filter { it.group == group.id }, memories, live, group)
        }
    }

    private fun tabs(groups: List<Group>, chosen: String, sites: List<Site>, live: Set<String>): View {
        val scroller = android.widget.HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        val row = line(6)
        row.setPadding(0, dp(6), 0, 0)
        fun chip(label: String, picked: Boolean, onClick: () -> Unit, onHold: (() -> Unit)? = null) {
            val view = text(label, 12f, if (picked) T.onAccent else T.ink, if (picked) Fonts.sansMedium else Fonts.sans)
            view.gravity = Gravity.CENTER
            view.setPadding(dp(12), 0, dp(12), dp(1))
            view.background = if (picked) rounded(T.accent, dpf(10f)) else rounded(T.key, dpf(10f))
            view.isClickable = true
            view.contentDescription = label + if (picked) ", chosen" else ""
            view.setOnClickListener { onClick() }
            if (onHold != null) view.setOnLongClickListener {
                onHold()
                true
            }
            row.put(view, WRAP_CONTENT, dp(32))
        }
        fun pick(id: String) {
            val defaults = Defaults.get(this)
            defaults.homeTab = id
            Defaults.save(this, defaults)
            paintList(sites, live)
        }
        chip("All", chosen == "all", { pick("all") })
        for (group in groups) chip(group.name, chosen == group.id, { pick(group.id) }, { groupSheet(group) })
        chip("+", false, { newGroup(null) })
        scroller.addView(row)
        return scroller
    }

    private fun section(area: LinearLayout, title: String, list: List<Site>, memories: Map<String, Memory>, live: Set<String>, group: Group?) {
        val head = line(8)
        head.setPadding(dp(4), dp(8), dp(4), 0)
        head.put(mono("$title · ${list.size}", 10f), 0, WRAP_CONTENT, 1f)
        if (group != null) {
            val edit = text("edit", 12f, T.accent, Fonts.sansMedium)
            edit.setPadding(dp(8), dp(4), dp(4), dp(4))
            edit.isClickable = true
            edit.contentDescription = "Edit ${group.name}"
            edit.setOnClickListener { groupSheet(group) }
            head.put(edit, WRAP_CONTENT)
        }
        area.put(head)
        if (list.isEmpty()) {
            area.put(note(if (group != null) "Nothing here yet. Tap edit to add apps, or hold an app and pick a group." else "Every app is in a group."))
            return
        }
        for (chunk in list.chunked(4)) {
            val row = line()
            row.gravity = Gravity.TOP
            chunk.forEach { row.put(tile(it, memories[it.id] ?: Memory(), it.id in live), 0, WRAP_CONTENT, 1f) }
            repeat(4 - chunk.size) { row.put(spacer(), 0, 1, 1f) }
            area.put(row)
        }
    }

    private fun tile(site: Site, memory: Memory, running: Boolean): View {
        val box = column(6)
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(0, dp(6), 0, dp(6))
        box.background = ripple(null, 14f)
        box.contentDescription = site.label + (if (running) ", running" else "") + if (site.locks) ", locked" else ""
        box.setOnClickListener { Shortcuts.open(this, site) }
        box.setOnLongClickListener {
            more(site)
            true
        }
        val frame = FrameLayout(this)
        val holder = FrameLayout(this)
        if (running) {
            holder.background = rounded(0, dpf(20f), dp(2), T.accent)
            holder.setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        holder.addView(appIcon(site, 54, 16f), FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
        frame.addView(holder, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER))
        if (!site.locks && site.keeps(Kinds.UNREAD) && memory.unread > 0) {
            val count = badge(memory.unread.toString(), T.accent)
            frame.addView(count, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        if (site.locks) {
            val lock = FrameLayout(this)
            lock.background = rounded(T.surface, dpf(10f), dp(1), T.border)
            lock.addView(glyph(Glyphs.LOCK, T.ink, 11, 2f), FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER))
            frame.addView(lock, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.BOTTOM or Gravity.END))
        }
        box.put(frame, dp(66), dp(64))
        val name = text(site.label, 12f, T.body)
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        box.put(name, MATCH_PARENT, WRAP_CONTENT)
        return box
    }

    private fun runCard(site: Site, memory: Memory): View {
        val card = column()
        card.background = rounded(T.surface, dpf(16f), dp(2), T.accentSoft)
        card.clipToOutline = true
        card.isClickable = true
        card.contentDescription = "${site.label}, running"
        card.setOnClickListener { Shortcuts.open(this, site) }
        card.setOnLongClickListener {
            more(site)
            true
        }
        val picture = Snapshots.get(this, site)
        val top = FrameLayout(this)
        top.background = rounded(site.color and 0x00FFFFFF or 0x26000000, 0f)
        if (picture != null) {
            val image = android.widget.ImageView(this)
            image.setImageBitmap(picture)
            image.scaleType = android.widget.ImageView.ScaleType.MATRIX
            image.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                val shown = view as android.widget.ImageView
                if (shown.width == 0) return@addOnLayoutChangeListener
                val scale = shown.width.toFloat() / picture.width
                shown.imageMatrix = android.graphics.Matrix().apply { setScale(scale, scale) }
            }
            top.addView(image, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            top.addView(glyph(if (site.locks) Glyphs.LOCK else Glyphs.GLOBE, T.faint, 22, 2f), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
        card.put(top, MATCH_PARENT, dp(104))
        val row = line(8)
        row.setPadding(dp(10), dp(8), dp(10), dp(10))
        row.put(appIcon(site, 26, 8f), dp(26), dp(26))
        val words = column(1)
        words.put(text(site.label, 13f, T.ink, Fonts.sansMedium).oneLine())
        val detail = when {
            site.locks -> "locked"
            site.keeps(Kinds.TITLE) && memory.title.isNotEmpty() -> memory.title
            else -> site.host
        }
        words.put(text(detail, 11f, T.muted).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.put(words, 0, WRAP_CONTENT, 1f)
        if (!site.locks && site.keeps(Kinds.UNREAD) && memory.unread > 0) row.put(badge(memory.unread.toString(), T.accent), WRAP_CONTENT)
        card.put(row)
        return card
    }

    private fun marked(label: String, low: String): CharSequence {
        val at = label.lowercase().indexOf(low)
        if (at < 0) return label
        val styled = android.text.SpannableString(label)
        styled.setSpan(android.text.style.BackgroundColorSpan(T.accentSoft), at, at + low.length, 0)
        return styled
    }

    private fun results(area: LinearLayout, sites: List<Site>, live: Set<String>) {
        val low = query.lowercase()
        val apps = matching(sites, query)
        if (apps.isNotEmpty()) {
            area.put(sectionTitle("YOUR APPS", Glyphs.GRID, apps.size.toString()))
            val list = card()
            for (site in apps) {
                val row = line(12)
                row.setPadding(0, dp(8), 0, dp(8))
                row.background = ripple(null, 8f)
                row.setOnClickListener { Shortcuts.open(this, site) }
                row.setOnLongClickListener {
                    more(site)
                    true
                }
                row.put(appIcon(site, 40, 12f), dp(40), dp(40))
                val words = column(1)
                words.put(text(marked(site.label, low), 14f, T.ink, Fonts.sansMedium).oneLine())
                words.put(text((if (site.locks) "locked" else site.host) + if (site.id in live) " · running" else "", 12f, T.muted).oneLine())
                row.put(words, 0, WRAP_CONTENT, 1f)
                row.put(primaryButtonSmall("open") { Shortcuts.open(this, site) }, WRAP_CONTENT, dp(32))
                list.custom(row)
            }
            area.put(list.view)
        }
        area.put(sectionTitle("ADD", Glyphs.PLUS))
        val add = card()
        if (looksLikeAddress(query)) {
            add.link(Glyphs.PLUS, "Make $query an app", null, "check it, then name it and pick its privacy") { addSite(query) }
        } else {
            add.link(Glyphs.PLUS, "Add a site", null, "type its address, like $query.com") { startActivity(Intent(this, AddActivity::class.java)) }
        }
        area.put(add.view)
        val pages = mutableListOf<Pair<Site, Visit>>()
        for (site in sites) {
            if (site.locks || !site.keeps(Kinds.RECENT)) continue
            for (visit in MemoryStore.get(this, site.id).recent) {
                if (visit.title.lowercase().contains(low) || visit.url.lowercase().contains(low)) pages += site to visit
            }
        }
        if (pages.isNotEmpty()) {
            area.put(sectionTitle("IN YOUR APPS’ RECENT PAGES", Glyphs.HISTORY))
            val list = card()
            for ((site, visit) in pages.sortedByDescending { it.second.at }.take(8)) {
                val row = line(12)
                row.setPadding(0, dp(8), 0, dp(8))
                row.background = ripple(null, 8f)
                row.setOnClickListener { Shortcuts.open(this, site, visit.url) }
                row.put(appIcon(site, 30, 9f), dp(30), dp(30))
                val words = column(1)
                words.put(text(marked(visit.title.ifEmpty { pathOf(visit.url) }, low), 13f, T.ink).oneLine())
                words.put(text("${site.label} · ${ago(visit.at)}", 11f, T.muted).oneLine())
                row.put(words, 0, WRAP_CONTENT, 1f)
                list.custom(row)
            }
            area.put(list.view)
        }
    }

    private fun primaryButtonSmall(label: String, onClick: () -> Unit): View {
        val view = text(label, 12f, T.onAccent, Fonts.sansMedium)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(12), 0, dp(12), dp(1))
        view.background = primaryBack(9f)
        view.isClickable = true
        view.setOnClickListener { onClick() }
        return view
    }

    private fun newGroup(site: Site?) {
        promptText("New group", "", "Self-hosted") { typed ->
            val name = typed.trim()
            if (name.isEmpty()) return@promptText
            val defaults = Defaults.get(this)
            val group = Group(name = name)
            defaults.groups += group
            if (site == null) defaults.homeTab = group.id
            Defaults.save(this, defaults)
            if (site != null) {
                site.group = group.id
                SiteStore.save(this, site)
            }
            redraw()
        }
    }

    private fun groupSheet(group: Group) {
        val sheet = Sheet(this)
        sheet.body.put(text(group.name, 18f, T.ink, Fonts.sansBold))
        val actions = card()
        actions.link(Glyphs.PEN, "rename", group.name) {
            sheet.dismiss()
            promptText("Group name", group.name, "Self-hosted") { typed ->
                if (typed.isNotBlank()) {
                    val defaults = Defaults.get(this)
                    defaults.groups.firstOrNull { it.id == group.id }?.name = typed.trim()
                    Defaults.save(this, defaults)
                }
                redraw()
            }
        }
        val defaults = Defaults.get(this)
        val at = defaults.groups.indexOfFirst { it.id == group.id }
        if (at > 0) actions.link(Glyphs.CHEVRON_LEFT, "move earlier", null) {
            defaults.groups.add(at - 1, defaults.groups.removeAt(at))
            Defaults.save(this, defaults)
            sheet.dismiss()
            redraw()
        }
        if (at in 0 until defaults.groups.size - 1) actions.link(Glyphs.CHEVRON, "move later", null) {
            defaults.groups.add(at + 1, defaults.groups.removeAt(at))
            Defaults.save(this, defaults)
            sheet.dismiss()
            redraw()
        }
        sheet.body.put(actions.view)
        sheet.body.put(sectionTitle("APPS IN IT"))
        val apps = card()
        for (site in SiteStore.all(this).sortedBy { it.label.lowercase() }) {
            val other = defaults.groups.firstOrNull { it.id == site.group && it.id != group.id }?.name
            apps.switch(null, site.label, other?.let { "now in $it" }, site.group == group.id) { on ->
                site.group = if (on) group.id else ""
                SiteStore.save(this, site)
            }
        }
        sheet.body.put(apps.view)
        sheet.body.put(secondaryButton("Delete this group", Glyphs.TRASH, T.danger) {
            sheet.dismiss()
            val fresh = Defaults.get(this)
            fresh.groups.removeAll { it.id == group.id }
            if (fresh.homeTab == group.id) fresh.homeTab = "all"
            Defaults.save(this, fresh)
            for (site in SiteStore.all(this).filter { it.group == group.id }) {
                site.group = ""
                SiteStore.save(this, site)
            }
            redraw()
        }, MATCH_PARENT, dp(46))
        sheet.setOnDismissListener { redraw() }
        sheet.show()
    }

    private fun groupPicker(site: Site) {
        val sheet = Sheet(this)
        sheet.body.put(text("Group for ${site.label}", 18f, T.ink, Fonts.sansBold))
        val list = card()
        list.link(null, "no group", if (site.group.isEmpty()) "chosen" else null) {
            sheet.dismiss()
            site.group = ""
            SiteStore.save(this, site)
            redraw()
        }
        for (group in Defaults.get(this).groups) {
            list.link(Glyphs.GRID, group.name, if (site.group == group.id) "chosen" else null) {
                sheet.dismiss()
                site.group = group.id
                SiteStore.save(this, site)
                redraw()
            }
        }
        list.link(Glyphs.PLUS, "new group", null) {
            sheet.dismiss()
            newGroup(site)
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun more(site: Site) {
        val sheet = Sheet(this)
        val top = line(12)
        top.put(appIcon(site, 44, 14f), dp(44), dp(44))
        val words = column(1)
        words.put(text(site.label, 17f, T.ink, Fonts.sansBold).oneLine())
        words.put(text(if (site.locks) "locked" else site.host, 12f, T.muted).oneLine())
        top.put(words, 0, WRAP_CONTENT, 1f)
        sheet.body.put(top)
        val list = card()
        list.link(Glyphs.PLAY, "Open") {
            sheet.dismiss()
            Shortcuts.open(this, site)
        }
        list.link(Glyphs.GRID, "Group", Defaults.get(this).groups.firstOrNull { it.id == site.group }?.name ?: "none") {
            sheet.dismiss()
            groupPicker(site)
        }
        list.link(Glyphs.SETTINGS, "All settings", if (site.locks) "unlock" else null) {
            sheet.dismiss()
            Gate.open(this, site) { startActivity(Intent(this, AppSettingsActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id)) }
        }
        list.link(Glyphs.EYE, "What ${site.label} remembers", if (site.locks) "unlock" else null) {
            sheet.dismiss()
            Gate.open(this, site) { startActivity(Intent(this, MemoryActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id)) }
        }
        if (Shortcuts.canPin(this) && !Shortcuts.pinned(this, site.id)) {
            list.link(Glyphs.COPY, "Make a second copy", if (site.locks) "unlock" else null) {
                sheet.dismiss()
                Gate.open(this, site) { startActivity(Intent(this, AppSettingsActivity::class.java).putExtra(AppSettingsActivity.SITE, site.id).putExtra(AppSettingsActivity.COPY, true)) }
            }
            list.link(Glyphs.HOME, "Add to home screen") {
                sheet.dismiss()
                if (!Shortcuts.pin(this, site)) toast("This launcher can’t add icons to the home screen")
            }
        }
        if (site.locks) {
            list.link(Glyphs.SHIELD, "Remove it, keep its settings", "unlock", "erases its lock, scripts, keys and stored data; keeps its settings") {
                sheet.dismiss()
                Gate.open(this, site) {
                    removeSite(this, site, true)
                    toast("${site.label} removed. Its settings are kept in Settings › kept settings.")
                    redraw()
                }
            }
            list.link(Glyphs.TRASH, "Remove the app and its lock", null, "for when you can’t unlock it; no unlock needed", T.danger) {
                sheet.dismiss()
                ask("Remove ${site.label} and its lock?", "Everything goes together: the app, its lock and PIN, its settings, what Leyden kept, and what the site stored. It can’t be undone. Add the site again for a fresh start.", "Remove", danger = true) {
                    removeSite(this, site)
                    redraw()
                }
            }
        } else {
            list.link(Glyphs.TRASH, "Remove", color = T.danger) {
                sheet.dismiss()
                confirmRemove(site)
            }
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun confirmRemove(site: Site) {
        removeSheet(site) { redraw() }
    }

    companion object {
        fun removeSite(activity: android.app.Activity, site: Site, keepSettings: Boolean = false) {
            if (keepSettings) Kept.save(activity, site)
            Storage.clearAll(activity, site, null) {
                Storage.forget(site)
            }
            Shortcuts.retire(activity, site.id)
            MemoryStore.drop(activity, site.id)
            SiteStore.remove(activity, site.id)
            Storage.markDirty(activity, site, false)
            LockState.clear(activity, site.id)
            Snapshots.drop(activity, site.id)
            AddonState.drop(activity, site.id)
            Files.release(activity, site.power.downloadFolder)
            Files.release(activity, site.power.shotFolder)
            val defaults = Defaults.get(activity)
            if (!keepSettings && defaults.rewrites.removeAll { it.site == site.id }) Defaults.save(activity, defaults)
            Share.publish(activity)
        }
    }
}

fun LinearLayout.centered(): LinearLayout {
    gravity = Gravity.CENTER
    return this
}
