package org.bond.pwaapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GitFile(val name: String, val path: String, val dir: Boolean, val size: Long)

class GitRepo(val kind: String, val api: String, val host: String, val owner: String, val name: String, var token: String) {
    val address: String
        get() = "$host/$owner/$name"
}

object Git {
    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun encPath(path: String): String = path.split('/').joinToString("/") { enc(it) }

    fun headers(repo: GitRepo): Map<String, String> {
        val out = mutableMapOf("Accept" to "application/json", "User-Agent" to "Leyden PWAs")
        if (repo.token.isNotEmpty()) {
            when (repo.kind) {
                "github" -> out["Authorization"] = "Bearer ${repo.token}"
                "gitlab" -> out["PRIVATE-TOKEN"] = repo.token
                else -> out["Authorization"] = "token ${repo.token}"
            }
        }
        return out
    }

    fun parse(input: String, token: String = ""): GitRepo {
        val text = normalizeAddress(input.trim()).removeSuffix("/").removeSuffix(".git")
        val uri = java.net.URI(text)
        val host = uri.host ?: throw IllegalArgumentException("That isn’t a repository address")
        val origin = "${uri.scheme}://${uri.authority}"
        val parts = uri.path.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) throw IllegalArgumentException("The address needs the owner and the repository, like host/owner/name")
        val owner = parts.dropLast(1).joinToString("/")
        val name = parts.last()
        if (host == "github.com") return GitRepo("github", "https://api.github.com/repos/$owner/$name", origin, owner, name, token)
        if (host == "gitlab.com") return GitRepo("gitlab", "$origin/api/v4/projects/${enc("$owner/$name")}", origin, owner, name, token)
        val probe = GitRepo("gitea", "$origin/api/v1/repos/$owner/$name", origin, owner, name, token)
        try {
            Fetcher.text("$origin/api/v1/version", headers(probe), 20_000)
            return probe
        } catch (error: Exception) {
            Unit
        }
        val lab = GitRepo("gitlab", "$origin/api/v4/projects/${enc("$owner/$name")}", origin, owner, name, token)
        Fetcher.text("$origin/api/v4/version", headers(lab), 20_000)
        return lab
    }

    fun defaultBranch(repo: GitRepo): String = JSONObject(Fetcher.text(repo.api, headers(repo))).optString("default_branch", "main")

    fun branches(repo: GitRepo): List<String> {
        val url = if (repo.kind == "gitlab") "${repo.api}/repository/branches?per_page=100" else "${repo.api}/branches?per_page=100&limit=50"
        val array = JSONArray(Fetcher.text(url, headers(repo)))
        return (0 until array.length()).map { array.getJSONObject(it).optString("name") }
    }

    fun tags(repo: GitRepo): List<String> {
        val url = if (repo.kind == "gitlab") "${repo.api}/repository/tags?per_page=100" else "${repo.api}/tags?per_page=100&limit=50"
        val array = JSONArray(Fetcher.text(url, headers(repo)))
        return (0 until array.length()).map { array.getJSONObject(it).optString("name") }
    }

    fun list(repo: GitRepo, ref: String, path: String): List<GitFile> {
        val out = mutableListOf<GitFile>()
        if (repo.kind == "gitlab") {
            val array = JSONArray(Fetcher.text("${repo.api}/repository/tree?per_page=100&ref=${enc(ref)}&path=${enc(path)}", headers(repo)))
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                out += GitFile(item.optString("name"), item.optString("path"), item.optString("type") == "tree", -1)
            }
        } else {
            val array = JSONArray(Fetcher.text("${repo.api}/contents/${encPath(path)}?ref=${enc(ref)}", headers(repo)))
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                out += GitFile(item.optString("name"), item.optString("path"), item.optString("type") == "dir", item.optLong("size", -1))
            }
        }
        return out.sortedWith(compareBy<GitFile>({ !it.dir }, { it.name.lowercase() }))
    }

    fun raw(repo: GitRepo, ref: String, path: String): String = when (repo.kind) {
        "github" -> Fetcher.text("https://raw.githubusercontent.com/${repo.owner}/${repo.name}/${encPath(ref)}/${encPath(path)}", headers(repo).filterKeys { it != "Accept" })
        "gitlab" -> Fetcher.text("${repo.api}/repository/files/${enc(path)}/raw?ref=${enc(ref)}", headers(repo))
        else -> Fetcher.text("${repo.api}/raw/${encPath(path)}?ref=${enc(ref)}", headers(repo))
    }

    fun commit(repo: GitRepo, ref: String): String = when (repo.kind) {
        "gitlab" -> JSONObject(Fetcher.text("${repo.api}/repository/commits/${enc(ref)}", headers(repo))).optString("id")
        "github" -> JSONObject(Fetcher.text("${repo.api}/commits/${enc(ref)}", headers(repo))).optString("sha")
        else -> JSONObject(Fetcher.text("${repo.api}/git/commits/${enc(ref)}", headers(repo))).optString("sha").ifEmpty {
            JSONObject(Fetcher.text("${repo.api}/branches/${enc(ref)}", headers(repo))).optJSONObject("commit")?.optString("id").orEmpty()
        }
    }

    fun kindOf(name: String): String? = when {
        name.endsWith(".user.js") -> "userscript"
        name.endsWith(".user.css") -> "userstyle"
        name.endsWith(".js") -> "script"
        name.endsWith(".css") -> "style"
        else -> null
    }
}

