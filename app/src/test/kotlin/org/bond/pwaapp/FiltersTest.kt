package org.bond.pwaapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private fun filters(vararg lines: String): Filters = Filters("test").apply { lines.forEach { add(it) } }

    private fun blocked(filters: Filters, url: String, page: String = "news.example.com"): Boolean =
        filters.match(url, hostOf(url), page)?.blocked == true

    @Test
    fun hostRulesBlockSubdomains() {
        val list = filters("||doubleclick.net^")
        assertTrue(blocked(list, "https://securepubads.g.doubleclick.net/tag/js/gpt.js"))
        assertTrue(blocked(list, "https://doubleclick.net/x"))
        assertFalse(blocked(list, "https://notdoubleclick.net/x"))
    }

    @Test
    fun exceptionsWin() {
        val list = filters("||cdn.example.org^", "@@||cdn.example.org/lib/")
        assertTrue(blocked(list, "https://cdn.example.org/ads/a.js"))
        assertFalse(blocked(list, "https://cdn.example.org/lib/jquery.js"))
    }

    @Test
    fun importantBeatsExceptions() {
        val list = filters("||track.example.org^\$important", "@@||track.example.org^")
        assertTrue(blocked(list, "https://track.example.org/p"))
    }

    @Test
    fun thirdPartyOption() {
        val list = filters("||widgets.example.com^\$third-party")
        assertTrue(blocked(list, "https://widgets.example.com/w.js", "other.org"))
        assertFalse(blocked(list, "https://widgets.example.com/w.js", "www.example.com"))
    }

    @Test
    fun domainOption() {
        val list = filters("/banner/*\$domain=shop.com|~deals.shop.com")
        assertTrue(blocked(list, "https://img.cdn.net/banner/1.png", "www.shop.com"))
        assertFalse(blocked(list, "https://img.cdn.net/banner/1.png", "deals.shop.com"))
        assertFalse(blocked(list, "https://img.cdn.net/banner/1.png", "other.com"))
    }

    @Test
    fun patternsWithSeparatorsAndWildcards() {
        val list = filters("/ads/banner^", "||example.net/*/pixel.gif", "|https://evil.example^")
        assertTrue(blocked(list, "https://a.com/ads/banner?x=1"))
        assertTrue(blocked(list, "https://a.com/ads/banner"))
        assertFalse(blocked(list, "https://a.com/ads/bannerman"))
        assertTrue(blocked(list, "https://www.example.net/a/b/pixel.gif"))
        assertTrue(blocked(list, "https://evil.example/x"))
        assertFalse(blocked(list, "https://evil.example.org/x"))
    }

    @Test
    fun unsupportedRulesAreSkipped() {
        val list = filters("||example.com^\$csp=script-src 'none'", "/^https?:\\/\\/ads\\./", "||popup.com^\$popup", "example.com##+js(nobab)")
        assertEquals(0, list.networkCount)
        assertNull(list.match("https://example.com/", "example.com", "example.com"))
    }

    @Test
    fun cosmeticRules() {
        val list = filters("##.ad-slot", "shop.com##.promo", "shop.com#@#.ad-slot", "##div:has-text(Sponsored)", "@@||bank.com^\$elemhide")
        val general = list.css("news.com", true)
        assertTrue(general.contains(".ad-slot{display:none!important}"))
        assertFalse(general.contains("has-text"))
        val shop = list.css("www.shop.com", true)
        assertTrue(shop.contains(".promo"))
        assertFalse(shop.contains(".ad-slot"))
        assertEquals("", list.css("online.bank.com", true))
        assertFalse(list.css("news.com", false).contains(".ad-slot"))
    }

    @Test
    fun listedHosts() {
        val list = filters("||tracker.io^", "||cdn.io^\$script,domain=a.com")
        assertTrue(list.hostListed("eu.tracker.io"))
        assertFalse(list.hostListed("cdn.io"))
    }

    @Test
    fun tokens() {
        assertEquals(listOf("https", "ads", "example", "com", "banner", "js"), Filters.tokens("https://ads.example.com/banner.js"))
        assertEquals("banner", Filters.bestToken("/ads/banner^"))
        assertNull(Filters.bestToken("*ad*"))
    }
}
