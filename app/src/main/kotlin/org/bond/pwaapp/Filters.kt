package org.bond.pwaapp

class NetworkRule(
    val pattern: String,
    val allow: Boolean,
    val thirdParty: Boolean?,
    val domains: List<String>?,
    val notDomains: List<String>?,
    val important: Boolean,
    val text: String,
)

class Verdict(val blocked: Boolean, val rule: String, val source: String)

class Filters(val source: String) {
    private val simpleBlock = HashSet<String>()
    private val hostBlock = HashMap<String, MutableList<NetworkRule>>()
    private val hostAllow = HashMap<String, MutableList<NetworkRule>>()
    private val tokenBlock = HashMap<String, MutableList<NetworkRule>>()
    private val tokenAllow = HashMap<String, MutableList<NetworkRule>>()
    private val looseBlock = mutableListOf<NetworkRule>()
    private val looseAllow = mutableListOf<NetworkRule>()
    private val genericHide = LinkedHashSet<String>()
    private val domainHide = HashMap<String, MutableList<String>>()
    private val domainUnhide = HashMap<String, MutableSet<String>>()
    private val noCosmetic = HashSet<String>()
    private val noGeneric = HashSet<String>()
    private var genericCss: String? = null

    var networkCount = 0
        private set
    var cosmeticCount = 0
        private set

    fun addAll(lines: Sequence<String>) {
        for (line in lines) add(line)
    }

