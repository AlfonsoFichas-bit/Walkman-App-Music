package com.buga.walkman.model

object SearchLogic {

    fun matches(query: String, text: List<String>): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val lq = q.lowercase()
        return text.any { it.lowercase().contains(lq) }
    }

    fun filterSongs(songs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return songs
        return songs.filter { matches(query, listOf(it.title, it.artist, it.album)) }
    }

    fun filterAlbums(albums: List<Album>, query: String): List<Album> {
        if (query.isBlank()) return albums
        return albums.filter { matches(query, listOf(it.title, it.artist)) }
    }
}
