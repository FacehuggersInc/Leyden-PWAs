package org.bond.pwaapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelaxTest {
    @Test
    fun matchesPagesAndHosts() {
        assertTrue(Relax.glob("login.example.com/verify*", "https://login.example.com/verify?next=/"))
        assertFalse(Relax.glob("login.example.com/verify*", "https://login.example.com/home"))
        assertTrue(Relax.glob("*.hcaptcha.com", "https://newassets.hcaptcha.com/captcha/v1/abc"))
        assertTrue(Relax.glob("www.google.com/recaptcha/*", "https://www.google.com/recaptcha/api2/anchor"))
        assertFalse(Relax.glob("www.google.com/recaptcha/*", "https://www.google.com/search"))
        assertTrue(Relax.known("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
    }

    @Test
    fun rulesForTheCurrentPage() {
        val url = "https://shop.example.com/check/start?x=1"
        assertTrue(Relax.glob(Relax.pageRule(url), url))
        assertTrue(Relax.glob(Relax.siteRule(url), "https://shop.example.com/anything"))
    }
}
