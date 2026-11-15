package org.bond.pwaapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesExtraTest {
    @Test
    fun selfHosted() {
        assertTrue(isSelfHosted("mail.tail3f2a.ts.net"))
        assertTrue(isSelfHosted("192.168.1.20"))
        assertTrue(isSelfHosted("100.101.5.2"))
        assertTrue(isSelfHosted("unraid.lan"))
        assertTrue(isSelfHosted("nas"))
        assertFalse(isSelfHosted("mail.google.com"))
        assertFalse(isSelfHosted("100.200.1.1"))
    }

    @Test
    fun sharedSuffixes() {
        assertEquals("tail3f2a.ts.net", baseDomain("mail.tail3f2a.ts.net"))
        assertTrue(sameSite("photos.tail3f2a.ts.net", "mail.tail3f2a.ts.net"))
        assertFalse(sameSite("mail.other1.ts.net", "mail.tail3f2a.ts.net"))
        assertEquals("me.github.io", baseDomain("me.github.io"))
        assertEquals("example.co.uk", baseDomain("www.example.co.uk"))
        assertEquals("example.com", baseDomain("a.b.example.com"))
    }

    @Test
    fun unread() {
        assertEquals(3, unreadFrom("Inbox (3) - Mail"))
        assertEquals(12, unreadFrom("(12) Home"))
        assertNull(unreadFrom("Inbox"))
    }

    @Test
    fun paths() {
        assertTrue(pathMatches("", "/x"))
        assertTrue(pathMatches("/drill", "/drill/3"))
        assertFalse(pathMatches("/drill", "/home"))
        assertTrue(pathMatches("/deck/*/drill", "/deck/9/drill"))
        assertFalse(pathMatches("/deck/*/drill", "/deck/9/list"))
    }

    @Test
    fun addresses() {
        assertEquals("example.com", hostOf("https://Example.com/a?b"))
        assertEquals("/a", pathOf("https://example.com/a?b"))
        assertEquals("", hostOf("not a url"))
    }
}
