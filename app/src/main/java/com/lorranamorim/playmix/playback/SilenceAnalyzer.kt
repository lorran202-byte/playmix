package com.lorranamorim.playmix.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Onde a música realmente começa e termina, sem os silêncios das pontas. */
data class AudioBounds(val startMs: Long, val endMs: Long)

/**
 * Lê o começo e o fim de cada arquivo e descobre os trechos mudos.
 * O resultado fica guardado no aparelho, então cada música é analisada uma vez só.
 */
class SilenceAnalyzer(private val context: Context) {

    private val cacheFile = File(context.filesDir, "bounds.json")
    private val cache = ConcurrentHashMap<String, AudioBounds>()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val o = JSONObject(cacheFile.readText())
                o.keys().forEach { k ->
                    val a = o.getJSONArray(k)
                    cache[k] = AudioBounds(a.getLong(0), a.getLong(1))
                }
            }
        }
    }

    fun cached(id: String): AudioBounds? = cache[id]

    /** Roda fora da linha principal. */
    fun analyze(id: String, uri: Uri, durationMs: Long): AudioBounds {
        cache[id]?.let { return it }
        val result = runCatching { compute(uri, durationMs) }.getOrNull() ?: AudioBounds(0, durationMs)
        cache[id] = result
        save()
        return result
    }

    @Synchronized
    private fun save() {
        runCatching {
            val o = JSONObject()
            cache.forEach { (k, v) -> o.put(k, org.json.JSONArray(listOf(v.startMs, v.endMs))) }
            cacheFile.writeText(o.toString())
        }
    }

    private fun compute(uri: Uri, durationMs: Long): AudioBounds {
        if (durationMs < 20_000) return AudioBounds(0, durationMs)
        val threshold = -45.0 // dBFS: abaixo disso é considerado silêncio

        val head = levels(uri, 0, min(15_000L, durationMs / 3))
        val start = head.firstOrNull { it.second > threshold }?.first ?: 0L

        val tailFrom = max(0L, durationMs - 25_000L)
        val tail = levels(uri, tailFrom, durationMs + 1_000)
        val lastLoud = tail.lastOrNull { it.second > threshold }?.first
        val end = if (lastLoud == null || tail.isEmpty()) durationMs else lastLoud + 150

        return AudioBounds(
            startMs = start.coerceIn(0L, 10_000L),
            endMs = end.coerceIn(durationMs - 20_000L, durationMs),
        )
    }

    /** Volume (dBFS) de cada pedaço decodificado entre [fromMs] e [toMs]: lista de (tempo ms, dB). */
    private fun levels(uri: Uri, fromMs: Long, toMs: Long): List<Pair<Long, Double>> {
        val out = ArrayList<Pair<Long, Double>>()
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            var trackIdx = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { trackIdx = i; fmt = f; break }
            }
            if (trackIdx < 0 || fmt == null) return out
            ex.selectTrack(trackIdx)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()
            ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var channels = fmt.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + 5_000

            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = ex.readSampleData(buf, 0)
                        val t = ex.sampleTime
                        if (size < 0 || t > toMs * 1000) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, t, 0)
                            ex.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        channels = of.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        floatPcm = Build.VERSION.SDK_INT >= 24 &&
                            of.getIntegerOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val timeMs = info.presentationTimeUs / 1000
                        if (info.size > 0 && timeMs >= fromMs) {
                            val buf = codec.getOutputBuffer(outIdx)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val db = rmsDb(buf.slice().order(ByteOrder.nativeOrder()), floatPcm)
                            out += timeMs to db
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
        return out
    }

    private fun rmsDb(buf: java.nio.ByteBuffer, floatPcm: Boolean): Double {
        var sum = 0.0
        var n = 0
        if (floatPcm) {
            val fb = buf.asFloatBuffer()
            while (fb.hasRemaining()) { val v = fb.get().toDouble(); sum += v * v; n++ }
        } else {
            val sb = buf.asShortBuffer()
            while (sb.hasRemaining()) { val v = sb.get() / 32768.0; sum += v * v; n++ }
        }
        if (n == 0) return -120.0
        val rms = sqrt(sum / n)
        return if (rms <= 1e-6) -120.0 else 20 * log10(rms)
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, def: Int): Int =
        if (containsKey(key)) getInteger(key) else def
}
