package com.lorranamorim.playmix.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lorranamorim.playmix.PlayMixApp
import com.lorranamorim.playmix.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private val container get() = (application as PlayMixApp).container
    private val store get() = container.store

    private var fadeInActive = false
    private var pendingRecord = true

    private val fadeTick = object : Runnable {
        override fun run() {
            applyFade()
            handler.postDelayed(this, 100)
        }
    }

    private val saveRunnable = Runnable { saveQueue() }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(listener)
        container.eq.attach(player.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(callback)
            .build()

        restoreQueue()
        handler.post(fadeTick)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        handler.removeCallbacksAndMessages(null)
        container.eq.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Itens chegam do app sem o endereço do arquivo; aqui ele é recolocado. */
    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: uriForId(item.mediaId)
                item.buildUpon().setUri(uri).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fadeInActive = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            pendingRecord = true
            if (player.isPlaying) recordIfPending()
            applyEqFor(mediaItem)
            scheduleSave()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.eq.attach(audioSessionId)
            applyEqFor(player.currentMediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordIfPending() else saveQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            scheduleSave()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            scheduleSave()
        }

        override fun onPlayerError(error: PlaybackException) {
            // arquivo apagado ou ilegível: pula para a próxima em vez de travar
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    /** Ajusta o equalizador ao gênero da música (lido das tags do arquivo). */
    private fun applyEqFor(item: MediaItem?) {
        val id = item?.mediaId?.toLongOrNull()
        val genre = id?.let { container.music.library.value.byId[it]?.genre }
        container.eq.apply(genre)
    }

    private fun recordIfPending() {
        if (!pendingRecord) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        pendingRecord = false
        store.recordPlay(id)
    }

    /** Transição suave: abaixa o volume no fim da faixa e sobe no começo da próxima. */
    private fun applyFade() {
        if (!player.isPlaying) return
        val fadeMs = store.data.value.settings.fadeSeconds * 1000L
        if (fadeMs <= 0) {
            if (player.volume != 1f) player.volume = 1f
            return
        }
        val pos = player.currentPosition
        val dur = player.duration
        var v = 1f
        if (fadeInActive) {
            if (pos < fadeMs) v = (pos.toFloat() / fadeMs).coerceIn(0.15f, 1f) else fadeInActive = false
        }
        val hasFollowing = player.hasNextMediaItem() || player.repeatMode != Player.REPEAT_MODE_OFF
        if (hasFollowing && dur != C.TIME_UNSET && dur > fadeMs * 2) {
            val remaining = dur - pos
            if (remaining < fadeMs) v = minOf(v, (remaining.toFloat() / fadeMs).coerceIn(0.15f, 1f))
        }
        if (player.volume != v) player.volume = v
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 1500)
    }

    private fun saveQueue() {
        if (!::player.isInitialized) return
        val items = JSONArray()
        for (i in 0 until player.mediaItemCount) {
            val m = player.getMediaItemAt(i)
            val md = m.mediaMetadata
            items.put(JSONObject().apply {
                put("id", m.mediaId)
                put("t", md.title?.toString() ?: "")
                put("a", md.artist?.toString() ?: "")
                put("al", md.albumTitle?.toString() ?: "")
                put("aid", md.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L)
                put("d", md.extras?.getLong(EXTRA_DURATION) ?: 0L)
                put("q", m.isUserQueued)
            })
        }
        store.saveQueue(JSONObject().apply {
            put("items", items)
            put("index", player.currentMediaItemIndex)
            put("pos", player.currentPosition)
            put("repeat", player.repeatMode)
        })
    }

    private fun restoreQueue() {
        val o = store.loadQueue() ?: return
        val arr = o.optJSONArray("items") ?: return
        if (arr.length() == 0) return
        val items = (0 until arr.length()).mapNotNull { i ->
            val it = arr.optJSONObject(i) ?: return@mapNotNull null
            buildMediaItem(
                it.optString("id"), it.optString("t"), it.optString("a"), it.optString("al"),
                it.optLong("aid"), it.optLong("d"), it.optBoolean("q"),
            ).let { item -> item.buildUpon().setUri(uriForId(item.mediaId)).build() }
        }
        val index = o.optInt("index", 0).coerceIn(0, items.size - 1)
        player.setMediaItems(items, index, o.optLong("pos", 0L).coerceAtLeast(0L))
        player.repeatMode = o.optInt("repeat", Player.REPEAT_MODE_OFF)
        player.playWhenReady = false
        player.prepare()
    }
}
