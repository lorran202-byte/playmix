package com.lorranamorim.playmix.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Lê as músicas do aparelho pelo MediaStore. */
class MusicScanner(private val context: Context) {

    fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
        )
        val hasGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasGenre) cols += MediaStore.Audio.Media.GENRE
        val hasRelPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (hasRelPath) cols += MediaStore.Audio.Media.RELATIVE_PATH

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val result = ArrayList<Track>()
        try {
            context.contentResolver.query(collection, cols.toTypedArray(), selection, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                @Suppress("DEPRECATION")
                val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val iGenre = if (hasGenre) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                val iRel = if (hasRelPath) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

                while (c.moveToNext()) {
                    val folder = when {
                        iRel >= 0 && !c.isNull(iRel) -> c.getString(iRel).trimEnd('/')
                        iData >= 0 && !c.isNull(iData) -> c.getString(iData).substringBeforeLast('/').substringAfter("/0/")
                        else -> ""
                    }
                    val rawArtist = c.getString(iArtist)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Artista desconhecido" else rawArtist
                    val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Sem álbum"
                    val genre = if (iGenre >= 0) c.getString(iGenre)?.takeIf { it.isNotBlank() } else null
                    result += Track(
                        id = c.getLong(iId),
                        title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Sem título",
                        artist = artist,
                        album = album,
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        dateAdded = c.getLong(iAdded) * 1000L,
                        genre = genre,
                        folder = folder,
                        trackNumber = c.getInt(iTrack) % 1000,
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return result
    }
}