    fun add(raw: String) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return
        if (cosmetic(line)) return
        network(line)
    }

    private fun cosmetic(line: String): Boolean {
        val unhideAt = line.indexOf("#@#")
        val hideAt = line.indexOf("##")
        if (unhideAt < 0 && hideAt < 0) {
            return line.contains("#?#") || line.contains("#$#") || line.contains("#%#") || line.contains("#@?#") || line.contains("#@$#") || line.contains("$$")
        }
        val unhide = unhideAt >= 0 && (hideAt < 0 || unhideAt < hideAt)
        val at = if (unhide) unhideAt else hideAt
        val domainsPart = line.substring(0, at)
        val selector = line.substring(at + if (unhide) 3 else 2).trim()
        if (selector.isEmpty() || selector.startsWith("+js") || selector.startsWith("^") || UNSUPPORTED_SELECTOR.any { selector.contains(it) }) return true
        val domains = domainsPart.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (domains.any { '*' in it || '/' in it }) return true
        val wanted = domains.filter { !it.startsWith("~") }
        if (domains.isNotEmpty() && wanted.isEmpty()) return true
        if (unhide) {
            if (wanted.isEmpty()) return true
            for (domain in wanted) domainUnhide.getOrPut(domain) { HashSet() }.add(selector)
            return true
        }
        cosmeticCount++
        if (wanted.isEmpty()) {
            genericHide += selector
            genericCss = null
        } else {
            for (domain in wanted) domainHide.getOrPut(domain) { mutableListOf() }.add(selector)
        }
        return true
    }

    private fun network(line: String): Boolean {
        var allow = false
        var body = line
        if (body.startsWith("@@")) {
            allow = true
            body = body.substring(2)
        }
        if (body.startsWith("/") && body.length > 2 && body.lastIndexOf('/') > 0 && body.substringBeforeLast('$').endsWith("/") && body.substringBeforeLast('$').length > 1) return false
        var options = ""
        val dollar = body.lastIndexOf('$')
        if (dollar >= 0 && !body.substring(dollar + 1).contains('/')) {
            options = body.substring(dollar + 1)
            body = body.substring(0, dollar)
        }
        var thirdParty: Boolean? = null
        var domains: MutableList<String>? = null
        var notDomains: MutableList<String>? = null
        var important = false
        var types = 0
        var documentish = 0
        var cosmeticException: String? = null
        for (option in options.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }) {
            val name = option.substringBefore('=')
            when {
                name == "third-party" || name == "3p" || name == "~first-party" || name == "~1p" -> thirdParty = true
                name == "~third-party" || name == "~3p" || name == "first-party" || name == "1p" -> thirdParty = false
                name == "domain" || name == "from" -> {
                    for (domain in option.substringAfter('=').split('|').map { it.trim() }.filter { it.isNotEmpty() }) {
                        if ('*' in domain) return false
                        if (domain.startsWith("~")) {
                            (notDomains ?: mutableListOf<String>().also { notDomains = it }).add(domain.substring(1))
                        } else {
                            (domains ?: mutableListOf<String>().also { domains = it }).add(domain)
                        }
                    }
                }
                name == "important" -> important = true
                name == "match-case" || name == "redirect" || name == "all" || name.startsWith("~") -> Unit
                name == "elemhide" || name == "ehide" -> cosmeticException = "all"
                name == "generichide" || name == "ghide" -> cosmeticException = cosmeticException ?: "generic"
                name == "document" || name == "doc" -> {
                    types++
                    documentish++
                    if (allow) cosmeticException = "all"
                }
                name == "popup" || name == "popunder" -> {
                    types++
                    documentish++
                }
                name in TYPES -> types++
                else -> return false
            }
        }
        if (cosmeticException != null) {
            if (allow) {
                val host = plainHost(body)
                if (host != null) {
                    if (cosmeticException == "all") noCosmetic += host else noGeneric += host
                    genericCss = null
                }
            }
            if (types == documentish) return true
        }
        if (types > 0 && types == documentish) return false
        val pattern = body.lowercase()
        if (pattern.isEmpty() || pattern == "*" || pattern == "|" || pattern == "||") return false
        val rule = NetworkRule(pattern, allow, thirdParty, domains, notDomains, important, line)
        networkCount++
        val host = plainHost(pattern)
        if (host != null) {
            if (!allow && thirdParty == null && domains == null && notDomains == null && !important) {
                simpleBlock += host
            } else {
                (if (allow) hostAllow else hostBlock).getOrPut(host) { mutableListOf() }.add(rule)
            }
            return true
        }
        val token = bestToken(pattern)
        if (token == null) {
            (if (allow) looseAllow else looseBlock).add(rule)
        } else {
            (if (allow) tokenAllow else tokenBlock).getOrPut(token) { mutableListOf() }.add(rule)
        }
        return true
    }

    fun match(url: String, host: String, pageHost: String): Verdict? {
        val address = url.lowercase()
        val third = baseDomain(host) != baseDomain(pageHost)
        val tokens = tokens(address)
        val allowed = find(address, host, pageHost, third, hostAllow, tokenAllow, looseAllow, tokens, null)
        val important = find(address, host, pageHost, third, hostBlock, tokenBlock, looseBlock, tokens, true)
        if (important != null) return Verdict(true, important.text, source)
        if (allowed != null) return Verdict(false, allowed.text, source)
        if (simpleBlock.isNotEmpty()) {
            var at = host
            while (true) {
                if (at in simpleBlock) return Verdict(true, "||$at^", source)
                val dot = at.indexOf('.')
                if (dot < 0) break
                at = at.substring(dot + 1)
                if ('.' !in at) break
            }
        }
        val blocked = find(address, host, pageHost, third, hostBlock, tokenBlock, looseBlock, tokens, false)
        return blocked?.let { Verdict(true, it.text, source) }
    }

    fun hostListed(host: String): Boolean {
        var at = host.lowercase()
        while ('.' in at) {
            if (at in simpleBlock) return true
            if (hostBlock[at]?.any { it.domains == null && it.thirdParty != false } == true) return true
            at = at.substringAfter('.')
        }
        return false
    }

    private fun find(
        url: String,
        host: String,
        pageHost: String,
        third: Boolean,
        byHost: Map<String, List<NetworkRule>>,
        byToken: Map<String, List<NetworkRule>>,
        loose: List<NetworkRule>,
        tokens: List<String>,
        importantOnly: Boolean?,
    ): NetworkRule? {
        fun fits(rule: NetworkRule): Boolean {
            if (importantOnly == true && !rule.important) return false
            if (rule.thirdParty != null && rule.thirdParty != third) return false
            if (rule.domains != null && rule.domains.none { within(pageHost, it) }) return false
            if (rule.notDomains != null && rule.notDomains.any { within(pageHost, it) }) return false
            return true
        }
        if (byHost.isNotEmpty()) {
            var at = host
            while (true) {
                byHost[at]?.firstOrNull { fits(it) }?.let { return it }
                val dot = at.indexOf('.')
                if (dot < 0) break
                at = at.substring(dot + 1)
            }
        }
        if (byToken.isNotEmpty()) {
            for (token in tokens) {
                byToken[token]?.firstOrNull { fits(it) && matches(it.pattern, url, host) }?.let { return it }
            }
        }
        return loose.firstOrNull { fits(it) && matches(it.pattern, url, host) }
    }

    fun css(host: String, generic: Boolean): String {
        val name = host.lowercase()
        if (chain(name).any { it in noCosmetic }) return ""
        val unhide = chain(name).flatMap { domainUnhide[it].orEmpty() }.toSet()
        val out = StringBuilder()
        val useGeneric = generic && chain(name).none { it in noGeneric }
        if (useGeneric) {
            if (unhide.isEmpty()) {
                out.append(genericCss ?: buildGeneric().also { genericCss = it })
            } else {
                for (selector in genericHide) if (selector !in unhide) out.append(selector).append("{display:none!important}\n")
            }
        }
        for (domain in chain(name)) {
            for (selector in domainHide[domain].orEmpty()) {
                if (selector !in unhide) out.append(selector).append("{display:none!important}\n")
            }
        }
        return out.toString()
    }

    private fun buildGeneric(): String {
        val out = StringBuilder()
        for (selector in genericHide) out.append(selector).append("{display:none!important}\n")
        return out.toString()
    }

    companion object {
        private val TYPES = setOf(
            "script", "image", "stylesheet", "css", "object", "xmlhttprequest", "xhr", "subdocument", "frame", "ping", "media",
            "font", "other", "websocket", "webrtc", "beacon", "object-subrequest", "inline-script", "inline-font",
        )
        private val UNSUPPORTED_SELECTOR = listOf(
            ":style(", ":remove(", ":-abp-", ":has-text(", ":xpath(", ":matches-", ":upward(", ":watch-attr(", ":min-text-length(",
            ":others(", ":if(", ":if-not(", ":nth-ancestor(", ":contains(", ":properties(", ":remove-attr(", ":remove-class(",
        )
        private val COMMON = setOf("http", "https", "www", "com", "net", "org", "js", "html", "php")
        private val HOST_CHARS = Regex("^[a-z0-9.-]+$")

        fun within(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")

        fun chain(host: String): List<String> {
            val out = mutableListOf<String>()
            var at = host
            while (at.isNotEmpty()) {
                out += at
                val dot = at.indexOf('.')
                if (dot < 0) break
                at = at.substring(dot + 1)
            }
            return out
        }

        fun plainHost(pattern: String): String? {
            if (!pattern.startsWith("||")) return null
            var rest = pattern.substring(2).lowercase()
            if (rest.endsWith("^|")) rest = rest.dropLast(2)
            else if (rest.endsWith("^")) rest = rest.dropLast(1)
            if (rest.isEmpty() || !HOST_CHARS.matches(rest) || '.' !in rest) return null
            return rest.trimEnd('.')
        }

        private fun isTokenChar(char: Char): Boolean = char in 'a'..'z' || char in '0'..'9' || char == '%'

        fun tokens(url: String): List<String> {
            val out = ArrayList<String>(24)
            var start = -1
            for (index in url.indices) {
                if (isTokenChar(url[index])) {
                    if (start < 0) start = index
                } else if (start >= 0) {
                    if (index - start >= 2) out += url.substring(start, index)
                    start = -1
                }
            }
            if (start >= 0 && url.length - start >= 2) out += url.substring(start)
            return out
        }

        fun bestToken(pattern: String): String? {
            val anchoredStart = pattern.startsWith("|")
            val anchoredEnd = pattern.endsWith("|")
            var best: String? = null
            var index = 0
            while (index < pattern.length) {
                if (!isTokenChar(pattern[index])) {
                    index++
                    continue
                }
                val start = index
                while (index < pattern.length && isTokenChar(pattern[index])) index++
                val before = if (start == 0) null else pattern[start - 1]
                val after = if (index >= pattern.length) null else pattern[index]
                val boundedBefore = if (before == null) anchoredStart else before != '*'
                val boundedAfter = if (after == null) anchoredEnd else after != '*'
                val token = pattern.substring(start, index)
                if (boundedBefore && boundedAfter && token.length >= 2 && token !in COMMON && (best == null || token.length > best.length)) best = token
            }
            return best
        }

        private fun separator(char: Char): Boolean = !(char.isLetterOrDigit() || char == '_' || char == '-' || char == '.' || char == '%')

        fun matches(pattern: String, url: String, host: String): Boolean {
            if (pattern.startsWith("||")) {
                val rest = pattern.substring(2)
                val end = rest.endsWith("|")
                val body = if (end) rest.dropLast(1) else rest
                val hostAt = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
                if (glob(body, url, hostAt, end)) return true
                var index = hostAt
                val hostEnd = hostAt + host.length
                while (index < hostEnd && index < url.length) {
                    if (url[index] == '.' && glob(body, url, index + 1, end)) return true
                    index++
                }
                return false
            }
            if (pattern.startsWith("|")) {
                val rest = pattern.substring(1)
                val end = rest.endsWith("|")
                return glob(if (end) rest.dropLast(1) else rest, url, 0, end)
            }
            val end = pattern.endsWith("|")
            val body = if (end) pattern.dropLast(1) else pattern
            return glob("*$body", url, 0, end)
        }

        fun glob(pattern: String, text: String, start: Int, end: Boolean): Boolean {
            var p = 0
            var s = start
            var starP = -1
            var starS = 0
            while (true) {
                if (p == pattern.length) {
                    if (!end || s == text.length) return true
                } else {
                    val char = pattern[p]
                    if (char == '*') {
                        starP = p
                        starS = s
                        p++
                        continue
                    }
                    if (s < text.length && (char == text[s] || (char == '^' && separator(text[s])))) {
                        p++
                        s++
                        continue
                    }
                    if (char == '^' && s == text.length) {
                        p++
                        continue
                    }
                }
                if (starP < 0 || starS >= text.length) return false
                starS++
                s = starS
                p = starP + 1
            }
        }
    }
}
