package com.goback.focus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavTabTest {

    @Test
    fun itemsAreFullyInitializedOnFirstAccess() {
        val tabs = NavTab.items

        assertEquals(5, tabs.size)
        tabs.forEach { tab ->
            assertNotNull(tab)
            assertNotNull(tab.route)
        }
        assertEquals(
            listOf("home", "focus", "analytics", "notes", "settings"),
            tabs.map { it.route }
        )
    }

    @Test
    fun itemsAreStableAcrossRepeatedAccess() {
        val first = NavTab.items
        val second = NavTab.items

        assertEquals(first.map { it.route }, second.map { it.route })
    }
}
