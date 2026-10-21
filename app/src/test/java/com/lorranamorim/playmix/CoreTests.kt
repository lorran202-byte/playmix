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
}
