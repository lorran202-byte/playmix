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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/** Andamento e uma batida de referência (tempo em ms dentro da música). */
/**
 * [phraseMs]: início de uma frase musical (bloco de 16 batidas), onde um DJ começaria a mixar.
 * [key]: tom musical 0–11 maior (C, C#…), 12–23 menor (Cm, C#m…), -1 desconhecido.
 */
data class BeatInfo(
    val bpm: Double,
    val beatMs: Long,
    val confidence: Double,
    val phraseMs: Long = beatMs,
    val key: Int = -1,
) {
    val periodMs: Double get() = 60_000.0 / bpm
}

/** Batidas do começo (para quem entra) e do fim (para quem sai) de uma música. */
data class TrackBeats(val head: BeatInfo?, val tail: BeatInfo?)

/**
 * Detecção de andamento (BPM) e de onde caem as batidas.
 * Matemática pura, sem Android, para poder ser testada no computador.
 */
object BeatDetect {
    private const val FPS = 200 // quadros por segundo da envoltória (5 ms)

    fun detect(mono: FloatArray, sampleRate: Int, regionStartMs: Long): BeatInfo? {
        val hop = sampleRate / FPS
        if (hop <= 0) return null
        val n = mono.size / hop
        if (n < FPS * 6) return null // menos de 6 s não dá para confiar

        // energia por quadro, com peso maior no grave (onde ficam bumbo e baixo)
        val a = exp(-2.0 * PI * 180.0 / sampleRate).toFloat()
        var lp = 0f
        val energy = DoubleArray(n)
        for (f in 0 until n) {
            var low = 0.0
            var full = 0.0
            val base = f * hop
            for (i in 0 until hop) {
                val x = mono[base + i]
                lp = (1 - a) * x + a * lp
                low += (lp * lp).toDouble()
                full += (x * x).toDouble()
            }
            energy[f] = 0.7 * low + 0.3 * full
        }

        // força de ataque: quanto a energia subiu em relação ao quadro anterior
        val env = DoubleArray(n)
        for (f in 1 until n) {
            env[f] = maxOf(0.0, ln(energy[f] + 1e-9) - ln(energy[f - 1] + 1e-9))
        }
        // tira a média para a autocorrelação ficar mais limpa
        val mean = env.average()
        for (f in env.indices) env[f] = maxOf(0.0, env[f] - mean)

        // autocorrelação nos andamentos entre 70 e 180 BPM, preferindo os perto de 120
        val minLag = (FPS * 60.0 / 180.0).toInt()
        val maxLag = (FPS * 60.0 / 70.0).toInt() + 1
        val r = DoubleArray(maxLag + 2)
        for (lag in (minLag - 1)..(maxLag + 1)) {
            var s = 0.0
            for (f in 0 until n - lag) s += env[f] * env[f + lag]
            r[lag] = s / (n - lag)
        }
        var best = -1
        var bestScore = 0.0
        for (lag in minLag..maxLag) {
            val bpm = FPS * 60.0 / lag
            val octave = ln(bpm / 120.0) / ln(2.0)
            val w = exp(-0.5 * (octave / 0.7) * (octave / 0.7))
            val sc = r[lag] * w
            if (sc > bestScore) { bestScore = sc; best = lag }
        }
        if (best < 0 || r[best] <= 0) return null

        // refina o período entre dois quadros (interpolação parabólica)
        val y0 = r[best - 1]; val y1 = r[best]; val y2 = r[best + 1]
        val denom = y0 - 2 * y1 + y2
        val offset = if (abs(denom) > 1e-12) (0.5 * (y0 - y2) / denom).coerceIn(-0.5, 0.5) else 0.0
        val period = best + offset
        val avg = (minLag..maxLag).map { r[it] }.average()
        val confidence = if (avg > 0) r[best] / avg else 0.0

        // fase: em que quadro caem as batidas
        var bestPhase = 0
        var bestPhaseScore = -1.0
        val pInt = period.roundToInt().coerceAtLeast(1)
        for (ph in 0 until pInt) {
            var s = 0.0
            var k = 0
            while (true) {
                val idx = (ph + k * period).roundToInt()
                if (idx >= n) break
                // pequena janela em volta para tolerar imprecisão
                s += env[idx] + 0.5 * (if (idx > 0) env[idx - 1] else 0.0) + 0.5 * (if (idx + 1 < n) env[idx + 1] else 0.0)
                k++
            }
            if (s > bestPhaseScore) { bestPhaseScore = s; bestPhase = ph }
        }
        val bpm = FPS * 60.0 / period
        val beatMs = regionStartMs + (bestPhase * 1000L / FPS)

        // frases: a energia costuma mudar a cada 16 batidas (entra refrão, cai o grave…).
        // Procura em qual batida, de 0 a 15, essas mudanças acontecem.
        val beatEnergy = ArrayList<Double>()
        var k = 0
        while (true) {
            val a0 = (bestPhase + k * period).roundToInt()
            val a1 = (bestPhase + (k + 1) * period).roundToInt()
            if (a1 > n) break
            var sum = 0.0
            for (f in a0 until a1) sum += energy[f]
            beatEnergy += ln(sum / maxOf(1, a1 - a0) + 1e-9)
            k++
        }
        var phraseBeat = 0
        if (beatEnergy.size >= 24) {
            var bestScore2 = -1.0
            for (o in 0 until 16) {
                var sc = 0.0
                var b = o
                while (b < beatEnergy.size) {
                    if (b >= 1) sc += abs(beatEnergy[b] - beatEnergy[b - 1])
                    if (b >= 2) sc += 0.5 * abs(beatEnergy[b] - beatEnergy[b - 2])
                    b += 16
                }
                // compara com a média de mudanças: só aceita se o ponto se destaca
                if (sc > bestScore2) { bestScore2 = sc; phraseBeat = o }
            }
            // prefere começos de compasso (múltiplos de 4) quando empatam de perto
        }
        val phraseMs = regionStartMs + ((bestPhase + phraseBeat * period) * 1000.0 / FPS).roundToInt()
        return BeatInfo(bpm, beatMs, confidence, phraseMs)
    }

