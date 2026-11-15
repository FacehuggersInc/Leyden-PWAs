package org.bond.pwaapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun readsCssColors() {
        assertEquals(opaque(0x1E2A24), parseCssColor("#1e2a24"))
        assertEquals(opaque(0xFFAA00), parseCssColor("#fa0"))
        assertEquals(opaque(0x112233), parseCssColor("#11223380"))
        assertEquals(opaque(0x0A141E), parseCssColor("rgb(10, 20, 30)"))
        assertEquals(opaque(0xFFFFFF), parseCssColor(" White "))
        assertNull(parseCssColor("#12"))
        assertNull(parseCssColor("not a color"))
        assertNull(parseCssColor(""))
        assertNull(parseCssColor(null))
    }

    @Test
    fun writesColorsAsHex() {
        assertEquals("#1E2A24", colorHex(opaque(0x1E2A24)))
    }

    @Test
    fun tellsLightFromDark() {
        assertTrue(isLight(opaque(0xFFFFFF)))
        assertFalse(isLight(opaque(0x1E2A24)))
    }

    @Test
    fun addsHttpsWhenNoSchemeIsGiven() {
        assertEquals("https://example.com", normalizeAddress(" example.com "))
        assertEquals("http://100.64.0.2:8550", normalizeAddress("http://100.64.0.2:8550"))
        assertEquals("", normalizeAddress("   "))
    }

    @Test
    fun findsTheAddressInSharedText() {
        assertEquals("https://example.com/page", sharedAddress("Look at this https://example.com/page"))
        assertEquals("example.com", sharedAddress("example.com"))
    }

    @Test
    fun findsTheBaseDomain() {
        assertEquals("example.com", baseDomain("app.example.com"))
        assertEquals("example.co.uk", baseDomain("mail.example.co.uk"))
        assertEquals("example.com", baseDomain("example.com"))
        assertEquals("192.168.1.10", baseDomain("192.168.1.10"))
        assertEquals("localhost", baseDomain("localhost"))
    }

    @Test
    fun keepsSubdomainsOfTheSameSite() {
        assertTrue(sameSite("auth.example.com", "app.example.com"))
        assertTrue(sameSite("example.com", "www.example.com"))
        assertFalse(sameSite("example.org", "example.com"))
        assertFalse(sameSite("notexample.com", "example.com"))
        assertTrue(sameSite(null, "example.com"))
    }

    @Test
    fun turnsAPhoneAgentIntoADesktopOne() {
        val phone = "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0 Mobile Safari/537.36"
        val desktop = desktopAgent(phone)
        assertFalse("Android" in desktop)
        assertFalse("Mobile" in desktop)
        assertTrue("(X11; Linux x86_64)" in desktop)
    }
}
