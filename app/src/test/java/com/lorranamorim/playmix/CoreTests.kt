package com.lorranamorim.playmix

import com.lorranamorim.playmix.data.LyricsRepository
import com.lorranamorim.playmix.playback.Shuffler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoreTests {

    private data class S(val id: Int, val artist: String)

    @Test
    fun shuffleKeepsEverySongOnce() {
        val songs = (1..500).map { S(it, "Artista ${it % 20}") }
        val out = Shuffler.shuffle(songs, { it.artist }, Random(1))
        assertEquals(songs.size, out.size)
        assertEquals(songs.toSet(), out.toSet())
    }

    @Test
    fun shuffleGivesNewOrderEachTime() {
        val songs = (1..300).map { S(it, "Artista ${it % 15}") }
        val a = Shuffler.shuffle(songs, { it.artist }, Random(10))
        val b = Shuffler.shuffle(songs, { it.artist }, Random(11))
        assertNotEquals(a, b)
        // as 30 primeiras não podem ser quase as mesmas (problema relatado no app de referência)
        val overlap = a.take(30).toSet().intersect(b.take(30).toSet()).size
        assertTrue("sobreposição alta: $overlap", overlap < 15)
    }

    @Test
    fun shuffleSpreadsArtists() {
        // 10 artistas com 10 músicas cada: quase nunca o mesmo artista em sequência
        val songs = (0 until 100).map { S(it, "A${it / 10}") }
        var repeats = 0
        repeat(50) { seed ->
            val out = Shuffler.shuffle(songs, { it.artist }, Random(seed))
            repeats += out.zipWithNext().count { (x, y) -> x.artist == y.artist }
        }
        // aleatório puro daria ~9 por embaralhamento (450 no total)
        assertTrue("repetições de artista: $repeats", repeats < 150)
    }

    @Test
    fun parsesSyncedLyrics() {
        val lrc = """
            [00:01.50] Primeira linha
            [00:05.20][01:10.00] Refrão
            [ar: alguém]
            [00:09.123] Terceira
        """.trimIndent()
        val lines = LyricsRepository.parseLrc(lrc)
        assertEquals(4, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Refrão", lines[1].text)
        assertEquals(9123L, lines[2].timeMs)
        assertEquals(70000L, lines[3].timeMs)
    }

    @Test
    fun cleansTitlesForLyricsSearch() {
        assertEquals("Evidências", LyricsRepository.cleanTitle("Evidências (Ao Vivo)"))
        assertEquals("Song", LyricsRepository.cleanTitle("Song - Remastered 2011"))
        assertEquals("Normal", LyricsRepository.cleanTitle("Normal"))
    }

    @Test
    fun picksEqByGenre() {
        val eq = com.lorranamorim.playmix.playback.EqPresets
        assertEquals("Sertanejo", eq.forGenre("Sertanejo Universitário").name)
        assertEquals("Funk", eq.forGenre("Funk Carioca").name)
        assertEquals("Forró", eq.forGenre("Forró / Piseiro").name)
        assertEquals("Rock", eq.forGenre("Pop Rock").name)
        assertEquals("Eletrônica", eq.forGenre("Dance").name)
        assertEquals("Pop", eq.forGenre("Pop").name)
        assertEquals("Equilibrado", eq.forGenre(null).name)
        assertEquals("Equilibrado", eq.forGenre("Desconhecido").name)
    }

    @Test
    fun eqCurveInterpolates() {
        val p = com.lorranamorim.playmix.playback.EqPresets.forGenre("funk")
        assertEquals(5.5f, p.gainAt(30f), 0.01f)
        assertEquals(2.5f, p.gainAt(16000f), 0.01f)
        val mid = p.gainAt(120f)
        assertTrue(mid < 5.5f && mid > 3.5f)
    }

    @Test
    fun splitsFeaturedArtistsButKeepsDuos() {
        val split = { s: String -> com.lorranamorim.playmix.data.splitArtists(s) }
        assertEquals(listOf("Rihanna", "Calvin Harris"), split("Rihanna, Calvin Harris"))
        assertEquals(listOf("Calvin Harris", "John Newman"), split("Calvin Harris feat. John Newman"))
        assertEquals(listOf("Henrique & Juliano"), split("Henrique & Juliano"))
        assertEquals(listOf("Diego e Arnaldo"), split("Diego e Arnaldo"))
        assertEquals(listOf("Alok", "Zeeba"), split("Alok x Zeeba"))
    }

    // ---------- batidas ----------

    /** Áudio de teste: "bumbos" curtos (seno grave com decaimento) num andamento fixo, com um chiado de fundo. */
    private fun clicks(bpm: Double, seconds: Int, firstBeatMs: Long, sr: Int = 44100): FloatArray {
        val out = FloatArray(sr * seconds)
        val rnd = java.util.Random(7)
        for (i in out.indices) out[i] = (rnd.nextGaussian() * 0.01).toFloat()
        val period = 60.0 / bpm
        var t = firstBeatMs / 1000.0
        while (t < seconds) {
            val start = (t * sr).toInt()
            for (j in 0 until (sr * 0.08).toInt()) {
                val idx = start + j
                if (idx >= out.size) break
                val env = Math.exp(-j / (sr * 0.02))
                out[idx] += (0.8 * env * Math.sin(2 * Math.PI * 60.0 * j / sr)).toFloat()
            }
            t += period
        }
        return out
    }

    @Test
    fun detectsTempoAndBeatPosition() {
        for ((bpm, first) in listOf(128.0 to 230L, 95.0 to 410L, 140.0 to 90L, 110.0 to 600L)) {
            val info = com.lorranamorim.playmix.playback.BeatDetect.detect(clicks(bpm, 20, first), 44100, 10_000L)
            assertTrue("não detectou $bpm", info != null)
            info!!
            assertEquals("bpm de $bpm", bpm, com.lorranamorim.playmix.playback.BeatDetect.normalize(info.bpm), 1.5)
            // a batida encontrada tem que cair numa batida real (com 25 ms de tolerância)
            val period = 60_000.0 / bpm
            val off = ((info.beatMs - 10_000L - first) % period + period) % period
            assertTrue("fase de $bpm errada: $off ms", off < 25 || off > period - 25)
        }
    }

    @Test
    fun beatmatchSpeedAndAlignment() {
        val bd = com.lorranamorim.playmix.playback.BeatDetect
        assertEquals(1.0 + 4.0 / 124.0, bd.speedFor(124.0, 128.0)!!, 0.001)
        assertEquals(1.0, bd.speedFor(64.0, 128.0)!!, 0.001) // meio tempo é o mesmo pulso
        assertTrue(bd.speedFor(100.0, 128.0) == null) // diferença grande demais
        val out = com.lorranamorim.playmix.playback.BeatInfo(120.0, 1000L, 2.0) // batidas a cada 500 ms
        val inc = com.lorranamorim.playmix.playback.BeatInfo(120.0, 200L, 2.0)
        // a que sai está 100 ms depois de uma batida; a que entra, 300 ms depois: precisa voltar 200 ms
        assertEquals(-200L, bd.alignCorrectionMs(60_100L, out, 5_500L, inc))
        assertEquals(0L, bd.alignCorrectionMs(60_000L, out, 5_200L, inc))
    }

    // ---------- tom musical ----------

    private fun chords(freqs: List<List<Double>>, secondsEach: Double, total: Int, sr: Int = 22050): FloatArray {
        val out = FloatArray(sr * total)
        for (i in out.indices) {
            val t = i.toDouble() / sr
            val chord = freqs[((t / secondsEach).toInt()) % freqs.size]
            var v = 0.0
            for (f in chord) {
                v += Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(2 * Math.PI * 2 * f * t)
            }
            out[i] = (v * 0.15).toFloat()
        }
        return out
    }

    private fun hz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    @Test
    fun detectsMajorKey() {
        // C – F – G – C (dó maior), notas em várias oitavas
        val c = listOf(hz(60), hz(64), hz(67), hz(48))
        val f = listOf(hz(65), hz(69), hz(72), hz(53))
        val g = listOf(hz(67), hz(71), hz(74), hz(55))
        val key = com.lorranamorim.playmix.playback.KeyDetect.detect(chords(listOf(c, f, g, c), 1.0, 20), 22050)
        assertEquals("tom detectado: ${com.lorranamorim.playmix.playback.KeyDetect.name(key)}", 0, key)
    }

    @Test
    fun camelotCompatibility() {
        val kd = com.lorranamorim.playmix.playback.KeyDetect
        assertEquals(8 to false, kd.camelot(0)) // C = 8B
        assertEquals(8 to true, kd.camelot(21)) // Am = 8A
        assertTrue(kd.compatible(0, 7)) // C e G (vizinhos)
        assertTrue(kd.compatible(0, 21)) // C e Am (relativos)
        assertTrue(!kd.compatible(0, 6)) // C e F# (opostos)
        assertTrue(kd.compatible(-1, 6)) // desconhecido não bloqueia
    }
}