    /** Coloca o andamento na faixa 80–160 (meio tempo e tempo dobrado contam como o mesmo pulso). */
    fun normalize(bpm: Double): Double {
        var b = bpm
        while (b < 80) b *= 2
        while (b >= 160) b /= 2
        return b
    }

    /**
     * Velocidade para a música que SAI ficar no andamento da que ENTRA.
     * Null quando os andamentos são diferentes demais (mudar mais que [maxChange] soaria estranho).
     */
    fun speedFor(outBpm: Double, inBpm: Double, maxChange: Double = 0.08): Double? {
        val o = normalize(outBpm)
        val i = normalize(inBpm)
        val s = i / o
        return if (abs(s - 1) <= maxChange) s else null
    }

    /**
     * Quanto adiantar (+) ou atrasar (−), em ms da música que entra, para as batidas coincidirem.
     * Fases calculadas em fração de batida.
     */
    fun alignCorrectionMs(outPos: Long, out: BeatInfo, inPos: Long, inc: BeatInfo): Long {
        val pOut = 60_000.0 / normalize(out.bpm)
        val pIn = 60_000.0 / normalize(inc.bpm)
        val phOut = frac((outPos - out.beatMs) / pOut)
        val phIn = frac((inPos - inc.beatMs) / pIn)
        var err = phOut - phIn
        if (err > 0.5) err -= 1.0
        if (err < -0.5) err += 1.0
        return (err * pIn).roundToInt().toLong()
    }

    private fun frac(x: Double): Double = x - kotlin.math.floor(x)
}

/** Decodifica trechos do arquivo e guarda o resultado da análise de batidas. */
class BeatAnalyzer(private val context: Context) {

    private val cacheFile = File(context.filesDir, "beats2.json")
    private val cache = ConcurrentHashMap<String, TrackBeats>()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val o = JSONObject(cacheFile.readText())
                o.keys().forEach { k ->
                    val a = o.getJSONArray(k)
                    cache[k] = TrackBeats(read(a, 0), read(a, 5))
                }
            }
        }
    }

    fun cached(id: String): TrackBeats? = cache[id]

    fun analyze(id: String, uri: Uri, bounds: AudioBounds) {
        if (cache.containsKey(id)) return
        val window = 20_000L
        val head = runCatching {
            val from = bounds.startMs
            decodeMono(uri, from, from + window)?.let { (pcm, sr, start) ->
                BeatDetect.detect(pcm, sr, start)?.copy(key = KeyDetect.detect(pcm, sr))
            }
        }.getOrNull()
        val tail = runCatching {
            val from = (bounds.endMs - window).coerceAtLeast(bounds.startMs)
            decodeMono(uri, from, bounds.endMs)?.let { (pcm, sr, start) ->
                BeatDetect.detect(pcm, sr, start)?.copy(key = KeyDetect.detect(pcm, sr))
            }
        }.getOrNull()
        cache[id] = TrackBeats(head?.takeIf { it.confidence > 1.25 }, tail?.takeIf { it.confidence > 1.25 })
        save()
    }

    @Synchronized
    private fun save() {
        runCatching {
            val o = JSONObject()
            cache.forEach { (k, v) ->
                val a = JSONArray()
                listOf(v.head, v.tail).forEach { b ->
                    a.put(b?.bpm ?: 0.0); a.put(b?.beatMs ?: 0L); a.put(b?.confidence ?: 0.0)
                    a.put(b?.phraseMs ?: 0L); a.put(b?.key ?: -1)
                }
                o.put(k, a)
            }
            cacheFile.writeText(o.toString())
        }
    }

    private fun read(a: JSONArray, i: Int): BeatInfo? {
        val bpm = a.optDouble(i, 0.0)
        return if (bpm <= 0.0) null else BeatInfo(bpm, a.optLong(i + 1), a.optDouble(i + 2), a.optLong(i + 3), a.optInt(i + 4, -1))
    }

    /** PCM mono em float entre [fromMs] e [toMs], a taxa de amostragem e o tempo (ms) da primeira amostra. */
    private fun decodeMono(uri: Uri, fromMs: Long, toMs: Long): Triple<FloatArray, Int, Long>? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var out = FloatArray(0)
        var count = 0
        var sampleRate = 44_100
        var firstPts = -1L
        try {
            ex.setDataSource(context, uri, null)
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { ex.selectTrack(i); fmt = f; break }
            }
            if (fmt == null) return null
            sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            out = FloatArray(((toMs - fromMs) / 1000.0 * sampleRate).toInt() + sampleRate)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + 15_000
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
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIdx >= 0) {
                    val pts = info.presentationTimeUs / 1000
                    if (info.size > 0 && pts >= fromMs && pts <= toMs) {
                        if (firstPts < 0) firstPts = pts
                        val buf = codec.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val b = buf.slice().order(ByteOrder.nativeOrder())
                        val ch = channels.coerceAtLeast(1)
                        if (floatPcm) {
                            val fb = b.asFloatBuffer()
                            while (fb.remaining() >= ch && count < out.size) {
                                var s = 0f
                                repeat(ch) { s += fb.get() }
                                out[count++] = s / ch
                            }
                        } else {
                            val sb = b.asShortBuffer()
                            while (sb.remaining() >= ch && count < out.size) {
                                var s = 0f
                                repeat(ch) { s += sb.get() / 32768f }
                                out[count++] = s / ch
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || count >= out.size) break
                }
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex.release() }
        }
        return if (count < sampleRate * 6) null else Triple(out.copyOf(count), sampleRate, firstPts.coerceAtLeast(fromMs))
    }
}

