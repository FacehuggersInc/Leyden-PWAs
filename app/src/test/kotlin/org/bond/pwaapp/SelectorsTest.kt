package org.bond.pwaapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectorsTest {
    @Test
    fun trimsLabelToStart() {
        val steps = Selectors.parse("button[aria-label=\"UNICORN-LINUX, needs you\"]")!!
        val part = steps[0].parts[1]
        assertEquals("aria-label", part.name)
        assertEquals("UNICORN-LINUX, needs you", part.value)
        part.op = "^="
        part.value = Selectors.prefixes(part.value).first()
        assertEquals("button[aria-label^=\"UNICORN-LINUX\"]", Selectors.build(steps))
    }

    @Test
    fun roundTripsChains() {
        val source = "div.menu > ul li:nth-of-type(2) a.link[data-id='7' i]"
        val steps = Selectors.parse(source)!!
        assertEquals(4, steps.size)
        assertEquals("div.menu > ul li:nth-of-type(2) a.link[data-id=\"7\" i]", Selectors.build(steps))
        steps[2].parts[1].on = false
        steps[0].parts[1].on = false
        assertEquals("div > ul li a.link[data-id=\"7\" i]", Selectors.build(steps))
    }

    @Test
    fun readsTextMatches() {
        val found = Selectors.text("text^:button|Skip")!!
        assertEquals("^", found.mode)
        assertEquals("button", found.tag)
        assertEquals("Skip", found.value)
        assertEquals("button that starts with “Skip”", Selectors.describe("text^:button|Skip"))
        assertEquals("stable", Selectors.stability("text:a|Home"))
    }

    @Test
    fun refusesWhatItCantRead() {
        assertNull(Selectors.parse("a, b"))
        assertNull(Selectors.parse("a[href"))
        assertEquals(listOf("UNICORN-LINUX"), Selectors.prefixes("UNICORN-LINUX, needs you").take(1))
    }
}
