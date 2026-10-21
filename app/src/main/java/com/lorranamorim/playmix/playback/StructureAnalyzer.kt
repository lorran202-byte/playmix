package com.lorranamorim.playmix.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Estrutura da música inteira, do jeito que importa para mixar:
 * [introEndMs] onde a introdução calma acaba e a música "entra" (-1 = sem introdução longa);
 * [outroStartMs] onde começa o final mais calmo, depois da última parte forte (-1 = sem final longo).
 */
data class SongStructure(val introEndMs: Long, val outroStartMs: Long)

/** Lógica pura (testável no computador): acha introdução e final a partir do volume da música. */
object StructureDetect {
    const val FRAME_MS = 100

    /**
     * [envDb]: volume (dB) a cada 100 ms da música inteira.
     * As frases (16 batidas) vêm das análises de batida do começo ([head]) e do fim ([tail]).
     */
    fun detect(envDb: FloatArray, head: BeatInfo?, tail: BeatInfo?, startMs: Long, endMs: Long): SongStructure {
        val durMs = envDb.size.toLong() * FRAME_MS
        if (durMs < 60_000) return SongStructure(-1, -1)

        fun meanDb(from: Long, to: Long): Double {
            val a = (from / FRAME_MS).toInt().coerceIn(0, envDb.size)
            val b = (to / FRAME_MS).toInt().coerceIn(0, envDb.size)
            if (b <= a) return -120.0
            var s = 0.0
            for (i in a until b) s += envDb[i]
            return s / (b - a)
        }

        // "volume do corpo" da música: o nível das partes fortes (percentil 80 das janelas de 4 s)
        val windows = ArrayList<Double>()
        var w = startMs
        while (w + 4_000 <= endMs) { windows += meanDb(w, w + 4_000); w += 4_000 }
        if (windows.size < 10) return SongStructure(-1, -1)
        val body = windows.sorted()[(windows.size * 0.8).toInt().coerceAtMost(windows.size - 1)]
        val loud = body - 3.5

        // final: anda de trás para frente, frase por frase, até achar a última frase forte
        var outro = -1L
        if (tail != null) {
            val phrase = 16 * 60_000.0 / BeatDetect.normalize(tail.bpm)
            var j = Math.floor((endMs - tail.phraseMs) / phrase).toInt()
            while (j > -200) {
                val a = (tail.phraseMs + j * phrase).toLong()
                val b = (a + phrase).toLong()
                if (a < startMs || a < durMs * 0.5) break
                if (b <= endMs && meanDb(a, b) >= loud) {
                    outro = b
                    break
                }
                j--
            }
            // só vale se o final calmo for longo o bastante para valer a pena pular e não exagerado
            val rest = endMs - outro
            if (outro < 0 || rest < 10_000 || rest > 75_000) outro = -1
        }

        // introdução: anda para frente até achar a primeira frase forte
        var intro = -1L
        if (head != null) {
            val phrase = 16 * 60_000.0 / BeatDetect.normalize(head.bpm)
            var j = 0
            while (j < 40) {
                val a = (head.phraseMs + j * phrase).toLong()
                val b = (a + phrase).toLong()
                if (b > durMs * 0.4) break
                if (meanDb(a, b) >= loud) {
                    intro = a
                    break
                }
                j++
            }
            val len = intro - startMs
            if (intro < 0 || len < 8_000 || len > 45_000) intro = -1
        }
        return SongStructure(intro, outro)
    }
}

/** Lê a música inteira (só o volume, sem guardar o áudio) e guarda a estrutura encontrada. */
class StructureAnalyzer(private val context: Context) {

    private val cacheFile = File(context.filesDir, "structure.json")
    private val cache = ConcurrentHashMap<String, SongStructure>()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val o = JSONObject(cacheFile.readText())
                o.keys().forEach { k -> val a = o.getJSONArray(k); cache[k] = SongStructure(a.getLong(0), a.getLong(1)) }
            }
        }
    }

    fun cached(id: String): SongStructure? = cache[id]

    fun analyze(id: String, uri: Uri, bounds: AudioBounds, beats: TrackBeats?) {
        if (cache.containsKey(id)) return
        val env = runCatching { envelope(uri) }.getOrNull()
        val result = if (env == null) SongStructure(-1, -1)
        else StructureDetect.detect(env, beats?.head, beats?.tail, bounds.startMs, bounds.endMs)
        cache[id] = result
        save()
    }

    @Synchronized
    private fun save() {
        runCatching {
            val o = JSONObject()
            cache.forEach { (k, v) -> o.put(k, JSONArray(listOf(v.introEndMs, v.outroStartMs))) }
            cacheFile.writeText(o.toString())
        }
    }

    /** Volume (dB) a cada 100 ms da música inteira. */
    private fun envelope(uri: Uri): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        val frames = ArrayList<Float>()
        try {
            ex.setDataSource(context, uri, null)
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { ex.selectTrack(i); fmt = f; break }
            }
            if (fmt == null) return null
            var sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var acc = 0.0
            var accN = 0
            var perFrame = sampleRate * StructureDetect.FRAME_MS / 1000
            val deadline = System.currentTimeMillis() + 40_000
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    perFrame = sampleRate * StructureDetect.FRAME_MS / 1000
                    floatPcm = Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val b = buf.slice().order(ByteOrder.nativeOrder())
                        val ch = channels.coerceAtLeast(1)
                        fun push(v: Double) {
                            acc += v * v
                            accN++
                            if (accN >= perFrame) {
                                val rms = sqrt(acc / accN)
                                frames += if (rms <= 1e-6) -120f else (20 * log10(rms)).toFloat()
                                acc = 0.0; accN = 0
                            }
                        }
                        if (floatPcm) {
                            val fb = b.asFloatBuffer()
                            while (fb.remaining() >= ch) { var s = 0.0; repeat(ch) { s += fb.get() }; push(s / ch) }
                        } else {
                            val sb = b.asShortBuffer()
                            while (sb.remaining() >= ch) { var s = 0.0; repeat(ch) { s += sb.get() / 32768.0 }; push(s / ch) }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
        return if (frames.size < 600) null else frames.toFloatArray()
    }
}
