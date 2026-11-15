package org.bond.pwaapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonsTest {
    private fun rule(from: String, to: String, vararg scopes: String) = Rewrite(from = from, to = to, scopes = scopes.toMutableSet())

    @Test
    fun rewritesKeepTheRest() {
        assertEquals("https://old.reddit.com/r/android/?sort=new", Rewrite.rewrite(rule("reddit.com", "old.reddit.com"), "https://www.reddit.com/r/android/?sort=new"))
        assertEquals("https://yt.home.lan/watch?v=abc", Rewrite.rewrite(rule("youtube.com/watch", "yt.home.lan/watch"), "https://m.youtube.com/watch?v=abc"))
        assertEquals("https://nitter.home.lan/user/status/1", Rewrite.rewrite(rule("twitter.com, x.com", "nitter.home.lan"), "https://x.com/user/status/1"))
        assertEquals(null, Rewrite.rewrite(rule("youtube.com/watch", "yt.home.lan/watch"), "https://youtube.com/channel/x"))
        assertEquals("http://lan.box/p", Rewrite.rewrite(rule("example.com", "http://lan.box"), "https://example.com/p"))
    }

    @Test
    fun rewriteScopesAndSites() {
        val rules = listOf(rule("reddit.com", "old.reddit.com", "inside").apply { site = "a" })
        assertEquals("https://old.reddit.com/", Rewrite.apply(rules, "https://reddit.com/", "inside", "a"))
        assertEquals("https://reddit.com/", Rewrite.apply(rules, "https://reddit.com/", "incoming", "a"))
        assertEquals("https://reddit.com/", Rewrite.apply(rules, "https://reddit.com/", "inside", "b"))
    }

    @Test
    fun userscriptHeaders() {
        val code = """
            // ==UserScript==
            // @name        Furigana everywhere
            // @version     2.3
            // @match       https://lexicon.home.lan/*
            // @grant       GM_xmlhttpRequest
            // @grant       GM_setClipboard
            // @connect     jisho.org
            // @run-at      document-start
            // ==/UserScript==
            console.log(1)
        """.trimIndent()
        val header = Headers.read(code)
        assertNotNull(header)
        header!!
        assertEquals("Furigana everywhere", header.name)
        assertEquals("2.3", header.version)
        assertEquals(listOf("https://lexicon.home.lan/*"), header.matches)
        assertEquals(listOf("GM_xmlhttpRequest", "GM_setClipboard"), header.grants)
        assertEquals(listOf("jisho.org"), header.connects)
        assertEquals("document-start", header.runAt)
        assertTrue(Headers.risky("  GM_xmlhttpRequest({url})"))
        assertFalse(Headers.risky("const x = 1;"))
    }

    @Test
    fun userCss() {
        val code = """
            /* ==UserStyle==
            @name Night ink
            @version 1.1
            @preprocessor default
            ==/UserStyle== */
            @-moz-document domain("lexicon.home.lan") {
              body { background: /*[[bg]]*/ #111; }
            }
            .always { color: red; }
        """.trimIndent()
        val (css, problem) = Headers.fromUserCss(code)
        assertEquals(null, problem)
        assertTrue(css.contains("/*pwa-doc domain(\"lexicon.home.lan\") */"))
        assertTrue(css.contains("body { background:  #111; }"))
        assertTrue(css.contains(".always { color: red; }"))
        assertEquals("Night ink", Headers.read(code)?.name)
        val (_, stylus) = Headers.fromUserCss(code.replace("@preprocessor default", "@preprocessor stylus"))
        assertNotNull(stylus)
    }

    @Test
    fun diffLines() {
        val changes = Diff.changes("a\nb\nc", "a\nB\nc\nd")
        assertEquals(listOf('-' to "b", '+' to "B", '+' to "d"), changes)
    }
}
