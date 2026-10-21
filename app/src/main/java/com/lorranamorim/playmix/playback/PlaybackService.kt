package com.lorranamorim.playmix.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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

    private var pendingRecord = true

    // ---------- crossfade ----------
    /** Segundo player: toca o fim da música que está saindo enquanto a próxima entra. */
    private lateinit var tailPlayer: ExoPlayer
    private var tailEq: android.media.audiofx.Equalizer? = null
    private lateinit var analyzer: SilenceAnalyzer
    private val analysisPool = java.util.concurrent.Executors.newSingleThreadExecutor()
    private enum class XState { IDLE, PREPARED, CROSSING }
    private var xState = XState.IDLE
    private var preparedIndex = -1
    private var preparedAt = 0L
    private var crossStart = 0L
    private var crossMs = 0L
    private var selfSeek = false

    // ---------- ajuste de batida ----------
    private lateinit var beats: BeatAnalyzer
    private lateinit var structure: StructureAnalyzer
    private class BeatPlan(val speed: Double, val out: BeatInfo, val inc: BeatInfo)
    private var activePlan: BeatPlan? = null
    private var needAlign = false
    private var alignTries = 0
    private var lastAlignAt = 0L

    private val fadeTick = object : Runnable {
        override fun run() {
            runCatching { crossfadeTick() }
            handler.postDelayed(this, 50)
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

        analyzer = SilenceAnalyzer(this)
        beats = BeatAnalyzer(this)
        structure = StructureAnalyzer(this)
        tailPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false, // o foco de áudio fica com o player principal
            )
            .build()
        tailEq = runCatching { android.media.audiofx.Equalizer(0, tailPlayer.audioSessionId) }.getOrNull()

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
        runCatching { tailEq?.release() }
        tailPlayer.release()
        analysisPool.shutdownNow()
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
            if (selfSeek) {
                selfSeek = false
            } else {
                // o usuário trocou de música no meio de um crossfade: encerra na hora
                if (xState == XState.CROSSING) endCrossfade()
                resetPrepared()
                // passagem automática sem crossfade: pula o silêncio do começo
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val start = mediaItem?.mediaId?.let { analyzer.cached(it) }?.startMs ?: 0L
                    if (start > 300) player.seekTo(start)
                }
            }
            analyzeAround()
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
            if (isPlaying) recordIfPending() else {
                saveQueue()
                // pausou no meio da transição: termina ela para não ficar som sobrando
                // (carregando a próxima no meio do crossfade não conta: só pausa de verdade)
                if (xState == XState.CROSSING && !player.playWhenReady) endCrossfade()
            }
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

    // ======================= CROSSFADE =======================

    /** Analisa (uma vez) a música atual e a próxima para saber onde estão os silêncios. */
    private fun analyzeAround() {
        val idx = player.currentMediaItemIndex
        listOf(idx, player.nextMediaItemIndex).filter { it in 0 until player.mediaItemCount }.forEach { i ->
            val item = player.getMediaItemAt(i)
            val id = item.mediaId
            if (analyzer.cached(id) != null && beats.cached(id) != null && structure.cached(id) != null) return@forEach
            val uri = uriForId(id) ?: return@forEach
            val dur = item.mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L
            if (dur <= 0) return@forEach
            runCatching {
                analysisPool.execute {
                    val bounds = analyzer.analyze(id, uri, dur)
                    // depois dos silêncios, o andamento e as batidas do começo e do fim
                    beats.analyze(id, uri, bounds)
                    // e a estrutura da música inteira (introdução e final)
                    structure.analyze(id, uri, bounds, beats.cached(id))
                }
            }
        }
    }

    private fun boundsOf(item: MediaItem?, durationMs: Long): AudioBounds =
        item?.mediaId?.let { analyzer.cached(it) } ?: AudioBounds(0, durationMs)

    private fun crossfadeTick() {
        if (xState == XState.CROSSING) {
            val t = ((System.currentTimeMillis() - crossStart).toFloat() / crossMs).coerceIn(0f, 1f)
            // curva de potência constante: o volume total não "afunda" no meio
            player.volume = kotlin.math.sin(t * Math.PI / 2).toFloat()
            tailPlayer.volume = kotlin.math.cos(t * Math.PI / 2).toFloat()
            when (activeKind) {
                Kind.CUT -> bassSwap((t * 2f).coerceAtMost(1f))
                Kind.FILTER -> filterSweep(t)
                else -> bassSwap(t)
            }
            alignBeats(t)
            if (t >= 1f) endCrossfade()
            return
        }
        if (!player.isPlaying) return
        if (player.volume != 1f) player.volume = 1f

        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val pos = player.currentPosition
        val bounds = boundsOf(player.currentMediaItem, dur)
        val end = bounds.endMs.coerceAtMost(dur)
        val canAdvance = player.hasNextMediaItem() && player.repeatMode != Player.REPEAT_MODE_ONE
        val cross = store.data.value.settings.fadeSeconds * 1000L

        if (!canAdvance) { resetPrepared(); return }

        if (cross <= 0) {
            // sem crossfade: emenda direto, cortando o silêncio do fim
            if (end < dur - 500 && pos >= end) jumpToNext()
            return
        }

        val tr = planTransition(pos, bounds, end, cross)
        val triggerAt = tr.triggerAt
        // ajuste de batida: nos 4 s antes da transição, a música que sai vai chegando
        // ao andamento da próxima (em 4 degraus, sem mudar o tom)
        val plan = tr.plan
        val ramp = (((pos - (triggerAt - 4_000)).toDouble() / 4_000).coerceIn(0.0, 1.0) * 4).toInt() / 4.0
        setSpeed(if (plan == null || pos < triggerAt - 4_000) 1.0 else 1.0 + (plan.speed - 1.0) * ramp)

        if (xState == XState.IDLE && pos >= triggerAt - 4_000 && pos < triggerAt) prepareTail(triggerAt)
        if (xState == XState.PREPARED && preparedIndex != player.currentMediaItemIndex) resetPrepared()
        if (pos >= triggerAt && pos < end + 1_000) startCrossfade(tr)
    }

    // ---------- planejamento da transição, como um DJ ----------
    private enum class Kind { BLEND, MIX, CUT, FILTER }
    private class Transition(
        val index: Int, val mediaId: String, val nextId: String, val kind: Kind, val triggerAt: Long,
        val crossMs: Long, val nextStart: Long, val plan: BeatPlan?, val frozen: Boolean,
    )
    private var planned: Transition? = null
    private var activeKind = Kind.BLEND

    /**
     * Decide como e quando passar para a próxima:
     * - MIX: andamentos parecidos e tons que combinam → mistura longa (8/16/32 batidas) começando numa frase
     * - CUT: andamentos parecidos mas tons que brigam → passagem rápida de 4 batidas
     * - FILTER: andamentos muito diferentes → a que sai vai sendo "abafada" por um filtro
     * - BLEND: sem análise ainda → crossfade simples
     * O plano congela nos últimos segundos para não mudar no meio do caminho.
     */
    private fun planTransition(pos: Long, bounds: AudioBounds, end: Long, crossSetting: Long): Transition {
        val idx = player.currentMediaItemIndex
        val curId = player.currentMediaItem?.mediaId ?: ""
        planned?.let { if (it.index == idx && it.mediaId == curId && it.frozen &&
                (player.nextMediaItemIndex != C.INDEX_UNSET && player.getMediaItemAt(player.nextMediaItemIndex).mediaId == it.nextId)
            ) return it }
        planned = null

        val nextIdx = player.nextMediaItemIndex
        val nextId = if (nextIdx == C.INDEX_UNSET) "" else player.getMediaItemAt(nextIdx).mediaId
        val nextDefault = analyzer.cached(nextId)?.startMs ?: 0L
        val maxCross = ((end - bounds.startMs) / 3).coerceAtLeast(1_000L)
        val out = beats.cached(curId)?.tail
        val inc = beats.cached(nextId)?.head
        val speed = if (store.data.value.settings.beatMatch && out != null && inc != null) BeatDetect.speedFor(out.bpm, inc.bpm) else null

        val tr: Transition
        if (speed != null && out != null && inc != null) {
            val keyOk = KeyDetect.compatible(out.key, inc.key)
            val pIn = 60_000.0 / BeatDetect.normalize(inc.bpm)
            val pOut = 60_000.0 / BeatDetect.normalize(out.bpm)
            val wanted = crossSetting / pIn
            val nBeats = if (!keyOk) 4 else listOf(8, 16, 32).minBy { kotlin.math.abs(it - wanted) }
            val crossMs = (nBeats * pIn).toLong().coerceAtMost(maxCross)
            val lastStart = end - (crossMs * speed).toLong()
            val phraseLen = 16 * pOut
            var trigger = (out.phraseMs + kotlin.math.floor((lastStart - out.phraseMs) / phraseLen) * phraseLen).toLong()
            if (trigger < bounds.startMs + 30_000 || trigger > lastStart) {
                // sem frase boa: pelo menos começa numa batida
                trigger = (out.beatMs + kotlin.math.floor((lastStart - out.beatMs) / pOut) * pOut).toLong()
            }
            // estrutura: se a música tem um final calmo longo, sai antes dele (no fim da última parte forte)
            val smart = store.data.value.settings.smartCut
            structure.cached(curId)?.outroStartMs?.takeIf { smart && it > 0 }?.let { o ->
                if (o + (crossMs * speed).toLong() <= end && o < trigger && o > trigger - 80_000) trigger = o
            }
            // a que entra começa no início de uma frase dela (ou na primeira batida)
            var nextStart = inc.phraseMs
            if (nextStart < nextDefault - 100 || nextStart - nextDefault > 8 * pIn) {
                nextStart = (inc.beatMs + kotlin.math.ceil((nextDefault - inc.beatMs) / pIn) * pIn).toLong()
            }
            // e se ela tem introdução calma longa, entra perto do fim dela: a parte forte chega junto com o fim da mistura
            structure.cached(nextId)?.introEndMs?.takeIf { smart && it > 0 }?.let { ie ->
                val phraseIn = 16 * pIn
                val cand = (inc.phraseMs + kotlin.math.floor((ie - crossMs - inc.phraseMs) / phraseIn) * phraseIn).toLong()
                if (cand > nextStart && cand >= nextDefault) nextStart = cand
            }
            tr = Transition(
                idx, curId, nextId, if (keyOk) Kind.MIX else Kind.CUT, trigger, crossMs, nextStart.coerceAtLeast(0L),
                BeatPlan(speed, out, inc), frozen = pos >= trigger - 5_000,
            )
        } else {
            val crossMs = crossSetting.coerceAtMost(maxCross)
            val kind = if (out != null && inc != null) Kind.FILTER else Kind.BLEND
            var trigger = end - crossMs
            structure.cached(curId)?.outroStartMs?.takeIf { store.data.value.settings.smartCut && it > 0 }?.let { o ->
                if (o + crossMs <= end && o < trigger) trigger = o
            }
            tr = Transition(idx, curId, nextId, kind, trigger, crossMs, nextDefault, null, frozen = pos >= trigger - 5_000)
        }
        planned = tr
        return tr
    }

    /** Filtro: a música que sai perde grave e agudo aos poucos, como se fosse se afastando. */
    private fun filterSweep(t: Float) {
        val eq = tailEq ?: return
        runCatching {
            val min = eq.bandLevelRange[0].toInt().coerceAtLeast(-1500)
            val cut = (min * t).toInt()
            for (b in 0 until eq.numberOfBands) {
                val band = b.toShort()
                val hz = eq.getCenterFreq(band) / 1000
                if (hz < 400 || hz > 3_000) eq.setBandLevel(band, cut.toShort())
            }
            eq.enabled = true
        }
    }

    /** Deixa o fim da música atual pronto no segundo player, sem tocar ainda. */
    private fun prepareTail(at: Long) {
        val item = player.currentMediaItem ?: return
        tailPlayer.setMediaItem(item.buildUpon().setUri(uriForId(item.mediaId)).build(), at)
        tailPlayer.volume = 0f
        tailPlayer.playWhenReady = false
        tailPlayer.prepare()
        preparedIndex = player.currentMediaItemIndex
        preparedAt = at
        xState = XState.PREPARED
    }

    private fun startCrossfade(tr: Transition) {
        val c = tr.crossMs
        val pos = player.currentPosition
        if (xState != XState.PREPARED) prepareTail(pos)
        // o segundo player continua a música que está saindo do ponto exato
        if (kotlin.math.abs(tailPlayer.currentPosition - pos) > 120) tailPlayer.seekTo(pos)
        val plan = tr.plan
        tailPlayer.playbackParameters = PlaybackParameters((plan?.speed ?: 1.0).toFloat())
        tailPlayer.volume = 1f
        tailPlayer.play()
        resetBass()

        // o player principal já vai para a próxima, começando depois do silêncio inicial
        val nextIdx = player.nextMediaItemIndex
        val nextItem = player.getMediaItemAt(nextIdx)
        // se a fila mudou depois do plano, usa só o corte de silêncio da música nova
        val nextStart = if (nextItem.mediaId == tr.nextId) tr.nextStart else (analyzer.cached(nextItem.mediaId)?.startMs ?: 0L)
        player.volume = 0f
        setSpeed(1.0) // a que entra toca no andamento original dela
        selfSeek = true
        player.seekTo(nextIdx, nextStart)

        activePlan = plan
        activeKind = tr.kind
        planned = null
        needAlign = plan != null
        alignTries = 0
        lastAlignAt = System.currentTimeMillis()
        crossStart = System.currentTimeMillis()
        crossMs = c
        xState = XState.CROSSING
    }

    private fun jumpToNext() {
        val nextIdx = player.nextMediaItemIndex
        if (nextIdx == C.INDEX_UNSET) return
        val start = analyzer.cached(player.getMediaItemAt(nextIdx).mediaId)?.startMs ?: 0L
        selfSeek = true
        player.seekTo(nextIdx, start)
    }

    private fun endCrossfade() {
        tailPlayer.stop()
        tailPlayer.playbackParameters = PlaybackParameters.DEFAULT
        activePlan = null
        needAlign = false
        tailPlayer.volume = 0f
        player.volume = 1f
        resetBass()
        xState = XState.IDLE
        preparedIndex = -1
    }

    private fun resetPrepared() {
        if (xState != XState.CROSSING) setSpeed(1.0)
        if (xState == XState.PREPARED) {
            tailPlayer.stop()
            xState = XState.IDLE
            preparedIndex = -1
        }
    }

    /** Plano de ajuste de batida entre a música atual e a próxima (null = andamentos incompatíveis ou sem análise). */
    private fun beatPlan(): BeatPlan? {
        if (!store.data.value.settings.beatMatch) return null
        val curId = player.currentMediaItem?.mediaId ?: return null
        val nextIdx = player.nextMediaItemIndex
        if (nextIdx == C.INDEX_UNSET) return null
        val out = beats.cached(curId)?.tail ?: return null
        val inc = beats.cached(player.getMediaItemAt(nextIdx).mediaId)?.head ?: return null
        val speed = BeatDetect.speedFor(out.bpm, inc.bpm) ?: return null
        return BeatPlan(speed, out, inc)
    }

    private fun setSpeed(speed: Double) {
        val f = speed.toFloat()
        if (kotlin.math.abs(player.playbackParameters.speed - f) > 0.0005f) {
            player.playbackParameters = PlaybackParameters(f)
        }
    }

    /**
     * Encaixa as batidas: no começo da transição (a que entra ainda está baixinha),
     * mede a diferença entre as batidas das duas e corrige a posição da que entra.
     */
    private fun alignBeats(t: Float) {
        val plan = activePlan ?: return
        if (!needAlign) return
        val now = System.currentTimeMillis()
        if (t > 0.4f || alignTries >= 3) { needAlign = false; return }
        if (!player.isPlaying || !tailPlayer.isPlaying || now - lastAlignAt < 250) return
        val corr = BeatDetect.alignCorrectionMs(tailPlayer.currentPosition, plan.out, player.currentPosition, plan.inc)
        alignTries++
        lastAlignAt = now
        if (kotlin.math.abs(corr) <= 12) { needAlign = false; return }
        player.seekTo((player.currentPosition + corr).coerceAtLeast(0L))
    }

    /** Troca de grave: a música que sai vai perdendo o grave, para não embolar com a que entra. */
    private fun bassSwap(t: Float) {
        val eq = tailEq ?: return
        runCatching {
            val min = eq.bandLevelRange[0].toInt().coerceAtLeast(-1500)
            val cut = (min * (t * 2f).coerceAtMost(1f)).toInt()
            for (b in 0 until eq.numberOfBands) {
                val band = b.toShort()
                if (eq.getCenterFreq(band) / 1000 < 300) eq.setBandLevel(band, cut.toShort())
            }
            eq.enabled = true
        }
    }

    private fun resetBass() {
        val eq = tailEq ?: return
        runCatching {
            for (b in 0 until eq.numberOfBands) eq.setBandLevel(b.toShort(), 0)
        }
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
