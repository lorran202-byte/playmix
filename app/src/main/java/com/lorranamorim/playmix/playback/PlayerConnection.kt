package com.lorranamorim.playmix.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lorranamorim.playmix.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerState(
    val connected: Boolean = false,
    val queue: List<MediaItem> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0L,
    val contextTitle: String = "",
    val sleep: SleepTimer = SleepTimer.Off,
) {
    val current: MediaItem? get() = queue.getOrNull(index)
    val currentId: Long? get() = current?.mediaId?.toLongOrNull()

    /** Bloco de músicas adicionadas pelo usuário logo depois da atual. */
    val userQueueRange: IntRange
        get() {
            if (index < 0) return IntRange.EMPTY
            var end = index + 1
            while (end < queue.size && queue[end].isUserQueued) end++
            return (index + 1) until end
        }
}

sealed interface SleepTimer {
    data object Off : SleepTimer
    data class At(val endsAtMs: Long) : SleepTimer
    data object EndOfTrack : SleepTimer
}

class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Ordem original da lista tocada, para desligar o aleatório sem perder a sequência. */
    private var originalOrder: List<String> = emptyList()
    private var sleepJob: Job? = null

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(listener)
                if (originalOrder.isEmpty()) originalOrder = items(c).map { it.mediaId }
                refresh()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_state.value.sleep == SleepTimer.EndOfTrack &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            ) {
                controller?.pause()
                controller?.seekTo(0)
                cancelSleep()
            }
        }
    }

    private fun items(c: Player) = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }

    private fun refresh() {
        val c = controller ?: return
        _state.update {
            it.copy(
                connected = true,
                queue = items(c),
                index = c.currentMediaItemIndex.takeIf { c.mediaItemCount > 0 } ?: -1,
                isPlaying = c.isPlaying,
                repeatMode = c.repeatMode,
                durationMs = c.duration.coerceAtLeast(0L),
            )
        }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    // ---------------- Tocar ----------------

    /**
     * Toca uma lista. Com [shuffle], começa pela faixa escolhida (se houver) e embaralha o resto.
     */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, contextTitle: String = "") {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        originalOrder = tracks.map { it.id.toString() }
        val start = startIndex.coerceIn(0, tracks.size - 1)
        val ordered: List<Track>
        val startPos: Int
        if (shuffle) {
            val first = if (startIndex >= 0) tracks[start] else null
            val rest = tracks.filterIndexed { i, _ -> first == null || i != start }
            ordered = listOfNotNull(first) + Shuffler.shuffle(rest, { it.artist })
            startPos = 0
        } else {
            ordered = tracks
            startPos = start
        }
        c.setMediaItems(ordered.map { it.toMediaItem() }, startPos, 0L)
        c.prepare()
        c.play()
        _state.update { it.copy(shuffle = shuffle, contextTitle = contextTitle) }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun skipTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val turningOn = !_state.value.shuffle
        val count = c.mediaItemCount
        if (count > 0) {
            val cur = c.currentMediaItemIndex
            var start = cur + 1
            while (start < count && c.getMediaItemAt(start).isUserQueued) start++
            val rest = (start until count).map { c.getMediaItemAt(it) }
            if (rest.isNotEmpty()) {
                val newRest = if (turningOn) {
                    Shuffler.shuffle(rest, { it.mediaMetadata.artist?.toString() ?: "" })
                } else {
                    val byId = rest.groupBy { it.mediaId }.mapValues { it.value.first() }
                    val curId = c.currentMediaItem?.mediaId
                    val pos = originalOrder.indexOf(curId)
                    val after = if (pos >= 0) originalOrder.drop(pos + 1) else originalOrder
                    val ordered = after.mapNotNull { byId[it] }.distinctBy { it.mediaId }
                    ordered + rest.filter { r -> ordered.none { it.mediaId == r.mediaId } }
                }
                c.replaceMediaItems(start, count, newRest)
            }
        }
        _state.update { it.copy(shuffle = turningOn) }
    }

    // ---------------- Fila ----------------

    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(track)); return }
        val end = _state.value.userQueueRange.let { if (it.isEmpty()) c.currentMediaItemIndex + 1 else it.last + 1 }
        c.addMediaItem(end.coerceAtMost(c.mediaItemCount), track.toMediaItem(userQueued = true))
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(track)); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem(userQueued = true))
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) c.removeMediaItem(index)
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun clearUpcoming() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
    }

    // ---------------- Timer de soneca ----------------

    fun setSleepMinutes(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleep = SleepTimer.At(endsAt)) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.update { it.copy(sleep = SleepTimer.Off) }
        }
    }

    fun setSleepEndOfTrack() {
        sleepJob?.cancel()
        _state.update { it.copy(sleep = SleepTimer.EndOfTrack) }
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        _state.update { it.copy(sleep = SleepTimer.Off) }
    }
}
