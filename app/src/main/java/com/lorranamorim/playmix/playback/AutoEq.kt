package com.lorranamorim.playmix.playback

import android.media.audiofx.Equalizer
import androidx.media3.common.C
import com.lorranamorim.playmix.data.LibraryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import kotlin.math.ln

/** Curva de equalização: ganho em dB em algumas frequências (Hz). */
data class EqPreset(val name: String, val points: List<Pair<Float, Float>>) {
    /** Ganho numa frequência qualquer, interpolando em escala logarítmica. */
    fun gainAt(hz: Float): Float {
        if (hz <= points.first().first) return points.first().second
        if (hz >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (f1, g1) = points[i]
            val (f2, g2) = points[i + 1]
            if (hz in f1..f2) {
                val t = (ln(hz) - ln(f1)) / (ln(f2) - ln(f1))
                return g1 + (g2 - g1) * t
            }
        }
        return 0f
    }
}

private fun preset(name: String, g60: Float, g230: Float, g910: Float, g3k6: Float, g14k: Float) =
    EqPreset(name, listOf(60f to g60, 230f to g230, 910f to g910, 3600f to g3k6, 14000f to g14k))

/**
 * Curvas por gênero. Ganhos moderados (máx. ~5 dB) para realçar sem distorcer.
 * A ordem importa: o primeiro que bater com o gênero da música vence.
 */
object EqPresets {
    val balanced = preset("Equilibrado", 2f, 0.5f, 0f, 0.5f, 1.5f)

    private val table: List<Pair<List<String>, EqPreset>> = listOf(
        listOf("funk", "brega funk", "mandelao") to preset("Funk", 5.5f, 3.5f, -0.5f, 1.5f, 2.5f),
        listOf("trap", "hip hop", "hip-hop", "hiphop", "rap", "drill") to preset("Hip-Hop", 5f, 3.5f, 0f, 1f, 2f),
        listOf("metal", "hardcore", "metalcore") to preset("Metal", 4f, 1.5f, -2f, 3f, 4f),
        listOf("rock", "punk", "grunge", "indie", "alternative", "alternativo") to preset("Rock", 4f, 2f, -1.5f, 2.5f, 4f),
        listOf("electro", "eletro", "edm", "house", "techno", "trance", "dance", "dubstep", "eletronica", "electronic", "drum") to preset("Eletrônica", 5f, 3f, 0f, 1.5f, 3.5f),
        listOf("reggaeton", "latin", "latina", "dembow") to preset("Latina", 5f, 3f, 0f, 1.5f, 3f),
        listOf("piseiro", "pisadinha", "forro", "arrocha", "xote", "baiao", "vaquejada") to preset("Forró", 3.5f, 2f, 1f, 2.5f, 2.5f),
        listOf("sertanejo", "country", "modao", "universitario") to preset("Sertanejo", 3f, 1.5f, 1.5f, 2.5f, 2f),
        listOf("pagode", "samba", "axe", "swingueira", "pagodao") to preset("Pagode", 3f, 2f, 1f, 2f, 2.5f),
        listOf("r&b", "rnb", "soul", "neo soul") to preset("R&B", 4f, 2.5f, 0f, 1.5f, 2.5f),
        listOf("reggae", "dub", "ska") to preset("Reggae", 4f, 1.5f, 0f, 1.5f, 2f),
        listOf("gospel", "worship", "louvor", "christian", "crista") to preset("Gospel", 2f, 1.5f, 2f, 2.5f, 2f),
        listOf("mpb", "bossa", "nova mpb") to preset("MPB", 1f, 1f, 1.5f, 2f, 2.5f),
        listOf("jazz", "blues") to preset("Jazz", 2.5f, 1f, 1f, 2f, 3f),
        listOf("classic", "classica", "classical", "orchestra", "orquestra", "instrumental", "piano") to preset("Clássica", 0f, 0f, 0f, 1.5f, 3f),
        listOf("acoustic", "acustico", "folk", "voz e violao", "unplugged") to preset("Acústico", 1.5f, 1f, 1.5f, 2.5f, 2f),
        listOf("pop", "k-pop", "kpop") to preset("Pop", 1f, 2f, 2.5f, 1.5f, 2f),
    )

    fun forGenre(genre: String?): EqPreset {
        if (genre.isNullOrBlank()) return balanced
        val g = Normalizer.normalize(genre.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return table.firstOrNull { (keys, _) -> keys.any { g.contains(it) } }?.second ?: balanced
    }
}

/**
 * Equalizador que se ajusta sozinho ao gênero da música que está tocando.
 * Fica preso à sessão de áudio do player do PlayMix.
 */
class AutoEq(private val store: LibraryStore) {

    private var eq: Equalizer? = null
    private var currentGenre: String? = null
    private val _preset = MutableStateFlow<String?>(null)
    /** Nome da curva em uso (null = desligado). */
    val preset: StateFlow<String?> = _preset.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        reapply()
    }

    fun apply(genre: String?) {
        currentGenre = genre
        reapply()
    }

    fun reapply() {
        val e = eq ?: run { _preset.value = null; return }
        if (!store.data.value.settings.autoEq) {
            runCatching { e.enabled = false }
            _preset.value = null
            return
        }
        val p = EqPresets.forGenre(currentGenre)
        runCatching {
            val range = e.bandLevelRange
            for (b in 0 until e.numberOfBands) {
                val band = b.toShort()
                val hz = e.getCenterFreq(band) / 1000f
                val mb = (p.gainAt(hz) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(band, mb.toShort())
            }
            e.enabled = true
            _preset.value = p.name
        }.onFailure { _preset.value = null }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
    }
}
