package com.lorranamorim.playmix.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val genre: String?,
    val folder: String,
    val trackNumber: Int,
) {
    val key: String get() = id.toString()
    /** Cada artista da faixa separado (participações viram artistas próprios). */
    val artists: List<String> get() = splitArtists(artist)
    val primaryArtist: String get() = artists.first()
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = albumArtUri(albumId)
}

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val tracks: List<Track>,
) {
    val artUri: Uri get() = albumArtUri(id)
}

data class Artist(
    val name: String,
    val tracks: List<Track>,
    val albums: List<Album>,
) {
    val artUri: Uri? get() = albums.firstOrNull()?.artUri
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PlayStat(val count: Int, val lastPlayed: Long)

/** De onde a música foi tocada: usado nos atalhos do Início e em "Próximas de". */
data class PlayContext(val type: ContextType, val id: String, val title: String) {
    val key: String get() = "${type.name}:$id"

    companion object {
        fun fromKey(key: String, title: String): PlayContext? {
            val idx = key.indexOf(':')
            if (idx <= 0) return null
            val type = runCatching { ContextType.valueOf(key.substring(0, idx)) }.getOrNull() ?: return null
            return PlayContext(type, key.substring(idx + 1), title)
        }
    }
}

enum class ContextType { PLAYLIST, ALBUM, ARTIST, LIKED, ALL, GENRE, SEARCH }

data class LibrarySettings(
    val fadeSeconds: Int = 4,
    val excludedFolders: Set<String> = emptySet(),
    /** Pastas escolhidas pelo usuário (caminho relativo). Vazio = todas as músicas do celular. */
    val musicFolders: Set<String> = emptySet(),
    val gridMode: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,
    val autoEq: Boolean = true,
    val profileName: String = "",
    /** 0 = sem foto; muda a cada foto nova para atualizar a imagem. */
    val profilePhotoVersion: Long = 0L,
)

enum class LibrarySort(val label: String) {
    RECENT("Recentes"),
    ADDED("Adicionados recentemente"),
    ALPHA("Ordem alfabética"),
}

data class LibraryData(
    val liked: List<Long> = emptyList(), // mais recentes primeiro
    val playlists: List<Playlist> = emptyList(),
    val pinned: List<String> = emptyList(), // chaves de PlayContext
    val stats: Map<Long, PlayStat> = emptyMap(),
    val recentContexts: List<Pair<String, String>> = emptyList(), // chave -> título, mais recente primeiro
    val settings: LibrarySettings = LibrarySettings(),
)


private val artistSeparators = Regex(
    """\s*(?:,|;|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+|\s+part\.?\s+|\s+participação\s+|\s+x\s+|\s+with\s+|\s+vs\.?\s+)\s*""",
    RegexOption.IGNORE_CASE,
)

fun splitArtists(raw: String): List<String> {
    val parts = raw.split(artistSeparators).map { it.trim().trim('(', ')') }.filter { it.isNotBlank() }.distinct()
    return parts.ifEmpty { listOf(raw) }
}
