package com.buga.walkman.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLogicTest {

    // --- matches: query vacío / en blanco ---

    @Test
    fun `empty query always matches`() {
        assertTrue(SearchLogic.matches("", listOf("anything")))
        assertTrue(SearchLogic.matches("", emptyList()))
    }

    @Test
    fun `blank query always matches`() {
        assertTrue(SearchLogic.matches("   ", listOf("anything")))
    }

    // --- matches: coincidencia exacta y substring ---

    @Test
    fun `matches exact text`() {
        assertTrue(SearchLogic.matches("Midnight", listOf("Midnight")))
    }

    @Test
    fun `matches substring`() {
        assertTrue(SearchLogic.matches("Midnight", listOf("Midnight Drive")))
    }

    @Test
    fun `no match returns false`() {
        assertFalse(SearchLogic.matches("zzz", listOf("Midnight Drive")))
    }

    // --- matches: case insensitive ---

    @Test
    fun `match is case insensitive on query`() {
        assertTrue(SearchLogic.matches("nIgHt", listOf("NIGHT")))
    }

    @Test
    fun `match is case insensitive on text`() {
        assertTrue(SearchLogic.matches("night", listOf("NIGHTCALL")))
    }

    // --- matches: múltiples campos (artista / álbum) ---

    @Test
    fun `matches when query is found in any field`() {
        assertTrue(SearchLogic.matches("neon", listOf("Track", "The Weeknd", "Neon Album")))
    }

    @Test
    fun `matches artist field`() {
        assertTrue(SearchLogic.matches("weeknd", listOf("Track A", "The Weeknd", "Album X")))
    }

    @Test
    fun `matches album field`() {
        assertTrue(SearchLogic.matches("nightcall", listOf("Track A", "Artist", "Nightcall")))
    }

    @Test
    fun `returns false when query not in any field`() {
        assertFalse(
            SearchLogic.matches("bleep", listOf("Track A", "Artist One", "Album Two"))
        )
    }

    // --- matches: query con espacios alrededor (trim) ---

    @Test
    fun `trims surrounding spaces from query`() {
        assertTrue(SearchLogic.matches("  night  ", listOf("Nightcall")))
    }

    // --- Filtering helper behaviour (usando matches) ---

    @Test
    fun `filter keeps all when query blank`() {
        val fields = listOf(
            listOf("Track A", "Artist", "Album"),
            listOf("Track B", "Artist 2", "Album 2")
        )
        val q = "   "
        assertEquals(2, fields.count { SearchLogic.matches(q, it) })
    }

    @Test
    fun `filter narrows to matching entries`() {
        val fields = listOf(
            listOf("Midnight Drive", "Neon", "Nightcall"),
            listOf("Daydream", "Artist", "Album"),
            listOf("Midnight City", "Neon", "Night Drive")
        )
        val matched = fields.filter { SearchLogic.matches("midnight", it) }
        assertEquals(2, matched.size)
        assertEquals("Midnight Drive", matched[0][0])
        assertEquals("Midnight City", matched[1][0])
    }

    @Test
    fun `filter returns empty when nothing matches`() {
        val fields = listOf(
            listOf("Track A", "Artist", "Album"),
            listOf("Track B", "Artist", "Album")
        )
        val matched = fields.filter { SearchLogic.matches("zzz", it) }
        assertTrue(matched.isEmpty())
    }

    @Test
    fun `filter preserves original order`() {
        val fields = listOf(
            listOf("Neon A", "Neon"),
            listOf("Plain", "Other"),
            listOf("Neon C"),
            listOf("Neon Moon")
        )
        val matched = fields.filter { SearchLogic.matches("neon", it) }
        assertEquals(listOf("Neon A", "Neon C", "Neon Moon"), matched.map { it.first() })
    }

    @Test
    fun `filter does not duplicate entries`() {
        val fields = listOf(
            listOf("Neon Sky", "Neon", "Neon Album"),
            listOf("Plain")
        )
        val matched = fields.filter { SearchLogic.matches("neon", it) }
        assertEquals(1, matched.size)
    }
}