/**
 * Detecção do tom musical (Krumhansl-Schmuckler): mede quanto de cada nota (dó, ré…) aparece
 * e compara com o "perfil" típico de cada tom maior e menor.
 */
object KeyDetect {
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(key: Int): String = when {
        key < 0 -> "?"
        key < 12 -> NAMES[key]
        else -> NAMES[key - 12] + "m"
    }

    /** Número da roda Camelot (1–12) e se é menor; usado para saber se dois tons combinam. */
    fun camelot(key: Int): Pair<Int, Boolean>? {
        if (key < 0) return null
        val minor = key >= 12
        val majorPc = if (minor) (key - 12 + 3) % 12 else key // menor usa o maior relativo
        val num = ((majorPc * 7) % 12 + 7) % 12 + 1
        return num to minor
    }

    /** Tons vizinhos na roda Camelot soam bem juntos. Desconhecido conta como compatível. */
    fun compatible(a: Int, b: Int): Boolean {
        val ca = camelot(a) ?: return true
        val cb = camelot(b) ?: return true
        if (ca.first == cb.first) return true
        if (ca.second != cb.second) return false
        val d = abs(ca.first - cb.first)
        return d == 1 || d == 11
    }

    fun detect(mono: FloatArray, sampleRate: Int): Int {
        val size = 8192
        if (mono.size < size * 4) return -1
        val chroma = DoubleArray(12)
        val re = DoubleArray(size)
        val im = DoubleArray(size)
        val window = DoubleArray(size) { 0.5 - 0.5 * kotlin.math.cos(2 * PI * it / (size - 1)) }
        // pré-calcula a nota de cada faixa de frequência útil (130 Hz a 2 kHz)
        val binPc = IntArray(size / 2) { b ->
            val f = b.toDouble() * sampleRate / size
            if (f < 130 || f > 2000) -1 else (((12 * ln(f / 440.0) / ln(2.0)).roundToInt() + 69) % 12 + 12) % 12
        }
        var start = 0
        while (start + size <= mono.size) {
            for (i in 0 until size) { re[i] = mono[start + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            for (b in 1 until size / 2) {
                val pc = binPc[b]
                if (pc >= 0) chroma[pc] += kotlin.math.sqrt(re[b] * re[b] + im[b] * im[b])
            }
            start += size
        }
        if (chroma.sum() <= 0) return -1
        var best = -1
        var bestR = -2.0
        var second = -2.0
        for (tonic in 0 until 12) {
            for ((profile, offset) in listOf(MAJOR to 0, MINOR to 12)) {
                val r = corr(chroma, DoubleArray(12) { profile[(it - tonic + 12) % 12] })
                if (r > bestR) { second = bestR; bestR = r; best = tonic + offset } else if (r > second) second = r
            }
        }
        // se o melhor tom quase empata com outro, não dá para confiar
        return if (bestR < 0.5 || bestR - second < 0.02) -1 else best
    }

    private fun corr(x: DoubleArray, y: DoubleArray): Double {
        val mx = x.average(); val my = y.average()
        var num = 0.0; var dx = 0.0; var dy = 0.0
        for (i in x.indices) {
            val a = x[i] - mx; val b = y[i] - my
            num += a * b; dx += a * a; dy += b * b
        }
        return if (dx <= 0 || dy <= 0) 0.0 else num / kotlin.math.sqrt(dx * dy)
    }

    /** FFT radix-2 no próprio vetor (tamanho potência de 2). */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
