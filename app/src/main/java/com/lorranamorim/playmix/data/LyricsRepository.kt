package com.lorranamorim.playmix.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String?,
) {
    val isSynced get() = synced.isNotEmpty()
    val isEmpty get() = synced.isEmpty() && plain.isNullOrBlank()
}

/**
 * Baixa letras do LRCLIB (serviço aberto e gratuito, sem chave) e guarda no aparelho.
 */
class LyricsRepository(context: Context) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun get(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val cache = File(dir, "${track.id}.json")
        if (cache.exists()) {
            return@withContext runCatching { parseCached(JSONObject(cache.readText())) }.getOrNull()
        }
        val found = fetch(track)
        // guarda até o "não achou" para não ficar buscando de novo toda vez
        runCatching {
            cache.writeText(JSONObject().apply {
                put("synced", found?.syncedRaw ?: JSONObject.NULL)
                put("plain", found?.plain ?: JSONObject.NULL)
            }.toString())
        }
        found?.let { Lyrics(parseLrc(it.syncedRaw), it.plain) }
    }

    fun clearCache(trackId: Long) {
        File(dir, "$trackId.json").delete()
    }

    private data class Raw(val syncedRaw: String?, val plain: String?)

    private fun fetch(t: Track): Raw? {
        val title = cleanTitle(t.title)
        val artist = t.artist.takeUnless { it == "Artista desconhecido" } ?: ""
        val params = buildString {
            append("track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist))
            if (t.album != "Sem álbum") append("&album_name=").append(enc(t.album))
            append("&duration=").append(t.durationMs / 1000)
        }
        httpGet("https://lrclib.net/api/get?$params")?.let { body ->
            val o = runCatching { JSONObject(body) }.getOrNull()
            if (o != null && (o.has("syncedLyrics") || o.has("plainLyrics"))) {
                val raw = Raw(o.optStringOrNull("syncedLyrics"), o.optStringOrNull("plainLyrics"))
                if (raw.syncedRaw != null || raw.plain != null) return raw
            }
        }
        // busca mais solta quando a exata não acha
        val q = enc("$artist $title".trim())
        httpGet("https://lrclib.net/api/search?q=$q")?.let { body ->
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
            var best: JSONObject? = null
            var bestDiff = Long.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val diff = kotlin.math.abs(o.optLong("duration") * 1000 - t.durationMs)
                val hasSynced = o.optStringOrNull("syncedLyrics") != null
                val score = diff - if (hasSynced) 5000 else 0
                if (score < bestDiff && diff < 15000) { best = o; bestDiff = score }
            }
            best?.let { return Raw(it.optStringOrNull("syncedLyrics"), it.optStringOrNull("plainLyrics")) }
        }
        return null
    }

    private fun httpGet(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "PlayMix/2.0 (Android)")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun parseCached(o: JSONObject): Lyrics? {
        val synced = o.optStringOrNull("synced")
        val plain = o.optStringOrNull("plain")
        if (synced == null && plain == null) return null
        return Lyrics(parseLrc(synced), plain)
    }

    companion object {
        private val timeTag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        fun parseLrc(raw: String?): List<LyricLine> {
            if (raw.isNullOrBlank()) return emptyList()
            val out = ArrayList<LyricLine>()
            raw.lineSequence().forEach { line ->
                val tags = timeTag.findAll(line).toList()
                if (tags.isEmpty()) return@forEach
                val text = line.substring(tags.last().range.last + 1).trim()
                tags.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val fracStr = m.groupValues[3]
                    val frac = when (fracStr.length) {
                        0 -> 0L
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        else -> fracStr.take(3).toLong()
                    }
                    out += LyricLine(min * 60_000 + sec * 1000 + frac, text)
                }
            }
            return out.sortedBy { it.timeMs }
        }

        /** Tira "(Ao Vivo)", "- Remastered" etc. que atrapalham a busca. */
        fun cleanTitle(t: String): String =
            t.replace(Regex("""\s*[\(\[].*?(remaster|ao vivo|live|feat|ft\.|version|versão|edit).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+-\s+.*(remaster|ao vivo|live|version|versão).*$""", RegexOption.IGNORE_CASE), "")
                .trim()

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
