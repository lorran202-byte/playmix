package com.lorranamorim.playmix.playback

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lorranamorim.playmix.data.Track
import com.lorranamorim.playmix.data.albumArtUri

const val EXTRA_USER_QUEUED = "pm_user_queued"
const val EXTRA_ALBUM_ID = "pm_album_id"
const val EXTRA_DURATION = "pm_duration"

fun uriForId(id: String): Uri? =
    id.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }

fun Track.toMediaItem(userQueued: Boolean = false): MediaItem =
    buildMediaItem(id.toString(), title, artist, album, albumId, durationMs, userQueued)

fun buildMediaItem(
    id: String,
    title: String,
    artist: String,
    album: String,
    albumId: Long,
    durationMs: Long,
    userQueued: Boolean,
): MediaItem {
    val extras = Bundle().apply {
        putBoolean(EXTRA_USER_QUEUED, userQueued)
        putLong(EXTRA_ALBUM_ID, albumId)
        putLong(EXTRA_DURATION, durationMs)
    }
    val uri = uriForId(id)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri(albumId))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build(),
        )
        .build()
}

val MediaItem.isUserQueued: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_USER_QUEUED, false) == true

fun MediaItem.withUserQueued(flag: Boolean): MediaItem {
    val m = mediaMetadata
    return buildMediaItem(
        mediaId,
        m.title?.toString() ?: "",
        m.artist?.toString() ?: "",
        m.albumTitle?.toString() ?: "",
        m.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
        m.extras?.getLong(EXTRA_DURATION) ?: 0L,
        flag,
    )
}
