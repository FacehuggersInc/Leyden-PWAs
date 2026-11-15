package org.bond.pwaapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class SourceActivity : Screen() {
    private lateinit var site: Site
    private var mode = "web"
    private var step = "address"
    private var address = ""
    private var token = ""
    private var busy = ""
    private var problem = ""
    private var repo: GitRepo? = null
    private var branches = listOf<String>()
    private var tags = listOf<String>()
    private var ref = ""
    private var refKind = "branch"
    private var path = ""
    private var files = listOf<GitFile>()
    private var fileName = ""
    private var filePath = ""
    private var code = ""
    private var commit = ""
    private var follow = "branch"
    private var updates = "ask"
    private var every = "daily"

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = SiteStore.get(this, intent.getStringExtra(AppSettingsActivity.SITE))
        super.onCreate(savedInstanceState)
        if (found == null) {
            finish()
            return
        }
        site = found
        guarded = found
        mode = intent.getStringExtra(MODE) ?: "web"
    }

    override fun onBack(): Boolean {
        when (step) {
            "preview" -> step = if (mode == "git") "files" else "address"
            "files" -> if (path.isNotEmpty()) path = path.substringBeforeLast('/', "") else step = "address"
            else -> return false
        }
        if (step == "files" && mode == "git") listFiles() else redrawTop()
        return true
    }

    private fun work(message: String, job: () -> Unit, done: () -> Unit) {
        busy = message
        problem = ""
        redraw()
        Thread {
            val failure = try {
                job()
                null
            } catch (error: Exception) {
                error.message ?: error.javaClass.simpleName
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = ""
                if (failure != null) problem = failure else done()
                redrawTop()
            }
        }.start()
    }

    override fun render() {
        if (!this::site.isInitialized) return
        when (step) {
            "address" -> addressStep()
            "files" -> filesStep()
            else -> previewStep()
        }
        if (busy.isNotEmpty()) {
            val box = line(10)
            box.background = surface()
            box.setPadding(dp(14), dp(16), dp(14), dp(16))
            val spinner = android.widget.ProgressBar(this)
            spinner.indeterminateTintList = android.content.res.ColorStateList.valueOf(T.accent)
            box.put(spinner, dp(22), dp(22))
            box.put(text(busy, 13f, T.body), 0, WRAP_CONTENT, 1f)
            content.put(box)
        }
        if (problem.isNotEmpty()) content.put(callout(Glyphs.WARN, "That didn’t work", problem, T.danger))
    }

    private fun addressStep() {
        if (mode == "web") {
            header("From an address", "${site.label} · a script or style on the web", site = site)
            content.put(sectionTitle("FILE ADDRESS", Glyphs.LINK, ".user.js, .user.css, .js or .css"))
            val input = field(address, "https://greasyfork.org/scripts/…/code/name.user.js", true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            content.put(input)
            content.put(note("Nothing runs yet. You’ll read the whole file and see what it can do first."))
            bottom.put(quietButton("Cancel", Glyphs.CLOSE) { finish() }, WRAP_CONTENT, dp(50))
            bottom.put(spacer(), 0, 1, 1f)
            bottom.put(primaryButton("Read it", Glyphs.ARROW_RIGHT) {
                address = normalizeAddress(input.text.toString())
                fileName = address.substringAfterLast('/').substringBefore('?')
                work("Downloading…", { code = Fetcher.text(address) }) { step = "preview" }
            }, dp(170), dp(50))
            showBottom()
            return
        }
        header("From a repository", "${site.label} · a script or style from git", site = site)
        content.put(sectionTitle("REPOSITORY", Glyphs.LINK, "GitHub, GitLab, Gitea, Forgejo"))
        val input = field(address, "git.home.lan/me/tweaks", true, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        content.put(input)
        val access = card()
        access.action(Glyphs.LOCK, "private repository", if (token.isEmpty()) "an access token, read only, kept with this app" else "token added", if (token.isEmpty()) "add token" else "change") {
            promptText("Access token (read only)", token, "token", true) { token = it.trim() }
        }
        content.put(access.view)
        content.put(note("Only the file you pick is downloaded. The repository’s other files and history stay on the server."))
        bottom.put(quietButton("Cancel", Glyphs.CLOSE) { finish() }, WRAP_CONTENT, dp(50))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Open", Glyphs.ARROW_RIGHT) {
            address = input.text.toString().trim()
            work("Opening the repository…", {
                val made = Git.parse(address, token)
                ref = Git.defaultBranch(made)
                branches = try {
                    Git.branches(made)
                } catch (error: Exception) {
                    listOf(ref)
                }
                tags = try {
                    Git.tags(made)
                } catch (error: Exception) {
                    emptyList()
                }
                repo = made
                path = ""
                files = Git.list(made, ref, path)
            }) { step = "files" }
        }, dp(150), dp(50))
        showBottom()
    }

    private fun listFiles() {
        val current = repo ?: return
        work("Reading the folder…", { files = Git.list(current, ref, path) }) { step = "files" }
    }

    private fun filesStep() {
        val current = repo ?: return
        header("From a repository", current.address.removePrefix("https://").removePrefix("http://"), site = site)
        val version = card()
        version.choice(Glyphs.HISTORY, "version", "$refKind: $ref", listOf(Option("branch"), Option("tag"), Option("commit")), refKind) { kind ->
            when (kind) {
                "branch" -> pickRef("Branch", branches) {
                    refKind = "branch"
                    ref = it
                    follow = "branch"
                    listFiles()
                }
                "tag" -> if (tags.isEmpty()) toast("This repository has no tags") else pickRef("Tag", tags) {
                    refKind = "tag"
                    ref = it
                    follow = "tags"
                    listFiles()
                }
                else -> promptText("Commit", "", "3f9c2a1…", true) {
                    if (it.isNotBlank()) {
                        refKind = "commit"
                        ref = it.trim()
                        follow = "commit"
                        listFiles()
                    }
                }
            }
        }
        content.put(version.view)
        content.put(sectionTitle("FILES", Glyphs.FOLDER, "scripts and styles are marked"))
        val list = card()
        list.custom(mono("${current.name} / $path", 11f, T.muted, 0f).apply { setPadding(0, dp(10), 0, dp(6)) })
        if (path.isNotEmpty()) list.link(Glyphs.ARROW_LEFT, "up a folder") {
            path = path.substringBeforeLast('/', "")
            listFiles()
        }
        for (file in files) {
            val kind = Git.kindOf(file.name)
            val detail = when {
                file.dir -> "folder"
                kind == null -> "not a script or style"
                file.size >= 0 -> bytes(file.size) + " · " + kind
                else -> kind
            }
            val row = list.link(if (file.dir) Glyphs.FOLDER else if (kind?.contains("style") == true) Glyphs.CODE else Glyphs.SCRIPT, file.name, null, detail) {
                if (file.dir) {
                    path = file.path
                    listFiles()
                } else if (kind != null) {
                    fileName = file.name
                    filePath = file.path
                    work("Downloading ${file.name}…", {
                        code = Git.raw(current, ref, file.path)
                        commit = try {
                            Git.commit(current, ref)
                        } catch (error: Exception) {
                            ""
                        }
                    }) { step = "preview" }
                }
            }
            if (!file.dir && kind == null) row.alpha = 0.5f
        }
        content.put(list.view)
    }

    private fun pickRef(title: String, options: List<String>, onPick: (String) -> Unit) {
        val sheet = Sheet(this)
        sheet.body.put(text(title, 18f, T.ink, Fonts.sansBold))
        val list = card()
        for (option in options.take(60)) list.link(null, option, if (option == ref) "chosen" else null) {
            sheet.dismiss()
            onPick(option)
        }
        sheet.body.put(list.view)
        sheet.show()
    }

    private fun previewStep() {
        val kind = Git.kindOf(fileName) ?: if (Headers.read(code)?.kind == "css") "userstyle" else "script"
        header(fileName, (if (mode == "git") "${repo?.name} · $ref" + (if (commit.isNotEmpty()) " · " + commit.take(7) else "") else hostOf(address)) + " · read only", site = site)
        content.put(CodeView.summary(this, code, kind))
        content.put(CodeView.readOnly(this, code, false))
        content.put(note("Lines marked ● reach outside the page. Nothing here has run; it’s only text until you add it."))
        bottom.put(quietButton("Back", Glyphs.ARROW_LEFT) { onBack() }, WRAP_CONTENT, dp(50))
        bottom.put(secondaryButton("Edit a copy", Glyphs.PEN) { add(true) }, WRAP_CONTENT, dp(50))
        bottom.put(spacer(), 0, 1, 1f)
        bottom.put(primaryButton("Add", Glyphs.CHECK) { review(kind) }, dp(120), dp(50))
        showBottom()
    }

    private fun made(): Insertion {
        val item = Updater.fromFile(code, fileName, Git.kindOf(fileName))
        item.source = if (mode == "git") {
            Source("git", "", repo?.address.orEmpty(), ref, filePath, commit, follow, updates, every, System.currentTimeMillis(), item.code, "", false, token)
        } else {
            Source("web", address, updates = updates, every = every, lastCheck = System.currentTimeMillis(), upstream = item.code)
        }
        return item
    }

    private fun review(kind: String) {
        if (kind == "userstyle") {
            val (_, problem) = Headers.fromUserCss(code)
            if (problem != null) {
                toast(problem)
                return
            }
        }
        val item = made()
        val sheet = Sheet(this)
        sheet.body.put(text("Add “${item.name}”?", 18f, T.ink, Fonts.sansBold))
        sheet.body.put(text(item.kindName + (if (item.version.isNotEmpty()) " · v${item.version}" else ""), 13f, T.muted))
        val from = card()
        from.value(Glyphs.LINK, "from", item.source?.label.orEmpty(), if (mode == "git") filePath else address)
        if (mode == "git") {
            from.choice(Glyphs.HISTORY, "follow", null, listOf(Option("commit", "this commit"), Option("branch"), Option("tags", "new tags")), follow) {
                follow = it
                sheet.dismiss()
                review(kind)
            }
        }
        from.choice(Glyphs.EYE, "when it changes", "ask shows what changed first", listOf(Option("ask"), Option("update"), Option("never")), updates) {
            updates = it
            sheet.dismiss()
            review(kind)
        }
        from.choice(Glyphs.CLOCK, "check", null, listOf(Option("open", "on open"), Option("daily"), Option("weekly")), every) {
            every = it
            sheet.dismiss()
            review(kind)
        }
        sheet.body.put(from.view)
        if (item.matches.isNotEmpty()) {
            sheet.body.put(sectionTitle("IT ASKS TO RUN ON", Glyphs.GLOBE))
            val runs = card()
            for (match in item.matches.take(6)) {
                val host = hostOf(match.replace("*.", "x.").replace("*", "x"))
                val own = host.isEmpty() || sameSite(host.removePrefix("x."), site.host)
                runs.value(Glyphs.GLOBE, match, if (own) "this app" else "other site")
            }
            runs.custom(text("Leyden only runs it on ${site.label}’s own pages.", 11f, T.muted).apply { setPadding(0, dp(8), 0, dp(10)) })
            sheet.body.put(runs.view)
        }
        val will = Headers.disclosures(item)
        if (will.isNotEmpty()) {
            sheet.body.put(sectionTitle("IT WILL", Glyphs.WARN, "from its header"))
            val list = card()
            for ((name, detail, tag) in will) list.value(Glyphs.WARN, name, tag, detail.ifEmpty { null })
            sheet.body.put(list.view)
        }
        sheet.body.put(callout(Glyphs.WARN, "Only add ${if (item.kind == "css") "styles" else "scripts"} you trust",
            if (item.kind == "css") "A style can change or hide anything on ${site.label}’s pages." else "It can read and change everything on ${site.label}’s pages, including what you type.", T.accent))
        val bar = line(8)
        bar.put(quietButton("Cancel", Glyphs.CLOSE) { sheet.dismiss() }, WRAP_CONTENT, dp(48))
        bar.put(spacer(), 0, 1, 1f)
        bar.put(primaryButton("Add", Glyphs.CHECK) {
            sheet.dismiss()
            add(false)
        }, dp(140), dp(48))
        sheet.body.put(bar)
        sheet.show()
    }

    private fun add(copy: Boolean) {
        val item = made()
        if (copy) item.source?.detached = true
        site = SiteStore.get(this, site.id) ?: site
        site.insertions += item
        SiteStore.save(this, site)
        val next = if (copy) Intent(this, CodeActivity::class.java) else Intent(this, ItemActivity::class.java)
        startActivity(next.putExtra(AppSettingsActivity.SITE, site.id).putExtra(ItemActivity.ITEM, item.id))
        finish()
    }

    companion object {
        const val MODE = "mode"
    }
}