object Updater {
    private const val DAY = 24L * 60 * 60 * 1000

    fun due(item: Insertion): Boolean {
        val source = item.source ?: return false
        if (source.detached || source.updates == "never") return false
        if (source.type == "git" && source.follow == "commit") return false
        val since = System.currentTimeMillis() - source.lastCheck
        return when (source.every) {
            "open" -> since > 10 * 60 * 1000
            "weekly" -> since > 7 * DAY
            else -> since > DAY
        }
    }

    fun checkAll(context: Context, siteId: String) {
        val site = SiteStore.get(context, siteId) ?: return
        val ids = site.insertions.filter { due(it) }.map { it.id }
        if (ids.isEmpty()) return
        Thread {
            for (id in ids) {
                try {
                    check(context, siteId, id, false)
                } catch (error: Exception) {
                    continue
                }
            }
        }.start()
    }

    fun latest(item: Insertion): Pair<String, String> {
        val source = item.source ?: return item.code to ""
        if (source.type != "git") {
            val header = Headers.read(item.code)
            val address = header?.updateUrl?.takeIf { it.startsWith("http") } ?: source.url
            return Fetcher.text(address) to ""
        }
        val repo = Git.parse(source.repo, source.token)
        return when (source.follow) {
            "commit" -> item.code to source.commit
            "tags" -> {
                val tag = Git.tags(repo).firstOrNull() ?: source.ref
                source.ref = tag
                Git.raw(repo, tag, source.path) to Git.commit(repo, tag)
            }
            else -> {
                val sha = Git.commit(repo, source.ref)
                if (sha.isNotEmpty() && sha == source.commit) item.code to sha else Git.raw(repo, sha.ifEmpty { source.ref }, source.path) to sha
            }
        }
    }

    fun check(context: Context, siteId: String, id: String, force: Boolean): Boolean {
        val site = SiteStore.get(context, siteId) ?: return false
        val item = site.insertions.firstOrNull { it.id == id } ?: return false
        if (!force && !due(item)) return false
        val source = item.source ?: return false
        val (code, commit) = latest(item)
        val fresh = SiteStore.get(context, siteId) ?: return false
        val target = fresh.insertions.firstOrNull { it.id == id } ?: return false
        val targetSource = target.source ?: return false
        targetSource.lastCheck = System.currentTimeMillis()
        targetSource.ref = source.ref
        val changed = code.isNotEmpty() && code != target.code
        if (changed) {
            if (commit.isNotEmpty()) targetSource.commit = commit
            val header = Headers.read(code)
            val moreGrants = header?.grants.orEmpty().any { it !in target.grants } || header?.connects.orEmpty().any { it !in target.connects }
            if (targetSource.updates == "update" && !moreGrants) {
                apply(target, code)
            } else {
                targetSource.pending = code
            }
        }
        SiteStore.save(context, fresh)
        return changed
    }

    fun apply(item: Insertion, code: String) {
        item.code = if (item.kind == "css" && Headers.read(code)?.kind == "css") Headers.fromUserCss(code).first else code
        item.source?.upstream = item.code
        item.source?.pending = ""
        Headers.read(code)?.let { header ->
            item.version = header.version
            item.grants = header.grants.toMutableList()
            item.connects = header.connects.toMutableList()
            item.matches = header.matches.toMutableList()
        }
    }

    fun accept(item: Insertion) {
        val pending = item.source?.pending ?: return
        if (pending.isNotEmpty()) apply(item, pending)
    }

    fun fromFile(code: String, name: String, kindHint: String?): Insertion {
        val header = Headers.read(code)
        val css = header?.kind == "css" || kindHint == "style" || kindHint == "userstyle" || (header == null && name.endsWith(".css"))
        val item = Insertion(name = header?.name?.ifEmpty { null } ?: name.substringAfterLast('/'), kind = if (css) "css" else "js")
        item.code = if (css && header != null) Headers.fromUserCss(code).first else code
        item.version = header?.version.orEmpty()
        item.grants = header?.grants.orEmpty().toMutableList()
        item.connects = header?.connects.orEmpty().toMutableList()
        item.matches = header?.matches.orEmpty().toMutableList()
        item.triggers = mutableListOf(
            Trigger(
                when (header?.runAt) {
                    "document-start" -> "before"
                    "document-end", "document-body" -> "ready"
                    else -> if (css) "before" else "loaded"
                }
            )
        )
        item.onFail = "note"
        return item
    }
}
